package com.itgeo.fitmate.api.wiki.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class WikiPageItem {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long spaceId;
    private String pageType;
    private String title;
    private String slug;
    private String contentMd;
    private Integer charCount;
    private String status;
    private String compiledAt;
    private String updatedAt;
}
