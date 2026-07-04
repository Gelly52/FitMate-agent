package com.itgeo.fitmate.api.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.itgeo.fitmate.api.auth.infrastructure.entity.UserPreference;
import com.itgeo.fitmate.api.auth.infrastructure.mapper.UserPreferenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserPreferenceServiceTest {

    private UserPreferenceMapper userPreferenceMapper;
    private UserPreferenceService service;

    @BeforeEach
    void setUp() {
        userPreferenceMapper = mock(UserPreferenceMapper.class);
        service = new UserPreferenceService(userPreferenceMapper);
    }

    @Test
    void getHeightCm_withHeightConfigured_returnsValue() {
        UserPreference pref = new UserPreference();
        pref.setPreferencesJson("{\"themeMode\":\"dark\",\"heightCm\":175}");
        when(userPreferenceMapper.selectOne(any())).thenReturn(pref);

        Integer height = service.getHeightCm(1L);

        assertEquals(175, height);
    }

    @Test
    void getHeightCm_notConfigured_returnsNull() {
        UserPreference pref = new UserPreference();
        pref.setPreferencesJson("{\"themeMode\":\"dark\"}");
        when(userPreferenceMapper.selectOne(any())).thenReturn(pref);

        Integer height = service.getHeightCm(1L);

        assertNull(height);
    }

    @Test
    void getHeightCm_noPreferenceRecord_returnsNull() {
        when(userPreferenceMapper.selectOne(any())).thenReturn(null);

        Integer height = service.getHeightCm(1L);

        assertNull(height);
    }
}
