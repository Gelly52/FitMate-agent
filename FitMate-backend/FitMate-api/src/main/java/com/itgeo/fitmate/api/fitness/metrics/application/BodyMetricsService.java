package com.itgeo.fitmate.api.fitness.metrics.application;

import com.itgeo.fitmate.api.fitness.metrics.dto.BodyMetricsLogRequest;
import com.itgeo.fitmate.api.fitness.training.dto.DateSummaryItem;
import java.util.List;

/**
 * 身体指标服务接口。
 */
public interface BodyMetricsService {
    /**
     * 记录指定用户的身体指标。
     *
     * @param userId 用户主键
     * @param request 身体指标请求体
     */
    void logBodyMetrics(Long userId, BodyMetricsLogRequest request);

    /**
     * 记录身体指标（指定来源）。
     *
     * @param userId 用户ID
     * @param request 请求
     * @param source 来源：manual/chat/import
     */
    void logBodyMetrics(Long userId, BodyMetricsLogRequest request, String source);

    /**
     * 查询指定用户最近的身体指标摘要。
     *
     * @param userId 用户主键
     * @param limit 返回条数
     * @return 日期摘要列表
     */
    List<DateSummaryItem> getRecentBodyMetrics(Long userId, Integer limit);
}
