package com.itgeo.fitmate.api.config;

import com.itgeo.fitmate.api.chat.infrastructure.LlmConfigCipher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfigBeanConfig {

    @Bean
    public LlmConfigCipher llmConfigCipher(LlmConfigProperties properties) {
        return new LlmConfigCipher(properties.getEncryptionKey());
    }
}
