package com.itgeo.fitmate.api.rag.dto;

import lombok.Data;
import org.springframework.ai.document.Document;

@Data
public class RagRetrievedChunk {

    private String chunkId;
    private String documentId;
    private Integer chunkSeq;
    private String fileName;
    private String userId;

    private Document document;

    private Integer vectorRank;
    private Integer keywordRank;
    private Double fusionScore;

    private Double rerankScore; // 最终重排分数
    private Integer rerankRank; // 重排后的名次
    private Double queryCoverageScore; // 文本覆盖率得分
    private Boolean dualHit; //是否同时命中
}
