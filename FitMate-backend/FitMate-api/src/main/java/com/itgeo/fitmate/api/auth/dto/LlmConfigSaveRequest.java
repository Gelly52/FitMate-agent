package com.itgeo.fitmate.api.auth.dto;

import lombok.Data;

/**
 * LLM 配置保存请求（PUT 用，apiKey 明文，空表示不修改原值）。
 */
@Data
public class LlmConfigSaveRequest {
    private String baseUrl;
    /** 明文，可为空（空表示不修改原 key） */
    private String apiKey;
    private String model;
    private Integer maxInputContextTokens;
    private Integer maxOutputContextTokens;
    private Boolean thinkingEnabled;
    private String reasoningEffort;
}
