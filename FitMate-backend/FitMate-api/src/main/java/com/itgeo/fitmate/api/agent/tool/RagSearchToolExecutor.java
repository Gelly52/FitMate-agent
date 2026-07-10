package com.itgeo.fitmate.api.agent.tool;

import cn.hutool.core.util.StrUtil;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.rag.application.DocumentService;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class RagSearchToolExecutor implements ToolExecutor {

    @Resource
    private ObjectProvider<DocumentService> documentServiceProvider;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "rag.search",
                "检索用户上传的原始文档片段（未加工、保留原文上下文）。适合需要精确引用原文或查阅 Wiki 未编译的资料时调用。参数: {\"query\": \"改写后的检索query\", \"topK\": 1-10}",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"topK\":{\"type\":\"integer\"}},\"required\":[\"query\"]}",
                true
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        String query = argumentText(call, "query");
        if (StrUtil.isBlank(query)) {
            return ToolResult.error("query不能为空");
        }
        int topK = normalizeNumber(argument(call, "topK"), 4, 10);
        List<Document> documents = documentServiceProvider.getObject()
                .doSearch(query, authenticatedUser.getUserId(), topK);
        List<Map<String, Object>> items = documents == null ? List.of() : documents.stream()
                .map(this::toItem)
                .collect(Collectors.toList());
        return ToolResult.ok(items.isEmpty() ? "知识库未检索到相关内容" : "知识库检索到 " + items.size() + " 条内容", items);
    }

    private Map<String, Object> toItem(Document document) {
        Map<String, Object> item = new HashMap<>();
        item.put("text", document.getText());
        item.put("metadata", document.getMetadata());
        return item;
    }

    private String argumentText(ToolCall call, String key) {
        Object value = argument(call, key);
        return value == null ? null : String.valueOf(value);
    }

    private Object argument(ToolCall call, String key) {
        return call == null || call.getArguments() == null ? null : call.getArguments().get(key);
    }

    private int normalizeNumber(Object raw, int defaultValue, int max) {
        if (raw instanceof Number number) {
            return Math.max(1, Math.min(number.intValue(), max));
        }
        if (raw instanceof String text) {
            try {
                return Math.max(1, Math.min(Integer.parseInt(text), max));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
