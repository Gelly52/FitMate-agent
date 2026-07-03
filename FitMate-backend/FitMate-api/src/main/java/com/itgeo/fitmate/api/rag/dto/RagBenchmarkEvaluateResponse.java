package com.itgeo.fitmate.api.rag.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RagBenchmarkEvaluateResponse {
    private Long runId;
    private String datasetName;
    private Integer questionCount;
    private Integer topK;
    private String status;
    private Integer hitCount;
    private Double hitRate;
    private Boolean userIsolationEnabled;
    private String isolationStrategy;
    private List<RagBenchmarkQuestionResultResponse> results;
    private Integer top1HitCount;
    private Double top1HitRate;
    private Double mrr;
    private Double avgFirstHitFileRank; // 建议只对命中的题求平均
    private Double avgUniqueRetrievedFileCount; // 能看 topK 的文件覆盖度
}
