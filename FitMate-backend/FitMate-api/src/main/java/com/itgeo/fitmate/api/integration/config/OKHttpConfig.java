package com.itgeo.fitmate.api.integration.config;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * OkHttp 客户端配置。
 */
@Configuration
public class OKHttpConfig implements WebMvcConfigurer {

    /**
     * 创建统一的 OkHttpClient，设置基础连接与读取超时。
     *
     * @return OkHttp 客户端
     */
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }
}
