package com.itgeo.fitmate.api.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.entity.CardioLog;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.mapper.CardioLogMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CardioQueryToolExecutorTest {

    private CardioLogMapper cardioLogMapper;
    private CardioQueryToolExecutor executor;

    @BeforeEach
    void setUp() {
        cardioLogMapper = mock(CardioLogMapper.class);
        executor = new CardioQueryToolExecutor(cardioLogMapper);
    }

    @Test
    void execute_hasRecords_returnsOkWithList() {
        CardioLog log = new CardioLog();
        log.setId(1L);
        log.setUserId(1L);
        log.setTrainingDate(LocalDate.of(2026, 7, 3));
        log.setCardioType("running");
        when(cardioLogMapper.selectList(any())).thenReturn(List.of(log));

        ToolCall call = new ToolCall("q1", "cardio.query", Map.of());
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_noRecords_returnsOkWithEmptyMessage() {
        when(cardioLogMapper.selectList(any())).thenReturn(List.of());

        ToolCall call = new ToolCall("q1", "cardio.query", Map.of());
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_nullUser_returnsError() {
        ToolCall call = new ToolCall("q1", "cardio.query", Map.of());
        ToolResult result = executor.execute(call, null);
        assertFalse(result.isSuccess());
    }
}
