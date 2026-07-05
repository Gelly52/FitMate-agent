package com.itgeo.fitmate.common.constant;

/**
 * Redis key 常量集合。
 * 集中维护跨模块共享的 Redis key 前缀，避免硬编码散落各处。
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {}

    /**
     * Agent 任务并发锁 key 前缀。
     * 完整 key 形如：fitmate:dev:agent:lock:session:{sessionId}:slot:{1..N}
     */
    public static final String AGENT_LOCK_KEY_PREFIX = "fitmate:dev:agent:lock:session:";

    /**
     * 单个登录 sessionId 允许同时运行的 Agent 任务上限。
     * 对应 N 个 slot 锁。
     */
    public static final int AGENT_LOCK_SLOT_COUNT = 3;
}
