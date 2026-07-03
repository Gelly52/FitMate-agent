package com.itgeo.fitmate.api.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.metrics.application.BodyMetricsService;
import com.itgeo.fitmate.api.fitness.metrics.dto.BodyMetricsLogRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BodyMetricsRecordToolExecutorTest {

    private BodyMetricsService bodyMetricsService;
    private BodyMetricsRecordToolExecutor executor;

    @BeforeEach
    void setUp() {
        bodyMetricsService = mock(BodyMetricsService.class);
        executor = new BodyMetricsRecordToolExecutor(bodyMetricsService);
    }

    @Test
    void execute_validInput_returnsOk() {
        ToolCall call = new ToolCall("b1", "body_metrics.record", Map.of(
                "date", "2026-07-03", "weight", 70.5, "body_fat", 18));
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
        verify(bodyMetricsService).logBodyMetrics(eq(1L), any(BodyMetricsLogRequest.class), eq("chat"));
    }

    @Test
    void execute_girthOnly_returnsOk() {
        ToolCall call = new ToolCall("b1", "body_metrics.record", Map.of(
                "date", "2026-07-03", "waist_girth", 80));
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_nullUser_returnsError() {
        ToolCall call = new ToolCall("b1", "body_metrics.record", Map.of("date", "2026-07-03"));
        ToolResult result = executor.execute(call, null);
        assertFalse(result.isSuccess());
    }

    @Test
    void execute_missingDate_returnsError() {
        ToolCall call = new ToolCall("b1", "body_metrics.record", Map.of());
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();
        ToolResult result = executor.execute(call, user);
        assertFalse(result.isSuccess());
    }
}
