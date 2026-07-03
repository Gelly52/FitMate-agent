package com.itgeo.fitmate.api.agent.memory.longterm.application.extractor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriter;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriteRequest;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;

class SessionMemoryExtractorTest {

    private ChatModel chatModel;
    private PromptTemplateManager promptTemplateManager;
    private MemoryWriter memoryWriter;
    private MemoryProperties properties;
    private SessionMemoryExtractor extractor;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        promptTemplateManager = mock(PromptTemplateManager.class);
        memoryWriter = mock(MemoryWriter.class);
        properties = new MemoryProperties();
        when(promptTemplateManager.buildMemoryExtractPrompt(any())).thenReturn("prompt");
        extractor = new SessionMemoryExtractor(chatModel, promptTemplateManager, memoryWriter, properties);
    }

    @Test
    void extract_shortConversation_skips() {
        // 2 轮对话，应跳过
        List<Map<String, String>> conversation = List.of(
                Map.of("role", "user", "content", "你好"),
                Map.of("role", "assistant", "content", "你好！有什么可以帮你的？"));

        extractor.extract(1L, 100L, conversation);

        verify(chatModel, never()).call(any(Prompt.class));
        verify(memoryWriter, never()).writeIfNotIgnored(any());
    }

    @Test
    void extract_validConversation_noMemoriesReturned_writesNothing() {
        List<Map<String, String>> conversation = List.of(
                Map.of("role", "user", "content", "我想咨询一下训练计划，我是个新手，想增肌，身高180体重70kg，每周能练4次"),
                Map.of("role", "assistant", "content", "好的，根据你的情况，我建议..."),
                Map.of("role", "user", "content", "明白了，谢谢"),
                Map.of("role", "assistant", "content", "不客气，有问题随时找我"));

        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse("{\"memories\": []}"));

        extractor.extract(1L, 100L, conversation);

        verify(memoryWriter, never()).writeIfNotIgnored(any());
    }

    @Test
    void extract_validConversation_memoriesReturned_writesEach() {
        List<Map<String, String>> conversation = List.of(
                Map.of("role", "user", "content", "我身高180体重70kg，目标是增肌到80kg，每周能练4次，有轻微腰椎间盘突出"),
                Map.of("role", "assistant", "content", "了解你的情况。根据你的腰椎问题，我建议避免大重量深蹲..."),
                Map.of("role", "user", "content", "好的，那我应该怎么调整训练？"),
                Map.of("role", "assistant", "content", "建议改为推拉腿分化，腿部以罗马尼亚硬拉和腿弯举为主..."));

        String llmOutput = "{\"memories\":[{\"type\":\"FACT\",\"content\":\"用户身高180cm体重70kg，目标增肌到80kg\",\"metadata\":{\"category\":\"body_condition\",\"tags\":[\"增肌\",\"身高180\"]}},{\"type\":\"FACT\",\"content\":\"用户有轻微腰椎间盘突出\",\"metadata\":{\"category\":\"condition\",\"tags\":[\"腰椎间盘突出\"]}},{\"type\":\"INSIGHT\",\"content\":\"该用户适合推拉腿分化训练，腿部避免大重量深蹲\",\"metadata\":{\"category\":\"training_style\",\"tags\":[\"推拉腿分化\"],\"confidence\":0.85}}]}";
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse(llmOutput));
        when(memoryWriter.writeIfNotIgnored(any())).thenReturn(true);

        extractor.extract(1L, 100L, conversation);

        ArgumentCaptor<MemoryWriteRequest> captor = ArgumentCaptor.forClass(MemoryWriteRequest.class);
        verify(memoryWriter, times(3)).writeIfNotIgnored(captor.capture());
        List<MemoryWriteRequest> requests = captor.getAllValues();
        assertEquals("FACT", requests.get(0).getMemoryType());
        assertEquals("session:100", requests.get(0).getSource());
        assertEquals("INSIGHT", requests.get(2).getMemoryType());
    }

    @Test
    void extract_llmReturnsInvalidJson_writesNothing_noException() {
        List<Map<String, String>> conversation = List.of(
                Map.of("role", "user", "content", "我身高180体重70kg，目标是增肌到80kg，每周能练4次，有轻微腰椎间盘突出"),
                Map.of("role", "assistant", "content", "好的..."),
                Map.of("role", "user", "content", "怎么调整？"),
                Map.of("role", "assistant", "content", "建议..."));

        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse("invalid json"));

        // 不应抛异常
        assertDoesNotThrow(() -> extractor.extract(1L, 100L, conversation));
        verify(memoryWriter, never()).writeIfNotIgnored(any());
    }

    private ChatResponse mockChatResponse(String text) {
        // Spring AI 1.1.0 中 Generation.getOutput() 与 ChatResponse.getResult() 均为 final 方法，
        // 无法被 Mockito stub，改用真实实例构造 ChatResponse。
        Generation generation = new Generation(new AssistantMessage(text));
        return new ChatResponse(List.of(generation));
    }
}
