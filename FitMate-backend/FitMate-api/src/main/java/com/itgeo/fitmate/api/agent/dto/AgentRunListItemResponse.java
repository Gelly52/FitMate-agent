package com.itgeo.fitmate.api.agent.dto;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Agent run 列表项响应。
 */
@Data
@ToString
@NoArgsConstructor
public class AgentRunListItemResponse {
    /** run 主记录ID。 */
    private Long runId;
    /** 父 run ID。仅 Sub-Agent run 有值，主 Agent run 为 null。列表查询默认只返回 parentRunId=null 的主 Agent run。 */
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
    /** 最近一次错误信息。 */
    private String errorMessage;
    /** run 创建时间。 */
    private LocalDateTime createdAt;
    /** run 开始执行时间。 */
    private LocalDateTime startedAt;
    /** run 完成时间。 */
    private LocalDateTime finishedAt;
}
