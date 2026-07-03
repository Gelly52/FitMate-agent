package com.itgeo.fitmate.api.chat.application;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.chat.dto.ChatEntity;
import com.itgeo.fitmate.api.chat.dto.ChatResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * 聊天能力服务契约。
 * <p>
 * 约定：
 * 1. 正式对话主链路已统一收敛到 Agent 模式，本接口仅保留调试方法与 Agent 场景的流式发送入口；
 * 2. 接口仅描述能力边界与输入输出约束，不包含具体提示词、SSE 推送或持久化实现细节；
 * 3. Agent 模式相关方法由 AgentLoopExecutor 在多轮循环中调用。
 */
public interface ChatService {

    /**
     * 测试同步聊天。
     *
     * @param prompt 提示词
     * @return 回复内容
     */
    String chatTest(String prompt);

    /**
     * 测试流式聊天，返回完整 {@link ChatResponse} 分片流。
     *
     * @param prompt 提示词
     * @return 流式响应
     */
    Flux<ChatResponse> streamResponse(String prompt);

    /**
     * 测试流式聊天，返回字符串分片。
     *
     * @param prompt 提示词
     * @return 文本分片流
     */
    Flux<String> streamStr(String prompt);

    /**
     * 执行 Agent 场景下的纯模型问答。
     *
     * @param chatEntity 聊天请求体
     * @param chatSessionId 已存在的会话ID
     * @param assistantMessageId 已创建的 assistant 占位消息ID
     * @param runId Agent 运行ID
     * @param authenticatedUser 当前登录用户上下文
     * @return 最终聊天结果
     */
    ChatResponseEntity doAgentChat(
            ChatEntity chatEntity,
            Long chatSessionId,
            Long assistantMessageId,
            Long runId,
            AuthenticatedUserContext authenticatedUser
    );

    /**
     * 执行 Agent 场景下的联网增强问答。
     *
     * @param chatEntity 聊天请求体
     * @param chatSessionId 已存在的会话ID
     * @param assistantMessageId 已创建的 assistant 占位消息ID
     * @param runId Agent 运行ID
     * @param authenticatedUser 当前登录用户上下文
     * @return 最终聊天结果
     */
    ChatResponseEntity doAgentInternetSearch(
            ChatEntity chatEntity,
            Long chatSessionId,
            Long assistantMessageId,
            Long runId,
            AuthenticatedUserContext authenticatedUser
    );

    /**
     * 根据请求中的增强开关自动分发 Agent 场景能力。
     *
     * @param chatEntity 聊天请求体
     * @param chatSessionId 已存在的会话ID
     * @param assistantMessageId 已创建的 assistant 占位消息ID
     * @param runId Agent 运行ID
     * @param authenticatedUser 当前登录用户上下文
     * @return 最终聊天结果
     */
    ChatResponseEntity doAgentWithEnhancers(
            ChatEntity chatEntity,
            Long chatSessionId,
            Long assistantMessageId,
            Long runId,
            AuthenticatedUserContext authenticatedUser
    );
}
