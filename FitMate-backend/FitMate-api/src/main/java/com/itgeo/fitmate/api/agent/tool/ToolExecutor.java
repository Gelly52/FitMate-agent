package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;

public interface ToolExecutor {

    ToolDescriptor descriptor();

    ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser);
}
