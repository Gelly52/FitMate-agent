package com.itgeo.fitmate.api.agent.core;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Agent 取消注册表。
 * 维护 runId → 取消标志的映射，供取消接口设置标志、Agent Loop 检查标志。
 */
@Component
public class AgentCancellationRegistry {

    private final ConcurrentHashMap<Long, Boolean> cancelFlags = new ConcurrentHashMap<>();

    /**
     * 注册一个 run，初始为未取消。
     */
    public void register(Long runId) {
        if (runId != null) {
            cancelFlags.put(runId, Boolean.FALSE);
        }
    }

    /**
     * 标记指定 run 为已取消。
     */
    public boolean cancel(Long runId) {
        if (runId == null) {
            return false;
        }
        return cancelFlags.replace(runId, Boolean.FALSE, Boolean.TRUE)
                || cancelFlags.putIfAbsent(runId, Boolean.TRUE) == null;
    }

    /**
     * 检查指定 run 是否已取消。
     */
    public boolean isCancelled(Long runId) {
        return runId != null && Boolean.TRUE.equals(cancelFlags.get(runId));
    }

    /**
     * run 结束后清理标志。
     */
    public void unregister(Long runId) {
        if (runId != null) {
            cancelFlags.remove(runId);
        }
    }
}
