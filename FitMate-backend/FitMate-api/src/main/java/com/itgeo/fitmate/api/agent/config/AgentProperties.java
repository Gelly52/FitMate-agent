package com.itgeo.fitmate.api.agent.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent Loop 运行配置。
 */
@Component
@ConfigurationProperties(prefix = "fitmate.agent")
public class AgentProperties {

    private Integer maxIterations = 20;
    private Integer maxToolCalls = 100;
    private Integer maxRunDurationSeconds = 1800;
    private Integer llmTimeoutSeconds = 120;
    private Integer toolTimeoutSeconds = 30;
    private Integer memoryWindowSize = 20;
    private List<String> enabledTools = new ArrayList<>(List.of(
            "date.now",
            "rag.search",
            "body_metrics.query",
            "training_log.query"
    ));
    /** 上下文压缩配置。 */
    private ContextCompressProperties contextCompress = new ContextCompressProperties();

    public Integer getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(Integer maxIterations) {
        this.maxIterations = maxIterations;
    }

    public Integer getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(Integer maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public Integer getMaxRunDurationSeconds() {
        return maxRunDurationSeconds;
    }

    public void setMaxRunDurationSeconds(Integer maxRunDurationSeconds) {
        this.maxRunDurationSeconds = maxRunDurationSeconds;
    }

    public Integer getLlmTimeoutSeconds() {
        return llmTimeoutSeconds;
    }

    public void setLlmTimeoutSeconds(Integer llmTimeoutSeconds) {
        this.llmTimeoutSeconds = llmTimeoutSeconds;
    }

    public Integer getToolTimeoutSeconds() {
        return toolTimeoutSeconds;
    }

    public void setToolTimeoutSeconds(Integer toolTimeoutSeconds) {
        this.toolTimeoutSeconds = toolTimeoutSeconds;
    }

    public Integer getMemoryWindowSize() {
        return memoryWindowSize;
    }

    public void setMemoryWindowSize(Integer memoryWindowSize) {
        this.memoryWindowSize = memoryWindowSize;
    }

    public List<String> getEnabledTools() {
        return enabledTools;
    }

    public void setEnabledTools(List<String> enabledTools) {
        this.enabledTools = enabledTools;
    }

    public ContextCompressProperties getContextCompress() {
        return contextCompress;
    }

    public void setContextCompress(ContextCompressProperties contextCompress) {
        this.contextCompress = contextCompress;
    }
}
