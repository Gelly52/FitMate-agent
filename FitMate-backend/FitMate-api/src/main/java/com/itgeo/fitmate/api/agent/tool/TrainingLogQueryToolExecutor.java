package com.itgeo.fitmate.api.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.training.infrastructure.entity.TrainingLog;
import com.itgeo.fitmate.api.fitness.training.infrastructure.mapper.TrainingLogMapper;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TrainingLogQueryToolExecutor implements ToolExecutor {

    @Resource
    private TrainingLogMapper trainingLogMapper;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "training_log.query",
                "查询当前用户最近训练日志，参数: {\"days\": 1-180, \"limit\": 1-50}",
                "{\"type\":\"object\",\"properties\":{\"days\":{\"type\":\"integer\"},\"limit\":{\"type\":\"integer\"}}}",
                true
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        int days = normalizeNumber(argument(call, "days"), 30, 180);
        int limit = normalizeNumber(argument(call, "limit"), 20, 50);
        QueryWrapper<TrainingLog> query = new QueryWrapper<>();
        query.eq("user_id", authenticatedUser.getUserId())
                .ge("training_date", LocalDate.now().minusDays(days))
                .orderByDesc("training_date")
                .last("LIMIT " + limit);
        List<TrainingLog> logs = trainingLogMapper.selectList(query);
        return ToolResult.ok(logs.isEmpty() ? "未查询到训练日志" : "已查询到训练日志 " + logs.size() + " 条", logs);
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
