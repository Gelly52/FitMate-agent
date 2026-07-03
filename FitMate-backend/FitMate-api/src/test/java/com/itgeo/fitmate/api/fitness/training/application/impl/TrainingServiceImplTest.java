package com.itgeo.fitmate.api.fitness.training.application.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.fitness.training.dto.TrainingExerciseItem;
import com.itgeo.fitmate.api.fitness.training.dto.TrainingLogRequest;
import com.itgeo.fitmate.api.fitness.training.infrastructure.entity.TrainingLog;
import com.itgeo.fitmate.api.fitness.training.infrastructure.mapper.TrainingExerciseMapper;
import com.itgeo.fitmate.api.fitness.training.infrastructure.mapper.TrainingLogMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TrainingServiceImplTest {

    private TrainingLogMapper trainingLogMapper;
    private TrainingExerciseMapper trainingExerciseMapper;
    private TrainingServiceImpl service;

    @BeforeEach
    void setUp() {
        trainingLogMapper = mock(TrainingLogMapper.class);
        trainingExerciseMapper = mock(TrainingExerciseMapper.class);
        service = new TrainingServiceImpl(trainingLogMapper, trainingExerciseMapper);
    }

    @Test
    void logTraining_withChatSource_persistsSource() {
        when(trainingLogMapper.selectOne(any())).thenReturn(null);

        TrainingLogRequest req = new TrainingLogRequest("2026-07-03",
                List.of(new TrainingExerciseItem("卧推", 3, 10, new BigDecimal("60"))));

        service.logTraining(1L, req, "chat");

        ArgumentCaptor<TrainingLog> captor = ArgumentCaptor.forClass(TrainingLog.class);
        verify(trainingLogMapper).insert(captor.capture());
        assertEquals("chat", captor.getValue().getSource());
    }

    @Test
    void logTraining_legacyOverload_defaultsToManual() {
        when(trainingLogMapper.selectOne(any())).thenReturn(null);

        TrainingLogRequest req = new TrainingLogRequest("2026-07-03",
                List.of(new TrainingExerciseItem("卧推", 3, 10, new BigDecimal("60"))));

        service.logTraining(1L, req);

        ArgumentCaptor<TrainingLog> captor = ArgumentCaptor.forClass(TrainingLog.class);
        verify(trainingLogMapper).insert(captor.capture());
        assertEquals("manual", captor.getValue().getSource());
    }
}
