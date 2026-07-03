package com.itgeo.fitmate.api.auth.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 用户登录会话实体。
 */
@Data
@ToString
@TableName("t_user_login_session")
public class UserLoginSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("refresh_token_hash")
    /** 刷新令牌哈希。 */
    private String refreshTokenHash;

    @TableField("client_ip")
    private String clientIp;

    @TableField("user_agent")
    private String userAgent;

    @TableField("expired_at")
    /** 会话过期时间。 */
    private LocalDateTime expiredAt;

    @TableField("revoked_at")
    /** 会话撤销时间。 */
    private LocalDateTime revokedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
