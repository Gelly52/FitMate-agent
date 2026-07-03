package com.itgeo.fitmate.api.wiki.dto;

import lombok.Data;

@Data
public class WikiCompileJobItem {
    private Long id;
    private Long spaceId;
    private String triggerType;
    private Long sourceDocId;
    private String status;
    private String errorMessage;
    private String startedAt;
    private String finishedAt;
}
