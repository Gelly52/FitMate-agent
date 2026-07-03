package com.itgeo.fitmate.api.fitness.metrics.application.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.fitness.metrics.dto.BodyMetricsLogRequest;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity.BodyMetrics;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.mapper.BodyMetricsMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BodyMetricsServiceImplTest {

    private BodyMetricsMapper bodyMetricsMapper;
    private BodyMetricsServiceImpl service;

    @BeforeEach
    void setUp() {
        bodyMetricsMapper = mock(BodyMetricsMapper.class);
        service = new BodyMetricsServiceImpl(bodyMetricsMapper);
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
}
