package com.itgeo.fitmate.api.auth.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.auth.infrastructure.entity.User;
import com.itgeo.fitmate.api.auth.infrastructure.entity.UserLoginSession;
import com.itgeo.fitmate.api.auth.infrastructure.mapper.UserLoginSessionMapper;
import com.itgeo.fitmate.api.auth.infrastructure.mapper.UserMapper;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * 负责统一完成登录 token 的鉴权解析，并产出当前请求可复用的用户上下文。
 * 核心链路：token -> session -> user -> AuthenticatedUserContext。
 */
@Service
public class TokenAuthService {

    @Resource
    private UserLoginSessionMapper userLoginSessionMapper;

    @Resource
    private UserMapper userMapper;

    /**
     * 根据请求头中的 token 解析当前登录用户。
     *
     * @param token 前端透传的登录 token
     * @return 当前请求可直接复用的已认证用户上下文
     */
    public AuthenticatedUserContext authenticateToken(String token) {
        if (StrUtil.isBlank(token)) {
            throw new IllegalArgumentException("请先登录后再继续操作");
        }
        UserLoginSession session = userLoginSessionMapper.selectOne(new LambdaQueryWrapper<UserLoginSession>()
                .eq(UserLoginSession::getRefreshTokenHash, SecureUtil.sha256(token))
                .last("limit 1"));
        return buildAuthenticatedUserContext(token, session);
    }

    /**
     * 根据 SSE 建连时携带的 sessionId 补齐当前连接的用户上下文。
     *
     * @param sessionId 已建立登录态对应的会话主键
     * @return 当前连接对应的已认证用户上下文
     */
    public AuthenticatedUserContext authenticateSessionId(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("SSE 连接凭证无效，请重新登录后重试");
        }
        UserLoginSession session = userLoginSessionMapper.selectById(sessionId);
        return buildAuthenticatedUserContext(null, session);
    }

    /**
     * 基于已查询到的会话记录组装统一鉴权上下文。
     *
     * <p>步骤：</p>
     * <ol>
     *     <li>先校验会话是否存在、是否被撤销、是否仍处于有效期内。</li>
     *     <li>再按会话中的 userId 加载用户，并校验账号是否仍可用。</li>
     *     <li>最后把 token、session 与用户基础信息收口为统一的上下文对象。</li>
     * </ol>
     */
    private AuthenticatedUserContext buildAuthenticatedUserContext(String token, UserLoginSession session) {
        // 步骤 1：先确认登录会话本身仍然有效，避免使用失效或已撤销的登录态。
        UserLoginSession validSession = requireValidSession(session);
        // 步骤 2：再根据会话归属用户补齐用户信息，并拦截已删除或已禁用账号。
        User user = requireValidUser(validSession.getUserId());
        // 步骤 3：将 token、session、user 三段信息汇总为当前请求统一使用的鉴权上下文。
        return AuthenticatedUserContext.builder()
                .token(token)
                .sessionId(validSession.getId())
                .userId(user.getId())
                .userKey(user.getUserKey())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .phone(user.getPhone())
                .email(user.getEmail())
                .sseClientId(AuthenticatedUserContext.buildSseClientId(validSession.getId()))
                .build();
    }

    /**
     * 校验登录会话是否仍可继续使用。
     */
    private UserLoginSession requireValidSession(UserLoginSession session) {
        // 会话不存在或已被撤销时，统一视为登录状态失效。
        if (session == null) {
            throw new IllegalArgumentException("登录状态已失效，请重新登录");
        }
        if (session.getRevokedAt() != null) {
            throw new IllegalArgumentException("登录状态已失效，请重新登录");
        }
        // 过期时间缺失或已到期时，不再允许继续复用该登录态。
        LocalDateTime expiredAt = session.getExpiredAt();
        if (expiredAt == null || !expiredAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("登录状态已过期，请重新登录");
        }
        return session;
    }

    /**
     * 校验会话归属用户是否存在且状态正常。
     */
    private User requireValidUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("当前用户不存在或已删除");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new IllegalArgumentException("当前用户已被禁用");
        }
        return user;
    }
}
