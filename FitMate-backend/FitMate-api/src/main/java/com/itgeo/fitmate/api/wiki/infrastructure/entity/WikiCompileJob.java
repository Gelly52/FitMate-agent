package com.itgeo.fitmate.api.wiki.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_wiki_compile_job")
public class WikiCompileJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spaceId;
    private String triggerType;     // DOC_UPLOAD/MANUAL/SCHEDULED/EVENT
    private Long sourceDocId;
    private String status;          // PENDING/RUNNING/SUCCESS/FAILED
    private String pagesTouchedJson;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long createdByUserId;
    private LocalDateTime createdAt;
}
