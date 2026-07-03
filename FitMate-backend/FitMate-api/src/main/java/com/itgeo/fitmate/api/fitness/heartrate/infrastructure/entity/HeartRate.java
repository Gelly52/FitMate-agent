package com.itgeo.fitmate.api.fitness.heartrate.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 心率记录实体。
 */
@Data
@ToString
@TableName("t_heart_rate")
public class HeartRate {
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("record_date")
    private LocalDate recordDate;
    @TableField("resting_hr")
    private Integer restingHr;
    @TableField("max_hr")
    private Integer maxHr;
    private Integer hrv;
    private String note;
    private String summary;
    private String source;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
