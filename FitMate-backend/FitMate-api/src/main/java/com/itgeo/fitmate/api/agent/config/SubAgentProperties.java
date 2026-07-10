package com.itgeo.fitmate.api.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Sub-Agent 运行配置，绑定 fitmate.agent.sub-agent。
 * Sub-Agent 是主 Agent 派生的子循环，拥有独立的预算。工具调用超时复用主 Agent 配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fitmate.agent.sub-agent")
public class SubAgentProperties {

    /** Sub-Agent 最大迭代轮次。 */
    private Integer maxIterations = 8;
    /** Sub-Agent 最大工具调用次数。 */
    private Integer maxToolCalls = 20;
    /** Sub-Agent 最大运行时长（秒）。 */
    private Integer maxRunDurationSeconds = 600;
}
