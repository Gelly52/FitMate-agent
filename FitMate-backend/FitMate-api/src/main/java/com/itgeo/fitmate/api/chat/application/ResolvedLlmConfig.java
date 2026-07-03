package com.itgeo.fitmate.api.chat.application;

import lombok.Data;

/**
 * 已解析的 LLM 配置（内部使用，apiKey 为明文）。
 */
@Data
public class ResolvedLlmConfig {
    private String baseUrl;
    private String apiKey;
    private String model;
    private Integer maxInputContextTokens;
    private Integer maxOutputContextTokens;
    private Boolean thinkingEnabled;
    private String reasoningEffort;
}
