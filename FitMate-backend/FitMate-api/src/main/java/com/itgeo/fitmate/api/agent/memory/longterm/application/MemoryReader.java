package com.itgeo.fitmate.api.agent.memory.longterm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserProfile;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserProfileMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryReader {

    private final UserProfileMapper profileMapper;
    private final MemoryProperties properties;

    /**
     * 加载用户画像区块文本，用于注入 Agent prompt。
     * 返回 "## 用户画像\n{profileText}" 或空串（禁用/无缓存/过期时）。
     */
    public String loadProfileSection(Long userId) {
        if (!properties.isEnabled()) {
            return "";
        }
        UserProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
        if (profile == null || profile.getProfileText() == null) {
            return "";
        }
        // 检查过期
        int ttlHours = properties.getProfile().getCacheTtlHours();
        if (profile.getUpdatedAt() != null
                && profile.getUpdatedAt().isBefore(LocalDateTime.now().minusHours(ttlHours))) {
            log.debug("画像缓存已过期 userId={}", userId);
            return "";
        }
        return "## 用户画像\n" + profile.getProfileText();
    }
}
