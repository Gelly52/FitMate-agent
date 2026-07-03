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
}
