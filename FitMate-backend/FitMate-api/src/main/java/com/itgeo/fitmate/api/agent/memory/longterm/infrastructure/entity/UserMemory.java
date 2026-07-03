package com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_user_memory")
public class UserMemory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String memoryType;      // FACT|EPISODIC|SNAPSHOT|INSIGHT
    private String content;
    private String metadataJson;
    private String source;
    private String contentHash;
    private String status;          // active|archived|ignored
    private LocalDateTime expiredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
