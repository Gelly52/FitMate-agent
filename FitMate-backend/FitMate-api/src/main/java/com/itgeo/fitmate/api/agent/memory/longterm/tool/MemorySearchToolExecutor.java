package com.itgeo.fitmate.api.agent.memory.longterm.tool;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import com.itgeo.fitmate.api.agent.tool.ToolCall;
import com.itgeo.fitmate.api.agent.tool.ToolDescriptor;
import com.itgeo.fitmate.api.agent.tool.ToolExecutor;
import com.itgeo.fitmate.api.agent.tool.ToolResult;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户长期记忆检索工具。
 *
 * 供 Agent 在对话中主动检索用户的 EPISODIC / INSIGHT 记忆，
 * 用于回答需要历史事件或分析结论支撑的问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemorySearchToolExecutor implements ToolExecutor {

    private final UserMemoryMapper memoryMapper;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "memory.search",
                "检索用户的长期记忆（事件 EPISODIC、洞察 INSIGHT）。当用户询问过去的训练决策、计划调整原因、里程碑事件，或需要基于历史分析结论给出建议时调用。参数: {\"query\": \"关键词\", \"limit\": 1-10}",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"limit\":{\"type\":\"integer\"}},\"required\":[\"query\"]}",
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
        int limit = normalizeLimit(argument(call, "limit"), 5);
        Long userId = authenticatedUser.getUserId();

        // 检索 EPISODIC / INSIGHT 类型的 active 记忆，匹配 content 或 metadata.tags
        List<UserMemory> memories = memoryMapper.selectList(new LambdaQueryWrapper<UserMemory>()
                .eq(UserMemory::getUserId, userId)
                .eq(UserMemory::getStatus, "active")
                .in(UserMemory::getMemoryType, "EPISODIC", "INSIGHT")
                .and(w -> w.like(UserMemory::getContent, query)
                        .or().apply("JSON_EXTRACT(metadata_json, '$.tags') LIKE {0}", "%" + query + "%"))
                .orderByDesc(UserMemory::getCreatedAt)
                .last("LIMIT " + limit));

        List<Map<String, Object>> items = new ArrayList<>();
        for (UserMemory m : memories) {
            Map<String, Object> item = new HashMap<>();
            item.put("type", m.getMemoryType());
            item.put("content", m.getContent());
            item.put("createdAt", m.getCreatedAt());
            items.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("memories", items);
        String summary = String.format("命中 %d 条记忆", items.size());
        return ToolResult.ok(summary, data);
    }

    private String argumentText(ToolCall call, String key) {
        Object value = argument(call, key);
        return value == null ? null : String.valueOf(value);
    }

    private Object argument(ToolCall call, String key) {
        return call == null || call.getArguments() == null ? null : call.getArguments().get(key);
    }

    private int normalizeLimit(Object raw, int defaultValue) {
        if (raw instanceof Number number) {
            return Math.max(1, Math.min(number.intValue(), 10));
        }
        if (raw instanceof String text) {
            try {
                return Math.max(1, Math.min(Integer.parseInt(text), 10));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
