package com.itgeo.fitmate.api.auth.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP server 连接测试结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpTestResult {

    /** 是否连通 */
    private boolean ok;

    /** 握手耗时（毫秒） */
    private Long latencyMs;

    /** 失败时的错误信息 */
    private String error;

    /** 连通后从 server 拉取到的工具名列表 */
    private List<String> tools;
}
