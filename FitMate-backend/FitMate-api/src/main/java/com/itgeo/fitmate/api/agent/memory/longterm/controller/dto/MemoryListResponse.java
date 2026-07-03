package com.itgeo.fitmate.api.agent.memory.longterm.controller.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class MemoryListResponse {
    private List<Item> items;
    private long total;
    private int page;
    private int size;

    @Data
    public static class Item {
        private Long id;
        private String memoryType;
        private String content;
        private String metadataJson;
        private String source;
        private String status;
        private LocalDateTime createdAt;
    }
}
