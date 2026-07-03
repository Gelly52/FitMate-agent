package com.itgeo.fitmate.mcp.fitness.training;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itgeo.fitmate.domain.common.ListSortEnum;
import com.itgeo.fitmate.mcp.fitness.training.infrastructure.entity.TrainingExercise;
import com.itgeo.fitmate.mcp.fitness.training.infrastructure.entity.TrainingLog;
import com.itgeo.fitmate.mcp.fitness.training.infrastructure.mapper.TrainingExerciseMapper;
import com.itgeo.fitmate.mcp.fitness.training.infrastructure.mapper.TrainingLogMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class TrainingLogTool {

    private final TrainingLogMapper trainingLogMapper;
    private final TrainingExerciseMapper trainingExerciseMapper;

    public TrainingLogTool(TrainingLogMapper trainingLogMapper,
                           TrainingExerciseMapper trainingExerciseMapper) {
        this.trainingLogMapper = trainingLogMapper;
        this.trainingExerciseMapper = trainingExerciseMapper;
    }

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CreateTrainingLogRequest {
        @ToolParam(description = "当前用户ID")
        private Long userId;

        @ToolParam(description = "本次训练日期，格式 yyyy-MM-dd")
        private String trainingDate;

        @ToolParam(description = "本次训练摘要")
        private String summary;

        @ToolParam(description = "本次训练主要肌群", required = false)
        private String primaryMuscleGroup;

        @ToolParam(description = "本次训练总训练量", required = false)
        private BigDecimal totalVolume;

        @ToolParam(description = "训练记录来源：manual/chat/import", required = false)
        private String source;
    }

    @Tool(description = "新增一条训练日志记录")
    public String createTrainingLog(CreateTrainingLogRequest request) {
        log.info("调用MCP工具：createTrainingLog");
        log.info("训练日志请求参数 request：{}", request);

        if (request == null || request.getUserId() == null || StringUtils.isBlank(request.getTrainingDate())) {
            return "训练日志创建失败：userId 和 trainingDate 不能为空";
        }

        TrainingLog trainingLog = new TrainingLog();
        trainingLog.setUserId(request.getUserId());
        trainingLog.setTrainingDate(LocalDate.parse(request.getTrainingDate()));
        trainingLog.setSummary(request.getSummary());
        trainingLog.setPrimaryMuscleGroup(request.getPrimaryMuscleGroup());
        trainingLog.setTotalVolume(request.getTotalVolume() == null ? BigDecimal.ZERO : request.getTotalVolume());
        trainingLog.setSource(request.getSource() == null ? "manual" : request.getSource());
        trainingLog.setCreatedAt(LocalDateTime.now());
        trainingLog.setUpdatedAt(LocalDateTime.now());

        trainingLogMapper.insert(trainingLog);
        return "训练日志创建成功，ID=" + trainingLog.getId();
    }

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QueryTrainingLogRequest {
        @ToolParam(description = "查询用户ID", required = false)
        private Long userId;

        @ToolParam(description = "训练日期，格式 yyyy-MM-dd", required = false)
        private String trainingDate;

        @ToolParam(description = "训练主要肌群", required = false)
        private String primaryMuscleGroup;

        @ToolParam(description = "训练记录来源：manual/chat/import", required = false)
        private String source;

        @ToolParam(description = "训练摘要关键字", required = false)
        private String summaryKeyword;

        @ToolParam(description = "排序方式：asc/desc", required = false)
        private ListSortEnum sortEnum;
    }

    @Tool(description = "按条件查询训练日志记录")
    public List<TrainingLog> queryTrainingLogs(QueryTrainingLogRequest request) {
        log.info("调用MCP工具：queryTrainingLogs");
        log.info("训练日志查询参数 request：{}", request);

        QueryWrapper<TrainingLog> queryWrapper = new QueryWrapper<>();

        if (request.getUserId() != null) {
            queryWrapper.eq("user_id", request.getUserId());
        }
        if (request.getTrainingDate() != null && !request.getTrainingDate().isBlank()) {
            queryWrapper.eq("training_date", request.getTrainingDate());
        }
        if (request.getPrimaryMuscleGroup() != null && !request.getPrimaryMuscleGroup().isBlank()) {
            queryWrapper.like("primary_muscle_group", request.getPrimaryMuscleGroup());
        }
        if (StringUtils.isNotBlank(request.getSource())) {
            queryWrapper.eq("source", request.getSource().trim());
        }
        if (StringUtils.isNotBlank(request.getSummaryKeyword())) {
            queryWrapper.like("summary", request.getSummaryKeyword().trim());
        }

        ListSortEnum sortEnum = request.getSortEnum();
        if (sortEnum == ListSortEnum.ASC) {
            queryWrapper.orderByAsc("training_date").orderByAsc("created_at");
        } else {
            queryWrapper.orderByDesc("training_date").orderByDesc("created_at");
        }
        return trainingLogMapper.selectList(queryWrapper);
    }

    @Tool(description = "根据训练日志ID查询动作明细")
    public List<TrainingExercise> queryTrainingExercises(
            @ToolParam(description = "训练日志ID") Long trainingLogId
    ) {
        log.info("调用MCP工具：queryTrainingExercises, trainingLogId={}", trainingLogId);

        QueryWrapper<TrainingExercise> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("training_log_id", trainingLogId);
        queryWrapper.orderByAsc("order_num");

        return trainingExerciseMapper.selectList(queryWrapper);
    }

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CreateTrainingExerciseRequest {
        @ToolParam(description = "所属训练日志ID")
        private Long trainingLogId;

        @ToolParam(description = "训练动作名称")
        private String exerciseName;

        @ToolParam(description = "动作组数", required = false)
        private Integer sets;

        @ToolParam(description = "每组次数", required = false)
        private Integer reps;

        @ToolParam(description = "动作重量kg", required = false)
        private BigDecimal weight;

        @ToolParam(description = "动作执行顺序", required = false)
        private Integer orderNum;

        @ToolParam(description = "动作对应肌群", required = false)
        private String estimatedMuscleGroup;
    }

    @Tool(description = "新增一条训练动作明细")
    public String createTrainingExercise(CreateTrainingExerciseRequest request) {
        log.info("调用MCP工具：createTrainingExercise");
        log.info("训练动作请求参数 request：{}", request);

        if (request == null || request.getTrainingLogId() == null || StringUtils.isBlank(request.getExerciseName())) {
            return "训练动作创建失败：trainingLogId 和 exerciseName 不能为空";
        }

        TrainingExercise trainingExercise = new TrainingExercise();
        trainingExercise.setTrainingLogId(request.getTrainingLogId());
        trainingExercise.setExerciseName(request.getExerciseName().trim());
        trainingExercise.setSets(request.getSets() == null ? 1 : request.getSets());
        trainingExercise.setReps(request.getReps() == null ? 1 : request.getReps());
        trainingExercise.setWeight(request.getWeight() == null ? BigDecimal.ZERO : request.getWeight());
        trainingExercise.setOrderNum(request.getOrderNum() == null ? 0 : request.getOrderNum());
        trainingExercise.setEstimatedMuscleGroup(trimToNull(request.getEstimatedMuscleGroup()));
        trainingExercise.setCreatedAt(LocalDateTime.now());

        trainingExerciseMapper.insert(trainingExercise);
        return "训练动作创建成功，ID=" + trainingExercise.getId();
    }

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ModifyTrainingLogRequest {
        @ToolParam(description = "训练日志ID")
        private Long id;

        @ToolParam(description = "修改后的训练日期，格式 yyyy-MM-dd", required = false)
        private String trainingDate;

        @ToolParam(description = "修改后的训练摘要", required = false)
        private String summary;

        @ToolParam(description = "修改后的主要训练肌群", required = false)
        private String primaryMuscleGroup;

        @ToolParam(description = "修改后的总训练量", required = false)
        private BigDecimal totalVolume;

        @ToolParam(description = "修改后的训练记录来源：manual/chat/import", required = false)
        private String source;
    }

    @Tool(description = "根据训练日志ID修改训练日志内容")
    public String modifyTrainingLog(ModifyTrainingLogRequest request) {
        log.info("调用MCP工具：modifyTrainingLog");
        log.info("训练日志修改参数 request：{}", request);

        if (request == null || request.getId() == null) {
            return "训练日志修改失败：id 不能为空";
        }

        TrainingLog trainingLog = new TrainingLog();
        trainingLog.setId(request.getId());

        if (StringUtils.isNotBlank(request.getTrainingDate())) {
            trainingLog.setTrainingDate(LocalDate.parse(request.getTrainingDate()));
        }
        if (StringUtils.isNotBlank(request.getSummary())) {
            trainingLog.setSummary(request.getSummary().trim());
        }
        if (StringUtils.isNotBlank(request.getPrimaryMuscleGroup())) {
            trainingLog.setPrimaryMuscleGroup(request.getPrimaryMuscleGroup().trim());
        }
        if (request.getTotalVolume() != null) {
            trainingLog.setTotalVolume(request.getTotalVolume());
        }
        if (StringUtils.isNotBlank(request.getSource())) {
            trainingLog.setSource(request.getSource().trim());
        }
        trainingLog.setUpdatedAt(LocalDateTime.now());

        int update = trainingLogMapper.updateById(trainingLog);
        return update > 0 ? "训练日志更新成功" : "训练日志更新失败，或记录不存在";
    }

    @Transactional
    @Tool(description = "根据训练日志ID删除训练日志及动作明细")
    public String deleteTrainingLog(
            @ToolParam(description = "训练日志ID") Long trainingLogId
    ) {
        log.info("调用MCP工具：deleteTrainingLog, trainingLogId={}", trainingLogId);

        if (trainingLogId == null) {
            return "训练日志删除失败：trainingLogId 不能为空";
        }

        QueryWrapper<TrainingExercise> exerciseQuery = new QueryWrapper<>();
        exerciseQuery.eq("training_log_id", trainingLogId);
        trainingExerciseMapper.delete(exerciseQuery);

        int deleted = trainingLogMapper.deleteById(trainingLogId);
        return deleted > 0 ? "训练日志删除成功" : "训练日志删除失败，或记录不存在";
    }

    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

}
