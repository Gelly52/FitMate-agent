package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 校验工具 allowlist 并执行工具。
 *
 * 统一在调度入口打印工具调用日志，避免每个 ToolExecutor 重复埋点。
 */
@Slf4j
@Component
public class ToolRouter {

    @Resource
    private ToolRegistry toolRegistry;

    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (call == null || call.getName() == null || call.getName().isBlank()) {
            return ToolResult.error("工具名不能为空");
        }
        log.info("[Tool] 调用工具 name={} id={} args={}",
                call.getName(),
                call.getToolCallId(),
                call.getArguments());
        return toolRegistry.findAllowed(call.getName())
                .map(executor -> safeExecute(executor, call, authenticatedUser))
                .orElseGet(() -> {
                    log.warn("[Tool] 工具未开放或不存在 name={}", call.getName());
                    return ToolResult.error("工具未开放或不存在: " + call.getName());
                });
    }

    private ToolResult safeExecute(ToolExecutor executor, ToolCall call, AuthenticatedUserContext authenticatedUser) {
        try {
            ToolResult result = executor.execute(call, authenticatedUser);
            if (result.isSuccess()) {
                log.info("[Tool] 工具返回 name={} success=true content={}",
                        call.getName(),
                        truncate(result.getContent()));
            } else {
                log.warn("[Tool] 工具返回 name={} success=false error={}",
                        call.getName(),
                        result.getErrorMessage());
            }
            return result;
        } catch (Exception e) {
            log.warn("[Tool] 工具异常 name={}", call.getName(), e);
            return ToolResult.error(e.getMessage() == null ? "工具执行失败" : e.getMessage());
        }
    }

    /** 截断过长 content，避免日志爆炸。 */
    private String truncate(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= 200 ? content : content.substring(0, 200) + "...";
    }
}
