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
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户长期记忆检索工具。
 * <p>
 * 供 Agent 在对话中主动检索用户的所有类型长期记忆（FACT/EPISODIC/INSIGHT）。
 * 支持两种查询模式：
 * 1. 泛化查询：query 为空或为 "all"/"全部"/"你的记忆" 等泛化词时，按时间倒序返回最近 N 条 active 记忆
 * 2. 关键词查询：query 为具体关键词时，对 content 与 metadata.tags 做 LIKE 模糊匹配
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemorySearchToolExecutor implements ToolExecutor {

    /**
     * 泛化查询关键词集合，命中时走"列出最近 N 条"逻辑而非 LIKE 匹配。
     */
    private static final Set<String> GENERIC_QUERIES = Set.of(
            "all", "*", "全部", "所有", "所有记忆", "全部记忆",
            "你的记忆", "我的记忆", "记忆", "列出", "列表",
            "查看", "看看", "查询所有", "查询全部", "list", "recent"
    );

    private final UserMemoryMapper memoryMapper;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "memory.search",
                "检索用户的长期记忆（FACT 稳定事实、EPISODIC 关键事件、INSIGHT 分析结论）。"
                        + "当用户询问过去的训练决策、计划调整原因、里程碑事件，或需要基于历史分析结论给出建议时调用。"
                        + "query 传具体关键词做模糊匹配（匹配 content 或 metadata.tags）；"
                        + "无具体关键词时可传 'all' 或空串，返回最近 N 条记忆。",
                "{\"type\":\"object\",\"properties\":{"
                        + "\"query\":{\"type\":\"string\",\"description\":\"关键词,如'减脂'/'深蹲'。传 'all' 或空串时返回最近记忆\"},"
                        + "\"limit\":{\"type\":\"integer\",\"description\":\"返回条数上限,1-10,默认5\"}"
                        + "}}",
                true
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        String query = argumentText(call, "query");
        int limit = normalizeLimit(argument(call, "limit"), 5);
        Long userId = authenticatedUser.getUserId();

        boolean isGeneric = StrUtil.isBlank(query) || GENERIC_QUERIES.contains(query.trim().toLowerCase());

        LambdaQueryWrapper<UserMemory> wrapper = new LambdaQueryWrapper<UserMemory>()
                .eq(UserMemory::getUserId, userId)
                .eq(UserMemory::getStatus, "active")
                .in(UserMemory::getMemoryType, "FACT", "EPISODIC", "INSIGHT")
                .orderByDesc(UserMemory::getCreatedAt)
                .last("LIMIT " + limit);

        // 非泛化查询时追加关键词匹配条件
        if (!isGeneric) {
            String keyword = query.trim();
            wrapper.and(w -> w.like(UserMemory::getContent, keyword)
                    .or().apply("JSON_EXTRACT(metadata_json, '$.tags') LIKE {0}", "%" + keyword + "%"));
        }

        List<UserMemory> memories = memoryMapper.selectList(wrapper);

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
        data.put("queryMode", isGeneric ? "recent" : "keyword");
        String summary = String.format("命中 %d 条记忆（%s）", items.size(),
                isGeneric ? "最近" + limit + "条" : "关键词: " + query);
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
