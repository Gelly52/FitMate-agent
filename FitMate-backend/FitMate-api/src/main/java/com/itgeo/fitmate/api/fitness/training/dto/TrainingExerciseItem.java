package com.itgeo.fitmate.api.fitness.training.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 训练动作请求项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingExerciseItem {
    /** 动作名称。 */
    private String name;
    /** 组数。 */
    private Integer sets;
    /** 每组次数。 */
    private Integer reps;
    /** 负重，单位千克。 */
    private BigDecimal weight;
}
