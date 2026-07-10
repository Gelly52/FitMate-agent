package com.itgeo.fitmate.api.auth.application;

import com.itgeo.fitmate.api.auth.dto.UserLoginResponse;
import com.itgeo.fitmate.api.auth.dto.UserPreferenceItem;
import com.itgeo.fitmate.api.auth.dto.UserProfileResponse;
import com.itgeo.fitmate.api.auth.dto.UserProfileUpdateRequest;
import com.itgeo.fitmate.api.auth.infrastructure.entity.User;
import java.util.Map;

/**
 * 用户登录服务接口。
 */
public interface UserService {

    /**
     * 发送手机号登录验证码（旧流程，保留兼容）。
     *
     * @param phone 手机号
     */
    void sendCode(String phone);

    /**
     * 使用手机号和验证码完成登录（旧流程，保留兼容）。
     *
     * @param phone 手机号
     * @param code 验证码
     * @param clientIp 客户端 IP
     * @param userAgent 客户端 User-Agent
     * @return 登录响应
     */
    UserLoginResponse login(String phone, String code, String clientIp, String userAgent);

    /**
     * 发送邮箱登录验证码。
     *
     * @param email 邮箱
     */
    void sendEmailCode(String email);

    /**
     * 使用邮箱、验证码与密码完成登录。邮箱不存在时自动注册并登录。
     *
     * @param email 邮箱
     * @param code 邮箱验证码
     * @param password 登录密码
     * @param clientIp 客户端 IP
     * @param userAgent 客户端 User-Agent
     * @return 登录响应
     */
    UserLoginResponse emailLogin(String email, String code, String password, String clientIp, String userAgent);

    /**
     * 检查邮箱注册状态，用于登录页判断是否需要验证码。
     * 当账号存在且已设置密码时，前端可允许跳过验证码。
     *
     * @param email 邮箱
     * @return 包含 exists 与 passwordSet 两个布尔字段
     */
    Map<String, Boolean> checkEmailRegistered(String email);

    /**
     * 注销指定令牌对应的登录会话。
     *
     * @param token 登录令牌
     */
    void logout(String token);

    /**
     * 获取当前登录用户的完整资料。
     *
     * @param userId 用户主键
     * @return 用户资料响应
     */
    UserProfileResponse getProfile(Long userId);

    /**
     * 更新当前登录用户的昵称/手机号。
     *
     * @param userId  用户主键
     * @param request 更新请求
     * @return 更新后的用户资料
     */
    UserProfileResponse updateProfile(Long userId, UserProfileUpdateRequest request);

    /**
     * 获取用户偏好设置，无记录返回默认值。
     *
     * @param userId 用户主键
     * @return 偏好设置项
     */
    UserPreferenceItem getPreferences(Long userId);

    /**
     * 保存用户偏好设置（upsert）。
     *
     * @param userId 用户主键
     * @param item   偏好设置项
     * @return 保存后的偏好设置项
     */
    UserPreferenceItem savePreferences(Long userId, UserPreferenceItem item);
}
