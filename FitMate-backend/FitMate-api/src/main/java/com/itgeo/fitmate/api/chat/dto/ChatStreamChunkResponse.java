package com.itgeo.fitmate.api.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 流式追加片段响应。
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class ChatStreamChunkResponse {
    /** 本次追加的内容分片。 */
    private String contentChunk;
    /** 机器人消息标识。 */
    private String botMsgId;
    /** Agent 运行主键，普通聊天场景可能为空。 */
    private Long runId;
    /** 当前聊天会话主键。 */
    private Long chatSessionId;
    /** 前端会话编码。 */
    private String sessionCode;
    /** 会话场景类型。 */
    private String sceneType;
    /** 当前轮来源类型。 */
    private String sourceType;
    /**
     * 内容分片类型：content（正式回答）/ thinking（思考过程）
     * 前端根据此字段决定渲染样式
     */
    private String chunkType;
}
