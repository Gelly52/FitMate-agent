package com.itgeo.fitmate.api.fitness.training.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 训练派生指标 DTO，供前端 aside 展示。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrainingSummaryDTO {
    /** 最近 7 天总训练量。 */
    private BigDecimal weekVolume;
    /** 最近 30 天总训练量。 */
    private BigDecimal monthVolume;
    /** 最近 7 天训练天数。 */
    private Integer weekTrainingDays;
    /** 最近 30 天训练天数。 */
    private Integer monthTrainingDays;
}
