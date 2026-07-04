package com.itgeo.fitmate.api.fitness.training.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.fitness.training.application.TrainingService;
import com.itgeo.fitmate.api.fitness.training.domain.MuscleGroupDictionary;
import com.itgeo.fitmate.api.fitness.training.dto.DateSummaryItem;
import com.itgeo.fitmate.api.fitness.training.dto.TrainingExerciseItem;
import com.itgeo.fitmate.api.fitness.training.dto.TrainingLogRequest;
import com.itgeo.fitmate.api.fitness.training.dto.TrainingSummaryDTO;
import com.itgeo.fitmate.api.fitness.training.infrastructure.entity.TrainingExercise;
import com.itgeo.fitmate.api.fitness.training.infrastructure.entity.TrainingLog;
import com.itgeo.fitmate.api.fitness.training.infrastructure.mapper.TrainingExerciseMapper;
import com.itgeo.fitmate.api.fitness.training.infrastructure.mapper.TrainingLogMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 训练日志服务实现，负责训练主记录与动作明细的写入查询。
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class TrainingServiceImpl implements TrainingService {

    private final TrainingLogMapper trainingLogMapper;

    private final TrainingExerciseMapper trainingExerciseMapper;

    public TrainingServiceImpl(TrainingLogMapper trainingLogMapper,
                               TrainingExerciseMapper trainingExerciseMapper) {
        this.trainingLogMapper = trainingLogMapper;
        this.trainingExerciseMapper = trainingExerciseMapper;
    }

    /**
     * 记录训练日志。
     * 处理流程：
     * 1. 校验请求并筛出有效训练动作；
     * 2. 解析训练日期并计算总训练量；
     * 3. 生成摘要并写入或更新训练主记录；
     * 4. 重建并保存训练动作明细。
     */
    @Override
    public void logTraining(Long userId, TrainingLogRequest request) {
        logTraining(userId, request, "manual");
    }

    @Override
    public void logTraining(Long userId, TrainingLogRequest request, String source) {
        // 先校验用户、训练日期和动作列表，并过滤掉无效动作名。
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (request.getDate() == null || request.getDate().isBlank()) {
            throw new IllegalArgumentException("训练日期不能为空");
        }
        if (request.getExercises() == null || request.getExercises().isEmpty()) {
            throw new IllegalArgumentException("训练动作不能为空");
        }
        List<TrainingExerciseItem> validExercises = request.getExercises().stream()
                .filter(item -> item != null && item.getName() != null && !item.getName().trim().isBlank())
                .toList();
        if (validExercises.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个有效训练动作");
        }

        // 解析训练日期，并按 sets * reps * weight 计算本次训练总量。
        LocalDate trainingDate = LocalDate.parse(request.getDate());
        BigDecimal totalVolume = BigDecimal.ZERO;
        for (TrainingExerciseItem item : validExercises) {
            int sets = item.getSets() == null || item.getSets() <= 0 ? 1 : item.getSets();
            int reps = item.getReps() == null || item.getReps() <= 0 ? 1 : item.getReps();
            BigDecimal weight = item.getWeight() == null ? BigDecimal.ZERO : item.getWeight();

            BigDecimal volume = weight.multiply(BigDecimal.valueOf((long) sets * reps));
            totalVolume = totalVolume.add(volume);
        }

        // 推断主肌群：统计每个动作匹配的肌群频次，取最高
        Map<String, Integer> muscleGroupCount = new HashMap<>();
        for (TrainingExerciseItem item : validExercises) {
            String group = MuscleGroupDictionary.inferMuscleGroup(item.getName());
            if (group != null) {
                muscleGroupCount.merge(group, 1, Integer::sum);
            }
        }
        String primaryMuscleGroup = muscleGroupCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        // 生成训练摘要，并按当天是否已有记录执行更新或新增。
        String summary = buildTrainingSummary(validExercises);
        TrainingLog existing = trainingLogMapper.selectOne(
                new LambdaQueryWrapper<TrainingLog>()
                        .eq(TrainingLog::getUserId, userId)
                        .eq(TrainingLog::getTrainingDate, trainingDate)
                        .last("limit 1")
        );
        Long trainingLogId = null;
        if (existing != null) {
            existing.setSummary(summary);
            existing.setTotalVolume(totalVolume);
            existing.setPrimaryMuscleGroup(primaryMuscleGroup);
            existing.setSource(source);
            trainingLogMapper.updateById(existing);
            trainingLogId = existing.getId();
            trainingExerciseMapper.delete(
                    new LambdaQueryWrapper<TrainingExercise>()
                            .eq(TrainingExercise::getTrainingLogId, trainingLogId)
            );
        } else {
            TrainingLog log = new TrainingLog();
            log.setUserId(userId);
            log.setTrainingDate(trainingDate);
            log.setSummary(summary);
            log.setPrimaryMuscleGroup(primaryMuscleGroup);
            log.setTotalVolume(totalVolume);
            log.setSource(source);
            trainingLogMapper.insert(log);
            trainingLogId = log.getId();
        }

        // 使用筛选后的动作列表重建训练明细，确保主表与详情表保持一致。
        for (int i = 0; i < validExercises.size(); i++) {
            TrainingExerciseItem item = validExercises.get(i);

            TrainingExercise exercise = new TrainingExercise();
            exercise.setTrainingLogId(trainingLogId);
            exercise.setExerciseName(item.getName().trim());
            exercise.setSets(item.getSets() == null || item.getSets() <= 0 ? 1 : item.getSets());
            exercise.setReps(item.getReps() == null || item.getReps() <= 0 ? 1 : item.getReps());
            exercise.setWeight(item.getWeight() == null ? BigDecimal.ZERO : item.getWeight());
            exercise.setOrderNum(i + 1);
            exercise.setEstimatedMuscleGroup(null);

            trainingExerciseMapper.insert(exercise);
        }


    }

    /**
     * 基于动作列表拼接训练摘要文本。
     */
    private String buildTrainingSummary(List<TrainingExerciseItem> exercises) {
        return exercises.stream()
                .limit(3)
                .map(item -> {
                    int sets = item.getSets() == null || item.getSets() <= 0 ? 1 : item.getSets();
                    int reps = item.getReps() == null || item.getReps() <= 0 ? 1 : item.getReps();
                    BigDecimal weight = item.getWeight() == null ? BigDecimal.ZERO : item.getWeight();
                    return item.getName().trim() + " " + sets + "组x" + reps + "次，" +
                            weight.stripTrailingZeros().toPlainString() + "kg";
                })
                .collect(Collectors.joining("；"));
    }

    /**
     * 查询最近训练摘要。
     * 处理流程：
     * 1. 校验用户参数；
     * 2. 规范查询条数并按日期倒序查询；
     * 3. 转换为日期摘要列表返回。
     */
    @Override
    public List<DateSummaryItem> getRecentTraining(Long userId, Integer limit) {
        // 校验用户参数。
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }

        // 规范返回条数并查询最近训练记录。
        int safeLimit = (limit == null || limit <= 0) ? 5 : Math.min(limit, 20);
        List<TrainingLog> logs = trainingLogMapper.selectList(
                new LambdaQueryWrapper<TrainingLog>()
                        .eq(TrainingLog::getUserId, userId)
                        .orderByDesc(TrainingLog::getTrainingDate)
                        .last("limit " + safeLimit)
        );

        // 转换为日期摘要列表返回。
        return logs.stream()
                .map(log -> new DateSummaryItem(
                        log.getTrainingDate() == null ? null : log.getTrainingDate().toString(),
                        log.getSummary()))
                .collect(Collectors.toList());
    }

    /**
     * 查询训练派生指标（周/月训练量与训练天数）。
     * 处理流程：
     * 1. 校验用户参数；
     * 2. 查询最近 30 天训练记录；
     * 3. 遍历统计周/月训练量与训练天数。
     */
    @Override
    public TrainingSummaryDTO getTrainingSummary(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }
        LocalDate now = LocalDate.now();
        LocalDate weekAgo = now.minusDays(7);
        LocalDate monthAgo = now.minusDays(30);

        List<TrainingLog> logs = trainingLogMapper.selectList(
                new LambdaQueryWrapper<TrainingLog>()
                        .eq(TrainingLog::getUserId, userId)
                        .ge(TrainingLog::getTrainingDate, monthAgo)
        );

        BigDecimal weekVolume = BigDecimal.ZERO;
        BigDecimal monthVolume = BigDecimal.ZERO;
        int weekDays = 0;
        int monthDays = 0;
        for (TrainingLog log : logs) {
            monthVolume = monthVolume.add(log.getTotalVolume() == null ? BigDecimal.ZERO : log.getTotalVolume());
            monthDays++;
            if (log.getTrainingDate() != null && !log.getTrainingDate().isBefore(weekAgo)) {
                weekVolume = weekVolume.add(log.getTotalVolume() == null ? BigDecimal.ZERO : log.getTotalVolume());
                weekDays++;
            }
        }
        return new TrainingSummaryDTO(weekVolume, monthVolume, weekDays, monthDays);
    }
}
