package com.itgeo.fitmate.api.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@TableName("t_agent_step")
public class AgentStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("agent_run_id")
    private Long agentRunId;
    @TableField("step_no")
    private Integer stepNo;
    @TableField("step_name")
    private String stepName;
    @TableField("step_status")
    private String stepStatus;
    @TableField("event_type")
    private String eventType;
    @TableField("tool_name")
    private String toolName;
    @TableField("tool_call_id")
    private String toolCallId;
    @TableField("subagent_run_id")
    private Long subagentRunId;
    @TableField("iteration_no")
    private Integer iterationNo;
    @TableField("duration_ms")
    private Long durationMs;
    @TableField("input_json")
    private String inputJson;
    @TableField("output_json")
    private String outputJson;
    @TableField("error_message")
    private String errorMessage;
    @TableField("started_at")
    private LocalDateTime startedAt;
    @TableField("finished_at")
    private LocalDateTime finishedAt;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
