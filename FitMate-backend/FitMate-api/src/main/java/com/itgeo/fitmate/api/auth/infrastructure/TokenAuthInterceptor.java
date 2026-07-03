package com.itgeo.fitmate.api.auth.infrastructure;

import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.auth.application.TokenAuthService;
import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.common.response.LeeResult;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 统一 token 鉴权拦截器。
 * 负责在请求进入业务控制器前完成 token 校验、上下文写入与失败响应收口。
 */
@Slf4j
@Component
public class TokenAuthInterceptor implements HandlerInterceptor {

    /**
     * request attribute 中保存已认证用户上下文的键名。
     */
    public static final String AUTHENTICATED_USER_ATTRIBUTE = "authenticatedUser";

    @Resource
    private TokenAuthService tokenAuthService;

    /**
     * 在请求进入控制器前执行统一 token 鉴权。
     *
     * @return {@code true} 表示鉴权通过并继续后续处理；{@code false} 表示已直接写回失败响应
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS 预检请求不携带业务 token，直接放行，由正式请求再进入统一鉴权。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        try {
            /*
             * 步骤：
             * 1. 从请求头读取 headerUserToken。
             * 2. 调用 TokenAuthService 完成 token -> session -> user 的鉴权解析。
             * 3. 鉴权成功后，同时写入 request attribute 与 UserContextHolder，供当前请求链路复用。
             */
            AuthenticatedUserContext authenticatedUser = tokenAuthService.authenticateToken(request.getHeader("headerUserToken"));
            request.setAttribute(AUTHENTICATED_USER_ATTRIBUTE, authenticatedUser);
            UserContextHolder.set(authenticatedUser);
            return true;
        } catch (IllegalArgumentException e) {
            // 业务可预期的鉴权失败统一返回 401 JSON，并立即清理线程上下文。
            UserContextHolder.clear();
            writeTokenError(response, e.getMessage());
            return false;
        } catch (Exception e) {
            // 非预期异常同样收口为统一 401，避免向前端泄露内部实现细节。
            UserContextHolder.clear();
            log.error("统一 token 鉴权失败", e);
            writeTokenError(response, "登录状态校验失败，请重新登录");
            return false;
        }
    }

    /**
     * 在请求完成后清理线程上下文，避免容器线程复用时串用上一次请求的数据。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContextHolder.clear();
    }

    /**
     * 输出统一的 401 JSON 鉴权失败响应。
     */
    private void writeTokenError(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSONUtil.toJsonStr(LeeResult.errorTokenMsg(message)));
        response.getWriter().flush();
    }
}
