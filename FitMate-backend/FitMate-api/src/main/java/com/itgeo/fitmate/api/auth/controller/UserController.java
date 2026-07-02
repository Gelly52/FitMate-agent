package com.itgeo.fitmate.api.auth.controller;

import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.auth.application.UserService;
import com.itgeo.fitmate.api.auth.dto.UserCodeRequest;
import com.itgeo.fitmate.api.auth.dto.UserLoginRequest;
import com.itgeo.fitmate.api.auth.dto.UserPreferenceItem;
import com.itgeo.fitmate.api.auth.dto.UserProfileUpdateRequest;
import com.itgeo.fitmate.api.auth.infrastructure.entity.User;
import com.itgeo.fitmate.api.sse.application.SseTicketService;
import com.itgeo.fitmate.common.response.LeeResult;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户登录与 SSE 建链相关接口。
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private SseTicketService sseTicketService;

    /**
     * 发送邮箱登录验证码。
     *
     * @param request 验证码请求体
     * @return 通用响应结果
     */
    @PostMapping("/code")
    public LeeResult sendCode(@RequestBody UserCodeRequest request) {
        try {
            userService.sendEmailCode(request == null ? null : request.getEmail());
            return LeeResult.ok();
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("发送验证码失败", e);
            return LeeResult.errorException("发送验证码失败");
        }
    }

    /**
     * 使用邮箱、验证码与密码完成登录。
     *
     * @param request 登录请求体
     * @param httpServletRequest HTTP 请求对象
     * @return 通用响应结果
     */
    @PostMapping("/login")
    public LeeResult login(@RequestBody UserLoginRequest request, HttpServletRequest httpServletRequest) {
        try {
            return LeeResult.ok(userService.emailLogin(
                    request == null ? null : request.getEmail(),
                    request == null ? null : request.getCode(),
                    request == null ? null : request.getPassword(),
                    resolveClientIp(httpServletRequest),
                    httpServletRequest.getHeader("User-Agent")
            ));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("邮箱登录失败", e);
            return LeeResult.errorException("登录失败");
        }
    }

    /**
     * 退出当前登录状态。
     *
     * @param httpServletRequest HTTP 请求对象
     * @return 通用响应结果
     */
    @PostMapping("/logout")
    public LeeResult logout(HttpServletRequest httpServletRequest) {
        try {
            userService.logout(httpServletRequest.getHeader("headerUserToken"));
            return LeeResult.ok();
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("退出登录失败", e);
            return LeeResult.errorException("退出登录失败");
        }
    }

    /**
     * 为当前登录用户创建 SSE 建链票据。
     *
     * @return 通用响应结果
     */
    @PostMapping("/sse-ticket")
    public LeeResult createSseTicket() {
        try {
            return LeeResult.ok(sseTicketService.createTicket(UserContextHolder.getRequired()));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("创建 SSE 连接票据失败", e);
            return LeeResult.errorException("创建 SSE 连接票据失败");
        }
    }

    /**
     * 获取当前登录用户的完整资料。
     *
     * @return 通用响应结果
     */
    @GetMapping("/profile")
    public LeeResult getProfile() {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(userService.getProfile(userId));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("获取用户资料失败", e);
            return LeeResult.errorException("获取用户资料失败");
        }
    }

    /**
     * 更新当前登录用户的昵称/手机号。
     *
     * @param request 更新请求体
     * @return 通用响应结果
     */
    @PutMapping("/profile")
    public LeeResult updateProfile(@RequestBody UserProfileUpdateRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(userService.updateProfile(userId, request));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("更新用户资料失败", e);
            return LeeResult.errorException("更新用户资料失败");
        }
    }

    /**
     * 获取当前登录用户的偏好设置。
     *
     * @return 通用响应结果
     */
    @GetMapping("/preferences")
    public LeeResult getPreferences() {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(userService.getPreferences(userId));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("获取用户偏好失败", e);
            return LeeResult.errorException("获取用户偏好失败");
        }
    }

    /**
     * 保存当前登录用户的偏好设置。
     *
     * @param request 偏好设置请求体
     * @return 通用响应结果
     */
    @PutMapping("/preferences")
    public LeeResult savePreferences(@RequestBody UserPreferenceItem request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(userService.savePreferences(userId, request));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("保存用户偏好失败", e);
            return LeeResult.errorException("保存用户偏好失败");
        }
    }

    /**
     * 优先读取 X-Forwarded-For，否则回退到直连地址。
     *
     * @param request HTTP 请求对象
     * @return 客户端 IP
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
