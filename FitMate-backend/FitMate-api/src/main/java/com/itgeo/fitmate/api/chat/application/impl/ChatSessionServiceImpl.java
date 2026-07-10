package com.itgeo.fitmate.api.chat.application.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.itgeo.fitmate.api.agent.infrastructure.entity.AgentRun;
import com.itgeo.fitmate.api.agent.infrastructure.entity.AgentStep;
import com.itgeo.fitmate.api.agent.infrastructure.mapper.AgentRunMapper;
import com.itgeo.fitmate.api.agent.infrastructure.mapper.AgentStepMapper;
import com.itgeo.fitmate.api.chat.application.ChatSessionService;
import com.itgeo.fitmate.api.chat.dto.ChatRecordItem;
import com.itgeo.fitmate.api.chat.dto.ChatRecordsResponse;
import com.itgeo.fitmate.api.chat.dto.ChatSessionRecordItem;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatMessage;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatSession;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ContextSummary;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatThinking;
import com.itgeo.fitmate.api.chat.infrastructure.mapper.ChatMessageMapper;
import com.itgeo.fitmate.api.chat.infrastructure.mapper.ChatSessionMapper;
import com.itgeo.fitmate.api.chat.infrastructure.mapper.ContextSummaryMapper;
import com.itgeo.fitmate.api.chat.infrastructure.mapper.ChatThinkingMapper;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 聊天会话与消息持久化服务实现。
 *
 * 职责：
 * 1. 创建或复用聊天会话；
 * 2. 追加用户消息；
 * 3. 创建 assistant 占位消息；
 * 4. 在流式输出完成后回填 assistant 最终内容；
 * 5. 为前端提供聊天历史查询能力。
 *
 * 说明：
 * - 当前实现只负责会话与消息的数据库持久化，不负责大模型调用；
 * - sceneType 当前只区分 agent 与 chat；
 * - sourceType 未传时默认使用 agent；
 * - seqNo 在单会话内按最大值递增；
 * - assistant 占位消息 content 必须写空字符串，不能写 null。
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class ChatSessionServiceImpl implements ChatSessionService {

    private static final String DEFAULT_SCENE_TYPE = "agent";
    private static final String DEFAULT_SOURCE_TYPE = "agent";
    private static final String DEFAULT_MESSAGE_TYPE = "text";
    private static final String USER_ROLE = "user";
    private static final String ASSISTANT_ROLE = "assistant";
    private static final int TITLE_MAX_LENGTH = 50;

    private static final int DEFAULT_QUERY_LIMIT = 20;
    private static final int MAX_QUERY_LIMIT = 50;

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private ContextSummaryMapper contextSummaryMapper;

    @Resource
    private ChatThinkingMapper chatThinkingMapper;

    @Resource
    private AgentRunMapper agentRunMapper;

    @Resource
    private AgentStepMapper agentStepMapper;

    /**
     * 创建默认 agent 场景会话。
     * <p>
     * 该方法只是对 {@link #createSession(Long, String, String, String)} 的便捷封装，
     * 不会在这里追加任何消息记录。
     */
    @Override
    public ChatSession createAgentSession(Long userId, String firstMessage, String botMsgId) {
        return createSession(userId, DEFAULT_SCENE_TYPE, firstMessage, botMsgId);
    }

    @Override
    public Long appendUserMessage(Long sessionId, String content, String sourceType) {
        // 1. 必须先校验会话存在，避免脏写消息
        validateSessionId(sessionId);
        // 2. 用户消息不能为空白
        if (StrUtil.isBlank(content)) {
            throw new IllegalArgumentException("消息内容不能为空");
        }

        // 3. 组装用户消息：role 固定为 user，messageType 固定为 text
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSeqNo(nextSeqNo(sessionId));
        message.setRole(USER_ROLE);
        message.setMessageType(DEFAULT_MESSAGE_TYPE);
        message.setSourceType(normalizeSourceType(sourceType));
        message.setContent(content.trim());

        // 4. 插入数据库后返回消息主键，供后续链路使用
        chatMessageMapper.insert(message);
        return message.getId();
    }

    @Override
    public Long createAssistantPlaceholder(Long sessionId, String botMsgId, String sourceType) {
        // 1. assistant 占位消息必须依附于已存在的会话，且必须有 botMsgId
        validateSessionId(sessionId);
        if (StrUtil.isBlank(botMsgId)) {
            throw new IllegalArgumentException("机器人消息ID不能为空");
        }

        // 2. 创建 assistant 占位消息：
        //    - role 固定为 assistant
        //    - content 先写空字符串
        //    - botMsgId 用于前端消息关联与幂等识别
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSeqNo(nextSeqNo(sessionId));
        message.setRole(ASSISTANT_ROLE);
        message.setMessageType(DEFAULT_MESSAGE_TYPE);
        message.setSourceType(normalizeSourceType(sourceType));
        message.setBotMsgId(botMsgId.trim());
        message.setContent("");

        // 3. 先写入占位消息
        chatMessageMapper.insert(message);

        // 4. 同步刷新会话表里的 lastBotMsgId，便于快速定位最近一条机器人消息
        ChatSession sessionUpdate = new ChatSession();
        sessionUpdate.setId(sessionId);
        sessionUpdate.setLastBotMsgId(botMsgId.trim());
        chatSessionMapper.updateById(sessionUpdate);

        // 5. 返回 assistant 消息主键，后续 finish 阶段通过该主键回填最终内容
        return message.getId();
    }

    @Override
    public void finishAssistantMessage(Long messageId, String content, String sourcesJson) {
        finishAssistantMessage(messageId, content, sourcesJson, null);
    }

    @Override
    public void finishAssistantMessage(Long messageId, String content, String sourcesJson, String usageJson) {
        // 1. messageId 是回填最终 assistant 消息的唯一定位条件
        if (messageId == null) {
            throw new IllegalArgumentException("messageId不能为空");
        }

        // 2. 仅更新最终消息内容、来源 JSON 与 token 用量快照
        ChatMessage update = new ChatMessage();
        update.setId(messageId);
        update.setContent(content == null ? "" : content);
        update.setSourcesJson(StrUtil.isBlank(sourcesJson) ? null : sourcesJson.trim());
        update.setUsageJson(StrUtil.isBlank(usageJson) ? null : usageJson.trim());

        // 3. updateById 会触发 t_chat_message.updated_at 自动刷新
        chatMessageMapper.updateById(update);
    }

    @Override
    public boolean existsByBotMsgId(String botMsgId) {
        // 空 botMsgId 不参与去重判断
        if (StrUtil.isBlank(botMsgId)) {
            return false;
        }

        // 注意：这里查的是消息表，而不是 session.lastBotMsgId，
        // 否则无法覆盖历史消息记录，只能判断“最近一条”。
        ChatMessage existing = chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getBotMsgId, botMsgId.trim())
                        .last("limit 1")
        );
        return existing != null;
    }

    /**
     * 查询当前用户最近更新的会话列表。
     */
    @Override
    public List<ChatSession> listRecentSessions(Long userId, Integer limit) {
        // 1. userId 不能为空
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        // 2. limit 调用私有方法：
        int safeLimit = normalizeQueryLimit(limit);

        // 3. 查询当前用户最近会话
        List<ChatSession> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getUpdatedAt)
                        .last("limit " + safeLimit)
        );
        return sessions;
    }

    /**
     * 查询当前用户指定会话下的全部消息。
     */
    @Override
    public List<ChatMessage> listMessagesBySessionId(Long userId, Long sessionId) {
        // 1.校验 userId
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }

        // 2.校验 sessionId
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId不能为空");
        }

        // 3. 先查询 session 是否存在且属于当前用户
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .last("limit 1")
        );

        // 4. 如果查不到，返回空列表
        if (session == null) {
            return List.of();
        }

        // 5. 查询消息列表
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getSeqNo)
        );
        // 6. 返回消息列表
        return messages;
    }

    @Override
    public List<ChatMessage> listMessagesBySessionIdOnly(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        return chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getSeqNo)
        );
    }

    /**
     * 查询当前用户的聊天历史。
     * <p>
     * 支持两种模式：
     * 1. 传 sessionId 时，只返回指定会话；
     * 2. 不传 sessionId 时，返回最近若干会话及其消息。
     */
    @Override
    public ChatRecordsResponse getChatRecords(Long userId, Long sessionId, Integer limit) {
        // 1. 校验 userId
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        // 2. 创建响应对象
        ChatRecordsResponse response = new ChatRecordsResponse();
        response.setUserId(userId);
        // 3. 如果传了 sessionId，只返回这个会话
        if (sessionId != null) {
            ChatSession session = chatSessionMapper.selectOne(
                    new LambdaQueryWrapper<ChatSession>()
                            .eq(ChatSession::getId, sessionId)
                            .eq(ChatSession::getUserId, userId)
                            .last("limit 1")
            );

            // 3.1 当前用户查不到这个会话，直接返回空
            if (session == null) {
                response.setTotalSessions(0);
                response.setSessions(List.of());
                return response;
            }

            // 3.2 查询这个会话下的消息
            List<ChatMessage> messages = listMessagesBySessionId(userId, sessionId);

            // 3.3 组装单个会话响应
            ChatSessionRecordItem sessionItem = buildSessionRecordItem(session, messages);

            response.setTotalSessions(1);
            response.setSessions(List.of(sessionItem));
            return response;
        }

        // 4. 如果没传 sessionId，查询最近会话列表
        List<ChatSession> sessions = listRecentSessions(userId, limit);

        // 4.1 没有任何会话，返回空
        if (sessions == null || sessions.isEmpty()) {
            response.setTotalSessions(0);
            response.setSessions(List.of());
            return response;
        }

        // 4.2 逐个会话查消息并组装
        List<ChatSessionRecordItem> sessionItems = sessions.stream()
                .map(session -> buildSessionRecordItem(
                        session,
                        listMessagesBySessionId(userId, session.getId())
                ))
                .collect(Collectors.toList());

        // 4.3 返回结果
        response.setTotalSessions(sessionItems.size());
        response.setSessions(sessionItems);
        return response;
    }

    /**
     * 按 userId 与 sessionCode 查询会话。
     */
    @Override
    public ChatSession findByUserIdAndSessionCode(Long userId, String sessionCode) {
        if (userId == null || StrUtil.isBlank(sessionCode)) {
            return null;
        }

        return chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getSessionCode, sessionCode.trim())
                        .last("limit 1")
        );
    }

    /**
     * 创建新的会话记录。
     * <p>
     * 说明：这里只写入会话元数据，不负责写用户消息或 assistant 消息。
     */
    @Override
    public ChatSession createSession(Long userId, String sceneType, String firstMessage, String botMsgId) {
        // 1. 基础参数校验：创建会话时，用户、首条消息、botMsgId 都不能为空
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (StrUtil.isBlank(firstMessage)) {
            throw new IllegalArgumentException("首条消息不能为空");
        }
        if (StrUtil.isBlank(botMsgId)) {
            throw new IllegalArgumentException("botMsgId不能为空");
        }

        // 2. 构建会话对象：sceneType 只允许 agent / chat 两种规范值
        ChatSession session = new ChatSession();
        session.setSessionCode("cs_" + IdUtil.fastSimpleUUID());
        session.setUserId(userId);
        session.setSceneType(normalizeSessionSceneType(sceneType));

        // 3. 使用首条消息生成标题，并记录最近一次机器人消息ID
        session.setTitle(buildTitle(firstMessage));
        session.setLastBotMsgId(botMsgId.trim());

        // 4. 插入数据库并返回带主键的会话对象
        chatSessionMapper.insert(session);
        return session;
    }

    /**
     * 解析可用会话：优先复用，不满足条件时新建。
     * <p>
     * 说明：该方法只决定“返回哪个会话”，不会在内部追加任何消息记录。
     */
    @Override
    public ChatSession resolveOrCreateSession(Long userId, String sessionCode, String sceneType, String firstMessage, String botMsgId) {
        // 1. userId 是解析或创建会话的基础条件
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }

        // 2. 调用方传了 sessionCode 时，优先尝试复用当前用户自己的会话
        if (StrUtil.isNotBlank(sessionCode)) {
            ChatSession existing = findByUserIdAndSessionCode(userId, sessionCode);
            if (existing != null) {
                if (isSceneCompatible(sceneType, existing.getSceneType())) {
                    return existing;
                }
                // 3. 找到旧会话但场景不兼容时，仅新建会话记录，不在这里写消息
                return createSession(userId, sceneType, firstMessage, botMsgId);
            }
        }

        // 4. 未传 sessionCode 或找不到可复用会话时，新建会话记录
        return createSession(userId, sceneType, firstMessage, botMsgId);
    }

    /**
     * 按会话ID与用户ID查询会话归属。
     */
    @Override
    public ChatSession findByIdAndUserId(Long sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            return null;
        }
        return chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .last("limit 1")
        );
    }

    /**
     * 检查当前用户是否已经存在指定 botMsgId 的消息记录。
     * <p>
     * 该查询会先从消息表找 botMsgId，再反查这些消息所属会话是否属于当前用户。
     */
    @Override
    public boolean existsByUserIdAndBotMsgId(Long userId, String botMsgId) {
        if (userId == null || StrUtil.isBlank(botMsgId)) {
            return false;
        }

        List<ChatMessage> candidates = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .select(ChatMessage::getSessionId)
                        .eq(ChatMessage::getBotMsgId, botMsgId.trim())
        );
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }

        List<Long> sessionIds = candidates.stream()
                .map(ChatMessage::getSessionId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (sessionIds.isEmpty()) {
            return false;
        }

        ChatSession existingSession = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .select(ChatSession::getId)
                        .eq(ChatSession::getUserId, userId)
                        .in(ChatSession::getId, sessionIds)
                        .last("limit 1")
        );
        return existingSession != null;
    }

    @Override
    public int deleteMessagesFromBotMsgId(Long userId, Long sessionId, String botMsgId) {
        // 1. 校验会话归属
        if (userId == null || sessionId == null || StrUtil.isBlank(botMsgId)) {
            throw new IllegalArgumentException("参数不能为空");
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }

        // 2. 通过 botMsgId 找到 assistant 消息，获取其 seqNo
        ChatMessage assistantMsg = chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getBotMsgId, botMsgId.trim())
                        .last("limit 1")
        );
        if (assistantMsg == null) {
            return 0;
        }
        int assistantSeqNo = assistantMsg.getSeqNo();
        // 用户消息的 seqNo = assistantSeqNo - 1（受理时先写 user 再写 assistant 占位）
        int rollbackFromSeqNo = assistantSeqNo - 1;

        // 3. 删除 seqNo >= rollbackFromSeqNo 的所有消息
        int deletedMessages = chatMessageMapper.delete(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .ge(ChatMessage::getSeqNo, rollbackFromSeqNo)
        );

        // 4. 清理覆盖回滚范围的上下文压缩摘要
        contextSummaryMapper.delete(
                new LambdaQueryWrapper<ContextSummary>()
                        .eq(ContextSummary::getSessionId, sessionId)
                        .ge(ContextSummary::getCompressedToSeq, rollbackFromSeqNo)
        );

        // 5. 更新会话的 lastBotMsgId：找到剩余消息中最后一条 assistant 消息的 botMsgId
        ChatMessage lastAssistant = chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getRole, "assistant")
                        .orderByDesc(ChatMessage::getSeqNo)
                        .last("limit 1")
        );
        String newLastBotMsgId = lastAssistant != null ? lastAssistant.getBotMsgId() : null;
        // 用 LambdaUpdateWrapper 显式 set，避免 newLastBotMsgId 为 null 时
        // updateById 不生成 SET 子句导致 SQL 语法错误
        chatSessionMapper.update(null,
                new LambdaUpdateWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .set(ChatSession::getLastBotMsgId, newLastBotMsgId));

        return deletedMessages;
    }

    private void validateSessionId(Long sessionId) {
        // 1. sessionId 不能为空
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId不能为空");
        }
        // 2. 必须保证会话真实存在，避免向不存在的 session 写消息
        if (chatSessionMapper.selectById(sessionId) == null) {
            throw new IllegalArgumentException("会话不存在");
        }
    }

    /**
     * 计算当前会话的下一条消息序号。
     * 规则：
     * - 如果当前会话还没有任何消息，则从 1 开始；
     * - 否则取当前最大 seqNo + 1。
     * 说明：
     * - 当前实现适用于 Phase 1 串行写消息场景；
     * - 如果后续同一会话出现高并发写入，需要进一步考虑 seqNo 并发控制。
     *
     * @param sessionId 会话ID
     * @return 下一条消息序号
     */
    private Integer nextSeqNo(Long sessionId) {
        // 查询当前会话下 seqNo 最大的一条消息
        ChatMessage lastMessage = chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getSeqNo)
                        .last("limit 1")
        );

        // 如果当前没有历史消息，则从 1 开始；否则在最大序号基础上加 1
        return lastMessage == null || lastMessage.getSeqNo() == null
                ? 1
                : lastMessage.getSeqNo() + 1;
    }

    /**
     * 规范化消息来源类型。
     * 规则：
     * - 如果未传 sourceType，则默认使用 agent；
     * - 如果有值，则去掉首尾空格后返回。
     * @param sourceType 原始来源类型
     * @return 规范化后的来源类型
     */
    private String normalizeSourceType(String sourceType) {
        return StrUtil.isBlank(sourceType) ? DEFAULT_SOURCE_TYPE : sourceType.trim();
    }

    /**
     * 根据首条消息生成会话标题。
     * 规则：
     * - 去掉首尾空格；
     * - 超过最大长度时截断；
     * - 当前用于生成 t_chat_session.title。
     * @param firstMessage 第一条消息内容
     * @return 规范化后的标题
     */
    private String buildTitle(String firstMessage) {
        String normalized = firstMessage.trim();
        return normalized.length() <= TITLE_MAX_LENGTH
                ? normalized
                : normalized.substring(0, TITLE_MAX_LENGTH);
    }

    /**
     * 规范化历史查询条数上限。
     */
    private int normalizeQueryLimit(Integer limit){
        if (limit == null || limit <= 0) {
            return DEFAULT_QUERY_LIMIT;
        }
        return Math.min(limit, MAX_QUERY_LIMIT);
    }

    /**
     * 组装单个会话及其消息列表的前端响应结构。
     * <p>
     * 合并上下文压缩记录为虚拟 system 消息（messageType=summary），按 compressedToSeq 排序定位，
     * 前端识别后渲染为胶囊提示。
     */
    private ChatSessionRecordItem buildSessionRecordItem(ChatSession session, List<ChatMessage> messages){
        ChatSessionRecordItem item = new ChatSessionRecordItem();
        item.setSessionId(session.getId());
        item.setSessionCode(session.getSessionCode());
        item.setSceneType(session.getSceneType());
        item.setTitle(session.getTitle());
        item.setLastBotMsgId(session.getLastBotMsgId());
        item.setCreatedAt(session.getCreatedAt());
        item.setUpdatedAt(session.getUpdatedAt());

        List<ChatRecordItem> items = messages.stream()
                .map(message -> buildChatRecordItem(session, message))
                .collect(Collectors.toCollection(ArrayList::new));

        // 合并压缩记录为虚拟 system 消息
        List<ContextSummary> summaries = contextSummaryMapper.selectList(
                new LambdaQueryWrapper<ContextSummary>()
                        .eq(ContextSummary::getSessionId, session.getId())
                        .orderByAsc(ContextSummary::getCompressedToSeq));
        for (ContextSummary s : summaries) {
            ChatRecordItem summaryItem = new ChatRecordItem();
            summaryItem.setSessionId(session.getId());
            summaryItem.setSessionCode(session.getSessionCode());
            summaryItem.setSceneType(session.getSceneType());
            summaryItem.setRole("system");
            summaryItem.setMessageType("summary");
            summaryItem.setSeqNo(s.getCompressedToSeq());
            summaryItem.setContent(s.getSummaryContent());
            summaryItem.setCreatedAt(s.getCreatedAt());
            // 用 sourcesJson 携带压缩元数据供前端展示
            summaryItem.setSourcesJson(JSONUtil.toJsonStr(new java.util.LinkedHashMap<String, Object>() {{
                put("compressedCount", s.getCompressedMessageCount());
                put("tokenBefore", s.getTokenBefore());
                put("tokenAfter", s.getTokenAfter());
                put("triggerType", s.getTriggerType());
            }}));
            items.add(summaryItem);
        }
        items.sort(Comparator.comparingInt(m -> m.getSeqNo() == null ? 0 : m.getSeqNo()));
        item.setMessages(items);

        return item;
    }

    /**
     * 组装单条聊天消息的前端响应结构。
     */
    private ChatRecordItem buildChatRecordItem(ChatSession session, ChatMessage message){
        ChatRecordItem item = new ChatRecordItem();
        item.setSessionId(session.getId());
        item.setSessionCode(session.getSessionCode());
        item.setSceneType(session.getSceneType());
        item.setMessageId(message.getId());
        item.setSeqNo(message.getSeqNo());
        item.setRole(message.getRole());
        item.setMessageType(message.getMessageType());
        item.setSourceType(message.getSourceType());
        item.setContent(message.getContent());
        item.setBotMsgId(message.getBotMsgId());
        item.setSourcesJson(message.getSourcesJson());
        item.setUsageJson(message.getUsageJson());
        item.setCreatedAt(message.getCreatedAt());
        return item;
    }

    /**
     * 规范化会话场景类型。
     * <p>
     * 当前只显式识别 agent；其余输入统一归一为 chat。
     */
    private String normalizeSessionSceneType(String sceneType) {
        if ("agent".equalsIgnoreCase(sceneType)) {
            return "agent";
        }
        return "chat";
    }

    /**
     * 检查请求场景与现有会话场景是否兼容。
     * <p>
     * 规则：
     * 1. 请求归一为 agent 时，只兼容 agent 会话；
     * 2. 其余请求统一按 chat 处理，只兼容 chat 会话。
     */
    private boolean isSceneCompatible(String requestSceneType, String existingSceneType) {
        String req = normalizeSessionSceneType(requestSceneType);
        String existing = normalizeSessionSceneType(existingSceneType);

        if ("agent".equals(req)) {
            return "agent".equals(existing);
        }
        return "chat".equals(existing);
    }

    @Override
    public void saveThinking(Long messageId, String content) {
        // 1. messageId 不能为空
        if (messageId == null) {
            throw new IllegalArgumentException("messageId不能为空");
        }
        // 2. content 为空时跳过，不保存空 thinking
        if (StrUtil.isBlank(content)) {
            return;
        }

        // 3. 查询是否已存在（按 messageId 唯一）
        ChatThinking existing = chatThinkingMapper.selectOne(
                new LambdaQueryWrapper<ChatThinking>()
                        .eq(ChatThinking::getMessageId, messageId)
                        .last("limit 1")
        );

        if (existing != null) {
            // 3.1 已存在则更新 content
            ChatThinking update = new ChatThinking();
            update.setId(existing.getId());
            update.setContent(content);
            chatThinkingMapper.updateById(update);
        } else {
            // 3.2 不存在则插入新记录
            ChatThinking thinking = new ChatThinking();
            thinking.setMessageId(messageId);
            thinking.setContent(content);
            chatThinkingMapper.insert(thinking);
        }
    }

    @Override
    public String getThinkingByMessageId(Long userId, Long messageId) {
        // 1. userId / messageId 不能为空
        if (userId == null || messageId == null) {
            return null;
        }
        // 2. 按 messageId 查询消息，定位所属 session
        ChatMessage message = chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getId, messageId)
                        .last("limit 1")
        );
        if (message == null) {
            return null;
        }
        // 3. 校验 session 归属当前用户，防止 IDOR 越权
        ChatSession session = chatSessionMapper.selectById(message.getSessionId());
        if (session == null || !userId.equals(session.getUserId())) {
            return null;
        }
        // 4. 归属校验通过后，查询思考内容
        ChatThinking thinking = chatThinkingMapper.selectOne(
                new LambdaQueryWrapper<ChatThinking>()
                        .eq(ChatThinking::getMessageId, messageId)
                        .last("limit 1")
        );
        return thinking == null ? null : thinking.getContent();
    }

    /**
     * 删除指定会话及其全部关联数据。
     * <p>
     * 清理顺序（外层依赖先删）：
     * 1. ChatThinking（按 message_id 关联，需先取 messageIds）
     * 2. ChatMessage（按 session_id）
     * 3. ContextSummary（按 session_id）
     * 4. AgentStep（按 agent_run_id，需先取该会话所有 runId）
     * 5. AgentRun（按 chat_session_id）
     * 6. ChatSession 本身
     * <p>
     * 整个过程在 {@link Transactional} 内执行，任一失败回滚。
     */
    @Override
    public int deleteSession(Long userId, Long sessionId) {
        // 1. 参数与归属校验
        if (userId == null || sessionId == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .last("limit 1")
        );
        if (session == null) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }

        // 2. 取该会话下所有消息ID，用于清理 ChatThinking
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .select(ChatMessage::getId)
                        .eq(ChatMessage::getSessionId, sessionId)
        );
        List<Long> messageIds = messages.stream()
                .map(ChatMessage::getId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        int deletedMessages = messageIds.size();

        // 3. 清理 ChatThinking（按 message_id 批量）
        if (!messageIds.isEmpty()) {
            chatThinkingMapper.delete(
                    new LambdaQueryWrapper<ChatThinking>()
                            .in(ChatThinking::getMessageId, messageIds)
            );
        }

        // 4. 清理 ChatMessage
        chatMessageMapper.delete(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
        );

        // 5. 清理 ContextSummary
        contextSummaryMapper.delete(
                new LambdaQueryWrapper<ContextSummary>()
                        .eq(ContextSummary::getSessionId, sessionId)
        );

        // 6. 清理 AgentStep + AgentRun（按 chat_session_id 取该会话所有 runId）
        List<AgentRun> runs = agentRunMapper.selectList(
                new LambdaQueryWrapper<AgentRun>()
                        .select(AgentRun::getId)
                        .eq(AgentRun::getChatSessionId, sessionId)
        );
        List<Long> runIds = runs.stream()
                .map(AgentRun::getId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (!runIds.isEmpty()) {
            agentStepMapper.delete(
                    new LambdaQueryWrapper<AgentStep>()
                            .in(AgentStep::getAgentRunId, runIds)
            );
        }
        agentRunMapper.delete(
                new LambdaQueryWrapper<AgentRun>()
                        .eq(AgentRun::getChatSessionId, sessionId)
        );

        // 7. 删除会话本身
        chatSessionMapper.deleteById(sessionId);

        return deletedMessages;
    }

    /**
     * 重命名会话标题。
     * 仅更新 title 字段，使用 LambdaUpdateWrapper 显式 set，避免空值覆盖。
     */
    @Override
    public ChatSession renameSession(Long userId, Long sessionId, String newTitle) {
        // 1. 参数与归属校验
        if (userId == null || sessionId == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        if (StrUtil.isBlank(newTitle)) {
            throw new IllegalArgumentException("标题不能为空");
        }
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .last("limit 1")
        );
        if (session == null) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }

        // 2. 规范化标题：trim 后截断到 TITLE_MAX_LENGTH
        String normalizedTitle = buildTitle(newTitle);

        // 3. 显式 set title，updateById 也能用，但这里用 update + wrapper 更明确
        chatSessionMapper.update(null,
                new LambdaUpdateWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .set(ChatSession::getTitle, normalizedTitle)
        );

        // 4. 返回更新后的会话对象
        session.setTitle(normalizedTitle);
        return session;
    }
}
