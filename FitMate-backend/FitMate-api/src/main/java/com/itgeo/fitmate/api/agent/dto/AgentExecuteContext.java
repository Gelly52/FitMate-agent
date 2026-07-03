package com.itgeo.fitmate.api.agent.dto;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.chat.dto.ChatEntity;
import com.itgeo.fitmate.api.chat.dto.TokenUsage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Agent 异步执行上下文，封装受理阶段产出的必要运行信息。
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class AgentExecuteContext {
    /** Agent 运行ID。 */
    private Long runId;
    /** 关联的聊天会话ID。 */
    private Long chatSessionId;
    /** assistant 占位消息ID。 */
    private Long assistantMessageId;
    /** 登录 sessionId 维度锁的 key。 */
    private String lockKey;
    /** 当前 run 对应的锁持有者标识。 */
    private String lockOwner;
    /** 当前登录用户上下文。 */
    private AuthenticatedUserContext authenticatedUser;
    /** 原始聊天请求体。 */
    private ChatEntity chatEntity;
    /** Agent 多轮循环累计的 token 用量，初始为空对象，每轮 LLM 调用后累加。 */
    private TokenUsage accumulatedUsage = new TokenUsage();
    /** 用户主动取消标志，由 AgentCancellationRegistry 设置，Agent Loop 每次迭代检查。 */
    private volatile boolean cancelled = false;
}
