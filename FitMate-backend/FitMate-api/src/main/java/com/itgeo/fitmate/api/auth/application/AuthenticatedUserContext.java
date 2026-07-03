package com.itgeo.fitmate.api.auth.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 已通过统一鉴权后的用户上下文快照。
 * 用于在当前请求链路中复用会话标识与用户基础信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticatedUserContext {

    /** 原始登录 token，部分基于 sessionId 的场景下可能为空。 */
    private String token;
    /** 当前登录会话主键。 */
    private Long sessionId;
    /** 当前登录用户主键。 */
    private Long userId;
    /** 业务侧稳定用户标识。 */
    private String userKey;
    /** 登录用户名。 */
    private String username;
    /** 用户昵称。 */
    private String nickname;
    /** 用户手机号。 */
    private String phone;
    /** 用户邮箱。 */
    private String email;
    /** 由 sessionId 派生的 SSE 客户端标识。 */
    private String sseClientId;

    /**
     * 根据登录会话主键生成 SSE 客户端标识。
     */
    public static String buildSseClientId(Long sessionId) {
        return sessionId == null ? null : "session:" + sessionId;
    }
}
