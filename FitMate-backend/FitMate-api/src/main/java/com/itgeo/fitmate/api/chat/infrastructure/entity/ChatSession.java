package com.itgeo.fitmate.api.chat.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 聊天会话持久化实体。
 */
@Data
@ToString
@TableName("t_chat_session")
public class ChatSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("session_code")
    private String sessionCode;
    @TableField("user_id")
    private Long userId;
    @TableField("scene_type")
    /** 会话场景类型。 */
    private String sceneType;
    private String title;
    @TableField("last_bot_msg_id")
    /** 最近一条机器人消息标识。 */
    private String lastBotMsgId;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
