package com.itgeo.fitmate.api.agent.memory.longterm.controller.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProfileResponse {
    private String profileText;
    private String profileTagsJson;
    private Integer memoryVersion;
    private LocalDateTime generatedAt;
}
