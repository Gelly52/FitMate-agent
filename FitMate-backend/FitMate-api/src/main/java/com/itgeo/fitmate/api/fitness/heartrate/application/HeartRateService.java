package com.itgeo.fitmate.api.fitness.heartrate.application;

import com.itgeo.fitmate.api.fitness.heartrate.dto.HeartRateLogRequest;

/**
 * 心率记录服务。
 */
public interface HeartRateService {

    /**
     * 记录心率（upsert，按 userId + date 唯一键）。
     *
     * @param userId 用户ID
     * @param request 请求
     * @param source 来源：manual/chat/import
     */
    void logHeartRate(Long userId, HeartRateLogRequest request, String source);
}
