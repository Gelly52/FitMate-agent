package com.itgeo.fitmate.api.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.entity.CardioLog;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.mapper.CardioLogMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 有氧训练查询工具（只读）。
 */
@Component
public class CardioQueryToolExecutor implements ToolExecutor {

    private final CardioLogMapper cardioLogMapper;

    public CardioQueryToolExecutor(CardioLogMapper cardioLogMapper) {
        this.cardioLogMapper = cardioLogMapper;
    }

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "cardio.query",
                "查询当前用户最近有氧训练记录，参数: {\"days\": 1-180, \"limit\": 1-50}",
                "{\"type\":\"object\",\"properties\":{\"days\":{\"type\":\"integer\"},\"limit\":{\"type\":\"integer\"}}}",
                true
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        Map<String, Object> args = call == null ? null : call.getArguments();
        int days = normalizeNumber(args == null ? null : args.get("days"), 30, 180);
        int limit = normalizeNumber(args == null ? null : args.get("limit"), 20, 50);
        QueryWrapper<CardioLog> query = new QueryWrapper<>();
        query.eq("user_id", authenticatedUser.getUserId())
                .ge("training_date", LocalDate.now().minusDays(days))
                .orderByDesc("training_date")
                .last("LIMIT " + limit);
        List<CardioLog> logs = cardioLogMapper.selectList(query);
        return ToolResult.ok(logs.isEmpty() ? "未查询到有氧训练记录" : "已查询到有氧训练记录 " + logs.size() + " 条", logs);
    }

    private int normalizeNumber(Object value, int defaultValue, int maxValue) {
        int result = defaultValue;
        if (value instanceof Number n) {
            result = n.intValue();
        } else if (value instanceof String s) {
            try {
                result = Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        if (result < 1) result = 1;
        if (result > maxValue) result = maxValue;
        return result;
    }
}
