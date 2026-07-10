package com.itgeo.fitmate.api.chat.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * LLM 思考内容持久化实体。
 * <p>
 * 一条 assistant 消息最多对应一条思考记录，通过 message_id 唯一关联。
 */
@Data
@ToString
@TableName("t_chat_thinking")
public class ChatThinking {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("message_id")
    private Long messageId;
    private String content;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
