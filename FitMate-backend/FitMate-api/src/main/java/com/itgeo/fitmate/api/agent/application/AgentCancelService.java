package com.itgeo.fitmate.api.agent.application;

/**
 * Agent 取消服务契约。
 */
public interface AgentCancelService {
    /**
     * 取消指定 runId 的 Agent 执行。
     *
     * @param userId 当前用户ID（权限校验用）
     * @param runId 要取消的 run ID
     * @return true 表示已成功设置取消标志
     */
    boolean cancel(Long userId, Long runId);
}
