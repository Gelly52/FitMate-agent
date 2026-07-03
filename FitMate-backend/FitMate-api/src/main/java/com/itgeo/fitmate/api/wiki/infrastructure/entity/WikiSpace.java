package com.itgeo.fitmate.api.wiki.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_wiki_space")
public class WikiSpace {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String scopeType;       // GLOBAL / USER
    private Long ownerUserId;       // GLOBAL=null
    private String title;
    private String description;
    private String status;          // ACTIVE / ARCHIVED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
