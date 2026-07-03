package com.itgeo.fitmate.api.sse.application.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.auth.application.TokenAuthService;
import com.itgeo.fitmate.api.sse.application.SseTicketService;
import com.itgeo.fitmate.api.sse.dto.SseTicketResponse;
import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * SSE 建链票据服务实现。
 * <p>
 * 负责为已登录用户生成短时有效的建链 ticket，并在建链时把 ticket 还原为用户上下文。
 * 该服务不是登录 token 服务；ticket 仅用于 SSE 连接前的一次性短时交换。
 */
@Service
public class SseTicketServiceImpl implements SseTicketService {

    private static final String SSE_TICKET_KEY_PREFIX = "fitmate:dev:sse:ticket:";
    private static final long SSE_TICKET_TTL_SECONDS = 60L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private TokenAuthService tokenAuthService;

    /**
     * 创建 SSE 建链 ticket。
     * <p>
     * 步骤：
     * 1. 校验当前登录上下文中存在可恢复的 sessionId；
     * 2. 生成短时有效的随机 ticket；
     * 3. 以 ticket 为 key、sessionId 为 value 写入 Redis；
     * 4. 返回 ticket 与过期时间给前端，用于后续 `/sse/connect` 建链。
     */
    @Override
    public SseTicketResponse createTicket(AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getSessionId() == null) {
            throw new IllegalArgumentException("登录状态已失效，请重新登录");
        }

        String ticket = IdUtil.fastSimpleUUID();
        stringRedisTemplate.opsForValue().set(
                buildTicketKey(ticket),
                String.valueOf(authenticatedUser.getSessionId()),
                SSE_TICKET_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        return new SseTicketResponse(ticket, SSE_TICKET_TTL_SECONDS);
    }

    /**
     * 消费 SSE 建链 ticket。
     * <p>
     * 步骤：
     * 1. 校验 ticket 非空；
     * 2. 从 Redis 读取 ticket 对应的 sessionId；
     * 3. 读取成功后删除该 ticket，缩小重复使用窗口；
     * 4. 再通过 sessionId 恢复完整的登录用户上下文。
     * <p>
     * 说明：这里是“先读后删”的顺序消费，不提供原子消费语义。
     */
    @Override
    public AuthenticatedUserContext consumeTicket(String ticket) {
        if (StrUtil.isBlank(ticket)) {
            throw new IllegalArgumentException("SSE 连接票据不能为空");
        }

        String ticketKey = buildTicketKey(ticket);
        String sessionIdValue = stringRedisTemplate.opsForValue().get(ticketKey);
        if (StrUtil.isBlank(sessionIdValue)) {
            throw new IllegalArgumentException("SSE 连接票据无效或已过期，请重新获取");
        }

        stringRedisTemplate.delete(ticketKey);
        try {
            return tokenAuthService.authenticateSessionId(Long.valueOf(sessionIdValue));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("SSE 连接票据数据异常，请重新获取");
        }
    }

    /**
     * 生成 Redis 中使用的 ticket 键。
     */
    private String buildTicketKey(String ticket) {
        return SSE_TICKET_KEY_PREFIX + ticket;
    }
}
