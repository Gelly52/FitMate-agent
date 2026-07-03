package com.itgeo.fitmate.api.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Agent 请求受理后的 ack 响应。
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class AgentExecuteAckResponse {
    /** Agent 运行ID。 */
    private Long runId;
    /** 关联的聊天会话ID。 */
    private Long chatSessionId;
    /** 关联的会话编码。 */
    private String sessionCode;
    /** 本次请求对应的机器人消息ID。 */
    private String botMsgId;
    /** 受理时的 run 状态。 */
    private String status;
    /** 是否命中幂等并直接返回既有 run。 */
    private Boolean duplicate;
}
