package com.itgeo.fitmate.api.agent.mcp;

import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.agent.tool.ToolCall;
import com.itgeo.fitmate.api.agent.tool.ToolDescriptor;
import com.itgeo.fitmate.api.agent.tool.ToolExecutor;
import com.itgeo.fitmate.api.agent.tool.ToolResult;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 将单个 MCP 工具适配为本地 {@link ToolExecutor}，让 Agent 主链路能透明调用 MCP 工具。
 * <p>
 * 每个 Bridge 实例绑定一个 {@link McpSyncClient} 和一个 {@link McpSchema.Tool}。
 * 当 McpClientPool 重建 client 时，对应的 Bridge 会被 McpToolRegistry 重建替换。
 */
@Slf4j
public class McpToolExecutorBridge implements ToolExecutor {

    private final McpSyncClient client;
    private final McpSchema.Tool tool;
    private final ToolDescriptor descriptor;

    public McpToolExecutorBridge(McpSyncClient client, McpSchema.Tool tool) {
        this.client = client;
        this.tool = tool;
        // MCP 工具的 inputSchema 是 JsonSchema 对象，序列化为 JSON 字符串作为 parametersSchema
        String inputSchema = "{\"type\":\"object\",\"properties\":{}}";
        if (tool.inputSchema() != null) {
            try {
                String serialized = JSONUtil.toJsonStr(tool.inputSchema());
                if (serialized != null && !serialized.isBlank()) {
                    inputSchema = serialized;
                }
            } catch (Exception e) {
                log.warn("[MCP] 序列化 inputSchema 失败 tool={} error={}", tool.name(), e.getMessage());
            }
        }
        // MCP 工具默认视为非只读（可能写入），由 LLM 决策是否调用
        this.descriptor = new ToolDescriptor(
                tool.name(),
                tool.description() == null ? "MCP tool: " + tool.name() : tool.description(),
                inputSchema,
                false
        );
    }

    @Override
    public ToolDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        try {
            Map<String, Object> arguments = call == null || call.getArguments() == null
                    ? Map.of() : call.getArguments();
            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(tool.name(), arguments);
            McpSchema.CallToolResult result = client.callTool(request);
            // 拼接所有 TextContent 的文本作为返回内容
            StringBuilder sb = new StringBuilder();
            if (result != null && result.content() != null) {
                for (McpSchema.Content content : result.content()) {
                    if (content instanceof McpSchema.TextContent textContent) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(textContent.text());
                    }
                }
            }
            String content = sb.length() == 0 ? "MCP 工具 " + tool.name() + " 返回空内容" : sb.toString();
            return ToolResult.ok(content, null);
        } catch (Exception e) {
            log.warn("[MCP] 工具调用失败 tool={} error={}", tool.name(), e.getMessage());
            return ToolResult.error(e.getMessage() == null ? "MCP 工具调用失败" : e.getMessage());
        }
    }
}
