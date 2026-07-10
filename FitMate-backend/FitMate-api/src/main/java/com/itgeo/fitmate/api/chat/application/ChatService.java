package com.itgeo.fitmate.api.chat.application;

import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * 聊天能力服务契约。
 * <p>
 * 正式对话主链路已统一收敛到 Agent 模式（/agent/execute），本接口仅保留调试方法。
 */
public interface ChatService {

    /**
     * 测试同步聊天。
     *
     * @param prompt 提示词
     * @return 回复内容
     */
    String chatTest(String prompt);

    /**
     * 测试流式聊天，返回完整 {@link ChatResponse} 分片流。
     *
     * @param prompt 提示词
     * @return 流式响应
     */
    Flux<ChatResponse> streamResponse(String prompt);

    /**
     * 测试流式聊天，返回字符串分片。
     *
     * @param prompt 提示词
     * @return 文本分片流
     */
    Flux<String> streamStr(String prompt);
}
