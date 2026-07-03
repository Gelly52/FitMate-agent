package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.diet.application.DietService;
import com.itgeo.fitmate.api.fitness.diet.dto.DietItemDTO;
import com.itgeo.fitmate.api.fitness.diet.dto.DietLogRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 饮食记录工具（增/改，upsert）。
 */
@Component
public class DietRecordToolExecutor implements ToolExecutor {

    private final DietService dietService;

    public DietRecordToolExecutor(DietService dietService) {
        this.dietService = dietService;
    }

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "diet.record",
                "记录当前用户当日饮食（增/改，按日期+餐次 upsert）。参数: {\"date\":\"yyyy-MM-dd\",\"meal_type\":\"breakfast/lunch/dinner/snack\",\"items\":[{\"name\",\"portion\",\"calories\":int,\"protein\":number,\"carbs\":number,\"fat\":number}],\"note\":\"\"}",
                "{\"type\":\"object\",\"properties\":{\"date\":{\"type\":\"string\",\"description\":\"yyyy-MM-dd 必填\"},\"meal_type\":{\"type\":\"string\",\"enum\":[\"breakfast\",\"lunch\",\"dinner\",\"snack\"]},\"items\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"portion\":{\"type\":\"string\"},\"calories\":{\"type\":\"integer\"},\"protein\":{\"type\":\"number\"},\"carbs\":{\"type\":\"number\"},\"fat\":{\"type\":\"number\"}},\"required\":[\"name\"]}},\"note\":{\"type\":\"string\"}},\"required\":[\"date\",\"meal_type\",\"items\"]}",
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
        Object mealObj = args.get("meal_type");
        if (!(mealObj instanceof String mealType) || mealType.isBlank()) {
            return ToolResult.error("meal_type 参数必填");
        }
        Object itemsObj = args.get("items");
        if (!(itemsObj instanceof List<?> rawList) || rawList.isEmpty()) {
            return ToolResult.error("items 参数必填且至少 1 条");
        }
        List<DietItemDTO> items = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> map)) {
                return ToolResult.error("items 元素必须是对象");
            }
            DietItemDTO dto = new DietItemDTO();
            dto.setName(asString(map.get("name")));
            dto.setPortion(asString(map.get("portion")));
            dto.setCalories(asInt(map.get("calories")));
            dto.setProtein(asBigDecimal(map.get("protein")));
            dto.setCarbs(asBigDecimal(map.get("carbs")));
            dto.setFat(asBigDecimal(map.get("fat")));
            items.add(dto);
        }
        DietLogRequest request = new DietLogRequest(date, mealType, items, asString(args.get("note")));
        try {
            dietService.logDiet(authenticatedUser.getUserId(), request, "chat");
            return ToolResult.ok("已记录饮食 " + mealType + " " + date + "，共 " + items.size() + " 项食物",
                    Map.of("date", date, "meal_type", mealType, "itemCount", items.size()));
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
