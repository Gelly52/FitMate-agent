package com.itgeo.fitmate.api.chat.dto;

import lombok.Data;

/**
 * LLM 测活结果。
 */
@Data
public class LlmTestResult {
    private Boolean ok;
    private String model;
    private Long latencyMs;
    private String error;
}
