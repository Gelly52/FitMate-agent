package com.itgeo.fitmate.api.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.diet.application.DietService;
import com.itgeo.fitmate.api.fitness.diet.dto.DietLogRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DietRecordToolExecutorTest {

    private DietService dietService;
    private DietRecordToolExecutor executor;

    @BeforeEach
    void setUp() {
        dietService = mock(DietService.class);
        executor = new DietRecordToolExecutor(dietService);
    }

    @Test
    void execute_validInput_returnsOk() {
        ToolCall call = new ToolCall("d1", "diet.record", Map.of(
                "date", "2026-07-03",
                "meal_type", "breakfast",
                "items", List.of(Map.of(
                        "name", "鸡蛋", "calories", 140, "protein", 12))));
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
        verify(dietService).logDiet(eq(1L), any(DietLogRequest.class), eq("chat"));
    }

    @Test
    void execute_nullUser_returnsError() {
        ToolCall call = new ToolCall("d1", "diet.record", Map.of(
                "date", "2026-07-03", "meal_type", "breakfast", "items", List.of()));
        ToolResult result = executor.execute(call, null);
        assertFalse(result.isSuccess());
    }

    @Test
    void execute_missingDate_returnsError() {
        ToolCall call = new ToolCall("d1", "diet.record", Map.of(
                "meal_type", "breakfast", "items", List.of(Map.of("name", "x"))));
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();
        ToolResult result = executor.execute(call, user);
        assertFalse(result.isSuccess());
    }
}
