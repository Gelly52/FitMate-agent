package com.itgeo.fitmate.api.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.itgeo.fitmate.api.auth.infrastructure.entity.UserPreference;
import com.itgeo.fitmate.api.auth.infrastructure.mapper.UserPreferenceMapper;
import org.springframework.stereotype.Service;

/**
 * 用户偏好服务，提供偏好 JSON 字段的解析读取。
 */
@Service
public class UserPreferenceService {

    private final UserPreferenceMapper userPreferenceMapper;

    public UserPreferenceService(UserPreferenceMapper userPreferenceMapper) {
        this.userPreferenceMapper = userPreferenceMapper;
    }

    /**
     * 获取用户身高（cm）。
     *
     * @param userId 用户ID
     * @return 身高 cm，未配置返回 null
     */
    public Integer getHeightCm(Long userId) {
        UserPreference pref = userPreferenceMapper.selectOne(
                new LambdaQueryWrapper<UserPreference>()
                        .eq(UserPreference::getUserId, userId)
                        .last("limit 1")
        );
        if (pref == null || pref.getPreferencesJson() == null) {
            return null;
        }
        try {
            ObjectNode node = new ObjectMapper().readValue(pref.getPreferencesJson(), ObjectNode.class);
            if (node.has("heightCm") && node.get("heightCm").isNumber()) {
                return node.get("heightCm").asInt();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
