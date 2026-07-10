package com.itgeo.fitmate.api.agent.dto;

import com.itgeo.fitmate.api.agent.core.SseChunkBuffer;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.chat.dto.ChatEntity;
import com.itgeo.fitmate.api.chat.dto.TokenUsage;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Agent 异步执行上下文，封装受理阶段产出的必要运行信息。
 * 主 Agent 和 Sub-Agent 共用此对象，通过 isSubAgent 标志区分。
 */
@Data
@ToString
@NoArgsConstructor
public class AgentExecuteContext {
    /** Agent 运行ID。 */
    private Long runId;
    /** 关联的聊天会话ID。 */
    private Long chatSessionId;
    /** assistant 占位消息ID。主 Agent 有值；Sub-Agent 不创建 ChatMessage，为 null。 */
    private Long assistantMessageId;
    /** 登录 sessionId 维度锁的 key。Sub-Agent 共享主 Agent 的 slot，不单独申请，为 null。 */
    private String lockKey;
    /** 当前 run 对应的锁持有者标识。Sub-Agent 为 null。 */
    private String lockOwner;
    /** 当前登录用户上下文。主/子 Agent 共享同一登录态。 */
    private AuthenticatedUserContext authenticatedUser;
    /** 原始聊天请求体。Sub-Agent 复用主 Agent 的请求体（仅用其开关字段）。 */
    private ChatEntity chatEntity;
    /** Agent 多轮循环累计的 token 用量，初始为空对象，每轮 LLM 调用后累加。 */
    private TokenUsage accumulatedUsage = new TokenUsage();
    /** 用户主动取消标志，由 AgentCancellationRegistry 设置，Agent Loop 每次迭代检查。 */
    private volatile boolean cancelled = false;
    /** Agent 多轮循环累积的思考内容，finishWithAnswer 时持久化。 */
    private StringBuilder accumulatedThinking = new StringBuilder();
    /** SSE thinking chunk 批处理缓冲器，减少高频 SSE 推送对线程与连接的争用。transient 避免被序列化。 */
    private transient SseChunkBuffer thinkingSseBuffer;
    /** SSE content chunk 批处理缓冲器。transient 避免被序列化。 */
    private transient SseChunkBuffer contentSseBuffer;
    /**
     * 动态 trace stepNo 内存计数器，替代每次 createStep 时的 SELECT MAX(stepNo) 查询。
     * 单 run 仅在 agent-exec 线程内递增，无竞争；transient 避免被序列化。
     */
    private transient AtomicInteger stepNoCounter;

    // ===== Sub-Agent 专用字段（主 Agent 这些字段保持默认值） =====

    /** 是否为 Sub-Agent context。主 Agent 为 false，Sub-Agent 为 true。 */
    private boolean isSubAgent = false;
    /** 父 runId。仅 Sub-Agent 有值，指向主 Agent 的 runId。 */
    private Long parentRunId;
    /** 主 Agent 分配给 Sub-Agent 的任务描述。仅 Sub-Agent 有值。 */
    private String subAgentTask;
    /** Sub-Agent 可用工具白名单（不含 spawn 能力）。仅 Sub-Agent 有值。 */
    private Set<String> subAgentAllowedTools;
    /** Sub-Agent 执行结果文本，完成后回传主 Agent。仅 Sub-Agent 完成时写入。 */
    private String subAgentResult;
    /** 当前活跃的 Sub-Agent runId。仅主 Agent 在 spawn 分支执行期间有值，用于取消级联。 */
    private volatile Long activeSubAgentRunId;

    /**
     * 主 Agent 上下文构造方法（保留原有全参构造签名，避免破坏现有调用）。
     */
    public AgentExecuteContext(Long runId, Long chatSessionId, Long assistantMessageId,
                               String lockKey, String lockOwner,
                               AuthenticatedUserContext authenticatedUser, ChatEntity chatEntity,
                               TokenUsage accumulatedUsage, boolean cancelled,
                               StringBuilder accumulatedThinking,
                               SseChunkBuffer thinkingSseBuffer, SseChunkBuffer contentSseBuffer,
                               AtomicInteger stepNoCounter) {
        this.runId = runId;
        this.chatSessionId = chatSessionId;
        this.assistantMessageId = assistantMessageId;
        this.lockKey = lockKey;
        this.lockOwner = lockOwner;
        this.authenticatedUser = authenticatedUser;
        this.chatEntity = chatEntity;
        this.accumulatedUsage = accumulatedUsage;
        this.cancelled = cancelled;
        this.accumulatedThinking = accumulatedThinking;
        this.thinkingSseBuffer = thinkingSseBuffer;
        this.contentSseBuffer = contentSseBuffer;
        this.stepNoCounter = stepNoCounter;
    }

    /**
     * Sub-Agent 上下文工厂方法。继承主 Agent 的共享字段（sseClientId、authenticatedUser、chatEntity），
     * 独立初始化累加器和计数器，不持有锁、不创建 ChatMessage。
     *
     * @param parentContext 主 Agent 上下文
     * @param subRunId Sub-Agent 的 runId
     * @param task 主 Agent 分配的任务描述
     * @param allowedTools Sub-Agent 可用工具白名单（不含 spawn）
     * @return Sub-Agent 上下文
     */
    public static AgentExecuteContext forSubAgent(AgentExecuteContext parentContext,
                                                  Long subRunId,
                                                  String task,
                                                  Set<String> allowedTools) {
        AgentExecuteContext ctx = new AgentExecuteContext();
        ctx.runId = subRunId;
        ctx.chatSessionId = parentContext.chatSessionId;
        ctx.assistantMessageId = null;
        ctx.lockKey = null;
        ctx.lockOwner = null;
        ctx.authenticatedUser = parentContext.authenticatedUser;
        ctx.chatEntity = parentContext.chatEntity;
        ctx.accumulatedUsage = new TokenUsage();
        ctx.cancelled = false;
        ctx.accumulatedThinking = new StringBuilder();
        ctx.thinkingSseBuffer = new SseChunkBuffer();
        ctx.contentSseBuffer = new SseChunkBuffer();
        ctx.stepNoCounter = new AtomicInteger(0);
        ctx.isSubAgent = true;
        ctx.parentRunId = parentContext.runId;
        ctx.subAgentTask = task;
        ctx.subAgentAllowedTools = allowedTools;
        return ctx;
    }
}
