package com.itgeo.fitmate.api.chat.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 上下文压缩记录实体。
 * <p>
 * 一次会话可产生多条压缩记录，加载历史时只取最新一条参与 prompt。
 */
@Data
@ToString
@TableName("t_context_summary")
public class ContextSummary {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("session_id")
    private Long sessionId;
    @TableField("summary_content")
    private String summaryContent;
    @TableField("compressed_from_seq")
    private Integer compressedFromSeq;
    @TableField("compressed_to_seq")
    private Integer compressedToSeq;
    @TableField("compressed_message_count")
    private Integer compressedMessageCount;
    @TableField("token_before")
    private Integer tokenBefore;
    @TableField("token_after")
    private Integer tokenAfter;
    @TableField("trigger_type")
    private String triggerType;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
