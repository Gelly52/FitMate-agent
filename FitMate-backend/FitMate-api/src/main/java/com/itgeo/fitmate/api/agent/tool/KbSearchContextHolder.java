package com.itgeo.fitmate.api.agent.tool;

/**
 * kb.search 工具的上下文传递（ThreadLocal）。
 * AgentLoopExecutor 在调用 kb.search 前设置 ragEnabled，工具执行后清理。
 */
public class KbSearchContextHolder {
    private static final ThreadLocal<Boolean> RAG_ENABLED = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> KB_ENABLED = new ThreadLocal<>();

    public static void setRagEnabled(Boolean enabled) { RAG_ENABLED.set(enabled); }
    public static Boolean getRagEnabled() {
        Boolean v = RAG_ENABLED.get();
        return v == null ? false : v;
    }
    public static void setKbEnabled(Boolean enabled) { KB_ENABLED.set(enabled); }
    public static Boolean getKbEnabled() {
        Boolean v = KB_ENABLED.get();
        return v == null ? true : v;
    }
    public static void clear() {
        RAG_ENABLED.remove();
        KB_ENABLED.remove();
    }
}
