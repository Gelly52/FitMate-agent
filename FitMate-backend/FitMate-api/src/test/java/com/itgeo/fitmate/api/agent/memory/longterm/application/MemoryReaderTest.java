package com.itgeo.fitmate.api.agent.memory.longterm.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserProfile;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserProfileMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryReaderTest {

    private UserProfileMapper profileMapper;
    private MemoryProperties properties;
    private MemoryReader reader;

    @BeforeEach
    void setUp() {
        profileMapper = mock(UserProfileMapper.class);
        properties = new MemoryProperties();
        reader = new MemoryReader(profileMapper, properties);
    }

    @Test
    void loadProfileSection_disabled_returnsEmpty() {
        properties.setEnabled(false);
        assertEquals("", reader.loadProfileSection(1L));
        verify(profileMapper, never()).selectOne(any());
    }

    @Test
    void loadProfileSection_noProfile_returnsEmpty() {
        when(profileMapper.selectOne(any())).thenReturn(null);
        assertEquals("", reader.loadProfileSection(1L));
    }

    @Test
    void loadProfileSection_expired_returnsEmpty() {
        UserProfile profile = new UserProfile();
        profile.setProfileText("旧画像");
        profile.setUpdatedAt(LocalDateTime.now().minusHours(25));
        when(profileMapper.selectOne(any())).thenReturn(profile);

        assertEquals("", reader.loadProfileSection(1L));
    }

    @Test
    void loadProfileSection_valid_returnsSection() {
        UserProfile profile = new UserProfile();
        profile.setProfileText("28岁男性，力量举训练者");
        profile.setUpdatedAt(LocalDateTime.now().minusHours(2));
        when(profileMapper.selectOne(any())).thenReturn(profile);

        String section = reader.loadProfileSection(1L);
        assertTrue(section.startsWith("## 用户画像"));
        assertTrue(section.contains("28岁男性"));
    }
}
