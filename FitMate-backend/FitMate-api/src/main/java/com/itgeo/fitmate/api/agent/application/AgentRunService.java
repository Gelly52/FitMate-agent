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
     * 创建一条动态执行轨迹。
     *
     * @param runId run 主记录ID
     * @param eventType 事件类型
     * @param stepName 展示名称
     * @param stepStatus 初始状态
     * @param toolName 工具名称，可为空
     * @param toolCallId 工具调用ID，可为空
     * @param iterationNo Agent Loop 迭代轮次，可为空
     * @param inputJson 输入快照JSON字符串
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
            String inputJson
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
}
