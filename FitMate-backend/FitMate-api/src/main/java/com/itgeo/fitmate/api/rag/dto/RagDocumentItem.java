package com.itgeo.fitmate.api.rag.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户上传的 RAG 文档列表项。
 *
 * 说明：
 * 1. sourceCount 表示原始文档片段数；
 * 2. chunkCount 表示切分后写入向量库的分片数；
 * 3. status 表示当前向量化状态。
 * 4. id 序列化为字符串，避免 JS 大数精度丢失。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RagDocumentItem {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String fileName;
    private Integer sourceCount;
    private Integer chunkCount;
    private String status;
    private LocalDateTime createdAt;
}
