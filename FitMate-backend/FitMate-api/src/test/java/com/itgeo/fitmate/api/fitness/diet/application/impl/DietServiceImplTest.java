package com.itgeo.fitmate.api.fitness.diet.application.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.fitness.diet.application.impl.DietServiceImpl;
import com.itgeo.fitmate.api.fitness.diet.dto.DietItemDTO;
import com.itgeo.fitmate.api.fitness.diet.dto.DietLogRequest;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietItem;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietLog;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper.DietItemMapper;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper.DietLogMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DietServiceImplTest {

    private DietLogMapper dietLogMapper;
    private DietItemMapper dietItemMapper;
    private DietServiceImpl service;

    @BeforeEach
    void setUp() {
        dietLogMapper = mock(DietLogMapper.class);
        dietItemMapper = mock(DietItemMapper.class);
        service = new DietServiceImpl(dietLogMapper, dietItemMapper);
    }

    @Test
    void logDiet_newRecord_insertsMainAndItemsWithTotals() {
        when(dietLogMapper.selectOne(any())).thenReturn(null);

        DietLogRequest req = new DietLogRequest("2026-07-03", "breakfast",
                List.of(
                        new DietItemDTO("鸡蛋", "2个", 140, new BigDecimal("12"), new BigDecimal("1"), new BigDecimal("10")),
                        new DietItemDTO("牛奶", "250ml", 150, new BigDecimal("8"), new BigDecimal("12"), new BigDecimal("8"))
                ), null);

        service.logDiet(1L, req, "chat");

        ArgumentCaptor<DietLog> logCaptor = ArgumentCaptor.forClass(DietLog.class);
        verify(dietLogMapper).insert(logCaptor.capture());
        DietLog savedLog = logCaptor.getValue();
        assertEquals(1L, savedLog.getUserId());
        assertEquals(LocalDate.of(2026, 7, 3), savedLog.getRecordDate());
        assertEquals("breakfast", savedLog.getMealType());
        assertEquals(290, savedLog.getTotalCalories());
        assertEquals(new BigDecimal("20.0"), savedLog.getTotalProtein());
        assertEquals(new BigDecimal("13.0"), savedLog.getTotalCarbs());
        assertEquals(new BigDecimal("18.0"), savedLog.getTotalFat());
        assertEquals("chat", savedLog.getSource());

        verify(dietItemMapper, times(2)).insert(any(DietItem.class));
    }

    @Test
    void logDiet_existingRecord_updatesAndRebuildsItems() {
        DietLog existing = new DietLog();
        existing.setId(77L);
        when(dietLogMapper.selectOne(any())).thenReturn(existing);

        DietLogRequest req = new DietLogRequest("2026-07-03", "lunch",
                List.of(new DietItemDTO("鸡胸肉", "150g", 240, new BigDecimal("50"), new BigDecimal("0"), new BigDecimal("5"))),
                null);

        service.logDiet(1L, req, "chat");

        verify(dietItemMapper).delete(any());
        verify(dietLogMapper).updateById(existing);
        verify(dietLogMapper, never()).insert(any(DietLog.class));
        assertEquals(240, existing.getTotalCalories());
        assertEquals("chat", existing.getSource());
        verify(dietItemMapper, times(1)).insert(any(DietItem.class));
    }

    @Test
    void logDiet_emptyItems_throws() {
        DietLogRequest req = new DietLogRequest("2026-07-03", "breakfast", List.of(), null);
        assertThrows(IllegalArgumentException.class,
                () -> service.logDiet(1L, req, "chat"));
    }

    @Test
    void logDiet_invalidDate_throws() {
        DietLogRequest req = new DietLogRequest("bad", "breakfast",
                List.of(new DietItemDTO("x", null, 100, null, null, null)), null);
        assertThrows(IllegalArgumentException.class,
                () -> service.logDiet(1L, req, "chat"));
    }
}
