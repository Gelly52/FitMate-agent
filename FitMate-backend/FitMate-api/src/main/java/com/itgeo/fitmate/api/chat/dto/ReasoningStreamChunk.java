package com.itgeo.fitmate.api.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DeepSeek 原生流式分片。
 * reasoningContent 来自 choices[].delta.reasoning_content，content 来自 choices[].delta.content。
 * usage 仅在开启 stream_options.include_usage 后的最后一个 chunk（choices 为空）中存在。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReasoningStreamChunk {
    private String reasoningContent;
    private String content;
    /** 流式终止帧携带的 token 用量，仅在最后一个 chunk 出现，其余分片为 null。 */
    private TokenUsage usage;

    /** 内容分片构造器。 */
    public ReasoningStreamChunk(String reasoningContent, String content) {
        this.reasoningContent = reasoningContent;
        this.content = content;
    }
}
