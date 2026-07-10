package com.itgeo.fitmate.api.chat.application;

import com.itgeo.fitmate.api.chat.dto.ChatRecordsResponse;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatMessage;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatSession;
import java.util.List;

/**
 * 聊天会话与消息持久化服务契约。
 * <p>
 * 负责会话创建/复用、消息写入、assistant 占位回填以及历史查询；
 * 接口只描述持久化层行为，不包含模型调用或 SSE 推送实现。
 */
public interface ChatSessionService {
    /**
     * 创建 agent 场景新会话。
     *
     * @param userId 用户ID
     * @param firstMessage 首条消息内容，用于生成会话标题
     * @param botMsgId 当前轮次对应的机器人消息ID
     * @return 新建的会话对象
     */
    ChatSession createAgentSession(Long userId, String firstMessage, String botMsgId);

    /**
     * 追加一条用户消息。
     *
     * @param sessionId 会话ID
     * @param content 消息内容
     * @param sourceType 消息来源类型
     * @return 新增消息ID
     */
    Long appendUserMessage(Long sessionId, String content, String sourceType);

    /**
     * 创建 assistant 占位消息。
     *
     * @param sessionId 会话ID
     * @param botMsgId 机器人消息ID
     * @param sourceType 消息来源类型
     * @return 新增消息ID
     */
    Long createAssistantPlaceholder(Long sessionId, String botMsgId, String sourceType);

    /**
     * 回填 assistant 最终消息内容与来源。
     *
     * @param messageId 消息ID
     * @param content 最终回复内容
     * @param sourcesJson 来源 JSON 字符串
     */
    void finishAssistantMessage(Long messageId, String content, String sourcesJson);

    /**
     * 回填 assistant 最终消息内容、来源与 token 用量快照。
     *
     * @param messageId 消息ID
     * @param content 最终回复内容
     * @param sourcesJson 来源 JSON 字符串
     * @param usageJson token 用量快照 JSON 字符串
     */
    void finishAssistantMessage(Long messageId, String content, String sourcesJson, String usageJson);

    /**
     * 检查是否存在指定 botMsgId 的消息记录。
     *
     * @param botMsgId 机器人消息ID
     * @return 是否存在
     */
    boolean existsByBotMsgId(String botMsgId);

    /**
     * 查询当前用户最近更新的会话列表。
     *
     * @param userId 用户ID
     * @param limit 返回条数上限
     * @return 会话列表
     */
    List<ChatSession> listRecentSessions(Long userId, Integer limit);

    /**
     * 查询当前用户指定会话下的消息列表。
     *
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 消息列表
     */
    List<ChatMessage> listMessagesBySessionId(Long userId, Long sessionId);

    /**
     * 仅按 sessionId 查询消息列表，不校验用户归属。
     * <p>
     * 仅供受信内部链路调用（如上下文压缩服务，调用前已校验会话归属）。
     *
     * @param sessionId 会话ID
     * @return 消息列表（按 seqNo 升序）
     */
    List<ChatMessage> listMessagesBySessionIdOnly(Long sessionId);

    /**
     * 查询当前用户聊天历史。
     *
     * @param userId 用户ID
     * @param sessionId 指定会话ID，可为空
     * @param limit 最近会话条数上限
     * @return 聊天历史响应
     */
    ChatRecordsResponse getChatRecords(Long userId, Long sessionId, Integer limit);

    /**
     * 按 userId 与 sessionCode 查找会话。
     *
     * @param userId 用户ID
     * @param sessionCode 会话编码
     * @return 匹配到的会话；不存在时返回 null
     */
    ChatSession findByUserIdAndSessionCode(Long userId, String sessionCode);

    /**
     * 创建新会话记录。
     * <p>
     * 该方法只负责写入会话元数据，不负责追加用户消息或 assistant 消息。
     *
     * @param userId 用户ID
     * @param sceneType 会话场景类型，当前仅区分 agent / chat
     * @param firstMessage 首条消息内容，仅用于生成标题
     * @param botMsgId 当前轮次对应的机器人消息ID
     * @return 新建的会话对象
     */
    ChatSession createSession(
            Long userId,
            String sceneType,
            String firstMessage,
            String botMsgId
    );

    /**
     * 按 sessionCode 复用会话，不存在或场景不兼容时新建。
     * <p>
     * 该方法只返回可用会话对象，不会在内部追加任何消息记录。
     *
     * @param userId 用户ID
     * @param sessionCode 候选会话编码
     * @param sceneType 目标会话场景类型
     * @param firstMessage 首条消息内容，仅在新建会话时用于生成标题
     * @param botMsgId 当前轮次对应的机器人消息ID，仅在新建会话时使用
     * @return 可复用或新建的会话对象
     */
    ChatSession resolveOrCreateSession(
            Long userId,
            String sessionCode,
            String sceneType,
            String firstMessage,
            String botMsgId
    );

    /**
     * 按会话主键和用户主键查询会话。
     *
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 匹配到的会话；不存在时返回 null
     */
    ChatSession findByIdAndUserId(Long sessionId, Long userId);

    /**
     * 检查当前用户是否存在指定 botMsgId 的消息记录。
     *
     * @param userId 用户ID
     * @param botMsgId 机器人消息ID
     * @return 是否存在
     */
    boolean existsByUserIdAndBotMsgId(Long userId, String botMsgId);

    /**
     * 删除指定 botMsgId 对应的用户消息及其之后的所有消息（含 assistant 回复）。
     * 同时清理覆盖该范围的上下文压缩摘要，并更新会话的 lastBotMsgId。
     *
     * @param userId 用户ID（权限校验）
     * @param sessionId 会话ID
     * @param botMsgId 机器人消息ID，用于定位 assistant 消息的 seqNo
     * @return 实际删除的消息条数
     */
    int deleteMessagesFromBotMsgId(Long userId, Long sessionId, String botMsgId);

    /**
     * 保存思考内容。已存在则覆盖更新。
     *
     * @param messageId 关联的 assistant 消息ID
     * @param content 思考内容全文
     */
    void saveThinking(Long messageId, String content);

    /**
     * 按消息ID查询思考内容，并校验消息归属当前用户。
     * <p>
     * 校验链：messageId → ChatMessage.sessionId → ChatSession.userId == 当前 userId。
     * 若消息不存在、会话不存在或会话不属于当前用户，均返回 null，避免 IDOR 越权读取。
     *
     * @param userId 当前登录用户ID（归属校验）
     * @param messageId 消息ID
     * @return 思考内容；不存在或无权访问时返回 null
     */
    String getThinkingByMessageId(Long userId, Long messageId);

    /**
     * 删除指定会话及其全部关联数据（消息、思考内容、上下文压缩摘要）。
     * <p>
     * 调用方必须传当前登录用户ID做归属校验，会话不属于该用户时抛 IllegalArgumentException。
     *
     * @param userId 用户ID（权限校验）
     * @param sessionId 会话ID
     * @return 实际删除的消息条数（不含摘要与思考记录）
     */
    int deleteSession(Long userId, Long sessionId);

    /**
     * 重命名指定会话标题。
     * <p>
     * 调用方必须传当前登录用户ID做归属校验，会话不属于该用户时抛 IllegalArgumentException。
     * 标题会被 trim 并截断到 50 字符以内。
     *
     * @param userId 用户ID（权限校验）
     * @param sessionId 会话ID
     * @param newTitle 新标题
     * @return 更新后的会话对象
     */
    ChatSession renameSession(Long userId, Long sessionId, String newTitle);
}
