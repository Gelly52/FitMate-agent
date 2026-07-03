package com.itgeo.fitmate.api.sse.controller;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.sse.application.SseTicketService;
import com.itgeo.fitmate.api.sse.domain.SSEMsgType;
import com.itgeo.fitmate.api.sse.infrastructure.SSEServer;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 连接与调试控制器。
 * <p>
 * 正式建链入口使用 ticket 方式建立连接；其余发送接口主要用于联调与排障。
 */
@RestController
@RequestMapping("/sse")
public class SSEController {

    @Resource
    private SseTicketService sseTicketService;

    /**
     * 使用 SSE ticket 建立连接。
     * <p>
     * 步骤：
     * 1. 消费短时 ticket，恢复当前登录用户上下文；
     * 2. 取出用户对应的 sseClientId；
     * 3. 按 sseClientId 建立或替换 SSE 长连接。
     * <p>
     * 该入口不是直接使用登录 token 建链。
     */
    @GetMapping(path = "/connect", produces = {MediaType.TEXT_EVENT_STREAM_VALUE})
    public SseEmitter connect(@RequestParam("ticket") String ticket) {
        AuthenticatedUserContext authenticatedUser = sseTicketService.consumeTicket(ticket);
        return SSEServer.connect(authenticatedUser.getSseClientId());
    }

    /**
     * 向当前登录用户对应的 SSE 连接发送一条普通调试消息。
     */
    @GetMapping("/sendMessage")
    public Object sendMessage(@RequestParam String message) {
        SSEServer.sendMsg(UserContextHolder.getRequired().getSseClientId(), message, SSEMsgType.MESSAGE);
        return "success";
    }

    /**
     * 调试接口：连续发送多条 ADD 事件，便于前端联调增量渲染效果。
     */
    @GetMapping("/sendMessageAdd")
    public Object sendMessageAdd(@RequestParam String message) {
        String sseClientId = UserContextHolder.getRequired().getSseClientId();
        for (int i = 0; i < 10; i++) {
            SSEServer.sendMsg(sseClientId, message, SSEMsgType.ADD);
        }
        return "success";
    }

    /**
     * 调试广播接口：向当前所有在线 SSE 连接发送同一条消息。
     */
    @GetMapping("/sendMessageAll")
    public Object sendMessageAll(@RequestParam String message) {
        SSEServer.sendMsgToAllUsers(message);
        return "success";
    }
}
