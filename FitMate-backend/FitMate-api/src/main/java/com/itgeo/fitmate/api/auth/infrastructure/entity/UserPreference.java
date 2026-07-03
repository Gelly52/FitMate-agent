package com.itgeo.fitmate.api.auth.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 用户偏好设置实体。
 */
@Data
@ToString
@TableName(value = "t_user_preference", autoResultMap = true)
public class UserPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    /** 偏好 JSON，原始字符串读写，由 service 层序列化/反序列化。 */
    private String preferencesJson;

    /** LLM 配置 JSON，原始字符串读写，由 service 层序列化/反序列化，apiKey 字段为 AES 加密密文。 */
    private String llmConfigJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
