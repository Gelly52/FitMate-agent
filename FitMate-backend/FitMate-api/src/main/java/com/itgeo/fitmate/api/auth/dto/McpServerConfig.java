package com.itgeo.fitmate.api.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个 MCP server 配置（SSE 传输）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpServerConfig {

    /** server 名称（用户自定义，用于标识） */
    private String name;

    /** MCP server base URL，如 http://localhost:9070 */
    private String url;

    /** SSE 端点路径，默认 /sse */
    private String sseEndpoint;

    /** 是否启用 */
    private Boolean enabled;
}
