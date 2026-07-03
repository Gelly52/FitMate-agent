package com.itgeo.fitmate.api.chat.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 聊天消息持久化实体。
 */
@Data
@ToString
@TableName("t_chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("session_id")
    private Long sessionId;
    @TableField("seq_no")
    /** 会话内消息顺序号。 */
    private Integer seqNo;
    private String role;
    @TableField("message_type")
    /** 消息类型。 */
    private String messageType;
    @TableField("source_type")
    /** 当前轮来源类型。 */
    private String sourceType;
    private String content;
    @TableField("bot_msg_id")
    /** 机器人消息标识。 */
    private String botMsgId;
    @TableField("sources_json")
    /** 来源信息原始 JSON。 */
    private String sourcesJson;
    @TableField("usage_json")
    /** Token 用量快照原始 JSON，仅 assistant 消息携带。 */
    private String usageJson;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
