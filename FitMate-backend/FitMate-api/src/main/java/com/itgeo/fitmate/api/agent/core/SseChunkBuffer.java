package com.itgeo.fitmate.api.agent.core;

/**
 * SSE chunk 批处理缓冲器。
 * <p>
 * 累积同类型（thinking / content）的 token 分片，达到时间窗口或大小阈值时统一推送，
 * 减少高频 SSE 调用对 agent-exec 线程和 SseEmitter 串行锁的争用。
 * <p>
 * 线程安全：单个 run 仅在 agent-exec 线程内访问，无需同步。
 */
public class SseChunkBuffer {

    /** 时间窗口（毫秒），超过则强制 flush。50ms 对前端感知几乎无影响。 */
    private static final long FLUSH_INTERVAL_MS = 50L;

    /** 单次累积字符数上限，防止极端长 token 一直不 flush。 */
    private static final int FLUSH_SIZE_THRESHOLD = 4096;

    private final StringBuilder buffer = new StringBuilder();

    /** 当前批次首个 chunk 的时间戳，用于时间窗口判断。 */
    private long firstPendingTime = 0L;

    /**
     * 累积 chunk 内容。
     *
     * @param content 本次追加的分片内容（非空）
     */
    public void append(String content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        if (buffer.length() == 0) {
            firstPendingTime = System.currentTimeMillis();
        }
        buffer.append(content);
    }

    /**
     * 判断是否达到 flush 条件（时间窗口或大小阈值）。
     */
    public boolean shouldFlush() {
        if (buffer.length() == 0) {
            return false;
        }
        return buffer.length() >= FLUSH_SIZE_THRESHOLD
                || (System.currentTimeMillis() - firstPendingTime) >= FLUSH_INTERVAL_MS;
    }

    /**
     * 取出并清空当前累积内容。
     *
     * @return 累积的完整内容；无累积时返回 null
     */
    public String drain() {
        if (buffer.length() == 0) {
            return null;
        }
        String content = buffer.toString();
        buffer.setLength(0);
        firstPendingTime = 0L;
        return content;
    }

    /**
     * 是否有未推送的累积内容。
     */
    public boolean hasPending() {
        return buffer.length() > 0;
    }
}
