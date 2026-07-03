package com.itgeo.fitmate.api.auth.dto;

import lombok.Data;

/**
 * 验证码登录请求。
 */
@Data
public class UserLoginRequest {
    /** 登录手机号（旧流程，保留兼容）。 */
    private String phone;
    /** 登录邮箱（邮箱登录流程使用）。 */
    private String email;
    /** 短信/邮箱验证码。 */
    private String code;
    /** 登录密码（邮箱登录流程使用）。 */
    private String password;
}
