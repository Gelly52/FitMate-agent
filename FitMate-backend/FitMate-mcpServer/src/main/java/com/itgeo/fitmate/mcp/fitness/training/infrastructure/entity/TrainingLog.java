package com.itgeo.fitmate.mcp.fitness.training.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@TableName("t_training_log")
public class TrainingLog {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;
    private LocalDate trainingDate;
    private String summary;
    private String primaryMuscleGroup;
    private BigDecimal totalVolume;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
