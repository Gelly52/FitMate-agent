package com.itgeo.fitmate.api.agent.llm;

import com.itgeo.fitmate.api.chat.dto.ReasoningStreamChunk;
import com.itgeo.fitmate.api.chat.infrastructure.ReasoningChatClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 基于 Spring AI ChatClient 的受控模型网关。
 */
@Component
public class SpringAiLlmGateway implements LlmGateway {

    private final ChatClient chatClient;
    private final ReasoningChatClient reasoningChatClient;

    public SpringAiLlmGateway(ChatClient.Builder chatClientBuilder,
                              ReasoningChatClient reasoningChatClient) {
        this.chatClient = chatClientBuilder.build();
        this.reasoningChatClient = reasoningChatClient;
    }

    @Override
    public String call(String prompt) {
        return chatClient.prompt(new Prompt(prompt)).call().content();
    }

    @Override
    public Flux<String> stream(String prompt) {
        return chatClient.prompt(new Prompt(prompt)).stream().content();
    }

    @Override
    public ReasoningStreamChunk callWithReasoning(String prompt) {
        return reasoningChatClient.call(prompt);
    }

    @Override
    public Flux<ReasoningStreamChunk> streamWithReasoning(String prompt) {
        return reasoningChatClient.stream(prompt);
    }
}
