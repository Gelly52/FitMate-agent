package com.itgeo.fitmate.api.fitness.cardio.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 有氧训练记录请求。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CardioLogRequest {
    /** 训练日期，格式 yyyy-MM-dd。 */
    private String date;
    /** 类型：running/cycling/swimming/rowing/jump_rope/other。 */
    private String cardioType;
    /** 距离 km。 */
    private BigDecimal distanceKm;
    /** 时长 分钟。 */
    private Integer durationMinutes;
    /** 平均心率。 */
    private Integer avgHeartRate;
    /** 备注。 */
    private String note;
}
