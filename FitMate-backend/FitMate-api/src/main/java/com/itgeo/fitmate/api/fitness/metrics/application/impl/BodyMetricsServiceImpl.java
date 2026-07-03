package com.itgeo.fitmate.api.fitness.metrics.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.fitness.metrics.application.BodyMetricsService;
import com.itgeo.fitmate.api.fitness.metrics.dto.BodyMetricsLogRequest;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity.BodyMetrics;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.mapper.BodyMetricsMapper;
import com.itgeo.fitmate.api.fitness.training.dto.DateSummaryItem;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 身体指标服务实现，负责身体指标记录的写入与摘要查询。
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class BodyMetricsServiceImpl implements BodyMetricsService {

    @Resource
    private BodyMetricsMapper bodyMetricsMapper;

    /**
     * 记录身体指标。
     * 处理流程：
     * 1. 校验登录态与请求参数；
     * 2. 解析记录日期并生成摘要；
     * 3. 按用户和日期查询当天记录，存在则更新，否则新增。
     */
    @Override
    public void logBodyMetrics(Long userId, BodyMetricsLogRequest request) {
        // 先校验用户与请求内容，确保至少填写体重或体脂中的一项。
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (request.getDate() == null || request.getDate().isBlank()) {
            throw new IllegalArgumentException("记录日期不能为空");
        }
        if (request.getWeight() == null && request.getBodyFat() == null) {
            throw new IllegalArgumentException("体重和体脂至少填写一项");
        }

        // 解析记录日期并生成用于展示的身体指标摘要。
        LocalDate recordDate = LocalDate.parse(request.getDate());
        String summary = buildBodyMetricsSummary(request);

        // 按“用户 + 日期”查找当天记录，存在则更新，不存在则插入新记录。
        BodyMetrics existing = bodyMetricsMapper.selectOne(
                new LambdaQueryWrapper<BodyMetrics>()
                        .eq(BodyMetrics::getUserId, userId)
                        .eq(BodyMetrics::getRecordDate, recordDate)
                        .last("limit 1")
        );
        if (existing != null) {
            existing.setWeight(request.getWeight());
            existing.setBodyFat(request.getBodyFat());
            existing.setSleepHours(request.getSleep());
            existing.setFatigueLevel(blankToNull(request.getFatigue()));
            existing.setNote(blankToNull(request.getNote()));
            existing.setSummary(summary);
            bodyMetricsMapper.updateById(existing);
        } else {
            BodyMetrics entity = new BodyMetrics();
            entity.setUserId(userId);
            entity.setRecordDate(recordDate);
            entity.setWeight(request.getWeight());
            entity.setBodyFat(request.getBodyFat());
            entity.setSleepHours(request.getSleep());
            entity.setFatigueLevel(blankToNull(request.getFatigue()));
            entity.setNote(blankToNull(request.getNote()));
            entity.setSummary(summary);
            bodyMetricsMapper.insert(entity);
        }

    }

    /**
     * 查询最近身体指标摘要。
     * 处理流程：
     * 1. 校验用户参数；
     * 2. 规范查询条数并按日期倒序查询；
     * 3. 转换为日期摘要列表返回。
     */
    @Override
    public List<DateSummaryItem> getRecentBodyMetrics(Long userId, Integer limit) {
        // 校验用户参数。
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }

        // 规范返回条数并查询最近记录。
        int safeLimit = (limit == null || limit <= 0) ? 5 : Math.min(limit, 20);
        List<BodyMetrics> records = bodyMetricsMapper.selectList(
                new LambdaQueryWrapper<BodyMetrics>()
                        .eq(BodyMetrics::getUserId, userId)
                        .orderByDesc(BodyMetrics::getRecordDate)
                        .last("limit " + safeLimit)
        );

        // 转换为日期摘要列表返回。
        return records.stream()
                .map(record -> new DateSummaryItem(
                        record.getRecordDate() == null ? null : record.getRecordDate().toString(),
                        record.getSummary()))
                .collect(Collectors.toList());
    }

    /**
     * 根据请求字段拼接身体指标摘要。
     */
    private String buildBodyMetricsSummary(BodyMetricsLogRequest request) {
        List<String> parts = new java.util.ArrayList<>();

        if (request.getWeight() != null) {
            parts.add("体重 " + request.getWeight().stripTrailingZeros().toPlainString() + "kg");
        }
        if (request.getBodyFat() != null) {
            parts.add("体脂 " + request.getBodyFat().stripTrailingZeros().toPlainString() + "%");
        }
        if (request.getSleep() != null) {
            parts.add("睡眠 " + request.getSleep().stripTrailingZeros().toPlainString() + "小时");
        }
        if (request.getFatigue() != null && !request.getFatigue().isBlank()) {
            parts.add("疲劳度 " + request.getFatigue().trim());
        }

        if (parts.isEmpty()) {
            return "暂无身体指标";
        }

        return String.join("，", parts);
    }

    /**
     * 将空白字符串归一化为 null。
     */
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
