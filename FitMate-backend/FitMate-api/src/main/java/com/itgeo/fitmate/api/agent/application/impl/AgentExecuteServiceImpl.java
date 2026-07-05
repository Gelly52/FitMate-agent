package com.itgeo.fitmate.api.agent.application.impl;

import cn.hutool.core.util.StrUtil;
import com.itgeo.fitmate.api.agent.application.AgentAsyncService;
import com.itgeo.fitmate.api.agent.application.AgentExecuteService;
import com.itgeo.fitmate.api.agent.application.AgentRunService;
import com.itgeo.fitmate.api.agent.dto.AgentExecuteAckResponse;
import com.itgeo.fitmate.api.agent.dto.AgentExecuteContext;
import com.itgeo.fitmate.api.agent.infrastructure.entity.AgentRun;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.chat.application.ChatSessionService;
import com.itgeo.fitmate.api.chat.dto.ChatEntity;
import com.itgeo.fitmate.api.chat.dto.TokenUsage;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatSession;
import com.itgeo.fitmate.api.sse.infrastructure.SSEServer;
import com.itgeo.fitmate.common.constant.RedisKeyConstants;
import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Agent 短事务受理服务实现。
 *
 * 职责：
 * 1. 在同步短事务内完成 /agent/execute 的参数校验与受理建档；
 * 2. 基于 userId + botMsgId 做幂等判断，避免重复创建 run；
 * 3. 以当前登录 sessionId 为维度做并发锁控制，限制同一登录会话并发执行；
 * 4. 在事务内创建或复用会话、补写消息占位并创建 run；
 * 5. 通过 afterCommit 在事务提交后派发异步执行链路。
 *
 * 说明：
 * - 本类只负责“受理”阶段，不负责模型调用、流式输出或运行态推进；
 * - 这里使用的锁维度是登录 sessionId，不是 chatSessionId；
 * - 同步受理阶段若抛异常，需要及时释放本次已获取的 Redis 锁，避免假占用；
 * - 并发上限通过“多槽位锁”实现：同一登录 sessionId 最多允许 MAX_CONCURRENT_AGENTS_PER_SESSION 个任务并行。
 */
@Slf4j
@Service
public class AgentExecuteServiceImpl implements AgentExecuteService {

    private static final long AGENT_LOCK_TTL_SECONDS = 120L;
    private static final String AGENT_LOCK_KEY_PREFIX = RedisKeyConstants.AGENT_LOCK_KEY_PREFIX;
    /**
     * 同一登录 sessionId 允许同时运行的 Agent 任务上限。
     * 通过为每个登录会话维护 N 个独立 slot 锁实现：申请时按序尝试，命中首个空槽即占用。
     */
    private static final int MAX_CONCURRENT_AGENTS_PER_SESSION = RedisKeyConstants.AGENT_LOCK_SLOT_COUNT;
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT;

    static {
        COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>();
        COMPARE_AND_DELETE_SCRIPT.setResultType(Long.class);
        COMPARE_AND_DELETE_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "  return redis.call('del', KEYS[1]) " +
                        "else " +
                        "  return 0 " +
                        "end"
        );
    }

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private AgentRunService agentRunService;

    @Resource
    private AgentAsyncService agentAsyncService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

/**
     * 受理一条 Agent 执行请求，并在事务提交后异步派发执行。
     * <p>
     * 受理步骤：
     * 1. 校验登录态、请求体和 SSE 通道是否满足受理前置条件；
     * 2. 先按 userId + botMsgId 查询既有 run，命中时直接返回幂等 ack；
     * 3. 在短事务内创建或复用 agent 会话，写入用户消息、assistant 占位消息和 run 主记录；
     * 4. 以登录 sessionId 为粒度申请并发锁，避免同一登录会话并发跑多个 Agent 任务；
     * 5. 组装异步执行上下文，并在 afterCommit 中派发到异步线程；
     * 6. 如果同步受理阶段发生异常，负责释放已获取的锁并交由事务整体回滚。
     *
     * @param authenticatedUser 当前登录用户上下文
     * @param chatEntity 聊天请求体
     * @return 受理 ack
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentExecuteAckResponse execute(AuthenticatedUserContext authenticatedUser, ChatEntity chatEntity) {
        validateExecuteRequest(authenticatedUser, chatEntity);

        String sseClientId = authenticatedUser.getSseClientId();
        if (!SSEServer.isConnected(sseClientId)) {
            throw new IllegalArgumentException("SSE通道未建立，请先连接后再发起任务");
        }

        Long userId = authenticatedUser.getUserId();
        String botMsgId = chatEntity.getBotMsgId().trim();

        // 当前阶段后端用户身份只认 token，对 currentUserName 做覆盖，避免信任前端传值。
        chatEntity.setCurrentUserName(authenticatedUser.getUserKey());

        // 步骤1：先做数据库幂等检查；如果同一 userId + botMsgId 已受理，直接返回已有 ack。
        AgentRun existing = agentRunService.findByUserIdAndBotMsgId(userId, botMsgId);
        if (existing != null) {
            ChatSession existingSession = chatSessionService.findByIdAndUserId(
                    existing.getChatSessionId(),
                    userId
            );
            String existingSessionCode = existingSession == null ? null : existingSession.getSessionCode();
            return new AgentExecuteAckResponse(
                    existing.getId(),
                    existing.getChatSessionId(),
                    existingSessionCode,
                    botMsgId,
                    existing.getStatus(),
                    true
            );
        }

        String sourceType = resolveSourceType(chatEntity);
        String lockKey = null;
        String lockOwner = null;
        boolean lockAcquired = false;
        try {
            // 步骤2：在短事务内完成会话、消息、run 建档；若后续加锁失败，整笔受理事务回滚。
            ChatSession session = chatSessionService.resolveOrCreateSession(
                    userId,
                    chatEntity.getSessionCode(),
                    "agent",
                    chatEntity.getMessage(),
                    botMsgId
            );
            chatSessionService.appendUserMessage(session.getId(), chatEntity.getMessage(), sourceType);
            Long assistantMessageId = chatSessionService.createAssistantPlaceholder(
                    session.getId(),
                    botMsgId,
                    sourceType
            );

            Long runId = agentRunService.createRun(
                    userId,
                    session.getId(),
                    botMsgId,
                    chatEntity.getMessage()
            );

            // 步骤3：按当前登录 sessionId 维度申请并发锁；限制的是登录会话并发，不是 chatSessionId。
            // 采用多槽位锁：依次尝试 slot 1..N，命中首个空槽即占用；全部占满则拒绝。
            lockOwner = String.valueOf(runId);
            String acquiredLockKey = tryAcquireSlotLock(authenticatedUser.getSessionId(), lockOwner);
            if (acquiredLockKey == null) {
                throw new IllegalArgumentException(
                        "当前并发任务数已达上限（" + MAX_CONCURRENT_AGENTS_PER_SESSION + "），请稍后再试"
                );
            }
            lockKey = acquiredLockKey;
            lockAcquired = true;

            chatEntity.setSessionCode(session.getSessionCode());

            // 步骤4：组装异步执行上下文，把受理阶段产出的 run / session / 锁信息显式传给异步线程。
            AgentExecuteContext context = new AgentExecuteContext(
                    runId,
                    session.getId(),
                    assistantMessageId,
                    lockKey,
                    lockOwner,
                    authenticatedUser,
                    chatEntity,
                    new TokenUsage(),
                    false,
                    new StringBuilder()
            );

            final Long dispatchedRunId = runId;
            final Long dispatchedAssistantMessageId = assistantMessageId;
            final String dispatchedLockKey = lockKey;
            final String dispatchedLockOwner = lockOwner;

            // 步骤5：通过 afterCommit 在事务提交后派发，避免异步线程读到未提交数据。
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        agentAsyncService.executeAsync(context);
                    } catch (Exception e) {
                        log.error("Agent任务派发失败, runId={}", dispatchedRunId, e);
                        releaseLock(dispatchedLockKey, dispatchedLockOwner);
                        agentRunService.markRunFailed(dispatchedRunId, "Agent任务派发失败");
                        chatSessionService.finishAssistantMessage(
                                dispatchedAssistantMessageId,
                                "任务派发失败，请稍后重试",
                                null
                        );
                    }
                }
            });

            return new AgentExecuteAckResponse(
                    runId,
                    session.getId(),
                    session.getSessionCode(),
                    botMsgId,
                    "pending",
                    false
            );
        } catch (RuntimeException e) {
            // 步骤6：同步受理阶段一旦异常，立即释放已获取的登录 sessionId 维度锁，避免事务回滚后遗留假占用。
            if (lockAcquired) {
                releaseLock(lockKey, lockOwner);
            }
            throw e;
        }
    }

    /**
     * 校验 Agent 执行请求的基础入参。
     */
    private void validateExecuteRequest(AuthenticatedUserContext authenticatedUser, ChatEntity chatEntity) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null || authenticatedUser.getSessionId() == null) {
            throw new IllegalArgumentException("登录状态已失效，请重新登录");
        }
        if (chatEntity == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (StrUtil.isBlank(chatEntity.getMessage())) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        if (StrUtil.isBlank(chatEntity.getBotMsgId())) {
            throw new IllegalArgumentException("botMsgId不能为空");
        }
    }

/**
     * 按序尝试为当前登录 sessionId 申请一个空闲 slot 锁。
     * <p>
     * 遍历 slot 1..N，对每个 slotKey 调用 {@code setIfAbsent}；
     * 命中首个空槽即返回该 slotKey；全部占满返回 null。
     * <p>
     * 说明：
     * - 每个 slot 是独立的 Redis key，拥有独立 TTL，单任务崩溃只会让自己那个槽过期，不影响其他并发；
     * - 申请过程非全局原子，但单 slot 的 setIfAbsent 是原子的，最坏情况是遍历全部 slot 后被拒绝，调用方重试即可。
     *
     * @param sessionId 当前登录 sessionId
     * @param lockOwner 本次 run 持有的 owner 标识（即 runId 字符串）
     * @return 成功占用时返回 slotKey；全部占满时返回 null
     */
    private String tryAcquireSlotLock(Long sessionId, String lockOwner) {
        for (int slot = 1; slot <= MAX_CONCURRENT_AGENTS_PER_SESSION; slot++) {
            String slotKey = buildSlotLockKey(sessionId, slot);
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                    slotKey,
                    lockOwner,
                    AGENT_LOCK_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            if (Boolean.TRUE.equals(locked)) {
                return slotKey;
            }
        }
        return null;
    }

    /**
     * 基于当前登录 sessionId + slot 序号构造并发锁 key。
     * 说明：这里的锁粒度是登录态 sessionId，不是 chatSessionId。
     */
    private String buildSlotLockKey(Long sessionId, int slot) {
        return AGENT_LOCK_KEY_PREFIX + sessionId + ":slot:" + slot;
    }

/**
     * 仅当 owner 匹配时释放登录 sessionId 维度的 Redis 并发锁。
     */
    private void releaseLock(String lockKey, String lockOwner) {
        if (StrUtil.isBlank(lockKey) || StrUtil.isBlank(lockOwner)) {
            return;
        }
        stringRedisTemplate.execute(
                COMPARE_AND_DELETE_SCRIPT,
                Collections.singletonList(lockKey),
                lockOwner
        );
    }

    private String resolveSourceType(ChatEntity chatEntity) {
        boolean ragEnabled = chatEntity != null && Boolean.TRUE.equals(chatEntity.getRagEnabled());
        boolean internetEnabled = chatEntity != null && Boolean.TRUE.equals(chatEntity.getInternetEnabled());

        // Agent 流程下，RAG 与联网都仅作为可选工具由 LLM 自主调用，无需互斥。
        // sourceType 这里仅作为消息元数据用于展示与回溯，按 RAG 优先记录。
        if (ragEnabled) {
            return "rag";
        }
        if (internetEnabled) {
            return "internet";
        }
        return "chat";
    }
}
