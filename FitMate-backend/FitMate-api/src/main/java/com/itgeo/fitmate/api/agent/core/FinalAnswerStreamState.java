package com.itgeo.fitmate.api.agent.core;

/**
 * 流式识别 LLM 决策 JSON 中的 final_answer 字段，并增量推送其值。
 *
 * 状态流转：
 *   DECIDING --"action":"final"--> FINAL_ANSWER_DETECTED --"final_answer":"--> IN_FINAL_ANSWER
 *   DECIDING --"final_answer":"--> IN_FINAL_ANSWER  (字段乱序)
 *   任意状态 --识别失败--> FAILED (fallback 整段推送)
 *
 * 线程不安全：每个 Agent 迭代创建一个实例。
 */
public class FinalAnswerStreamState {

    private static final String ACTION_FINAL_MARKER = "\"action\":\"final\"";
    private static final String FINAL_ANSWER_KEY = "\"final_answer\":\"";

    enum State { DECIDING, FINAL_ANSWER_DETECTED, IN_FINAL_ANSWER, FAILED }

    private State state = State.DECIDING;
    private final StringBuilder pendingEscaped = new StringBuilder();
    private boolean hasStreamed = false;
    private boolean fieldClosed = false; // final_answer 字段值是否已遇到闭合引号

    public String onNext(String contentDelta, String fullContent) {
        if (state == State.FAILED) {
            return "";
        }

        // DECIDING：扫描 action=final 或 final_answer
        if (state == State.DECIDING) {
            // 检测 markdown 代码块包裹：以 ``` 开头（去除前导空白）直接判 FAILED
            String trimmed = fullContent.trim();
            if (trimmed.startsWith("```")) {
                state = State.FAILED;
                return "";
            }
            if (fullContent.contains(ACTION_FINAL_MARKER)) {
                state = State.FINAL_ANSWER_DETECTED;
            } else if (fullContent.contains(FINAL_ANSWER_KEY)) {
                // 字段乱序：final_answer 在 action 前
                int idx = fullContent.indexOf(FINAL_ANSWER_KEY);
                if (idx >= 0) {
                    state = State.IN_FINAL_ANSWER;
                    pendingEscaped.append(fullContent.substring(idx + FINAL_ANSWER_KEY.length()));
                    return flushPending();
                }
            } else {
                return "";
            }
        }

        if (state == State.FINAL_ANSWER_DETECTED) {
            int idx = fullContent.indexOf(FINAL_ANSWER_KEY);
            if (idx < 0) {
                return "";
            }
            state = State.IN_FINAL_ANSWER;
            pendingEscaped.append(fullContent.substring(idx + FINAL_ANSWER_KEY.length()));
            return flushPending();
        }

        if (state == State.IN_FINAL_ANSWER) {
            if (fieldClosed) {
                // 字段已闭合，后续 delta 属于其他字段，不推送
                return "";
            }
            pendingEscaped.append(contentDelta);
            return flushPending();
        }

        return "";
    }

    /**
     * 反转义 pendingEscaped，返回可安全推送的内容。
     * 末尾不完整的转义序列保留在 pendingEscaped 中。
     */
    private String flushPending() {
        if (pendingEscaped.length() == 0) {
            return "";
        }
        // 检查是否遇到字段结束符（未转义的 "）
        int endIdx = findUnescapedQuote(pendingEscaped);
        if (endIdx >= 0) {
            // 字段结束：只推送到 endIdx，之后的内容丢弃（属于后续字段）
            StringBuilder toFlush = new StringBuilder(pendingEscaped.substring(0, endIdx));
            pendingEscaped.setLength(0);
            fieldClosed = true;
            String result = JsonStringUnescaper.unescape(toFlush);
            hasStreamed = true;
            return result;
        }
        // 字段未结束：反转义并保留末尾不完整转义
        String unescaped = JsonStringUnescaper.unescape(pendingEscaped);
        String safe = JsonStringUnescaper.retainIncompleteEscape(unescaped, pendingEscaped);
        hasStreamed = true;
        return safe;
    }

    /**
     * 在 buffer 中查找未转义的 " 字符位置（即字段结束符）。
     * 转义的 \" 不算。
     */
    private int findUnescapedQuote(StringBuilder buffer) {
        for (int i = 0; i < buffer.length(); i++) {
            char c = buffer.charAt(i);
            if (c == '"') {
                // 检查前面是否偶数个 \
                int backslashes = 0;
                for (int j = i - 1; j >= 0 && buffer.charAt(j) == '\\'; j--) backslashes++;
                if (backslashes % 2 == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    public boolean hasStreamed() {
        return hasStreamed;
    }
}
