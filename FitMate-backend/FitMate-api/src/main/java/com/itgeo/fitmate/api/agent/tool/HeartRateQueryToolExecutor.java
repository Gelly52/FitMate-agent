package com.itgeo.fitmate.api.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.entity.HeartRate;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.mapper.HeartRateMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 心率查询工具（只读）。
 */
@Component
public class HeartRateQueryToolExecutor implements ToolExecutor {

    private final HeartRateMapper heartRateMapper;

    public HeartRateQueryToolExecutor(HeartRateMapper heartRateMapper) {
        this.heartRateMapper = heartRateMapper;
    }

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "heart_rate.query",
                "查询当前用户最近心率记录，参数: {\"days\": 1-180, \"limit\": 1-50}",
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
        QueryWrapper<HeartRate> query = new QueryWrapper<>();
        query.eq("user_id", authenticatedUser.getUserId())
                .ge("record_date", LocalDate.now().minusDays(days))
                .orderByDesc("record_date")
                .last("LIMIT " + limit);
        List<HeartRate> logs = heartRateMapper.selectList(query);
        return ToolResult.ok(logs.isEmpty() ? "未查询到心率记录" : "已查询到心率记录 " + logs.size() + " 条", logs);
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
