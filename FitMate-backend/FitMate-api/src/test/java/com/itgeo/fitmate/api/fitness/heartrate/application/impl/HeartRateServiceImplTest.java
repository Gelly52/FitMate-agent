package com.itgeo.fitmate.api.fitness.heartrate.application.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.fitness.heartrate.application.impl.HeartRateServiceImpl;
import com.itgeo.fitmate.api.fitness.heartrate.dto.HeartRateLogRequest;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.entity.HeartRate;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.mapper.HeartRateMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HeartRateServiceImplTest {

    private HeartRateMapper heartRateMapper;
    private HeartRateServiceImpl service;

    @BeforeEach
    void setUp() {
        heartRateMapper = mock(HeartRateMapper.class);
        service = new HeartRateServiceImpl(heartRateMapper);
    }

    @Test
    void logHeartRate_newRecord_inserts() {
        when(heartRateMapper.selectOne(any())).thenReturn(null);

        HeartRateLogRequest req = new HeartRateLogRequest("2026-07-03", 60, 180, 50, null);

        service.logHeartRate(1L, req, "chat");

        ArgumentCaptor<HeartRate> captor = ArgumentCaptor.forClass(HeartRate.class);
        verify(heartRateMapper).insert(captor.capture());
        HeartRate saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals(LocalDate.of(2026, 7, 3), saved.getRecordDate());
        assertEquals(60, saved.getRestingHr());
        assertEquals("chat", saved.getSource());
        assertNotNull(saved.getSummary());
    }

    @Test
    void logHeartRate_existingRecord_updatesById() {
        HeartRate existing = new HeartRate();
        existing.setId(88L);
        when(heartRateMapper.selectOne(any())).thenReturn(existing);

        HeartRateLogRequest req = new HeartRateLogRequest("2026-07-03", 62, null, null, null);

        service.logHeartRate(1L, req, "chat");

        verify(heartRateMapper).updateById(existing);
        verify(heartRateMapper, never()).insert(any(HeartRate.class));
        assertEquals(62, existing.getRestingHr());
        assertEquals("chat", existing.getSource());
    }

    @Test
    void logHeartRate_allNull_throws() {
        HeartRateLogRequest req = new HeartRateLogRequest("2026-07-03", null, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.logHeartRate(1L, req, "chat"));
    }

    @Test
    void logHeartRate_invalidDate_throws() {
        HeartRateLogRequest req = new HeartRateLogRequest("07-03-2026", 60, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.logHeartRate(1L, req, "chat"));
    }
}
