package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.training.application.TrainingService;
import com.itgeo.fitmate.api.fitness.training.dto.TrainingExerciseItem;
import com.itgeo.fitmate.api.fitness.training.dto.TrainingLogRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 训练日志记录工具（增/改，upsert）。
 */
@Component
public class TrainingLogRecordToolExecutor implements ToolExecutor {

    private final TrainingService trainingService;

    public TrainingLogRecordToolExecutor(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "training_log.record",
                "记录当前用户当日训练日志（增/改，按日期 upsert）。参数: {\"date\":\"yyyy-MM-dd\",\"exercises\":[{\"name\",\"sets\",\"reps\",\"weight\"}]}",
                "{\"type\":\"object\",\"properties\":{\"date\":{\"type\":\"string\",\"description\":\"yyyy-MM-dd 必填\"},\"exercises\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"sets\":{\"type\":\"integer\"},\"reps\":{\"type\":\"integer\"},\"weight\":{\"type\":\"number\"}},\"required\":[\"name\",\"sets\",\"reps\",\"weight\"]}}},\"required\":[\"date\",\"exercises\"]}",
                false
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        Map<String, Object> args = call.getArguments();
        Object dateObj = args == null ? null : args.get("date");
        if (!(dateObj instanceof String date) || date.isBlank()) {
            return ToolResult.error("date 参数必填且为 yyyy-MM-dd 字符串");
        }
        Object exercisesObj = args.get("exercises");
        if (!(exercisesObj instanceof List<?> rawList) || rawList.isEmpty()) {
            return ToolResult.error("exercises 参数必填且至少 1 条");
        }
        List<TrainingExerciseItem> exercises = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> map)) {
                return ToolResult.error("exercises 元素必须是对象");
            }
            TrainingExerciseItem ex = new TrainingExerciseItem();
            ex.setName(asString(map.get("name")));
            ex.setSets(asInt(map.get("sets")));
            ex.setReps(asInt(map.get("reps")));
            ex.setWeight(asBigDecimal(map.get("weight")));
            exercises.add(ex);
        }
        TrainingLogRequest request = new TrainingLogRequest(date, exercises);
        try {
            trainingService.logTraining(authenticatedUser.getUserId(), request, "chat");
            return ToolResult.ok("已记录训练日志 " + date + "，共 " + exercises.size() + " 个动作",
                    Map.of("date", date, "exerciseCount", exercises.size()));
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
