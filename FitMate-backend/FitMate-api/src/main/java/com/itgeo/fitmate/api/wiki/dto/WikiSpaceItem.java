package com.itgeo.fitmate.api.wiki.dto;

import lombok.Data;

@Data
public class WikiSpaceItem {
    private Long id;
    private String scopeType;
    private Long ownerUserId;
    private String title;
    private String description;
    private String status;
}
