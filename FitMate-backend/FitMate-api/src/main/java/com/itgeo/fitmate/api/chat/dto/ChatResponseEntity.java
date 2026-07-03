package com.itgeo.fitmate.api.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 聊天链路结束响应。
 *
 * 说明：
 * 1. message 为最终拼接后的完整回复；
 * 2. chatSessionId / sessionCode 用于前端恢复当前会话；
 * 3. sceneType / sourceType 用于前端判断续聊模式；
 * 4. sources 用于承载 RAG / 联网问答的来源信息。
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class ChatResponseEntity {
    /** 最终回复全文。 */
    private String message;
    /** 机器人消息标识。 */
    private String botMsgId;
    /** Agent 运行主键，普通聊天场景可能为空。 */
    private Long runId;
    /** 当前聊天会话主键。 */
    private Long chatSessionId;
    /** 前端会话编码。 */
    private String sessionCode;
    /** 当前轮来源类型。 */
    private String sourceType;
    /** 来源信息集合。 */
    private Object sources;
    /** 会话场景类型。 */
    private String sceneType;
    /** Token 用量快照，用于前端实时更新用量显示。 */
    private TokenUsage usage;
}
