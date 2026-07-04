package com.itgeo.fitmate.api.fitness.metrics.application.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.auth.application.UserPreferenceService;
import com.itgeo.fitmate.api.fitness.metrics.dto.BodyMetricsLogRequest;
import com.itgeo.fitmate.api.fitness.metrics.dto.BodyMetricsSummaryDTO;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity.BodyMetrics;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.mapper.BodyMetricsMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BodyMetricsServiceImplTest {

    private BodyMetricsMapper bodyMetricsMapper;
    private UserPreferenceService userPreferenceService;
    private BodyMetricsServiceImpl service;

    @BeforeEach
    void setUp() {
        bodyMetricsMapper = mock(BodyMetricsMapper.class);
        userPreferenceService = mock(UserPreferenceService.class);
        service = new BodyMetricsServiceImpl(bodyMetricsMapper, userPreferenceService);
    }

    @Test
    void logBodyMetrics_withChatSource_persistsSource() {
        when(bodyMetricsMapper.selectOne(any())).thenReturn(null);

        BodyMetricsLogRequest req = new BodyMetricsLogRequest();
        req.setDate("2026-07-03");
        req.setWeight(new BigDecimal("70.5"));

        service.logBodyMetrics(1L, req, "chat");

        ArgumentCaptor<BodyMetrics> captor = ArgumentCaptor.forClass(BodyMetrics.class);
        verify(bodyMetricsMapper).insert(captor.capture());
        assertEquals("chat", captor.getValue().getSource());
    }

    @Test
    void logBodyMetrics_girthOnly_insertsSuccessfully() {
        when(bodyMetricsMapper.selectOne(any())).thenReturn(null);

        BodyMetricsLogRequest req = new BodyMetricsLogRequest();
        req.setDate("2026-07-03");
        req.setWaistGirth(new BigDecimal("80"));

        service.logBodyMetrics(1L, req, "chat");

        ArgumentCaptor<BodyMetrics> captor = ArgumentCaptor.forClass(BodyMetrics.class);
        verify(bodyMetricsMapper).insert(captor.capture());
        assertEquals(new BigDecimal("80"), captor.getValue().getWaistGirth());
    }

    @Test
    void logBodyMetrics_allNull_throws() {
        BodyMetricsLogRequest req = new BodyMetricsLogRequest();
        req.setDate("2026-07-03");
        assertThrows(IllegalArgumentException.class,
                () -> service.logBodyMetrics(1L, req, "chat"));
    }

    @Test
    void logBodyMetrics_legacyOverload_defaultsToManual() {
        when(bodyMetricsMapper.selectOne(any())).thenReturn(null);

        BodyMetricsLogRequest req = new BodyMetricsLogRequest();
        req.setDate("2026-07-03");
        req.setWeight(new BigDecimal("70"));

        service.logBodyMetrics(1L, req);

        ArgumentCaptor<BodyMetrics> captor = ArgumentCaptor.forClass(BodyMetrics.class);
        verify(bodyMetricsMapper).insert(captor.capture());
        assertEquals("manual", captor.getValue().getSource());
    }

    @Test
    void getBodyMetricsSummary_withHeight_calculatesBmiAndChangeRate() {
        // 准备：最新体重 70.5，上一次 71.5，身高 175cm
        BodyMetrics latest = new BodyMetrics();
        latest.setWeight(new BigDecimal("70.5"));
        latest.setRecordDate(java.time.LocalDate.now());
        BodyMetrics previous = new BodyMetrics();
        previous.setWeight(new BigDecimal("71.5"));
        previous.setRecordDate(java.time.LocalDate.now().minusDays(7));
        when(bodyMetricsMapper.selectList(any())).thenReturn(java.util.Arrays.asList(latest, previous));
        when(userPreferenceService.getHeightCm(1L)).thenReturn(175);

        // 执行
        BodyMetricsSummaryDTO summary = service.getBodyMetricsSummary(1L);

        // 验证：BMI = 70.5 / (1.75)^2 = 23.02
        assertEquals(new BigDecimal("23.02"), summary.getBmi());
        assertEquals(new BigDecimal("-1.40"), summary.getWeightChangeRate());
        assertEquals(new BigDecimal("70.5"), summary.getLatestWeight());
        assertEquals(new BigDecimal("71.5"), summary.getPreviousWeight());
    }

    @Test
    void getBodyMetricsSummary_noHeight_bmiNull() {
        BodyMetrics latest = new BodyMetrics();
        latest.setWeight(new BigDecimal("70.5"));
        when(bodyMetricsMapper.selectList(any())).thenReturn(java.util.Collections.singletonList(latest));
        when(userPreferenceService.getHeightCm(1L)).thenReturn(null);

        BodyMetricsSummaryDTO summary = service.getBodyMetricsSummary(1L);

        assertNull(summary.getBmi());
        assertEquals(new BigDecimal("70.5"), summary.getLatestWeight());
        assertNull(summary.getPreviousWeight());
        assertNull(summary.getWeightChangeRate());
    }
}
