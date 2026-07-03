package com.itgeo.fitmate.api.fitness.diet.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.ToString;

/**
 * 饮食日志主表实体。
 */
@Data
@ToString
@TableName("t_diet_log")
public class DietLog {
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("record_date")
    private LocalDate recordDate;
    @TableField("meal_type")
    private String mealType;
    @TableField("total_calories")
    private Integer totalCalories;
    @TableField("total_protein")
    private BigDecimal totalProtein;
    @TableField("total_carbs")
    private BigDecimal totalCarbs;
    @TableField("total_fat")
    private BigDecimal totalFat;
    private String note;
    private String summary;
    private String source;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 查询时关联的明细列表（非数据库字段）。 */
    @TableField(exist = false)
    private List<DietItem> items;
}
