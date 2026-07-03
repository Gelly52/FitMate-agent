package com.itgeo.fitmate.mcp.rag.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@TableName("t_rag_document")
public class RagDocumentMeta {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String fileName;
    private Integer sourceCount;
    private String fileHash;
    private String fileType;
    private Long fileSize;
    private String storageUri;
    private Integer chunkCount;
    private String vectorStatus;
    private String metadataJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
