package com.itgeo.fitmate.api.wiki.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class WikiCompileJobItem {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long spaceId;
    private String triggerType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceDocId;
    private String status;
    private String errorMessage;
    private String startedAt;
    private String finishedAt;
}
