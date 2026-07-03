package com.itgeo.fitmate.api.fitness.training.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 用户训练日志实体。
 */
@Data
@ToString
@TableName("t_training_log")
public class TrainingLog {
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("training_date")
    private LocalDate trainingDate;
    /** 摘要展示文本。 */
    private String summary;
    @TableField("primary_muscle_group")
    /** 主要训练肌群。 */
    private String primaryMuscleGroup;
    @TableField("total_volume")
    /** 总训练量。 */
    private BigDecimal totalVolume;
    /** 记录来源。 */
    private String source;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
