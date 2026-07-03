package com.itgeo.fitmate.api.fitness.cardio.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 有氧训练日志实体。
 */
@Data
@ToString
@TableName("t_cardio_log")
public class CardioLog {
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("training_date")
    private LocalDate trainingDate;
    @TableField("cardio_type")
    private String cardioType;
    @TableField("distance_km")
    private BigDecimal distanceKm;
    @TableField("duration_minutes")
    private Integer durationMinutes;
    @TableField("avg_pace")
    private String avgPace;
    @TableField("avg_heart_rate")
    private Integer avgHeartRate;
    @TableField("calories_burned")
    private Integer caloriesBurned;
    private String note;
    private String summary;
    private String source;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
