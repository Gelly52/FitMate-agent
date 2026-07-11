package com.itgeo.fitmate.api.agent.application.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.agent.application.AgentAsyncService;
import com.itgeo.fitmate.api.agent.application.AgentRunService;
import com.itgeo.fitmate.api.agent.core.AgentCancellationRegistry;
import com.itgeo.fitmate.api.agent.core.AgentCancelledException;
import com.itgeo.fitmate.api.agent.core.AgentLoopExecutor;
import com.itgeo.fitmate.api.agent.dto.AgentExecuteContext;
import com.itgeo.fitmate.api.agent.dto.AgentFinishResponse;
import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.chat.application.ChatSessionService;
import com.itgeo.fitmate.api.sse.domain.SSEMsgType;
import com.itgeo.fitmate.api.sse.infrastructure.SSEServer;
import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Agent 异步执行壳。
 * <p>
 * 本类只负责异步生命周期、run 状态、失败兜底和 Redis 锁续期/释放；
 * 具体 LLM 决策、工具调用和动态 trace 由 {@link AgentLoopExecutor} 负责。
 */
@Service
@Slf4j
public class AgentAsyncServiceImpl implements AgentAsyncService {

    private static final long AGENT_LOCK_TTL_SECONDS = 120L;
    private static final long AGENT_LOCK_RENEW_INTERVAL_SECONDS = 30L;
    private static final DefaultRedisScript<Long> COMPARE_AND_EXPIRE_SCRIPT;
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT;

    static {
        COMPARE_AND_EXPIRE_SCRIPT = new DefaultRedisScript<>();
        COMPARE_AND_EXPIRE_SCRIPT.setResultType(Long.class);
        COMPARE_AND_EXPIRE_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "  return redis.call('expire', KEYS[1], ARGV[2]) " +
                        "else " +
                        "  return 0 " +
                        "end"
        );

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
    private AgentRunService agentRunService;

    @Resource
    private AgentLoopExecutor agentLoopExecutor;

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AgentCancellationRegistry cancellationRegistry;

    @Async("agentTaskExecutor")
    @Override
    public void executeAsync(AgentExecuteContext context) {
        ScheduledExecutorService renewExecutor = null;
        ScheduledFuture<?> renewFuture = null;
        if (StrUtil.isNotBlank(context.getLockKey()) && StrUtil.isNotBlank(context.getLockOwner())) {
            renewExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "agent-lock-renew-" + context.getRunId());
                thread.setDaemon(true);
                return thread;
            });
            renewFuture = renewExecutor.scheduleAtFixedRate(
                    () -> renewLockQuietly(context),
                    AGENT_LOCK_RENEW_INTERVAL_SECONDS,
                    AGENT_LOCK_RENEW_INTERVAL_SECONDS,
                    TimeUnit.SECONDS
            );
        }

        try {
            // 把受理阶段捕获的登录上下文绑定到当前异步线程，供下游 ReasoningChatClient / ContextCompressService
            // 通过 UserContextHolder 读取到用户态，从而命中用户自定义 LLM 配置和 KV cache。
            UserContextHolder.set(context.getAuthenticatedUser());
            agentRunService.markRunRunning(context.getRunId());
            agentLoopExecutor.run(context);
        } catch (AgentCancelledException e) {
            log.info("Agent execution cancelled by user, runId={}", context.getRunId());
            // FINISH 之前 flush 残留 chunk，避免最后一批 thinking/content 丢失
            agentLoopExecutor.flushSseBuffers(context);
            handleCancellation(context, e.getPartialContent());
        } catch (Exception e) {
            log.error("Agent async execution failed, runId={}", context.getRunId(), e);
            // FINISH 之前 flush 残留 chunk，避免最后一批 thinking/content 丢失
            agentLoopExecutor.flushSseBuffers(context);
            String failedMessage = "Task failed: " + (e.getMessage() == null ? "Unknown error" : e.getMessage());
            agentRunService.markRunFailed(context.getRunId(), failedMessage);
            sendFailureFinish(context, failedMessage);
        } finally {
            stopRenewTask(renewFuture, renewExecutor);
            releaseLock(context.getLockKey(), context.getLockOwner());
            cancellationRegistry.unregister(context.getRunId());
            UserContextHolder.clear();
        }
    }

    private void renewLockQuietly(AgentExecuteContext context) {
        try {
            boolean renewed = renewLock(context.getLockKey(), context.getLockOwner(), AGENT_LOCK_TTL_SECONDS);
            if (!renewed) {
                log.warn("Agent lock renewal failed, runId={}, lockKey={}", context.getRunId(), context.getLockKey());
            }
        } catch (Exception ex) {
            log.warn("Agent lock renewal exception, runId={}, lockKey={}", context.getRunId(), context.getLockKey(), ex);
        }
    }

    private void sendFailureFinish(AgentExecuteContext context, String failedMessage) {
        try {
            chatSessionService.finishAssistantMessage(context.getAssistantMessageId(), failedMessage, null);
        } catch (Exception ex) {
            log.warn("Failed to backfill failure message, runId={}", context.getRunId(), ex);
        }

        AgentFinishResponse failed = new AgentFinishResponse(
                failedMessage,
                context.getChatEntity() == null ? null : context.getChatEntity().getBotMsgId(),
                context.getRunId(),
                "failed",
                null,
                context.getChatSessionId(),
                context.getChatEntity() == null ? null : context.getChatEntity().getSessionCode(),
                context.getAccumulatedUsage()
        );
        SSEServer.sendMsg(
                context.getAuthenticatedUser().getSseClientId(),
                JSONUtil.toJsonStr(failed),
                SSEMsgType.FINISH
        );
    }

    private void handleCancellation(AgentExecuteContext context, String partialContent) {
        // 1. 回填 assistant 消息：部分内容 + "已中断"标注
        String displayContent = (partialContent == null ? "" : partialContent);
        if (displayContent.isBlank()) {
            displayContent = "> ⚠️ **Stopped** — Generation cancelled by user.";
        } else {
            displayContent = displayContent + "\n\n> ⚠️ **Stopped** — Generation cancelled by user.";
        }
        try {
            chatSessionService.finishAssistantMessage(
                    context.getAssistantMessageId(),
                    displayContent,
                    null
            );
        } catch (Exception ex) {
            log.warn("Failed to backfill interrupted message, runId={}", context.getRunId(), ex);
        }

        // 2. 标记 run 为 cancelled
        agentRunService.markRunCancelled(context.getRunId(), "User cancelled");

        // 3. 推送 interrupted FINISH 事件给前端
        AgentFinishResponse interrupted = new AgentFinishResponse(
                displayContent,
                context.getChatEntity() == null ? null : context.getChatEntity().getBotMsgId(),
                context.getRunId(),
                "interrupted",
                null,
                context.getChatSessionId(),
                context.getChatEntity() == null ? null : context.getChatEntity().getSessionCode(),
                context.getAccumulatedUsage()
        );
        SSEServer.sendMsg(
                context.getAuthenticatedUser().getSseClientId(),
                JSONUtil.toJsonStr(interrupted),
                SSEMsgType.FINISH
        );
    }

    private boolean renewLock(String lockKey, String lockOwner, long ttlSeconds) {
        if (StrUtil.isBlank(lockKey) || StrUtil.isBlank(lockOwner)) {
            return false;
        }
        Long result = stringRedisTemplate.execute(
                COMPARE_AND_EXPIRE_SCRIPT,
                Collections.singletonList(lockKey),
                lockOwner,
                String.valueOf(ttlSeconds)
        );
        return result != null && result > 0;
    }

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

    private void stopRenewTask(ScheduledFuture<?> renewFuture, ScheduledExecutorService renewExecutor) {
        if (renewFuture != null) {
            renewFuture.cancel(true);
        }
        if (renewExecutor != null) {
            renewExecutor.shutdownNow();
        }
    }
}
