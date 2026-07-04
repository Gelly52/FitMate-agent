package com.itgeo.fitmate.api.fitness.diet.application;

import com.itgeo.fitmate.api.fitness.diet.dto.DietLogRequest;

/**
 * 饮食记录服务。
 */
public interface DietService {

    /**
     * 记录饮食（upsert，按 userId + date + mealType 唯一键）。
     *
     * @param userId 用户ID
     * @param request 请求
     * @param source 来源：manual/chat/import
     */
    void logDiet(Long userId, DietLogRequest request, String source);

    /**
     * 查询最近饮食摘要。
     *
     * @param userId 用户ID
     * @param limit  返回条数
     * @return 日期摘要列表
     */
    java.util.List<com.itgeo.fitmate.api.fitness.training.dto.DateSummaryItem> getRecentDiet(Long userId, Integer limit);
}
