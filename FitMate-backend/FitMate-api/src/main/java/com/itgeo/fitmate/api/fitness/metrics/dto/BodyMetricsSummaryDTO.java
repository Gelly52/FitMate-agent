package com.itgeo.fitmate.api.fitness.metrics.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 身体指标派生指标 DTO，供前端 aside 展示。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BodyMetricsSummaryDTO {
    /** BMI，身高未配置时为 null。 */
    private BigDecimal bmi;
    /** 体重变化率（%），最新体重相对上一次的变化率。 */
    private BigDecimal weightChangeRate;
    /** 最新体重。 */
    private BigDecimal latestWeight;
    /** 上一次体重。 */
    private BigDecimal previousWeight;
}
