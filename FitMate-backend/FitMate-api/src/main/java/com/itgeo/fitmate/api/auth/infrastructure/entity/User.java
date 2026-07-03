package com.itgeo.fitmate.api.auth.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 用户基础信息实体。
 */
@Data
@ToString
@TableName("t_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_key")
    /** 对外稳定用户标识。 */
    private String userKey;

    private String username;

    @TableField("password_hash")
    /** 密码哈希。 */
    private String passwordHash;

    private String nickname;
    private String email;
    private String phone;
    /** 用户状态。 */
    private Integer status;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
