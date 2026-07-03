package com.itgeo.fitmate.api.fitness.diet.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 饮食明细 DTO。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DietItemDTO {
    /** 食物名称。 */
    private String name;
    /** 份量描述。 */
    private String portion;
    /** 热量 kcal。 */
    private Integer calories;
    /** 蛋白质 g。 */
    private BigDecimal protein;
    /** 碳水 g。 */
    private BigDecimal carbs;
    /** 脂肪 g。 */
    private BigDecimal fat;
}
