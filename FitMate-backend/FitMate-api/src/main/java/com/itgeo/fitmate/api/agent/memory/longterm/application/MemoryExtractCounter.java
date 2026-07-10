package com.itgeo.fitmate.api.agent.memory.longterm.application;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 会话级记忆提取计数器，基于 Redis 记录每个会话上次提取时的用户消息数，
 * 用于实现"每 N 轮用户消息触发一次记忆提取"。
 */
@Slf4j
@Service
public class MemoryExtractCounter {

    private static final String KEY_PREFIX = "fitmate:dev:memory:session:extracted-count:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 获取指定会话上次提取记忆时的用户消息数（首次返回 0）。
     */
    public long getLastExtractedUserMsgCount(Long sessionId) {
        try {
            String value = stringRedisTemplate.opsForValue().get(buildKey(sessionId));
            if (value == null || value.isBlank()) {
                return 0L;
            }
            return Long.parseLong(value);
        } catch (Exception e) {
            log.warn("读取记忆提取计数失败 sessionId={}", sessionId, e);
            return 0L;
        }
    }

    /**
     * 记录本次提取时的用户消息数。
     */
    public void markExtracted(Long sessionId, long currentUserMsgCount) {
        try {
            stringRedisTemplate.opsForValue().set(buildKey(sessionId), String.valueOf(currentUserMsgCount));
        } catch (Exception e) {
            log.warn("写入记忆提取计数失败 sessionId={}", sessionId, e);
        }
    }

    private String buildKey(Long sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
