package com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 用户身体指标记录实体。
 */
@Data
@ToString
@TableName("t_body_metrics")
public class BodyMetrics {
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("record_date")
    private LocalDate recordDate;
    private BigDecimal weight;
    @TableField("body_fat")
    private BigDecimal bodyFat;
    @TableField("chest_girth")
    private BigDecimal chestGirth;
    @TableField("waist_girth")
    private BigDecimal waistGirth;
    @TableField("hip_girth")
    private BigDecimal hipGirth;
    @TableField("arm_girth")
    private BigDecimal armGirth;
    @TableField("thigh_girth")
    private BigDecimal thighGirth;
    @TableField("sleep_hours")
    /** 睡眠时长，单位小时。 */
    private BigDecimal sleepHours;
    @TableField("fatigue_level")
    /** 疲劳等级。 */
    private String fatigueLevel;
    private String note;
    /** 摘要展示文本。 */
    private String summary;
    /** 来源：manual/chat/import。 */
    private String source;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
