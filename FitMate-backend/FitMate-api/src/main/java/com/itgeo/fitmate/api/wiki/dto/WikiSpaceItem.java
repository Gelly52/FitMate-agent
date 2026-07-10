package com.itgeo.fitmate.api.wiki.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class WikiSpaceItem {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String scopeType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerUserId;
    private String title;
    private String description;
    private String status;
}
