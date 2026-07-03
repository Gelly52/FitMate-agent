package com.itgeo.fitmate.api.fitness.metrics.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 身体指标记录请求。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BodyMetricsLogRequest {
    /** 记录日期，格式 yyyy-MM-dd。 */
    private String date;
    /** 体重，单位千克。 */
    private BigDecimal weight;
    /** 体脂率。 */
    private BigDecimal bodyFat;
    /** 胸围 cm。 */
    private BigDecimal chestGirth;
    /** 腰围 cm。 */
    private BigDecimal waistGirth;
    /** 臀围 cm。 */
    private BigDecimal hipGirth;
    /** 臂围 cm。 */
    private BigDecimal armGirth;
    /** 大腿围 cm。 */
    private BigDecimal thighGirth;
    /** 睡眠时长，单位小时。 */
    private BigDecimal sleep;
    /** 疲劳等级。 */
    private String fatigue;
    /** 备注信息。 */
    private String note;
}
