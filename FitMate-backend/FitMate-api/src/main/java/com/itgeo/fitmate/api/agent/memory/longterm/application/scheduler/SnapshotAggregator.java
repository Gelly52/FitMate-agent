package com.itgeo.fitmate.api.agent.memory.longterm.application.scheduler;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriter;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriteRequest;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity.BodyMetrics;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.mapper.BodyMetricsMapper;
import com.itgeo.fitmate.api.fitness.training.infrastructure.entity.TrainingLog;
import com.itgeo.fitmate.api.fitness.training.infrastructure.mapper.TrainingLogMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotAggregator {

    private final TrainingLogMapper trainingLogMapper;
    private final BodyMetricsMapper bodyMetricsMapper;
    private final UserMemoryMapper userMemoryMapper;
    private final MemoryWriter memoryWriter;
    private final MemoryProperties properties;

    @Scheduled(cron = "${fitmate.memory.snapshot.cron:0 0 2 * * *}")
    public void aggregateSnapshots() {
        if (!properties.isEnabled()) {
            return;
        }
        log.info("开始定时快照聚合");
        // 查询所有有训练/体测记录的用户
        List<Long> trainingUsers = trainingLogMapper.selectList(null).stream()
                .map(TrainingLog::getUserId).distinct().collect(Collectors.toList());
        List<Long> metricsUsers = bodyMetricsMapper.selectList(null).stream()
                .map(BodyMetrics::getUserId).distinct().collect(Collectors.toList());
        Set<Long> userIds = new HashSet<>();
        userIds.addAll(trainingUsers);
        userIds.addAll(metricsUsers);

        for (Long userId : userIds) {
            try {
                aggregateForUser(userId);
            } catch (Exception e) {
                log.error("用户快照聚合失败 userId={}", userId, e);
            }
        }
        log.info("定时快照聚合完成，处理 {} 个用户", userIds.size());
    }

    public void aggregateForUser(Long userId) {
        int windowDays = properties.getSnapshot().getWindowDays();
        LocalDate from = LocalDate.now().minusDays(windowDays);

        // 查询最近 N 天训练记录
        List<TrainingLog> trainingLogs = trainingLogMapper.selectList(new LambdaQueryWrapper<TrainingLog>()
                .eq(TrainingLog::getUserId, userId)
                .ge(TrainingLog::getTrainingDate, from));

        // 查询最近 N 天体测记录
        List<BodyMetrics> metrics = bodyMetricsMapper.selectList(new LambdaQueryWrapper<BodyMetrics>()
                .eq(BodyMetrics::getUserId, userId)
                .ge(BodyMetrics::getRecordDate, from));

        if (trainingLogs.isEmpty() && metrics.isEmpty()) {
            return;
        }

        // 聚合
        int trainingDays = trainingLogs.size();
        BigDecimal totalVolume = trainingLogs.stream()
                .map(TrainingLog::getTotalVolume)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String muscleGroups = trainingLogs.stream()
                .map(TrainingLog::getPrimaryMuscleGroup)
                .filter(g -> g != null && !g.isBlank())
                .distinct().collect(Collectors.joining("/"));

        double avgWeight = metrics.stream()
                .map(BodyMetrics::getWeight)
                .filter(w -> w != null && w.compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.averagingDouble(BigDecimal::doubleValue));
        BigDecimal firstWeight = metrics.stream()
                .map(BodyMetrics::getWeight)
                .filter(w -> w != null && w.compareTo(BigDecimal.ZERO) > 0)
                .findFirst().orElse(null);
        BigDecimal lastWeight = metrics.stream()
                .map(BodyMetrics::getWeight)
                .filter(w -> w != null && w.compareTo(BigDecimal.ZERO) > 0)
                .reduce((a, b) -> b).orElse(null);
        double weightChange = (firstWeight != null && lastWeight != null)
                ? lastWeight.subtract(firstWeight).doubleValue() : 0;

        String fatigueLevel = metrics.stream()
                .map(BodyMetrics::getFatigueLevel)
                .filter(f -> f != null && !f.isBlank())
                .collect(Collectors.groupingBy(f -> f, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("未知");

        // 生成自然语言摘要
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("近%d天训练%d次", windowDays, trainingDays));
        if (totalVolume.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format("，总训练量%dkg", totalVolume.intValue()));
        }
        if (avgWeight > 0) {
            sb.append(String.format("，平均体重%.1fkg", avgWeight));
            if (Math.abs(weightChange) > 0.01) {
                sb.append(String.format("（%s%.1fkg）", weightChange > 0 ? "上升" : "下降", Math.abs(weightChange)));
            }
        }
        sb.append("，疲劳水平").append(fatigueLevel);
        if (!muscleGroups.isEmpty()) {
            sb.append("，主要训练").append(muscleGroups);
        }
        String content = sb.toString();

        // metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("period", from + "~" + LocalDate.now());
        Map<String, Object> metricsData = new HashMap<>();
        metricsData.put("training_days", trainingDays);
        metricsData.put("total_volume", totalVolume.intValue());
        if (avgWeight > 0) metricsData.put("avg_weight", Math.round(avgWeight * 10) / 10.0);
        if (weightChange != 0) metricsData.put("weight_change", Math.round(weightChange * 10) / 10.0);
        metadata.put("metrics", metricsData);

        // 归档旧 SNAPSHOT
        List<UserMemory> oldSnapshots = userMemoryMapper.selectList(new LambdaQueryWrapper<UserMemory>()
                .eq(UserMemory::getUserId, userId)
                .eq(UserMemory::getMemoryType, "SNAPSHOT")
                .eq(UserMemory::getStatus, "active"));
        for (UserMemory old : oldSnapshots) {
            old.setStatus("archived");
            old.setExpiredAt(LocalDateTime.now());
            userMemoryMapper.updateById(old);
        }

        // 写入新 SNAPSHOT
        MemoryWriteRequest req = MemoryWriteRequest.builder()
                .userId(userId)
                .memoryType("SNAPSHOT")
                .content(content)
                .metadataJson(JSONUtil.toJsonStr(metadata))
                .source("schedule")
                .build();
        memoryWriter.writeIfNotIgnored(req);
        log.info("用户快照聚合完成 userId={} content={}", userId, content);
    }
}
