package com.itgeo.fitmate.api.auth.dto;

import lombok.Data;

/**
 * 用户登录响应。
 */
@Data
public class UserLoginResponse {
    /** 登录令牌。 */
    private String token;
    /** 过期时间字符串。 */
    private String expiresAt;
    /** 是否为新注册用户。 */
    private Boolean newUser;
    /** 当前登录用户信息。 */
    private LoginUserInfo userInfo;
}
