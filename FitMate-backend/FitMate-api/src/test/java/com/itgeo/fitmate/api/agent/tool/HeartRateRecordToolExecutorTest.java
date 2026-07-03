package com.itgeo.fitmate.api.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.heartrate.application.HeartRateService;
import com.itgeo.fitmate.api.fitness.heartrate.dto.HeartRateLogRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HeartRateRecordToolExecutorTest {

    private HeartRateService heartRateService;
    private HeartRateRecordToolExecutor executor;

    @BeforeEach
    void setUp() {
        heartRateService = mock(HeartRateService.class);
        executor = new HeartRateRecordToolExecutor(heartRateService);
    }

    @Test
    void execute_validInput_returnsOk() {
        ToolCall call = new ToolCall("h1", "heart_rate.record", Map.of(
                "date", "2026-07-03", "resting_hr", 60, "max_hr", 180, "hrv", 50));
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
        verify(heartRateService).logHeartRate(eq(1L), any(HeartRateLogRequest.class), eq("chat"));
    }

    @Test
    void execute_nullUser_returnsError() {
        ToolCall call = new ToolCall("h1", "heart_rate.record", Map.of("date", "2026-07-03"));
        ToolResult result = executor.execute(call, null);
        assertFalse(result.isSuccess());
    }

    @Test
    void execute_missingDate_returnsError() {
        ToolCall call = new ToolCall("h1", "heart_rate.record", Map.of("resting_hr", 60));
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();
        ToolResult result = executor.execute(call, user);
        assertFalse(result.isSuccess());
    }
}
