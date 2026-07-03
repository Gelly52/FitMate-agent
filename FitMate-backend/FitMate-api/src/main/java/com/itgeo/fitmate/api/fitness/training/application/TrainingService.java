package com.itgeo.fitmate.api.fitness.training.application;

import com.itgeo.fitmate.api.fitness.training.dto.DateSummaryItem;
import com.itgeo.fitmate.api.fitness.training.dto.TrainingLogRequest;
import java.util.List;

/**
 * 训练日志服务接口。
 */
public interface TrainingService {
    /**
     * 记录指定用户的训练日志。
     *
     * @param userId 用户主键
     * @param trainingLogRequest 训练日志请求体
     */
    void logTraining(Long userId, TrainingLogRequest trainingLogRequest);

    /**
     * 记录训练日志（指定来源）。
     *
     * @param userId 用户ID
     * @param request 请求
     * @param source 来源：manual/chat/import
     */
    void logTraining(Long userId, TrainingLogRequest request, String source);

    /**
     * 查询指定用户最近的训练摘要。
     *
     * @param userId 用户主键
     * @param limit 返回条数
     * @return 日期摘要列表
     */
    List<DateSummaryItem> getRecentTraining(Long userId, Integer limit);
}
