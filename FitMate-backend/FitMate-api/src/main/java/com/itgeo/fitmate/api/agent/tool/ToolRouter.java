package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 校验工具 allowlist 并执行工具。
 */
@Component
public class ToolRouter {

    @Resource
    private ToolRegistry toolRegistry;

    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (call == null || call.getName() == null || call.getName().isBlank()) {
            return ToolResult.error("工具名不能为空");
        }
        return toolRegistry.findAllowed(call.getName())
                .map(executor -> safeExecute(executor, call, authenticatedUser))
                .orElseGet(() -> ToolResult.error("工具未开放或不存在: " + call.getName()));
    }

    private ToolResult safeExecute(ToolExecutor executor, ToolCall call, AuthenticatedUserContext authenticatedUser) {
        try {
            return executor.execute(call, authenticatedUser);
        } catch (Exception e) {
            return ToolResult.error(e.getMessage() == null ? "工具执行失败" : e.getMessage());
        }
    }
}
