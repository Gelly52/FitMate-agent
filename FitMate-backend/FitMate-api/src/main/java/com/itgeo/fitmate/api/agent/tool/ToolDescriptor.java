package com.itgeo.fitmate.api.agent.tool;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class ToolDescriptor {
    private String name;
    private String description;
    private String parametersSchema;
    private boolean readOnly;

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getParametersSchema() {
        return parametersSchema;
    }

    public boolean isReadOnly() {
        return readOnly;
    }
}
