package com.itgeo.fitmate.api.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.entity.HeartRate;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.mapper.HeartRateMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HeartRateQueryToolExecutorTest {

    private HeartRateMapper heartRateMapper;
    private HeartRateQueryToolExecutor executor;

    @BeforeEach
    void setUp() {
        heartRateMapper = mock(HeartRateMapper.class);
        executor = new HeartRateQueryToolExecutor(heartRateMapper);
    }

    @Test
    void execute_hasRecords_returnsOk() {
        HeartRate hr = new HeartRate();
        hr.setId(1L);
        hr.setUserId(1L);
        hr.setRecordDate(LocalDate.of(2026, 7, 3));
        when(heartRateMapper.selectList(any())).thenReturn(List.of(hr));

        ToolCall call = new ToolCall("q1", "heart_rate.query", Map.of());
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_nullUser_returnsError() {
        ToolCall call = new ToolCall("q1", "heart_rate.query", Map.of());
        ToolResult result = executor.execute(call, null);
        assertFalse(result.isSuccess());
    }
}
