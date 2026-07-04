package com.itgeo.fitmate.api.fitness.cardio.application;

import com.itgeo.fitmate.api.fitness.cardio.dto.CardioLogRequest;

/**
 * 有氧训练记录服务。
 */
public interface CardioService {

    /**
     * 记录有氧训练（upsert，按 userId + date + cardioType 唯一键）。
     *
     * @param userId 用户ID
     * @param request 请求
     * @param source 来源：manual/chat/import
     */
    void logCardio(Long userId, CardioLogRequest request, String source);

    /**
     * 查询最近有氧训练摘要。
     *
     * @param userId 用户ID
     * @param limit  返回条数
     * @return 日期摘要列表
     */
    java.util.List<com.itgeo.fitmate.api.fitness.training.dto.DateSummaryItem> getRecentCardio(Long userId, Integer limit);
}
