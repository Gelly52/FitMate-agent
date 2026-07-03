package com.itgeo.fitmate.api.chat.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个会话的历史记录项。
 *
 * 说明：
 * 1. sessionId / sessionCode / sceneType 描述会话身份；
 * 2. title / lastBotMsgId 用于前端列表展示与高亮；
 * 3. messages 保存当前会话下按 seqNo 正序排列的消息列表。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatSessionRecordItem {
    /** 会话主键。 */
    private Long sessionId;
    /** 前端会话编码。 */
    private String sessionCode;
    /** 会话场景类型。 */
    private String sceneType;
    /** 会话标题。 */
    private String title;
    /** 最近一条机器人消息标识。 */
    private String lastBotMsgId;
    /** 会话创建时间。 */
    private LocalDateTime createdAt;
    /** 会话更新时间。 */
    private LocalDateTime updatedAt;
    /** 当前会话消息列表。 */
    private List<ChatRecordItem> messages;
}
