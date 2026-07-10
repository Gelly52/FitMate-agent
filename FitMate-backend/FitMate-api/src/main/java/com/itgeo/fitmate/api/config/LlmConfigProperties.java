package com.itgeo.fitmate.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 配置属性（fitmate.llm.*）。
 * encryption-key 用于 AES 加密 apiKey；default.* 为 DB 无用户配置时的回退值。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fitmate.llm")
public class LlmConfigProperties {
    /** AES-256 密钥（32 字节 Base64），env 注入，启动时 fail-fast 校验 */
    private String encryptionKey;
    private DefaultConfig defaultConfig = new DefaultConfig();

    @Data
    public static class DefaultConfig {
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        private String model = "deepseek-v4-flash";
        private Integer maxInputContextTokens = 512000;
        private Integer maxOutputContextTokens = 384000;
        private Boolean thinkingEnabled = true;
        private String reasoningEffort = "high";
    }
}
