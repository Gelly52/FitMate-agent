package com.itgeo.fitmate.api.sse.domain;

/**
 * SSE 消息类型枚举，约定服务端推送给前端的事件类别。
 */
public enum SSEMsgType {
    /**
     * 单次完整消息推送，适用于非流式场景。
     */
    MESSAGE("message", "单次完整消息"),
    /**
     * 流式追加分片，前端按顺序拼接内容。
     */
    ADD("add", "流式追加分片"),
    /**
     * 思考过程分片，前端可折叠或用不同样式展示。
     */
    THINKING("thinking", "思考过程分片"),
    /**
     * 本轮输出结束事件，通常携带最终结果快照。
     */
    FINISH("finish", "输出结束事件"),
    /**
     * 业务自定义事件，当前主要用于 Agent 步骤状态通知。
     */
    AGENT_STEP("agentStep", "Agent 步骤事件"),
    /**
     * 业务自定义事件，保留给其他扩展场景。
     */
    CUSTOM_EVENT("custom_event", "业务自定义事件"),
    /**
     * 错误事件。
     */
    ERROR("error", "错误事件"),
    /**
     * 兼容部分上游协议的结束标记，作为预留类型保留，不等同于业务 FINISH 载荷。
     */
    DONE("done", "兼容结束标记");

    /** 事件类型编码。 */
    public final String type;
    /** 类型说明文本。 */
    public final String value;

    SSEMsgType(String type, String value) {
        this.type = type;
        this.value = value;
    }

}
