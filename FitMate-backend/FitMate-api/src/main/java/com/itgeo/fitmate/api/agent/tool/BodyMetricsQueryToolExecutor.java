package com.itgeo.fitmate.api.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity.BodyMetrics;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.mapper.BodyMetricsMapper;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BodyMetricsQueryToolExecutor implements ToolExecutor {

    @Resource
    private BodyMetricsMapper bodyMetricsMapper;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "body_metrics.query",
                "查询当前用户最近身体指标，参数: {\"limit\": 1-20}",
                "{\"type\":\"object\",\"properties\":{\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":20}}}",
                true
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        int limit = normalizeLimit(call == null ? null : call.getArguments() == null ? null : call.getArguments().get("limit"), 5, 20);
        QueryWrapper<BodyMetrics> query = new QueryWrapper<>();
        query.eq("user_id", authenticatedUser.getUserId())
                .orderByDesc("record_date")
                .last("LIMIT " + limit);
        List<BodyMetrics> metrics = bodyMetricsMapper.selectList(query);
        return ToolResult.ok(metrics.isEmpty() ? "未查询到身体指标记录" : "已查询到身体指标记录 " + metrics.size() + " 条", metrics);
    }

    private int normalizeLimit(Object raw, int defaultValue, int max) {
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
