package com.itgeo.fitmate.api.agent.memory.longterm.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MemoryWriterTest {

    private UserMemoryMapper mapper;
    private MemoryWriter writer;

    @BeforeEach
    void setUp() {
        mapper = mock(UserMemoryMapper.class);
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        writer = new MemoryWriter(mapper);
    }

    @Test
    void writeMemory_newContent_insertsActive() {
        MemoryWriteRequest req = MemoryWriteRequest.builder()
                .userId(1L)
                .memoryType("FACT")
                .content("用户目标是减脂到15%体脂")
                .metadataJson("{\"category\":\"goal\"}")
                .source("session:100")
                .build();

        writer.writeIfNotIgnored(req);

        ArgumentCaptor<UserMemory> captor = ArgumentCaptor.forClass(UserMemory.class);
        verify(mapper).insert(captor.capture());
        UserMemory saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals("FACT", saved.getMemoryType());
        assertEquals("用户目标是减脂到15%体脂", saved.getContent());
        assertEquals("active", saved.getStatus());
        assertNotNull(saved.getContentHash());
        assertEquals(64, saved.getContentHash().length()); // SHA-256 hex
    }

    @Test
    void writeMemory_contentHashIgnored_skips() {
        UserMemory existing = new UserMemory();
        existing.setContentHash("abc123");
        existing.setStatus("ignored");
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(existing));

        MemoryWriteRequest req = MemoryWriteRequest.builder()
                .userId(1L)
                .memoryType("FACT")
                .content("test")
                .source("session:1")
                .build();
        // 设置相同 hash
        req.setContentHash("abc123");

        writer.writeIfNotIgnored(req);

        verify(mapper, never()).insert(any(UserMemory.class));
    }

    @Test
    void writeMemory_contentHashActive_skips() {
        UserMemory existing = new UserMemory();
        existing.setContentHash("abc123");
        existing.setStatus("active");
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(existing));

        MemoryWriteRequest req = MemoryWriteRequest.builder()
                .userId(1L)
                .memoryType("FACT")
                .content("test")
                .source("session:1")
                .build();
        req.setContentHash("abc123");

        writer.writeIfNotIgnored(req);

        verify(mapper, never()).insert(any(UserMemory.class));
    }
}
