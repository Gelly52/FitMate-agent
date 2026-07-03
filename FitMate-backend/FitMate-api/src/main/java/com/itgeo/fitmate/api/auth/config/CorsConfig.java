package com.itgeo.fitmate.api.auth.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域访问配置。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /** 允许跨域访问的来源配置，多个域名用逗号分隔。 */
    @Value("${website.domain}")
    private String domain;

    /**
     * 为全站接口注册跨域规则。
     *
     * @param registry 跨域注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {

        String[] allowedOrigins = Arrays.stream(domain.split(","))
                .map(String::trim)
                .toArray(String[]::new);

        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(60 * 60);
    }
}
