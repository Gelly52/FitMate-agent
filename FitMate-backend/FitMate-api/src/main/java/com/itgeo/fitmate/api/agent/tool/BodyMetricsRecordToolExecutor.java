package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.metrics.application.BodyMetricsService;
import com.itgeo.fitmate.api.fitness.metrics.dto.BodyMetricsLogRequest;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 身体指标记录工具（增/改，upsert）。
 */
@Component
public class BodyMetricsRecordToolExecutor implements ToolExecutor {

    private final BodyMetricsService bodyMetricsService;

    public BodyMetricsRecordToolExecutor(BodyMetricsService bodyMetricsService) {
        this.bodyMetricsService = bodyMetricsService;
    }

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "body_metrics.record",
                "记录当前用户当日身体指标（增/改，按日期 upsert）。参数: {\"date\":\"yyyy-MM-dd\",\"weight\":number,\"body_fat\":number,\"sleep_hours\":number,\"fatigue\":\"低/中/高\",\"chest_girth\":number,\"waist_girth\":number,\"hip_girth\":number,\"arm_girth\":number,\"thigh_girth\":number,\"note\":\"\"}",
                "{\"type\":\"object\",\"properties\":{\"date\":{\"type\":\"string\",\"description\":\"yyyy-MM-dd 必填\"},\"weight\":{\"type\":\"number\"},\"body_fat\":{\"type\":\"number\"},\"sleep_hours\":{\"type\":\"number\"},\"fatigue\":{\"type\":\"string\",\"enum\":[\"低\",\"中\",\"高\"]},\"chest_girth\":{\"type\":\"number\"},\"waist_girth\":{\"type\":\"number\"},\"hip_girth\":{\"type\":\"number\"},\"arm_girth\":{\"type\":\"number\"},\"thigh_girth\":{\"type\":\"number\"},\"note\":{\"type\":\"string\"}},\"required\":[\"date\"]}",
                false
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        Map<String, Object> args = call.getArguments();
        if (args == null) {
            return ToolResult.error("参数不能为空");
        }
        Object dateObj = args.get("date");
        if (!(dateObj instanceof String date) || date.isBlank()) {
            return ToolResult.error("date 参数必填且为 yyyy-MM-dd 字符串");
        }
        BodyMetricsLogRequest request = new BodyMetricsLogRequest();
        request.setDate(date);
        request.setWeight(asBigDecimal(args.get("weight")));
        request.setBodyFat(asBigDecimal(args.get("body_fat")));
        request.setSleep(asBigDecimal(args.get("sleep_hours")));
        request.setFatigue(asString(args.get("fatigue")));
        request.setNote(asString(args.get("note")));
        // 围度字段（在 DTO 中新增）
        request.setChestGirth(asBigDecimal(args.get("chest_girth")));
        request.setWaistGirth(asBigDecimal(args.get("waist_girth")));
        request.setHipGirth(asBigDecimal(args.get("hip_girth")));
        request.setArmGirth(asBigDecimal(args.get("arm_girth")));
        request.setThighGirth(asBigDecimal(args.get("thigh_girth")));
        try {
            bodyMetricsService.logBodyMetrics(authenticatedUser.getUserId(), request, "chat");
            return ToolResult.ok("已记录身体指标 " + date, Map.of("date", date));
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        }
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private BigDecimal asBigDecimal(Object o) {
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (o instanceof String s) {
            try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
