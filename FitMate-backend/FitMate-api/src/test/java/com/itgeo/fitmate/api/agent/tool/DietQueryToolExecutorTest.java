package com.itgeo.fitmate.api.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietItem;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietLog;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper.DietItemMapper;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper.DietLogMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DietQueryToolExecutorTest {

    private DietLogMapper dietLogMapper;
    private DietItemMapper dietItemMapper;
    private DietQueryToolExecutor executor;

    @BeforeEach
    void setUp() {
        dietLogMapper = mock(DietLogMapper.class);
        dietItemMapper = mock(DietItemMapper.class);
        executor = new DietQueryToolExecutor(dietLogMapper, dietItemMapper);
    }

    @Test
    void execute_hasRecords_returnsOkWithItems() {
        DietLog log = new DietLog();
        log.setId(1L);
        log.setUserId(1L);
        log.setRecordDate(LocalDate.of(2026, 7, 3));
        DietItem item = new DietItem();
        item.setDietLogId(1L);
        when(dietLogMapper.selectList(any())).thenReturn(List.of(log));
        when(dietItemMapper.selectList(any())).thenReturn(List.of(item));

        ToolCall call = new ToolCall("q1", "diet.query", Map.of());
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_nullUser_returnsError() {
        ToolCall call = new ToolCall("q1", "diet.query", Map.of());
        ToolResult result = executor.execute(call, null);
        assertFalse(result.isSuccess());
    }
}
