package com.itgeo.fitmate.api.rag.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 用户上传 RAG 文档的索引实体。
 *
 * 说明：
 * 1. fileName 为用户原始文件名；
 * 2. sourceCount / chunkCount 分别记录原文数量与切分数量；
 * 3. status 记录当前向量化状态。
 */
@Data
@ToString
@TableName("t_rag_document")
public class RagDocument {
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("file_name")
    private String fileName;
    @TableField("source_count")
    private Integer sourceCount;
    @TableField("chunk_count")
    private Integer chunkCount;
    @TableField("vector_status")
    private String status;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
