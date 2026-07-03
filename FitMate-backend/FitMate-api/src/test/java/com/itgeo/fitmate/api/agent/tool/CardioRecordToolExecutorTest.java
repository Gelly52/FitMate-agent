package com.itgeo.fitmate.api.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.cardio.application.CardioService;
import com.itgeo.fitmate.api.fitness.cardio.dto.CardioLogRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CardioRecordToolExecutorTest {

    private CardioService cardioService;
    private CardioRecordToolExecutor executor;

    @BeforeEach
    void setUp() {
        cardioService = mock(CardioService.class);
        executor = new CardioRecordToolExecutor(cardioService);
    }

    @Test
    void execute_validInput_returnsOk() {
        ToolCall call = new ToolCall("c1", "cardio.record", Map.of(
                "date", "2026-07-03",
                "cardio_type", "running",
                "distance_km", 5.0,
                "duration_minutes", 30,
                "avg_heart_rate", 150));
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
        verify(cardioService).logCardio(eq(1L), any(CardioLogRequest.class), eq("chat"));
    }

    @Test
    void execute_nullUser_returnsError() {
        ToolCall call = new ToolCall("c1", "cardio.record", Map.of("date", "2026-07-03", "cardio_type", "running"));
        ToolResult result = executor.execute(call, null);
        assertFalse(result.isSuccess());
    }

    @Test
    void execute_missingDate_returnsError() {
        ToolCall call = new ToolCall("c1", "cardio.record", Map.of("cardio_type", "running"));
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();
        ToolResult result = executor.execute(call, user);
        assertFalse(result.isSuccess());
    }
}
