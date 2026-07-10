package com.itgeo.fitmate.api.agent.memory.longterm.application.extractor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryExtractCounter;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriter;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriteRequest;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import com.itgeo.fitmate.api.agent.prompt.AgentPromptBuilder;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.chat.dto.ReasoningStreamChunk;
import com.itgeo.fitmate.api.chat.infrastructure.ReasoningChatClient;
import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

class SessionMemoryExtractorTest {

    private ReasoningChatClient reasoningChatClient;
    private PromptTemplateManager promptTemplateManager;
    private MemoryWriter memoryWriter;
    private MemoryProperties properties;
    private AgentPromptBuilder agentPromptBuilder;
    private UserMemoryMapper userMemoryMapper;
    private MemoryExtractCounter memoryExtractCounter;
    private SessionMemoryExtractor extractor;
    private AuthenticatedUserContext authenticatedUser;

    @BeforeEach
    void setUp() {
        reasoningChatClient = mock(ReasoningChatClient.class);
        promptTemplateManager = mock(PromptTemplateManager.class);
        memoryWriter = mock(MemoryWriter.class);
        properties = new MemoryProperties();
        agentPromptBuilder = mock(AgentPromptBuilder.class);
        userMemoryMapper = mock(UserMemoryMapper.class);
        memoryExtractCounter = mock(MemoryExtractCounter.class);

        when(promptTemplateManager.buildMemoryExtractSuffix(any())).thenReturn("suffix");
        when(agentPromptBuilder.buildPromptPrefix(any(), any(), any(), any())).thenReturn("prefix");
        when(userMemoryMapper.selectList(any())).thenReturn(Collections.emptyList());

        extractor = new SessionMemoryExtractor(
                reasoningChatClient,
                promptTemplateManager,
                memoryWriter,
                properties,
                agentPromptBuilder,
                userMemoryMapper,
                memoryExtractCounter
        );

        authenticatedUser = AuthenticatedUserContext.builder()
                .userId(1L)
                .sessionId(100L)
                .build();
    }

    @Test
    void extract_shortConversation_skips() {
        // 2 轮对话，应跳过
        List<Map<String, String>> conversation = List.of(
                Map.of("role", "user", "content", "你好"),
                Map.of("role", "assistant", "content", "你好！有什么可以帮你的？"));

        extractor.extract(1L, 100L, conversation, List.of(), "", "", 0L, authenticatedUser);

        verify(reasoningChatClient, never()).stream(any(), any());
        verify(memoryWriter, never()).writeIfNotIgnored(any());
    }

    @Test
    void extract_validConversation_noMemoriesReturned_writesNothing() {
        // 对话内容需超过 minConversationChars=100 默认门槛，否则会被过短对话过滤跳过
        List<Map<String, String>> conversation = List.of(
                Map.of("role", "user", "content", "我想咨询一下训练计划，我是个新手，想增肌，身高180体重70kg，每周能练4次，希望3个月内见效"),
                Map.of("role", "assistant", "content", "好的，根据你的身高体重和增肌目标，我建议采用四天分化训练，每次专注一个大肌群，配合充足蛋白质摄入。"),
                Map.of("role", "user", "content", "明白了，谢谢你的建议，我会按照这个计划执行，有疑问再请教你"),
                Map.of("role", "assistant", "content", "不客气，有问题随时找我。训练过程中注意渐进负重和充分休息，祝你训练顺利。"));

        stubLlmStream("{\"memories\": []}");

        extractor.extract(1L, 100L, conversation, List.of(), "", "", 4L, authenticatedUser);

        verify(memoryWriter, never()).writeIfNotIgnored(any());
        verify(memoryExtractCounter).markExtracted(100L, 4L);
    }

    @Test
    void extract_validConversation_memoriesReturned_writesEach() {
        List<Map<String, String>> conversation = List.of(
                Map.of("role", "user", "content", "我身高180体重70kg，目标是增肌到80kg，每周能练4次，有轻微腰椎间盘突出"),
                Map.of("role", "assistant", "content", "了解你的情况。根据你的腰椎问题，我建议避免大重量深蹲..."),
                Map.of("role", "user", "content", "好的，那我应该怎么调整训练？"),
                Map.of("role", "assistant", "content", "建议改为推拉腿分化，腿部以罗马尼亚硬拉和腿弯举为主..."));

        String llmOutput = "{\"memories\":[{\"type\":\"FACT\",\"content\":\"用户身高180cm体重70kg，目标增肌到80kg\",\"metadata\":{\"category\":\"body_condition\",\"tags\":[\"增肌\",\"身高180\"]}},{\"type\":\"FACT\",\"content\":\"用户有轻微腰椎间盘突出\",\"metadata\":{\"category\":\"condition\",\"tags\":[\"腰椎间盘突出\"]}},{\"type\":\"INSIGHT\",\"content\":\"该用户适合推拉腿分化训练，腿部避免大重量深蹲\",\"metadata\":{\"category\":\"training_style\",\"tags\":[\"推拉腿分化\"],\"confidence\":0.85}}]}";
        stubLlmStream(llmOutput);
        when(memoryWriter.writeIfNotIgnored(any())).thenReturn(true);

        extractor.extract(1L, 100L, conversation, List.of(), "", "", 4L, authenticatedUser);

        ArgumentCaptor<MemoryWriteRequest> captor = ArgumentCaptor.forClass(MemoryWriteRequest.class);
        verify(memoryWriter, times(3)).writeIfNotIgnored(captor.capture());
        List<MemoryWriteRequest> requests = captor.getAllValues();
        assertEquals("FACT", requests.get(0).getMemoryType());
        assertEquals("session:100", requests.get(0).getSource());
        assertEquals("INSIGHT", requests.get(2).getMemoryType());
        verify(memoryExtractCounter).markExtracted(100L, 4L);
    }

    @Test
    void extract_llmReturnsInvalidJson_writesNothing_noException() {
        // 对话内容需超过 minConversationChars=100 默认门槛，确保走到 LLM 调用与 JSON 解析阶段
        List<Map<String, String>> conversation = List.of(
                Map.of("role", "user", "content", "我身高180体重70kg，目标是增肌到80kg，每周能练4次，有轻微腰椎间盘突出，希望3个月见效"),
                Map.of("role", "assistant", "content", "好的，根据你的身高体重和腰椎情况，我建议避免大重量深蹲，改用罗马尼亚硬拉和腿弯举。"),
                Map.of("role", "user", "content", "好的，那我应该怎么调整训练计划？请给出具体的训练动作和组数安排。"),
                Map.of("role", "assistant", "content", "建议改为推拉腿分化训练，腿部以罗马尼亚硬拉和腿弯举为主，每周四次，每次60分钟。"));

        stubLlmStream("invalid json");

        // 不应抛异常
        assertDoesNotThrow(() ->
                extractor.extract(1L, 100L, conversation, List.of(), "", "", 4L, authenticatedUser));
        verify(memoryWriter, never()).writeIfNotIgnored(any());
        verify(memoryExtractCounter, never()).markExtracted(any(), anyLong());
    }

    /**
     * 模拟 ReasoningChatClient.stream 返回携带指定 LLM 输出的 Flux，
     * usage 字段保持 null 以避免被 doExtract 当作终止帧跳过。
     */
    private void stubLlmStream(String llmOutput) {
        ReasoningStreamChunk chunk = new ReasoningStreamChunk(null, llmOutput, null);
        when(reasoningChatClient.stream(any(), any())).thenReturn(Flux.just(chunk));
    }
}
