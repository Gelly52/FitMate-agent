package com.itgeo.fitmate.api.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.training.infrastructure.entity.TrainingExercise;
import com.itgeo.fitmate.api.fitness.training.infrastructure.entity.TrainingLog;
import com.itgeo.fitmate.api.fitness.training.infrastructure.mapper.TrainingExerciseMapper;
import com.itgeo.fitmate.api.fitness.training.infrastructure.mapper.TrainingLogMapper;
import jakarta.annotation.Resource;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 训练日志删除工具（按 id 删除，含子表动作明细）。
 * <p>
 * 直连 Mapper，与 TrainingLogQueryToolExecutor 风格一致。
 * 校验记录归属当前用户，防止越权删除。
 * 带 @Transactional 保证主子表删除原子性。
 */
@Component
public class TrainingLogDeleteToolExecutor implements ToolExecutor {

    @Resource
    private TrainingLogMapper trainingLogMapper;

    @Resource
    private TrainingExerciseMapper trainingExerciseMapper;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "training_log.delete",
                "按训练日志ID删除训练日志及其动作明细（事务性）。参数: {\"id\":Long}",
                "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"description\":\"训练日志ID，必填\"}},\"required\":[\"id\"]}",
                false
        );
    }

    @Override
    @Transactional
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
            return ToolResult.error("无权删除他人的训练日志");
        }

        // 先删子表动作明细，再删主表
        trainingExerciseMapper.delete(
                new QueryWrapper<TrainingExercise>().eq("training_log_id", id)
        );
        int deleted = trainingLogMapper.deleteById(id);
        if (deleted > 0) {
            return ToolResult.ok("训练日志删除成功 id=" + id, Map.of("id", id, "deleted", deleted));
        }
        return ToolResult.error("训练日志删除失败，或记录不存在");
    }
}
