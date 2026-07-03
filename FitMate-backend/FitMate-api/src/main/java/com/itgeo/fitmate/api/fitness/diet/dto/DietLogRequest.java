package com.itgeo.fitmate.api.fitness.diet.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 饮食日志记录请求。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DietLogRequest {
    /** 记录日期，格式 yyyy-MM-dd。 */
    private String date;
    /** 餐次：breakfast/lunch/dinner/snack。 */
    private String mealType;
    /** 食物明细列表。 */
    private List<DietItemDTO> items;
    /** 备注。 */
    private String note;
}
