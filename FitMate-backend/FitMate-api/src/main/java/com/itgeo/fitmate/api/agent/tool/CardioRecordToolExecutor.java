package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.cardio.application.CardioService;
import com.itgeo.fitmate.api.fitness.cardio.dto.CardioLogRequest;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 有氧训练记录工具（增/改，upsert）。
 */
@Component
public class CardioRecordToolExecutor implements ToolExecutor {

    private final CardioService cardioService;

    public CardioRecordToolExecutor(CardioService cardioService) {
        this.cardioService = cardioService;
    }

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "cardio.record",
                "记录当前用户当日有氧训练（增/改，按日期+类型 upsert）。参数: {\"date\":\"yyyy-MM-dd\",\"cardio_type\":\"running/cycling/swimming/rowing/jump_rope/other\",\"distance_km\":number,\"duration_minutes\":int,\"avg_heart_rate\":int,\"note\":\"\"}",
                "{\"type\":\"object\",\"properties\":{\"date\":{\"type\":\"string\",\"description\":\"yyyy-MM-dd 必填\"},\"cardio_type\":{\"type\":\"string\",\"enum\":[\"running\",\"cycling\",\"swimming\",\"rowing\",\"jump_rope\",\"other\"]},\"distance_km\":{\"type\":\"number\"},\"duration_minutes\":{\"type\":\"integer\"},\"avg_heart_rate\":{\"type\":\"integer\"},\"note\":{\"type\":\"string\"}},\"required\":[\"date\",\"cardio_type\"]}",
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
        Object typeObj = args.get("cardio_type");
        if (!(typeObj instanceof String cardioType) || cardioType.isBlank()) {
            return ToolResult.error("cardio_type 参数必填");
        }
        CardioLogRequest request = new CardioLogRequest();
        request.setDate(date);
        request.setCardioType(cardioType);
        request.setDistanceKm(asBigDecimal(args.get("distance_km")));
        request.setDurationMinutes(asInt(args.get("duration_minutes")));
        request.setAvgHeartRate(asInt(args.get("avg_heart_rate")));
        request.setNote(asString(args.get("note")));
        try {
            cardioService.logCardio(authenticatedUser.getUserId(), request, "chat");
            return ToolResult.ok("已记录有氧训练 " + cardioType + " " + date, Map.of("date", date, "cardio_type", cardioType));
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

    private BigDecimal asBigDecimal(Object o) {
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (o instanceof String s) {
            try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
