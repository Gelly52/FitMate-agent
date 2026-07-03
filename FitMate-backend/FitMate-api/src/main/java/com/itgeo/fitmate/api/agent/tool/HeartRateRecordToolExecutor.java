package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.heartrate.application.HeartRateService;
import com.itgeo.fitmate.api.fitness.heartrate.dto.HeartRateLogRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 心率记录工具（增/改，upsert）。
 */
@Component
public class HeartRateRecordToolExecutor implements ToolExecutor {

    private final HeartRateService heartRateService;

    public HeartRateRecordToolExecutor(HeartRateService heartRateService) {
        this.heartRateService = heartRateService;
    }

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "heart_rate.record",
                "记录当前用户当日心率数据（增/改，按日期 upsert）。参数: {\"date\":\"yyyy-MM-dd\",\"resting_hr\":int,\"max_hr\":int,\"hrv\":int,\"note\":\"\"}",
                "{\"type\":\"object\",\"properties\":{\"date\":{\"type\":\"string\",\"description\":\"yyyy-MM-dd 必填\"},\"resting_hr\":{\"type\":\"integer\"},\"max_hr\":{\"type\":\"integer\"},\"hrv\":{\"type\":\"integer\"},\"note\":{\"type\":\"string\"}},\"required\":[\"date\"]}",
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
        HeartRateLogRequest request = new HeartRateLogRequest();
        request.setDate(date);
        request.setRestingHr(asInt(args.get("resting_hr")));
        request.setMaxHr(asInt(args.get("max_hr")));
        request.setHrv(asInt(args.get("hrv")));
        request.setNote(asString(args.get("note")));
        try {
            heartRateService.logHeartRate(authenticatedUser.getUserId(), request, "chat");
            return ToolResult.ok("已记录心率数据 " + date, Map.of("date", date));
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        }
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
