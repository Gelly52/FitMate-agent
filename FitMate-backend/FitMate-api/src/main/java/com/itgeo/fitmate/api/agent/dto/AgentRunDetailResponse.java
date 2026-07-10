package com.itgeo.fitmate.api.agent.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Agent run 详情响应，包含主记录与步骤明细。
 */
@Data
@ToString
@NoArgsConstructor
public class AgentRunDetailResponse {
    /** run 主记录ID。 */
    private Long runId;
    /** 父 run ID。仅 Sub-Agent run 有值，主 Agent run 为 null。 */
    private Long parentRunId;
    /** 关联的聊天会话ID。 */
    private Long chatSessionId;
    /** 关联的会话编码。 */
    private String sessionCode;
    /** 机器人消息ID。 */
    private String botMsgId;
    /** 用户本次请求文本。 */
    private String requestText;
    /** run 当前状态。 */
    private String status;
    /** 成功时记录的结果快照。 */
    private String resultJson;
    /** 失败时记录的错误信息。 */
    private String errorMessage;
    /** run 创建时间。 */
    private LocalDateTime createdAt;
    /** run 开始执行时间。 */
    private LocalDateTime startedAt;
    /** run 完成时间。 */
    private LocalDateTime finishedAt;
    /** 该 run 下的步骤明细。 */
    private List<AgentRunStepResponse> steps;
    /** 派生的 Sub-Agent run 详情列表。仅主 Agent run 有值，Sub-Agent run 为 null（避免无限递归）。 */
    private List<AgentRunDetailResponse> subRuns;
}
