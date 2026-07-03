package com.itgeo.fitmate.api.agent.tool;

import cn.hutool.core.util.StrUtil;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.search.application.WebSearchService;
import com.itgeo.fitmate.api.search.dto.SearchResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * web.search 工具执行器。
 * 通过 WebSearchService 执行联网搜索（默认 Bing 中国），返回标题/URL/摘要列表。
 * 受 internetEnabled 开关控制（在 AgentLoopExecutor.resolveAllowedTools 中过滤）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebSearchToolExecutor implements ToolExecutor {

    private final WebSearchService webSearchService;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "web.search",
                "联网搜索引擎，获取互联网上的最新信息。当用户问题涉及知识库外的实时内容、新闻、最新数据或需要外部权威资料时调用此工具。"
                        + "返回结果为标题、链接、摘要列表。参数: {\"query\": \"搜索词\"}",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}",
                true
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        String query = argumentText(call, "query");
        if (StrUtil.isBlank(query)) {
            return ToolResult.error("query 不能为空");
        }
        try {
            List<SearchResult> results = webSearchService.search(query);
            if (results == null || results.isEmpty()) {
                return ToolResult.ok("未检索到相关结果", List.of());
            }
            String summary = String.format("联网搜索命中 %d 条结果", results.size());
            return ToolResult.ok(summary, results);
        } catch (Exception e) {
            log.warn("web.search 执行失败, query={}", query, e);
            return ToolResult.error("联网搜索失败: " + e.getMessage());
        }
    }

    private String argumentText(ToolCall call, String key) {
        Object value = call == null || call.getArguments() == null ? null : call.getArguments().get(key);
        return value == null ? null : String.valueOf(value);
    }
}
