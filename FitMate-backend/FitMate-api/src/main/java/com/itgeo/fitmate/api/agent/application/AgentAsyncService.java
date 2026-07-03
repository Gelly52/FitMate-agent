package com.itgeo.fitmate.api.agent.application;

import com.itgeo.fitmate.api.agent.dto.AgentExecuteContext;

/**
 * Agent 异步执行服务契约。
 *
 * 职责：
 * 1. 在后台推进 run / step 状态流转；
 * 2. 协调 Agent 能力执行与运行态收尾；
 * 3. 在必要时完成失败兜底与锁释放。
 */
public interface AgentAsyncService {

/**
     * 异步执行一次 Agent run，并完成运行态闭环。
     *
     * @param context Agent 执行上下文
     */
    void executeAsync(AgentExecuteContext context);
}
