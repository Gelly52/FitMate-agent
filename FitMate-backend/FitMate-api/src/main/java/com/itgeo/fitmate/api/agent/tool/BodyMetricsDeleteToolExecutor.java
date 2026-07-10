package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity.BodyMetrics;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.mapper.BodyMetricsMapper;
import jakarta.annotation.Resource;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 身体指标删除工具（按 id 删除）。
 * <p>
 * 直连 Mapper，与本地 query 工具风格一致。
 * 校验记录归属当前用户，防止越权删除。
 */
@Component
public class BodyMetricsDeleteToolExecutor implements ToolExecutor {

    @Resource
    private BodyMetricsMapper bodyMetricsMapper;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "body_metrics.delete",
                "按身体指标记录ID删除对应记录。参数: {\"id\":Long}",
                "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"description\":\"身体指标记录ID，必填\"}},\"required\":[\"id\"]}",
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
            return ToolResult.error("无权删除他人的身体指标记录");
        }

        int deleted = bodyMetricsMapper.deleteById(id);
        if (deleted > 0) {
            return ToolResult.ok("身体指标删除成功 id=" + id, Map.of("id", id, "deleted", deleted));
        }
        return ToolResult.error("身体指标删除失败，或记录不存在");
    }
}
