package com.itgeo.fitmate.api.wiki.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_wiki_log")
public class WikiLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spaceId;
    private String entryType;       // INGEST/QUERY/LINT/COMPILE
    private String entrySummary;
    private String sourceRef;
    private LocalDateTime createdAt;
}
