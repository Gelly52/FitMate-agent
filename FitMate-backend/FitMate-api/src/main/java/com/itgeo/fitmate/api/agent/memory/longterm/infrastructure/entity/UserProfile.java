package com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_user_profile")
public class UserProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String profileText;
    private String profileTagsJson;
    private Integer memoryVersion;
    private LocalDateTime generatedAt;
    private LocalDateTime updatedAt;
}
