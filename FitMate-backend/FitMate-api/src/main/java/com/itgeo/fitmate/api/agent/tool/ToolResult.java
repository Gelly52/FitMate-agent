package com.itgeo.fitmate.api.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {
    private boolean success;
    private String content;
    private Object data;
    private String errorMessage;

    public static ToolResult ok(String content, Object data) {
        return new ToolResult(true, content, data, null);
    }

    public static ToolResult error(String message) {
        return new ToolResult(false, null, null, message);
    }
}
