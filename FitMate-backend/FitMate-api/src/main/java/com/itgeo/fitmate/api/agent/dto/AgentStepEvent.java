package com.itgeo.fitmate.api.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Agent step 状态事件载荷。
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class AgentStepEvent {
    /** 动态 trace 主键。 */
    private Long stepId;
    /** 所属 run ID。 */
    private Long runId;
    /** 当前步骤编号。 */
    private Integer stepNo;
    /** 当前步骤名称。 */
    private String stepName;
    /** 当前步骤状态。 */
    private String stepStatus;
    /** 动态事件类型，如 llm_started / tool_call_finished。 */
    private String eventType;
    /** 关联工具名称。 */
    private String toolName;
    /** 工具调用ID。 */
    private String toolCallId;
    /** Agent Loop 迭代轮次。 */
    private Integer iterationNo;
    /** 事件耗时毫秒数。 */
    private Long durationMs;
    /** 步骤输入快照。 */
    private String inputJson;
    /** 步骤输出快照。 */
    private String outputJson;
    /** 错误信息。 */
    private String errorMessage;
    /** 面向前端的步骤提示信息。 */
    private String message;
}
