package com.itgeo.fitmate.api.agent.memory.dto;

import com.itgeo.fitmate.api.chat.infrastructure.entity.ContextSummary;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 内存加载结果：最新摘要 + 摘要之后的原始消息。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MemoryLoadResult {
    /** 最新摘要记录，无则 null。 */
    private ContextSummary summary;
    /** 参与拼 prompt 的消息列表（role+content）。 */
    private List<Map<String, String>> messages;
}
