package com.itgeo.fitmate.api.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RagBenchmarkQuestionRequest {
    private String question;
    private String expectedFileName;
}
