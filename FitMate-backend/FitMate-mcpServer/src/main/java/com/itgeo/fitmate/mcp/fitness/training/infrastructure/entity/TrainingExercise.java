package com.itgeo.fitmate.mcp.fitness.training.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@TableName("t_training_exercise")
public class TrainingExercise {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long trainingLogId;
    private String exerciseName;
    private Integer sets;
    private Integer reps;
    private BigDecimal weight;
    private Integer orderNum;
    private String estimatedMuscleGroup;
    private LocalDateTime createdAt;
}
