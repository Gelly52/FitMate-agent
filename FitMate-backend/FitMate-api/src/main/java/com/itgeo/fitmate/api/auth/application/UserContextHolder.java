package com.itgeo.fitmate.api.auth.application;

/**
 * 当前请求线程内的鉴权上下文持有器。
 * 基于 ThreadLocal 保存数据，仅在当前请求处理线程内有效，不能直接跨异步线程复用。
 */
public final class UserContextHolder {

    /**
     * 保存当前请求线程的已认证用户上下文。
     */
    private static final ThreadLocal<AuthenticatedUserContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 工具类不允许实例化。
     */
    private UserContextHolder() {
    }

    /**
     * 写入当前请求线程的鉴权上下文。
     */
    public static void set(AuthenticatedUserContext context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 读取当前请求线程已写入的鉴权上下文。
     */
    public static AuthenticatedUserContext get() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 读取当前请求线程的鉴权上下文；若未完成鉴权则直接抛错。
     */
    public static AuthenticatedUserContext getRequired() {
        AuthenticatedUserContext context = get();
        if (context == null) {
            throw new IllegalStateException("当前请求缺少鉴权上下文");
        }
        return context;
    }

    /**
     * 请求结束后清理当前线程上下文，避免线程复用时串值。
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }
}
