package com.itgeo.fitmate.api.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.training.application.TrainingService;
import com.itgeo.fitmate.api.fitness.training.dto.TrainingLogRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrainingLogRecordToolExecutorTest {

    private TrainingService trainingService;
    private TrainingLogRecordToolExecutor executor;

    @BeforeEach
    void setUp() {
        trainingService = mock(TrainingService.class);
        executor = new TrainingLogRecordToolExecutor(trainingService);
    }

    @Test
    void execute_validInput_returnsOk() {
        Map<String, Object> args = new HashMap<>();
        args.put("date", "2026-07-03");
        args.put("exercises", List.of(Map.of(
                "name", "卧推", "sets", 3, "reps", 10, "weight", 60)));
        ToolCall call = new ToolCall("call-1", "training_log.record", args);

        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
        verify(trainingService).logTraining(eq(1L), any(TrainingLogRequest.class), eq("chat"));
    }

    @Test
    void execute_nullUser_returnsError() {
        ToolCall call = new ToolCall("call-1", "training_log.record", Map.of("date", "2026-07-03"));
        ToolResult result = executor.execute(call, null);
        assertFalse(result.isSuccess());
    }

    @Test
    void execute_missingDate_returnsError() {
        ToolCall call = new ToolCall("call-1", "training_log.record", Map.of());
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();
        ToolResult result = executor.execute(call, user);
        assertFalse(result.isSuccess());
    }
}
