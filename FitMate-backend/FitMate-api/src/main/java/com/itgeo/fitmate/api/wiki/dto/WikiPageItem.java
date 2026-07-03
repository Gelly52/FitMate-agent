package com.itgeo.fitmate.api.wiki.dto;

import lombok.Data;

@Data
public class WikiPageItem {
    private Long id;
    private Long spaceId;
    private String pageType;
    private String title;
    private String slug;
    private String contentMd;
    private Integer charCount;
    private String status;
    private String compiledAt;
}
