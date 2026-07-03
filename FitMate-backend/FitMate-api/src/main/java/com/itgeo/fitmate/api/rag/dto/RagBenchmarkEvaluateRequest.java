package com.itgeo.fitmate.api.rag.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RagBenchmarkEvaluateRequest {
    private String datasetName;
    private Integer topK;
    private List<RagBenchmarkQuestionRequest> questions;
}
