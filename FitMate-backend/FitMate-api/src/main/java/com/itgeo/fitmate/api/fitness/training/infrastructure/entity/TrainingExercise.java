package com.itgeo.fitmate.api.fitness.training.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 训练日志下的动作明细实体。
 */
@Data
@ToString
@TableName("t_training_exercise")
public class TrainingExercise {
    private Long id;
    @TableField("training_log_id")
    private Long trainingLogId;
    @TableField("exercise_name")
    private String exerciseName;
    private Integer sets;
    private Integer reps;
    private BigDecimal weight;
    @TableField("order_num")
    /** 动作排序号。 */
    private Integer orderNum;
    @TableField("estimated_muscle_group")
    /** 估算肌群。 */
    private String estimatedMuscleGroup;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
