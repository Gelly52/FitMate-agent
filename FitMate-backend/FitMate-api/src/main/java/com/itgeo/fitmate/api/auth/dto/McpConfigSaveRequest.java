package com.itgeo.fitmate.api.auth.dto;

import java.util.List;
import lombok.Data;

/**
 * MCP 配置保存请求（PUT 用）。
 */
@Data
public class McpConfigSaveRequest {

    /** 用户自定义 MCP server 列表，传 null 或空列表表示清空自定义配置 */
    private List<McpServerConfig> servers;
}
