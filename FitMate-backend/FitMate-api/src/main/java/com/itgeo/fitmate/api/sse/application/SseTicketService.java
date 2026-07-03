package com.itgeo.fitmate.api.sse.application;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.sse.dto.SseTicketResponse;

/**
 * SSE 建链票据服务契约。
 * <p>
 * 提供短时票据的签发与消费能力，用于把已登录用户上下文安全地交给 SSE 建链接口。
 */
public interface SseTicketService {

    /**
     * 为当前登录用户创建一个短时有效的 SSE 建链票据。
     *
     * @param authenticatedUser 当前登录用户上下文
     * @return 票据与过期时间
     */
    SseTicketResponse createTicket(AuthenticatedUserContext authenticatedUser);

    /**
     * 消费 SSE 建链票据，并解析出可用于建链的用户上下文。
     *
     * @param ticket SSE 建链票据
     * @return 已恢复的登录用户上下文
     */
    AuthenticatedUserContext consumeTicket(String ticket);
}
