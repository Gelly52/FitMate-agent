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

    @Test
    void logTraining_withBenchPress_setsPrimaryMuscleGroupChest() {
        // 准备：卧推动作
        TrainingLogRequest request = new TrainingLogRequest();
        request.setDate("2026-07-03");
        TrainingExerciseItem item = new TrainingExerciseItem();
        item.setName("杠铃卧推");
        item.setSets(3);
        item.setReps(10);
        item.setWeight(new BigDecimal("60"));
        request.setExercises(java.util.Collections.singletonList(item));

        when(trainingLogMapper.selectOne(any())).thenReturn(null);

        // 执行
        service.logTraining(1L, request, "chat");

        // 验证：primaryMuscleGroup 被推断为胸肌
        ArgumentCaptor<TrainingLog> captor = ArgumentCaptor.forClass(TrainingLog.class);
        verify(trainingLogMapper).insert(captor.capture());
        TrainingLog saved = captor.getValue();
        assertEquals("胸肌", saved.getPrimaryMuscleGroup());
    }

    @Test
    void logTraining_withMultipleMovements_setsMostFrequentMuscleGroup() {
        // 准备：2 个胸肌动作 + 1 个肩部动作，应推断为胸肌
        TrainingLogRequest request = new TrainingLogRequest();
        request.setDate("2026-07-03");
        TrainingExerciseItem benchPress = new TrainingExerciseItem();
        benchPress.setName("卧推");
        benchPress.setSets(3);
        benchPress.setReps(10);
        benchPress.setWeight(new BigDecimal("60"));
        TrainingExerciseItem inclinePress = new TrainingExerciseItem();
        inclinePress.setName("上斜卧推");
        inclinePress.setSets(3);
        inclinePress.setReps(10);
        inclinePress.setWeight(new BigDecimal("50"));
        TrainingExerciseItem shoulderPress = new TrainingExerciseItem();
        shoulderPress.setName("推举");
        shoulderPress.setSets(3);
        shoulderPress.setReps(10);
        shoulderPress.setWeight(new BigDecimal("40"));
        request.setExercises(java.util.Arrays.asList(benchPress, inclinePress, shoulderPress));

        when(trainingLogMapper.selectOne(any())).thenReturn(null);

        // 执行
        service.logTraining(1L, request, "chat");

        // 验证：胸肌出现 2 次，肩部 1 次，推断为胸肌
        ArgumentCaptor<TrainingLog> captor = ArgumentCaptor.forClass(TrainingLog.class);
        verify(trainingLogMapper).insert(captor.capture());
        TrainingLog saved = captor.getValue();
        assertEquals("胸肌", saved.getPrimaryMuscleGroup());
    }
}
