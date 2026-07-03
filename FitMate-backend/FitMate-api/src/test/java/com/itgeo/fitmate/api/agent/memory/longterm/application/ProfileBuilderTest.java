package com.itgeo.fitmate.api.agent.memory.longterm.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserProfile;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserProfileMapper;
import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;

class ProfileBuilderTest {

    private UserMemoryMapper memoryMapper;
    private UserProfileMapper profileMapper;
    private ChatModel chatModel;
    private PromptTemplateManager promptTemplateManager;
    private MemoryProperties properties;
    private ProfileBuilder builder;

    @BeforeEach
    void setUp() {
        memoryMapper = mock(UserMemoryMapper.class);
        profileMapper = mock(UserProfileMapper.class);
        chatModel = mock(ChatModel.class);
        promptTemplateManager = mock(PromptTemplateManager.class);
        properties = new MemoryProperties();
        when(promptTemplateManager.buildProfileBuildPrompt(any(), any(), any(), any())).thenReturn("prompt");
        builder = new ProfileBuilder(memoryMapper, profileMapper, chatModel, promptTemplateManager, properties);
    }

    @Test
    void rebuild_noMemories_skips() {
        when(memoryMapper.selectList(any())).thenReturn(Collections.emptyList());

        builder.rebuild(1L);

        verify(chatModel, never()).call(any(Prompt.class));
        verify(profileMapper, never()).insert(any(UserProfile.class));
        verify(profileMapper, never()).updateById(any(UserProfile.class));
    }

    @Test
    void rebuild_hasMemories_generatesProfile() {
        UserMemory fact = new UserMemory();
        fact.setMemoryType("FACT");
        fact.setContent("用户身高180cm，目标增肌");
        fact.setStatus("active");
        when(memoryMapper.selectList(any())).thenReturn(List.of(fact));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse(
                "{\"profile_text\":\"28岁男性，目标增肌\",\"tags\":[{\"label\":\"增肌期\",\"weight\":0.9,\"category\":\"goal\"}]}"));
        when(profileMapper.selectOne(any())).thenReturn(null);

        builder.rebuild(1L);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileMapper).insert(captor.capture());
        UserProfile saved = captor.getValue();
        assertTrue(saved.getProfileText().contains("增肌"));
        assertNotNull(saved.getProfileTagsJson());
    }

    @Test
    void rebuild_existingProfile_updates() {
        UserMemory fact = new UserMemory();
        fact.setMemoryType("FACT");
        fact.setContent("用户身高180cm");
        fact.setStatus("active");
        when(memoryMapper.selectList(any())).thenReturn(List.of(fact));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse(
                "{\"profile_text\":\"更新后的画像\",\"tags\":[]}"));

        UserProfile existing = new UserProfile();
        existing.setId(5L);
        existing.setUserId(1L);
        when(profileMapper.selectOne(any())).thenReturn(existing);

        builder.rebuild(1L);

        verify(profileMapper).updateById(any(UserProfile.class));
        verify(profileMapper, never()).insert(any(UserProfile.class));
    }

    private ChatResponse mockChatResponse(String text) {
        // Spring AI 1.1.0 中 Generation.getOutput() 与 ChatResponse.getResult() 均为 final 方法，
        // 无法被 Mockito stub，改用真实实例构造 ChatResponse。
        Generation generation = new Generation(new AssistantMessage(text));
        return new ChatResponse(List.of(generation));
    }
}
