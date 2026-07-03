package com.itgeo.fitmate.api.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietItem;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietLog;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper.DietItemMapper;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper.DietLogMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 饮食查询工具（只读，含明细）。
 */
@Component
public class DietQueryToolExecutor implements ToolExecutor {

    private final DietLogMapper dietLogMapper;
    private final DietItemMapper dietItemMapper;

    public DietQueryToolExecutor(DietLogMapper dietLogMapper, DietItemMapper dietItemMapper) {
        this.dietLogMapper = dietLogMapper;
        this.dietItemMapper = dietItemMapper;
    }

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "diet.query",
                "查询当前用户最近饮食记录（含食物明细），参数: {\"days\": 1-180, \"limit\": 1-50}",
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
        QueryWrapper<DietLog> query = new QueryWrapper<>();
        query.eq("user_id", authenticatedUser.getUserId())
                .ge("record_date", LocalDate.now().minusDays(days))
                .orderByDesc("record_date")
                .last("LIMIT " + limit);
        List<DietLog> logs = dietLogMapper.selectList(query);
        if (logs.isEmpty()) {
            return ToolResult.ok("未查询到饮食记录", logs);
        }
        // 批量查明细
        List<Long> logIds = logs.stream().map(DietLog::getId).collect(Collectors.toList());
        List<DietItem> items = dietItemMapper.selectList(
                new QueryWrapper<DietItem>().in("diet_log_id", logIds));
        Map<Long, List<DietItem>> itemMap = items.stream()
                .collect(Collectors.groupingBy(DietItem::getDietLogId));
        for (DietLog log : logs) {
            log.setItems(itemMap.getOrDefault(log.getId(), new ArrayList<>()));
        }
        return ToolResult.ok("已查询到饮食记录 " + logs.size() + " 条", logs);
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
