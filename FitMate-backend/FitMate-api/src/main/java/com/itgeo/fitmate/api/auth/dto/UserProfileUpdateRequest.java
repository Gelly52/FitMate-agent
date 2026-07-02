package com.itgeo.fitmate.api.auth.dto;

import lombok.Data;

/**
 * 用户资料更新请求。仅允许 nickname / phone，email/username 不可改。
 */
@Data
public class UserProfileUpdateRequest {
    /** 昵称，非空时长度 1-100。 */
    private String nickname;
    /** 手机号，非空时校验格式。 */
    private String phone;
}
