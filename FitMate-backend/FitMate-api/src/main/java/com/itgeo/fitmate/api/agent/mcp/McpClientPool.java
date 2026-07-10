package com.itgeo.fitmate.api.agent.mcp;

import cn.hutool.core.util.StrUtil;
import com.itgeo.fitmate.api.auth.dto.McpServerConfig;
import com.itgeo.fitmate.api.auth.dto.McpTestResult;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 每用户 MCP client 连接池。
 * <p>
 * 绕开 Spring AI 的全局自动装配，按 userId 独立管理 {@link McpSyncClient} 生命周期。
 * 配置变更时调用 {@link #refresh} 关旧建新，不影响其他用户、无需重启服务。
 */
@Slf4j
@Component
public class McpClientPool {

    /** 连接/请求超时 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    /** userId → 该用户启用的 MCP clients */
    private final ConcurrentHashMap<Long, List<McpSyncClient>> pool = new ConcurrentHashMap<>();

    /** 获取指定用户的 clients（不存在返回空列表，不自动创建） */
    public List<McpSyncClient> getClients(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<McpSyncClient> clients = pool.get(userId);
        return clients == null ? Collections.emptyList() : clients;
    }

    /**
     * 刷新指定用户的 MCP clients：关闭旧 clients，用新配置创建新 clients。
     * 只创建 enabled=true 的 server 连接。
     *
     * @return 新创建的 clients
     */
    public List<McpSyncClient> refresh(Long userId, List<McpServerConfig> configs) {
        close(userId);
        if (configs == null || configs.isEmpty()) {
            return Collections.emptyList();
        }
        List<McpSyncClient> newClients = new ArrayList<>();
        for (McpServerConfig config : configs) {
            if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
                continue;
            }
            if (StrUtil.isBlank(config.getUrl())) {
                continue;
            }
            try {
                McpSyncClient client = createClient(config);
                newClients.add(client);
                log.info("[MCP] 用户 {} 建立 MCP 连接: name={} url={}{}", userId, config.getName(),
                        config.getUrl(), StrUtil.blankToDefault(config.getSseEndpoint(), "/sse"));
            } catch (Exception e) {
                log.warn("[MCP] 用户 {} 建立 MCP 连接失败: name={} url={} error={}", userId, config.getName(),
                        config.getUrl(), e.getMessage());
            }
        }
        pool.put(userId, newClients);
        return newClients;
    }

    /** 关闭指定用户的所有 clients */
    public void close(Long userId) {
        if (userId == null) {
            return;
        }
        List<McpSyncClient> old = pool.remove(userId);
        if (old != null) {
            for (McpSyncClient client : old) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.warn("[MCP] 关闭 client 失败: {}", e.getMessage());
                }
            }
        }
    }

    /** 关闭所有用户的 clients（应用关闭时调用） */
    public void closeAll() {
        pool.keySet().forEach(this::close);
    }

    /**
     * 测试单个 MCP server 连接：TCP 端口探测 → MCP initialize → listTools → 关闭。
     * 返回工具名列表作为连通验证。
     * 失败时返回根本异常信息（沿 cause 链下沉），并区分网络层/协议层错误。
     */
    public McpTestResult testConnection(McpServerConfig config) {
        if (config == null || StrUtil.isBlank(config.getUrl())) {
            McpTestResult result = new McpTestResult();
            result.setOk(false);
            result.setError("URL 不能为空");
            return result;
        }
        long started = System.currentTimeMillis();
        McpSyncClient client = null;
        try {
            // 第一步：TCP 端口探测（3s 超时），快速识别「端口不通/Unknown host」
            // 不用 HTTP GET 探测，因为 SSE 端点是流式响应，HTTP GET 会阻塞在读 body 阶段
            String probeError = preProbeTcpPort(config);
            if (probeError != null) {
                McpTestResult result = new McpTestResult();
                result.setOk(false);
                result.setLatencyMs(System.currentTimeMillis() - started);
                result.setError(probeError);
                return result;
            }
            // 第二步：用 MCP 协议握手 initialize + listTools
            client = createClient(config);
            McpSchema.ListToolsResult toolsResult = client.listTools();
            List<String> toolNames = new ArrayList<>();
            if (toolsResult != null && toolsResult.tools() != null) {
                for (McpSchema.Tool tool : toolsResult.tools()) {
                    if (tool.name() != null) {
                        toolNames.add(tool.name());
                    }
                }
            }
            McpTestResult result = new McpTestResult();
            result.setOk(true);
            result.setLatencyMs(System.currentTimeMillis() - started);
            result.setTools(toolNames);
            return result;
        } catch (Exception e) {
            McpTestResult result = new McpTestResult();
            result.setOk(false);
            result.setLatencyMs(System.currentTimeMillis() - started);
            result.setError(resolveRootCause(e));
            log.warn("[MCP] 测试连接失败: url={} sseEndpoint={} error={}",
                    config.getUrl(), config.getSseEndpoint(), result.getError(), e);
            return result;
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * TCP 端口探测：解析 url 的 host+port，用 Socket 短超时（3s）尝试建立 TCP 连接。
     * 不发送 HTTP 请求，避免 SSE 流式响应导致阻塞。
     * 返回 null 表示端口可达；返回非 null 字符串表示错误描述。
     */
    private String preProbeTcpPort(McpServerConfig config) {
        String baseUrl = config.getUrl();
        try {
            java.net.URI uri = java.net.URI.create(baseUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            if (StrUtil.isBlank(host)) {
                return "URL 解析失败，无法识别 host: " + baseUrl;
            }
            if (port <= 0) {
                // 默认 http=80, https=443
                port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), 3000);
            }
            return null;
        } catch (java.net.ConnectException e) {
            return "无法连接到 " + baseUrl + "（连接被拒绝；常见原因：MCP server 未启动，或端口/地址错误）";
        } catch (java.net.UnknownHostException e) {
            return "未知主机: " + baseUrl + "（DNS 解析失败）";
        } catch (java.net.SocketTimeoutException e) {
            return "连接超时: " + baseUrl + "（3s 内未响应；常见原因：MCP server 未启动，或网络不通）";
        } catch (Exception e) {
            String msg = e.getMessage();
            return "预探测异常: " + (msg == null ? e.getClass().getSimpleName() : msg);
        }
    }

    /**
     * 沿 cause 链下沉，找到最根本的异常信息。
     * 用于绕开 Spring AI MCP client 的笼统外壳异常（如 "Client failed to initialize by explicit API call"）。
     */
    private String resolveRootCause(Throwable e) {
        Throwable cur = e;
        StringBuilder chain = new StringBuilder();
        int depth = 0;
        while (cur != null && depth < 8) {
            String msg = cur.getMessage();
            String cls = cur.getClass().getSimpleName();
            if (chain.length() > 0) {
                chain.append(" ← ");
            }
            chain.append(cls).append(": ").append(msg == null ? "(no message)" : msg);
            if (cur.getCause() == null || cur.getCause() == cur) {
                break;
            }
            cur = cur.getCause();
            depth++;
        }
        // 返回最底层的根本原因；若根本原因没消息，返回完整链
        String rootMsg = cur != null ? cur.getMessage() : null;
        if (rootMsg == null || rootMsg.isEmpty()) {
            return chain.toString();
        }
        // 优先返回根本原因的类名 + 消息（更直观）
        return cur.getClass().getSimpleName() + ": " + rootMsg;
    }

    /** 创建并初始化一个 McpSyncClient */
    private McpSyncClient createClient(McpServerConfig config) {
        String baseUrl = config.getUrl();
        String sseEndpoint = StrUtil.blankToDefault(config.getSseEndpoint(), "/sse");
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(baseUrl)
                .sseEndpoint(sseEndpoint)
                .build();
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(REQUEST_TIMEOUT)
                .build();
        client.initialize();
        return client;
    }
}
