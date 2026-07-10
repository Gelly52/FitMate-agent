package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.training.infrastructure.entity.TrainingLog;
import com.itgeo.fitmate.api.fitness.training.infrastructure.mapper.TrainingLogMapper;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 训练日志修改工具（按 id 修改非空字段）。
 * <p>
 * 直连 Mapper，与 TrainingLogQueryToolExecutor 风格一致。
 * 校验记录归属当前用户，防止越权修改。
 */
@Component
public class TrainingLogModifyToolExecutor implements ToolExecutor {

    @Resource
    private TrainingLogMapper trainingLogMapper;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "training_log.modify",
                "按训练日志ID修改训练日志内容（仅传需要修改的字段）。参数: {\"id\":Long,\"training_date\":\"yyyy-MM-dd\",\"summary\":\"\",\"primary_muscle_group\":\"\",\"total_volume\":number,\"source\":\"manual/chat/import\"}",
                "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"description\":\"训练日志ID，必填\"},\"training_date\":{\"type\":\"string\",\"description\":\"yyyy-MM-dd\"},\"summary\":{\"type\":\"string\"},\"primary_muscle_group\":{\"type\":\"string\"},\"total_volume\":{\"type\":\"number\"},\"source\":{\"type\":\"string\",\"enum\":[\"manual\",\"chat\",\"import\"]}},\"required\":[\"id\"]}",
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
        Object idObj = args.get("id");
        if (!(idObj instanceof Number n)) {
            return ToolResult.error("id 参数必填且为数字");
        }
        Long id = n.longValue();

        // 校验记录归属当前用户
        TrainingLog existing = trainingLogMapper.selectById(id);
        if (existing == null) {
            return ToolResult.error("训练日志不存在: id=" + id);
        }
        if (!authenticatedUser.getUserId().equals(existing.getUserId())) {
            return ToolResult.error("无权修改他人的训练日志");
        }

        TrainingLog patch = new TrainingLog();
        patch.setId(id);

        Object dateObj = args.get("training_date");
        if (dateObj instanceof String s && !s.isBlank()) {
            try {
                patch.setTrainingDate(LocalDate.parse(s));
            } catch (Exception e) {
                return ToolResult.error("training_date 格式错误，应为 yyyy-MM-dd");
            }
        }
        Object summaryObj = args.get("summary");
        if (summaryObj instanceof String s && !s.isBlank()) {
            patch.setSummary(s.trim());
        }
        Object muscleObj = args.get("primary_muscle_group");
        if (muscleObj instanceof String s && !s.isBlank()) {
            patch.setPrimaryMuscleGroup(s.trim());
        }
        Object volumeObj = args.get("total_volume");
        if (volumeObj != null) {
            patch.setTotalVolume(asBigDecimal(volumeObj));
        }
        Object sourceObj = args.get("source");
        if (sourceObj instanceof String s && !s.isBlank()) {
            patch.setSource(s.trim());
        }
        patch.setUpdatedAt(LocalDateTime.now());

        int updated = trainingLogMapper.updateById(patch);
        if (updated > 0) {
            return ToolResult.ok("训练日志更新成功 id=" + id, Map.of("id", id, "updated", updated));
        }
        return ToolResult.error("训练日志更新失败，或记录不存在");
    }

    private BigDecimal asBigDecimal(Object o) {
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (o instanceof String s) {
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
