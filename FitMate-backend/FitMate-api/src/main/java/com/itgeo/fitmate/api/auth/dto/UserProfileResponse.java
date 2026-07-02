package com.itgeo.fitmate.api.auth.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户资料响应。
 */
@Data
public class UserProfileResponse {
    private String nickname;
    private String phone;
    private String email;
    private String username;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}
