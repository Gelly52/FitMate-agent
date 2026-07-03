package com.itgeo.fitmate.api.agent.memory.longterm.application.extractor;

import java.util.List;
import lombok.Data;

@Data
public class MemoryExtractResult {
    private List<ExtractedMemory> memories;

    @Data
    public static class ExtractedMemory {
        private String type;         // FACT|EPISODIC|INSIGHT
        private String content;
        private Object metadata;     // 将被序列化为 JSON 字符串存储
    }
}
