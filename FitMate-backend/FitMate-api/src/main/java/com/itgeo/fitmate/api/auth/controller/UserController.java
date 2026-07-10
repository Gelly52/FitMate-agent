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
import org.springframework.web.bind.annotation.RequestParam;
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

    @Resource
    private com.itgeo.fitmate.api.chat.application.LlmConfigResolver llmConfigResolver;

    @Resource
    private com.itgeo.fitmate.api.chat.infrastructure.LlmProxyClient llmProxyClient;

    @Resource
    private com.itgeo.fitmate.api.chat.application.McpConfigResolver mcpConfigResolver;

    @Resource
    private com.itgeo.fitmate.api.agent.mcp.McpClientPool mcpClientPool;

    @Resource
    private com.itgeo.fitmate.api.agent.mcp.McpToolRegistry mcpToolRegistry;

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
     * 检查邮箱注册状态，用于登录页判断是否需要验证码。
     * 当账号存在且已设置密码时，前端允许跳过验证码直接用密码登录。
     *
     * @param email 邮箱
     * @return 通用响应结果，data 含 exists 与 passwordSet 字段
     */
    @GetMapping("/check-email")
    public LeeResult checkEmail(@RequestParam("email") String email) {
        try {
            return LeeResult.ok(userService.checkEmailRegistered(email));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("检查邮箱注册状态失败", e);
            return LeeResult.errorException("检查邮箱注册状态失败");
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
     * 获取当前登录用户的 LLM 配置（apiKey 脱敏）。
     *
     * @return 通用响应结果
     */
    @GetMapping("/llm-config")
    public LeeResult getLlmConfig() {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(llmConfigResolver.getByUserId(userId));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("获取 LLM 配置失败", e);
            return LeeResult.errorException("获取 LLM 配置失败");
        }
    }

    /**
     * 保存当前登录用户的 LLM 配置（apiKey 为空表示不修改原值）。
     *
     * @param request 保存请求体
     * @return 通用响应结果
     */
    @PutMapping("/llm-config")
    public LeeResult saveLlmConfig(@RequestBody com.itgeo.fitmate.api.auth.dto.LlmConfigSaveRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            llmConfigResolver.saveByUserId(userId, request);
            return LeeResult.ok(llmConfigResolver.getByUserId(userId));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("保存 LLM 配置失败", e);
            return LeeResult.errorException("保存 LLM 配置失败");
        }
    }

    /**
     * 代理调用 DeepSeek GET /models 拉取模型列表。
     * 请求体字段为空时用当前用户已存配置。
     *
     * @param request 代理请求体（可为空）
     * @return 通用响应结果
     */
    @PostMapping("/llm/models")
    public LeeResult listLlmModels(@RequestBody(required = false) com.itgeo.fitmate.api.chat.dto.LlmProxyRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            com.itgeo.fitmate.api.chat.application.ResolvedLlmConfig resolved = llmConfigResolver.resolveByUserId(userId);
            String baseUrl = request != null && request.getBaseUrl() != null ? request.getBaseUrl() : resolved.getBaseUrl();
            String apiKey = request != null && request.getApiKey() != null ? request.getApiKey() : resolved.getApiKey();
            return LeeResult.ok(llmProxyClient.listModels(baseUrl, apiKey));
        } catch (Exception e) {
            log.error("拉取模型列表失败", e);
            return LeeResult.errorMsg(e.getMessage());
        }
    }

    /**
     * 测活：极简 chat completion（max_tokens=1, thinking=disabled）。
     * 请求体字段为空时用当前用户已存配置。
     *
     * @param request 代理请求体（可为空）
     * @return 通用响应结果
     */
    @PostMapping("/llm/test")
    public LeeResult testLlmConnection(@RequestBody(required = false) com.itgeo.fitmate.api.chat.dto.LlmProxyRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            com.itgeo.fitmate.api.chat.application.ResolvedLlmConfig resolved = llmConfigResolver.resolveByUserId(userId);
            String baseUrl = request != null && request.getBaseUrl() != null ? request.getBaseUrl() : resolved.getBaseUrl();
            String apiKey = request != null && request.getApiKey() != null ? request.getApiKey() : resolved.getApiKey();
            String model = request != null && request.getModel() != null ? request.getModel() : resolved.getModel();
            return LeeResult.ok(llmProxyClient.testConnection(baseUrl, apiKey, model));
        } catch (Exception e) {
            log.error("LLM 测活失败", e);
            return LeeResult.errorMsg(e.getMessage());
        }
    }

    /**
     * 代理调用 DeepSeek GET /user/balance 查询账户余额。
     * 请求体字段为空时用当前用户已存配置。
     *
     * @param request 代理请求体（可为空）
     * @return 通用响应结果
     */
    @PostMapping("/llm/balance")
    public LeeResult getLlmBalance(@RequestBody(required = false) com.itgeo.fitmate.api.chat.dto.LlmProxyRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            com.itgeo.fitmate.api.chat.application.ResolvedLlmConfig resolved = llmConfigResolver.resolveByUserId(userId);
            String baseUrl = request != null && request.getBaseUrl() != null ? request.getBaseUrl() : resolved.getBaseUrl();
            String apiKey = request != null && request.getApiKey() != null ? request.getApiKey() : resolved.getApiKey();
            return LeeResult.ok(llmProxyClient.getBalance(baseUrl, apiKey));
        } catch (Exception e) {
            log.error("查询 DeepSeek 余额失败", e);
            return LeeResult.errorMsg(e.getMessage());
        }
    }

    /**
     * 获取当前登录用户的 MCP 自定义 server 配置。
     *
     * @return 通用响应结果
     */
    @GetMapping("/mcp-config")
    public LeeResult getMcpConfig() {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(mcpConfigResolver.getByUserId(userId));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("获取 MCP 配置失败", e);
            return LeeResult.errorException("获取 MCP 配置失败");
        }
    }

    /**
     * 保存当前登录用户的 MCP 自定义 server 配置，保存后立即刷新 MCP 工具连接。
     *
     * @param request 保存请求体
     * @return 通用响应结果
     */
    @PutMapping("/mcp-config")
    public LeeResult saveMcpConfig(@RequestBody com.itgeo.fitmate.api.auth.dto.McpConfigSaveRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            mcpConfigResolver.saveByUserId(userId, request);
            // 保存后立即刷新该用户的 MCP 工具连接（热生效，不重启服务）
            mcpToolRegistry.refresh(userId);
            return LeeResult.ok(mcpConfigResolver.getByUserId(userId));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("保存 MCP 配置失败", e);
            return LeeResult.errorException("保存 MCP 配置失败");
        }
    }

    /**
     * 测试单个 MCP server 连接：临时建立连接 → listTools → 关闭。
     *
     * @param request 单个 server 配置
     * @return 通用响应结果，data 含 ok/latencyMs/error/tools
     */
    @PostMapping("/mcp/test")
    public LeeResult testMcpConnection(@RequestBody com.itgeo.fitmate.api.auth.dto.McpServerConfig request) {
        log.info("[MCP-TEST] 收到测试请求: {}", request == null ? "null" : request.getUrl());
        try {
            return LeeResult.ok(mcpClientPool.testConnection(request));
        } catch (Exception e) {
            log.error("MCP 连接测试失败", e);
            return LeeResult.errorMsg(e.getMessage());
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
