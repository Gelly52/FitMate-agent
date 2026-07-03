package com.itgeo.fitmate.api.auth.dto;

import lombok.Data;

/**
 * 发送登录验证码请求。
 */
@Data
public class UserCodeRequest {
    /** 登录手机号（旧流程，保留兼容）。 */
    private String phone;
    /** 登录邮箱（邮箱登录流程使用）。 */
    private String email;
}
