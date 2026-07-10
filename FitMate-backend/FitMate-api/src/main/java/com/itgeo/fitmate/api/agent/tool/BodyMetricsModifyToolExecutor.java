package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity.BodyMetrics;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.mapper.BodyMetricsMapper;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 身体指标修改工具（按 id 修改非空字段）。
 * <p>
 * 直连 Mapper，与本地 query 工具风格一致。
 * 校验记录归属当前用户，防止越权修改。
 * 支持围度字段（chest/waist/hip/arm/thigh girth），比 MCP 版本更完整。
 */
@Component
public class BodyMetricsModifyToolExecutor implements ToolExecutor {

    @Resource
    private BodyMetricsMapper bodyMetricsMapper;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "body_metrics.modify",
                "按身体指标记录ID修改记录内容（仅传需要修改的字段）。参数: {\"id\":Long,\"record_date\":\"yyyy-MM-dd\",\"weight\":number,\"body_fat\":number,\"chest_girth\":number,\"waist_girth\":number,\"hip_girth\":number,\"arm_girth\":number,\"thigh_girth\":number,\"sleep_hours\":number,\"fatigue_level\":\"低/中/高\",\"note\":\"\",\"summary\":\"\",\"source\":\"manual/chat/import\"}",
                "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"description\":\"身体指标记录ID，必填\"},\"record_date\":{\"type\":\"string\",\"description\":\"yyyy-MM-dd\"},\"weight\":{\"type\":\"number\"},\"body_fat\":{\"type\":\"number\"},\"chest_girth\":{\"type\":\"number\"},\"waist_girth\":{\"type\":\"number\"},\"hip_girth\":{\"type\":\"number\"},\"arm_girth\":{\"type\":\"number\"},\"thigh_girth\":{\"type\":\"number\"},\"sleep_hours\":{\"type\":\"number\"},\"fatigue_level\":{\"type\":\"string\",\"enum\":[\"低\",\"中\",\"高\"]},\"note\":{\"type\":\"string\"},\"summary\":{\"type\":\"string\"},\"source\":{\"type\":\"string\",\"enum\":[\"manual\",\"chat\",\"import\"]}},\"required\":[\"id\"]}",
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
        BodyMetrics existing = bodyMetricsMapper.selectById(id);
        if (existing == null) {
            return ToolResult.error("身体指标记录不存在: id=" + id);
        }
        if (!authenticatedUser.getUserId().equals(existing.getUserId())) {
            return ToolResult.error("无权修改他人的身体指标记录");
        }

        BodyMetrics patch = new BodyMetrics();
        patch.setId(id);

        Object dateObj = args.get("record_date");
        if (dateObj instanceof String s && !s.isBlank()) {
            try {
                patch.setRecordDate(LocalDate.parse(s));
            } catch (Exception e) {
                return ToolResult.error("record_date 格式错误，应为 yyyy-MM-dd");
            }
        }
        if (args.get("weight") != null) {
            patch.setWeight(asBigDecimal(args.get("weight")));
        }
        if (args.get("body_fat") != null) {
            patch.setBodyFat(asBigDecimal(args.get("body_fat")));
        }
        if (args.get("chest_girth") != null) {
            patch.setChestGirth(asBigDecimal(args.get("chest_girth")));
        }
        if (args.get("waist_girth") != null) {
            patch.setWaistGirth(asBigDecimal(args.get("waist_girth")));
        }
        if (args.get("hip_girth") != null) {
            patch.setHipGirth(asBigDecimal(args.get("hip_girth")));
        }
        if (args.get("arm_girth") != null) {
            patch.setArmGirth(asBigDecimal(args.get("arm_girth")));
        }
        if (args.get("thigh_girth") != null) {
            patch.setThighGirth(asBigDecimal(args.get("thigh_girth")));
        }
        if (args.get("sleep_hours") != null) {
            patch.setSleepHours(asBigDecimal(args.get("sleep_hours")));
        }
        Object fatigueObj = args.get("fatigue_level");
        if (fatigueObj instanceof String s && !s.isBlank()) {
            patch.setFatigueLevel(s.trim());
        }
        Object noteObj = args.get("note");
        if (noteObj instanceof String s && !s.isBlank()) {
            patch.setNote(s.trim());
        }
        Object summaryObj = args.get("summary");
        if (summaryObj instanceof String s && !s.isBlank()) {
            patch.setSummary(s.trim());
        }
        Object sourceObj = args.get("source");
        if (sourceObj instanceof String s && !s.isBlank()) {
            patch.setSource(s.trim());
        }
        patch.setUpdatedAt(LocalDateTime.now());

        int updated = bodyMetricsMapper.updateById(patch);
        if (updated > 0) {
            return ToolResult.ok("身体指标更新成功 id=" + id, Map.of("id", id, "updated", updated));
        }
        return ToolResult.error("身体指标更新失败，或记录不存在");
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
