package com.itgeo.fitmate.api.agent.mcp;

import com.itgeo.fitmate.api.agent.tool.ToolDescriptor;
import com.itgeo.fitmate.api.agent.tool.ToolExecutor;
import com.itgeo.fitmate.api.auth.dto.McpServerConfig;
import com.itgeo.fitmate.api.chat.application.McpConfigResolver;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 按用户隔离的 MCP 工具注册表。
 * <p>
 * 每个用户的自定义 MCP server 配置不同，对应的 MCP 工具也不同。
 * 本类维护 userId → List&lt;ToolExecutor&gt; 映射，桥接后的 MCP 工具按用户隔离。
 * <p>
 * 刷新流程：McpConfigResolver 读配置 → McpClientPool 重建 clients → 对每个 client listTools →
 * 为每个 tool 创建 {@link McpToolExecutorBridge} → 缓存到本注册表。
 */
@Slf4j
@Component
public class McpToolRegistry {

    @Resource
    private McpConfigResolver mcpConfigResolver;

    @Resource
    private McpClientPool mcpClientPool;

    /** userId → 该用户的 MCP 桥接工具列表 */
    private final ConcurrentHashMap<Long, List<ToolExecutor>> userTools = new ConcurrentHashMap<>();

    /**
     * 刷新指定用户的 MCP 工具：重建 clients + 重建 Bridge。
     * 应在用户保存 MCP 配置后调用，以及 Agent 首次执行前按需调用。
     */
    public void refresh(Long userId) {
        if (userId == null) {
            return;
        }
        List<McpServerConfig> configs = mcpConfigResolver.resolveByUserId(userId);
        List<McpSyncClient> clients = mcpClientPool.refresh(userId, configs);
        if (clients.isEmpty()) {
            userTools.remove(userId);
            return;
        }
        List<ToolExecutor> bridges = new ArrayList<>();
        for (McpSyncClient client : clients) {
            try {
                McpSchema.ListToolsResult toolsResult = client.listTools();
                if (toolsResult == null || toolsResult.tools() == null) {
                    continue;
                }
                for (McpSchema.Tool tool : toolsResult.tools()) {
                    if (tool.name() == null || tool.name().isBlank()) {
                        continue;
                    }
                    bridges.add(new McpToolExecutorBridge(client, tool));
                }
            } catch (Exception e) {
                log.warn("[MCP] 拉取工具列表失败 userId={} error={}", userId, e.getMessage());
            }
        }
        userTools.put(userId, bridges);
        log.info("[MCP] 用户 {} 刷新 MCP 工具完成，共 {} 个工具", userId, bridges.size());
    }

    /**
     * 确保指定用户的 MCP 工具已加载（懒加载）。
     * 如果用户尚未刷新过，从 DB 读配置并刷新。
     */
    public void ensureLoaded(Long userId) {
        if (userId == null) {
            return;
        }
        if (!userTools.containsKey(userId)) {
            refresh(userId);
        }
    }

    /** 获取指定用户的 MCP 工具 descriptors */
    public List<ToolDescriptor> getDescriptors(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<ToolExecutor> tools = userTools.get(userId);
        if (tools == null || tools.isEmpty()) {
            return Collections.emptyList();
        }
        List<ToolDescriptor> descriptors = new ArrayList<>();
        for (ToolExecutor executor : tools) {
            descriptors.add(executor.descriptor());
        }
        return descriptors;
    }

    /** 查找指定用户的某个 MCP 工具 */
    public Optional<ToolExecutor> findTool(Long userId, String name) {
        if (userId == null || name == null) {
            return Optional.empty();
        }
        List<ToolExecutor> tools = userTools.get(userId);
        if (tools == null || tools.isEmpty()) {
            return Optional.empty();
        }
        for (ToolExecutor executor : tools) {
            if (name.equals(executor.descriptor().getName())) {
                return Optional.of(executor);
            }
        }
        return Optional.empty();
    }

    /** 关闭指定用户的所有 MCP 资源 */
    public void evict(Long userId) {
        if (userId == null) {
            return;
        }
        userTools.remove(userId);
        mcpClientPool.close(userId);
    }
}
