package com.itgeo.fitmate.api.chat.dto;

import lombok.Data;

/**
 * list-models / test 代理请求体。字段为空时后端用当前用户已存配置。
 */
@Data
public class LlmProxyRequest {
    private String baseUrl;
    private String apiKey;
    private String model;
}
