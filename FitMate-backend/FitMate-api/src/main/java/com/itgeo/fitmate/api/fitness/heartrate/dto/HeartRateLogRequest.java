package com.itgeo.fitmate.api.fitness.heartrate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 心率记录请求。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HeartRateLogRequest {
    /** 记录日期，格式 yyyy-MM-dd。 */
    private String date;
    /** 静息心率。 */
    private Integer restingHr;
    /** 最大心率。 */
    private Integer maxHr;
    /** 心率变异性 ms。 */
    private Integer hrv;
    /** 备注。 */
    private String note;
}
