package com.itgeo.fitmate.api.agent.application;

import com.itgeo.fitmate.api.agent.dto.AgentRunDetailResponse;
import com.itgeo.fitmate.api.agent.dto.AgentRunListItemResponse;
import com.itgeo.fitmate.api.agent.infrastructure.entity.AgentRun;
import com.itgeo.fitmate.api.agent.infrastructure.entity.AgentStep;
import java.util.List;

/**
 * Agent run / step 持久化与查询服务契约。
 *
 * 职责：
 * 1. 管理 run / step 的创建与状态流转；
 * 2. 提供运行列表与运行详情查询能力。
 */
public interface AgentRunService {

/**
     * 按 userId + botMsgId 查询既有 run，供受理层做幂等判断。
     *
     * @param userId 用户ID
     * @param botMsgId 机器人消息ID
     * @return 已存在的 run；不存在时返回 null
     */
    AgentRun findByUserIdAndBotMsgId(Long userId, String botMsgId);

    /**
     * 按 userId + runId 查询 run，用于权限校验。
     *
     * @param userId 用户ID
     * @param runId run 主记录ID
     * @return run 实体；不存在或不属于该用户时返回 null
     */
    AgentRun findByIdAndUserId(Long userId, Long runId);

/**
     * 创建一条 run 主记录。
     *
     * @param userId 用户ID
     * @param chatSessionId 会话ID
     * @param botMsgId 机器人消息ID
     * @param requestText 请求文本
     * @return run 主记录ID
     */
    Long createRun(Long userId, Long chatSessionId, String botMsgId, String requestText);

    /**
     * 创建一条 Sub-Agent run 主记录。
     * <p>
     * 与主 Agent createRun 的差异：
     * - 携带 parentRunId，建立父子 run 关系；
     * - 不携带 botMsgId（Sub-Agent 不创建 ChatMessage，幂等查询按 botMsgId 不会冲突）；
     * - requestText 用主 Agent 分配的任务描述填充。
     *
     * @param parentRunId 主 Agent run ID
     * @param userId 用户ID（与主 Agent 共享登录态）
     * @param chatSessionId 会话ID（与主 Agent 共享同一会话）
     * @param taskText 主 Agent 分配给 Sub-Agent 的任务描述
     * @return Sub-Agent run 主记录ID
     */
    Long createSubAgentRun(Long parentRunId, Long userId, Long chatSessionId, String taskText);

/**
     * 创建一条动态执行轨迹（使用调用方提供的 stepNo，避免 SELECT MAX 查询）。
     * <p>
     * 用于 Agent 执行链路：stepNo 由 AgentExecuteContext 的内存计数器递增产生，
     * 避免每次 createStep 都查一次数据库。数据库 INSERT 仍然照常执行，历史查询不受影响。
     *
     * @param runId run 主记录ID
     * @param eventType 事件类型
     * @param stepName 展示名称
     * @param stepStatus 初始状态
     * @param toolName 工具名称，可为空
     * @param toolCallId 工具调用ID，可为空
     * @param iterationNo Agent Loop 迭代轮次，可为空
     * @param inputJson 输入快照JSON字符串
     * @param stepNo 调用方提供的步骤序号
     * @return 新建 trace 记录
     */
    AgentStep createStep(
            Long runId,
            String eventType,
            String stepName,
            String stepStatus,
            String toolName,
            String toolCallId,
            Integer iterationNo,
            String inputJson,
            Integer stepNo
    );

    /**
     * 创建一条动态执行轨迹（带 subagentRunId，用于 subagent_started / subagent_finished 事件）。
     * <p>
     * 与 {@link #createStep} 的差异仅在于显式携带 subagentRunId，让前端能通过 SSE AGENT_STEP 事件
     * 的 subagentRunId 字段建立父子 run 关系，从而把 Sub-Agent 的 trace 嵌套渲染到主 Agent 步骤下。
     *
     * @param subagentRunId Sub-Agent run ID；非 subagent 事件传 null
     * @see #createStep(Long, String, String, String, String, String, Integer, String, Integer)
     */
    AgentStep createStep(
            Long runId,
            String eventType,
            String stepName,
            String stepStatus,
            String toolName,
            String toolCallId,
            Long subagentRunId,
            Integer iterationNo,
            String inputJson,
            Integer stepNo
    );

/**
     * 按 step 主键将动态轨迹标记为成功。
     *
     * @param stepId step 主键
     * @param eventType 完成事件类型
     * @param outputJson 输出快照JSON字符串
     * @param durationMs 耗时毫秒数
     */
    void markStepSuccess(Long stepId, String eventType, String outputJson, Long durationMs);

/**
     * 按 step 主键将动态轨迹标记为失败。
     *
     * @param stepId step 主键
     * @param eventType 失败事件类型
     * @param errorMessage 错误消息
     * @param durationMs 耗时毫秒数
     */
    void markStepFailed(Long stepId, String eventType, String errorMessage, Long durationMs);

/**
     * 将 run 状态更新为 running。
     *
     * @param runId run 主记录ID
     */
    void markRunRunning(Long runId);

/**
     * 将 run 状态更新为 success，并记录结果快照。
     *
     * @param runId run 主记录ID
     * @param resultJson 结果JSON字符串
     */
    void markRunSuccess(Long runId, String resultJson);

/**
     * 将 run 状态更新为 failed，并记录错误信息。
     *
     * @param runId run 主记录ID
     * @param errorMessage 错误消息
     */
    void markRunFailed(Long runId, String errorMessage);

/**
     * 将 run 状态更新为 cancelled，并记录取消原因。
     *
     * @param runId run 主记录ID
     * @param reason 取消原因
     */
    void markRunCancelled(Long runId, String reason);

/**
     * 将指定 step 状态更新为 running。
     *
     * @param runId run 主记录ID
     * @param stepNo 步骤编号
     * @param inputJson 输入JSON字符串
     */
    void markStepRunning(Long runId, Integer stepNo, String inputJson);

/**
     * 将指定 step 状态更新为 success，并记录输出快照。
     *
     * @param runId run 主记录ID
     * @param stepNo 步骤编号
     * @param outputJson 输出JSON字符串
     */
    void markStepSuccess(Long runId, Integer stepNo, String outputJson);

/**
     * 将指定 step 状态更新为 failed，并记录错误信息。
     *
     * @param runId run 主记录ID
     * @param stepNo 步骤编号
     * @param errorMessage 错误消息
     */
    void markStepFailed(Long runId, Integer stepNo, String errorMessage);

/**
     * 查询当前用户最近的 run 列表。
     *
     * @param userId 用户ID
     * @param status 可选状态过滤
     * @param limit 返回条数
     * @return 运行列表
     */
    List<AgentRunListItemResponse> listRuns(Long userId, String status, Integer limit);

/**
     * 查询当前用户指定 run 的详情及 step 列表。
     *
     * @param userId 用户ID
     * @param runId 运行ID
     * @return 运行详情；不存在时返回 null
     */
    AgentRunDetailResponse getRunDetail(Long userId, Long runId);

    /**
     * 按 botMsgId 查询当前用户指定 run 的详情及 step 列表。
     * <p>
     * 用于历史会话消息二次加载执行轨迹：前端展开历史 bot 消息的思考过程时，
     * 通过 botMsgId 反查关联的 AgentRun，并补齐 step 列表，以便还原本轮执行链路。
     *
     * @param userId   用户ID
     * @param botMsgId 机器人消息ID
     * @return 运行详情；不存在时返回 null
     */
    AgentRunDetailResponse getRunDetailByBotMsgId(Long userId, String botMsgId);
}
