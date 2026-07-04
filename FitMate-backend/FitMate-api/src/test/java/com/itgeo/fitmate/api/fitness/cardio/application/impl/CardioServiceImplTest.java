package com.itgeo.fitmate.api.fitness.cardio.application.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.fitness.cardio.application.impl.CardioServiceImpl;
import com.itgeo.fitmate.api.fitness.cardio.dto.CardioLogRequest;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.entity.CardioLog;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.mapper.CardioLogMapper;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity.BodyMetrics;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.mapper.BodyMetricsMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CardioServiceImplTest {

    private CardioLogMapper cardioLogMapper;
    private BodyMetricsMapper bodyMetricsMapper;
    private CardioServiceImpl service;

    @BeforeEach
    void setUp() {
        cardioLogMapper = mock(CardioLogMapper.class);
        bodyMetricsMapper = mock(BodyMetricsMapper.class);
        service = new CardioServiceImpl(cardioLogMapper, bodyMetricsMapper);
    }

    @Test
    void logCardio_newRecord_insertsWithCalculatedPace() {
        when(cardioLogMapper.selectOne(any())).thenReturn(null);

        CardioLogRequest req = new CardioLogRequest(
                "2026-07-03", "running",
                new BigDecimal("5.0"), 30, 150, null);

        service.logCardio(1L, req, "chat");

        ArgumentCaptor<CardioLog> captor = ArgumentCaptor.forClass(CardioLog.class);
        verify(cardioLogMapper).insert(captor.capture());
        CardioLog saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals(LocalDate.of(2026, 7, 3), saved.getTrainingDate());
        assertEquals("running", saved.getCardioType());
        assertEquals("06:00/km", saved.getAvgPace());
        assertEquals("chat", saved.getSource());
        assertNotNull(saved.getSummary());
    }

    @Test
    void logCardio_existingRecord_updatesById() {
        CardioLog existing = new CardioLog();
        existing.setId(99L);
        when(cardioLogMapper.selectOne(any())).thenReturn(existing);

        CardioLogRequest req = new CardioLogRequest(
                "2026-07-03", "running",
                new BigDecimal("10.0"), 60, 155, null);

        service.logCardio(1L, req, "chat");

        verify(cardioLogMapper).updateById(existing);
        verify(cardioLogMapper, never()).insert(any(CardioLog.class));
        assertEquals("06:00/km", existing.getAvgPace());
        assertEquals("chat", existing.getSource());
    }

    @Test
    void logCardio_noDistanceNoPace_leftNull() {
        when(cardioLogMapper.selectOne(any())).thenReturn(null);

        CardioLogRequest req = new CardioLogRequest(
                "2026-07-03", "other", null, 30, null, null);

        service.logCardio(1L, req, "chat");

        ArgumentCaptor<CardioLog> captor = ArgumentCaptor.forClass(CardioLog.class);
        verify(cardioLogMapper).insert(captor.capture());
        assertNull(captor.getValue().getAvgPace());
    }

    @Test
    void logCardio_nullDistanceAndDuration_throws() {
        CardioLogRequest req = new CardioLogRequest(
                "2026-07-03", "running", null, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.logCardio(1L, req, "chat"));
    }

    @Test
    void logCardio_invalidDate_throws() {
        CardioLogRequest req = new CardioLogRequest(
                "2026/07/03", "running", new BigDecimal("5"), 30, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.logCardio(1L, req, "chat"));
    }

    @Test
    void logCardio_withWeight_calculatesCaloriesBurned() {
        // 准备：有最近体重记录 70kg
        BodyMetrics bm = new BodyMetrics();
        bm.setWeight(new BigDecimal("70"));
        when(bodyMetricsMapper.selectOne(any())).thenReturn(bm);
        when(cardioLogMapper.selectOne(any())).thenReturn(null);

        CardioLogRequest request = new CardioLogRequest();
        request.setDate("2026-07-03");
        request.setCardioType("running");
        request.setDistanceKm(new BigDecimal("5"));
        request.setDurationMinutes(30);

        // 执行
        service.logCardio(1L, request, "manual");

        // 验证：caloriesBurned = 9.8 × 70 × 0.5 = 343
        ArgumentCaptor<CardioLog> captor = ArgumentCaptor.forClass(CardioLog.class);
        verify(cardioLogMapper).insert(captor.capture());
        CardioLog saved = captor.getValue();
        assertEquals(343, saved.getCaloriesBurned());
    }

    @Test
    void logCardio_noWeightRecord_caloriesBurnedNull() {
        // 准备：无体重记录
        when(bodyMetricsMapper.selectOne(any())).thenReturn(null);
        when(cardioLogMapper.selectOne(any())).thenReturn(null);

        CardioLogRequest request = new CardioLogRequest();
        request.setDate("2026-07-03");
        request.setCardioType("running");
        request.setDistanceKm(new BigDecimal("5"));
        request.setDurationMinutes(30);

        // 执行
        service.logCardio(1L, request, "manual");

        // 验证：caloriesBurned 为 null（不报错）
        ArgumentCaptor<CardioLog> captor = ArgumentCaptor.forClass(CardioLog.class);
        verify(cardioLogMapper).insert(captor.capture());
        CardioLog saved = captor.getValue();
        assertNull(saved.getCaloriesBurned());
    }
}
