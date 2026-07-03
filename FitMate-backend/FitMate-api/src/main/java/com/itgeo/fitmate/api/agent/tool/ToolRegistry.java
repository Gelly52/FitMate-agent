package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.agent.config.AgentProperties;
import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Agent 工具注册表，只暴露配置允许的工具。
 */
@Component
public class ToolRegistry {

    private final Map<String, ToolExecutor> executors;

    @Resource
    private AgentProperties agentProperties;

    public ToolRegistry(List<ToolExecutor> toolExecutors) {
        this.executors = toolExecutors.stream()
                .collect(Collectors.toMap(
                        executor -> executor.descriptor().getName(),
                        executor -> executor,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    public List<ToolDescriptor> allowedDescriptors() {
        return enabledToolNames().stream()
                .map(executors::get)
                .filter(java.util.Objects::nonNull)
                .map(ToolExecutor::descriptor)
                .collect(Collectors.toList());
    }

    public Optional<ToolExecutor> findAllowed(String name) {
        if (name == null || !enabledToolNames().contains(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(executors.get(name));
    }

    private List<String> enabledToolNames() {
        return agentProperties.getEnabledTools() == null ? List.of() : agentProperties.getEnabledTools();
    }
}
