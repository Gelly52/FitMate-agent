package com.itgeo.fitmate.api.agent.dto;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Agent run 详情中的步骤响应。
 */
@Data
@ToString
@NoArgsConstructor
public class AgentRunStepResponse {

    /** 动态 trace 主键。 */
    private Long stepId;
    /** 步骤编号。 */
    private Integer stepNo;
    /** 步骤名称。 */
    private String stepName;
    /** 步骤状态。 */
    private String stepStatus;
    /** 动态事件类型。 */
    private String eventType;
    /** 关联工具名称。 */
    private String toolName;
    /** 工具调用ID。 */
    private String toolCallId;
    /** 派生的 Sub-Agent run ID。仅 subagent_started / subagent_finished 事件有值。 */
    private Long subagentRunId;
    /** Agent Loop 迭代轮次。 */
    private Integer iterationNo;
    /** 事件耗时毫秒数。 */
    private Long durationMs;
    /** 步骤输入快照。 */
    private String inputJson;
    /** 步骤输出快照。 */
    private String outputJson;
    /** 步骤错误信息。 */
    private String errorMessage;
    /** 步骤开始时间。 */
    private LocalDateTime startedAt;
    /** 步骤完成时间。 */
    private LocalDateTime finishedAt;

}
