package com.itgeo.fitmate.api.auth.dto;

import lombok.Data;

/**
 * LLM 配置项（GET 返回用，apiKey 脱敏）。
 */
@Data
public class LlmConfigItem {
    private String baseUrl;
    /** 脱敏值，如 sk-****e05f */
    private String apiKey;
    private String model;
    private Integer maxInputContextTokens;
    private Integer maxOutputContextTokens;
    private Boolean thinkingEnabled;
    /** high / max */
    private String reasoningEffort;
    /** 是否使用系统默认配置（DB无自定义配置时为true，此时apiKey显示为系统默认key的脱敏值） */
    private Boolean usingDefault;
}
