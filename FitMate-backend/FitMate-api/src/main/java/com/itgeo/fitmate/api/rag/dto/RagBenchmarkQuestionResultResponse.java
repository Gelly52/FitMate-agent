package com.itgeo.fitmate.api.rag.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RagBenchmarkQuestionResultResponse {
    private Integer index;
    private String question;
    private String expectedFileName;
    private Boolean hit;
    private List<String> retrievedFileNames;
    private String topHitPreview;
    private Integer firstHitChunkRank;
    private Integer firstHitFileRank;
    private Boolean top1Hit;

    private Double top1FusionScore;
    private Double top1RerankScore;
    private Double top1QueryCoverageScore;
    private Boolean top1DualHit;

    private Double reciprocalRank;
    private Integer retrievedChunkCount;
    private Integer uniqueRetrievedFileCount;
    private Integer duplicateChunkCount;
    private String top1FileName;
}
