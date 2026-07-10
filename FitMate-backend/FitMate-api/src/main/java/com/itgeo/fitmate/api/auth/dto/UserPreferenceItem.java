package com.itgeo.fitmate.api.auth.dto;

import lombok.Data;

/**
 * 用户偏好设置项，对应 preferences_json 的结构。
 */
@Data
public class UserPreferenceItem {
    /** 主题模式：light / dark / auto */
    private String themeMode;
    /** 强调色：blue / green / orange / purple */
    private String accentColor;
    /** 身高（cm），用于BMI计算 */
    private Integer heightCm;
}
