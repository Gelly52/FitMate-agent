package com.itgeo.fitmate.api.agent.core;

/**
 * Agent 执行被用户主动取消时抛出。
 * 用于区分取消与普通失败，使异步执行壳能执行不同的收尾逻辑。
 */
public class AgentCancelledException extends RuntimeException {
    /** 已生成的部分内容，用于回填 assistant 消息。 */
    private final String partialContent;

    public AgentCancelledException(String partialContent) {
        super("Agent执行已被用户取消");
        this.partialContent = partialContent;
    }

    public String getPartialContent() {
        return partialContent;
    }
}
