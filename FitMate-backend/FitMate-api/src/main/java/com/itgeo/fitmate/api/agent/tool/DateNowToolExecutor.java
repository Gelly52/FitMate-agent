package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class DateNowToolExecutor implements ToolExecutor {

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor("date.now", "获取当前日期时间", "{}", true);
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return ToolResult.ok("当前时间: " + now, now);
    }
}
