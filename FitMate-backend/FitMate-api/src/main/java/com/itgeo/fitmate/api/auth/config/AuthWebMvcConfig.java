package com.itgeo.fitmate.api.auth.config;

import com.itgeo.fitmate.api.auth.infrastructure.TokenAuthInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 统一注册 token 鉴权拦截器，并维护需要拦截的业务路径与白名单路径。
 */
@Configuration
public class AuthWebMvcConfig implements WebMvcConfigurer {

    @Resource
    private TokenAuthInterceptor tokenAuthInterceptor;

    /**
     * 配置统一 token 鉴权拦截规则。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 统一拦截需要登录态的业务入口，并显式声明不进入该拦截器的白名单路径。
        registry.addInterceptor(tokenAuthInterceptor)
                .addPathPatterns(
                        "/user/**",
                        "/chat/**",
                        "/rag/**",
                        "/internet/**",
                        "/sse/**",
                        "/training/**",
                        "/body-metrics/**",
                        "/agent/**",
                        "/memory/**",
                        "/wiki/**")
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/user/check-email",
                        "/hello/**",
                        "/sse/connect", // 建连入口不走统一 token 鉴权
                        "/error");
    }
}
