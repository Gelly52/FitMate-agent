package com.itgeo.fitmate.api.agent.memory.longterm.application.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriter;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriteRequest;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity.BodyMetrics;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.mapper.BodyMetricsMapper;
import com.itgeo.fitmate.api.fitness.training.infrastructure.entity.TrainingLog;
import com.itgeo.fitmate.api.fitness.training.infrastructure.mapper.TrainingLogMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SnapshotAggregatorTest {

    private TrainingLogMapper trainingLogMapper;
    private BodyMetricsMapper bodyMetricsMapper;
    private UserMemoryMapper userMemoryMapper;
    private MemoryWriter memoryWriter;
    private MemoryProperties properties;
    private SnapshotAggregator aggregator;

    @BeforeEach
    void setUp() {
        trainingLogMapper = mock(TrainingLogMapper.class);
        bodyMetricsMapper = mock(BodyMetricsMapper.class);
        userMemoryMapper = mock(UserMemoryMapper.class);
        memoryWriter = mock(MemoryWriter.class);
        properties = new MemoryProperties();
        aggregator = new SnapshotAggregator(trainingLogMapper, bodyMetricsMapper, userMemoryMapper, memoryWriter, properties);
    }

    @Test
    void aggregateForUser_noData_writesNothing() {
        when(trainingLogMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(bodyMetricsMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userMemoryMapper.selectList(any())).thenReturn(Collections.emptyList());

        aggregator.aggregateForUser(1L);

        verify(memoryWriter, never()).writeIfNotIgnored(any(MemoryWriteRequest.class));
    }

    @Test
    void aggregateForUser_hasTrainingData_writesSnapshot() {
        TrainingLog log1 = new TrainingLog();
        log1.setTrainingDate(LocalDate.now().minusDays(3));
        log1.setTotalVolume(new BigDecimal("8000"));
        log1.setPrimaryMuscleGroup("胸");
        TrainingLog log2 = new TrainingLog();
        log2.setTrainingDate(LocalDate.now().minusDays(1));
        log2.setTotalVolume(new BigDecimal("10000"));
        log2.setPrimaryMuscleGroup("背");
        when(trainingLogMapper.selectList(any())).thenReturn(List.of(log1, log2));
        when(bodyMetricsMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userMemoryMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(memoryWriter.writeIfNotIgnored(any())).thenReturn(true);

        aggregator.aggregateForUser(1L);

        ArgumentCaptor<MemoryWriteRequest> captor = ArgumentCaptor.forClass(MemoryWriteRequest.class);
        verify(memoryWriter).writeIfNotIgnored(captor.capture());
        MemoryWriteRequest req = captor.getValue();
        assertEquals("SNAPSHOT", req.getMemoryType());
        assertEquals("schedule", req.getSource());
        assertTrue(req.getContent().contains("训练2次"));
        assertTrue(req.getContent().contains("18000"));
    }

    @Test
    void aggregateForUser_existingSnapshot_archivesOld() {
        TrainingLog log1 = new TrainingLog();
        log1.setTrainingDate(LocalDate.now().minusDays(1));
        log1.setTotalVolume(new BigDecimal("5000"));
        log1.setPrimaryMuscleGroup("腿");
        when(trainingLogMapper.selectList(any())).thenReturn(List.of(log1));
        when(bodyMetricsMapper.selectList(any())).thenReturn(Collections.emptyList());

        UserMemory oldSnapshot = new UserMemory();
        oldSnapshot.setId(99L);
        oldSnapshot.setMemoryType("SNAPSHOT");
        oldSnapshot.setStatus("active");
        when(userMemoryMapper.selectList(any())).thenReturn(List.of(oldSnapshot));
        when(memoryWriter.writeIfNotIgnored(any())).thenReturn(true);

        aggregator.aggregateForUser(1L);

        // 旧的应被归档
        ArgumentCaptor<UserMemory> updateCaptor = ArgumentCaptor.forClass(UserMemory.class);
        verify(userMemoryMapper).updateById(updateCaptor.capture());
        assertEquals("archived", updateCaptor.getValue().getStatus());
    }
}
