package com.itgeo.fitmate.api.fitness.diet.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 饮食明细实体。
 */
@Data
@ToString
@TableName("t_diet_item")
public class DietItem {
    private Long id;
    @TableField("diet_log_id")
    private Long dietLogId;
    @TableField("food_name")
    private String foodName;
    private String portion;
    private Integer calories;
    private BigDecimal protein;
    private BigDecimal carbs;
    private BigDecimal fat;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
