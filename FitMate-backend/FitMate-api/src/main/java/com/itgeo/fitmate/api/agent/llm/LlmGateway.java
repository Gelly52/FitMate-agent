package com.itgeo.fitmate.api.agent.llm;

import com.itgeo.fitmate.api.chat.dto.ReasoningStreamChunk;
import reactor.core.publisher.Flux;

/**
 * Agent 专用模型访问边界，不直接暴露任何工具回调。
 */
public interface LlmGateway {

    String call(String prompt);

    Flux<String> stream(String prompt);

    default ReasoningStreamChunk callWithReasoning(String prompt) {
        return new ReasoningStreamChunk("", call(prompt));
    }

    default Flux<ReasoningStreamChunk> streamWithReasoning(String prompt) {
        return stream(prompt).map(content -> new ReasoningStreamChunk("", content));
    }
}
