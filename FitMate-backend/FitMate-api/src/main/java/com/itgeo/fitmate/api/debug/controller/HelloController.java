package com.itgeo.fitmate.api.debug.controller;

import com.itgeo.fitmate.api.chat.application.ChatService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 示例与调试接口。
 */
@RestController
@RequestMapping("/hello")
public class HelloController {

    @Resource
    private ChatService chatService;

    /**
     * 基础连通性测试接口。
     *
     * @return 固定问候语
     */
    @GetMapping("/world")
    public String hello() {
        return "Hello, World!";
    }

    /**
     * 普通聊天测试接口。
     *
     * @param msg 用户输入消息
     * @return 模型返回文本
     */
    @GetMapping("/chat")
    public String chat(String msg) {
        return chatService.chatTest(msg);
    }

    /**
     * 返回模型原始流式响应。
     *
     * @param msg 用户输入消息
     * @param response HTTP 响应对象
     * @return 流式聊天响应
     */
    @GetMapping("/chat/stream/response")
    public Flux<ChatResponse> chatStreamResponse(String msg, HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        return chatService.streamResponse(msg);
    }

    /**
     * 返回文本形式的流式聊天结果。
     *
     * @param msg 用户输入消息
     * @param response HTTP 响应对象
     * @return 文本流
     */
    @GetMapping("/chat/stream/str")
    public Flux<String> chatStreamStr(String msg, HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8");
        return chatService.streamStr(msg);
    }
}
