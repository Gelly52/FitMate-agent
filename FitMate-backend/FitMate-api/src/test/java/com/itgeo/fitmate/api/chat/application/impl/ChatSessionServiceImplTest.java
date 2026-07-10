package com.itgeo.fitmate.api.chat.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.agent.infrastructure.mapper.AgentRunMapper;
import com.itgeo.fitmate.api.agent.infrastructure.mapper.AgentStepMapper;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatMessage;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatSession;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatThinking;
import com.itgeo.fitmate.api.chat.infrastructure.mapper.ChatMessageMapper;
import com.itgeo.fitmate.api.chat.infrastructure.mapper.ChatSessionMapper;
import com.itgeo.fitmate.api.chat.infrastructure.mapper.ChatThinkingMapper;
import com.itgeo.fitmate.api.chat.infrastructure.mapper.ContextSummaryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatSessionServiceImpl 多用户隔离相关测试。
 * <p>
 * 重点覆盖 getThinkingByMessageId 的归属校验，防止 IDOR 越权读取其他用户的思考内容。
 */
@ExtendWith(MockitoExtension.class)
class ChatSessionServiceImplTest {

    @Mock
    private ChatSessionMapper chatSessionMapper;
    @Mock
    private ChatMessageMapper chatMessageMapper;
    @Mock
    private ContextSummaryMapper contextSummaryMapper;
    @Mock
    private ChatThinkingMapper chatThinkingMapper;
    @Mock
    private AgentRunMapper agentRunMapper;
    @Mock
    private AgentStepMapper agentStepMapper;

    @InjectMocks
    private ChatSessionServiceImpl chatSessionService;

    private static final Long USER_A_ID = 1001L;
    private static final Long USER_B_ID = 1002L;
    private static final Long SESSION_A_ID = 5001L;
    private static final Long MESSAGE_ID = 9001L;

    private ChatMessage messageOfUserA;
    private ChatSession sessionOfUserA;
    private ChatThinking thinkingRecord;

    @BeforeEach
    void setUp() {
        messageOfUserA = new ChatMessage();
        messageOfUserA.setId(MESSAGE_ID);
        messageOfUserA.setSessionId(SESSION_A_ID);

        sessionOfUserA = new ChatSession();
        sessionOfUserA.setId(SESSION_A_ID);
        sessionOfUserA.setUserId(USER_A_ID);

        thinkingRecord = new ChatThinking();
        thinkingRecord.setMessageId(MESSAGE_ID);
        thinkingRecord.setContent("user A secret thinking");
    }

    /**
     * 正常场景：message 属于当前用户，应返回思考内容。
     */
    @Test
    void getThinkingByMessageId_ownerUser_shouldReturnContent() {
        // given: message 属于 sessionA，sessionA 属于 USER_A
        when(chatMessageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(messageOfUserA);
        when(chatSessionMapper.selectById(SESSION_A_ID)).thenReturn(sessionOfUserA);
        when(chatThinkingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(thinkingRecord);

        // when: USER_A 查询自己的思考内容
        String result = chatSessionService.getThinkingByMessageId(USER_A_ID, MESSAGE_ID);

        // then: 返回真实内容
        assertEquals("user A secret thinking", result);
        // 应该查询 message、session、thinking 三次
        verify(chatMessageMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        verify(chatSessionMapper, times(1)).selectById(SESSION_A_ID);
        verify(chatThinkingMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
    }

    /**
     * IDOR 场景：message 属于其他用户，应返回 null 且不查询 thinking 表。
     * <p>
     * 这是本次修复的核心：USER_B 尝试读取 USER_A 的 messageId，必须被拦截。
     */
    @Test
    void getThinkingByMessageId_crossUser_shouldReturnNullAndNotQueryThinking() {
        // given: message 属于 sessionA，sessionA 属于 USER_A
        when(chatMessageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(messageOfUserA);
        when(chatSessionMapper.selectById(SESSION_A_ID)).thenReturn(sessionOfUserA);

        // when: USER_B 尝试查询 USER_A 的 messageId
        String result = chatSessionService.getThinkingByMessageId(USER_B_ID, MESSAGE_ID);

        // then: 返回 null，且不应查询 thinking 表（防止泄露）
        assertNull(result, "跨用户查询必须返回 null");
        verify(chatThinkingMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    /**
     * 边界场景：message 不存在，应返回 null。
     */
    @Test
    void getThinkingByMessageId_messageNotExists_shouldReturnNull() {
        // given: message 查不到
        when(chatMessageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // when
        String result = chatSessionService.getThinkingByMessageId(USER_A_ID, MESSAGE_ID);

        // then
        assertNull(result);
        verify(chatSessionMapper, never()).selectById(any(Long.class));
        verify(chatThinkingMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    /**
     * 边界场景：messageId 为 null，应返回 null。
     */
    @Test
    void getThinkingByMessageId_nullMessageId_shouldReturnNull() {
        // when
        String result = chatSessionService.getThinkingByMessageId(USER_A_ID, null);

        // then
        assertNull(result);
        verify(chatMessageMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    /**
     * 边界场景：userId 为 null，应返回 null（防御性）。
     */
    @Test
    void getThinkingByMessageId_nullUserId_shouldReturnNull() {
        // when
        String result = chatSessionService.getThinkingByMessageId(null, MESSAGE_ID);

        // then
        assertNull(result);
        verify(chatMessageMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }
}
