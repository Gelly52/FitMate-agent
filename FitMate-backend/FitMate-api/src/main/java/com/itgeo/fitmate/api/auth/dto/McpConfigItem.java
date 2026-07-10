package com.itgeo.fitmate.api.auth.dto;

import java.util.List;
import lombok.Data;

/**
 * MCP 配置项（GET 返回用）。
 */
@Data
public class McpConfigItem {

    /** 用户自定义 MCP server 列表，可为空 */
    private List<McpServerConfig> servers;
}
