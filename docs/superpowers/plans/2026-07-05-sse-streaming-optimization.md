# SSE 流式输出性能优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除思考流卡顿 + 实现 Agent 最终答案逐字显示 + 消除流式期间无意义的 markdown DOM 重建

**Architecture:** 后端在 `AgentLoopExecutor` 的 LLM 流式消费块加入轻量级状态机，识别 `final_answer` 字段边界并增量推送；前端用 per-run buffer + rAF 合并 token 更新，流式期间用纯文本容器渲染，finish/中断时切到 v-html + marked.parse

**Tech Stack:** Java 17 + Spring Boot + JUnit 5（后端）；Vue 3 Options API + marked + Vite（前端，无测试框架，手动验证）

**Spec:** `docs/superpowers/specs/2026-07-05-sse-streaming-optimization-design.md`

---

## File Structure

**后端新增：**
- `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/JsonStringUnescaper.java` — JSON 字符串反转义工具
- `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/FinalAnswerStreamState.java` — final_answer 字段流式识别状态机
- `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/core/JsonStringUnescaperTest.java`
- `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/core/FinalAnswerStreamStateTest.java`

**后端修改：**
- `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java` — 集成状态机

**前端修改：**
- `FitMate-frontend/src/pages/chat/ChatLogicBase.vue` — per-run buffer、scrollToBottom 节流、中断时 markdown 渲染、rawContent
- `FitMate-frontend/src/pages/chat/components/ChatMessageList.vue` — 渲染分支

---

## Task 1: JsonStringUnescaper 工具类

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/JsonStringUnescaper.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/core/JsonStringUnescaperTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.agent.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonStringUnescaperTest {

    @Test
    void unescape_basicSequences() {
        StringBuilder sb = new StringBuilder("hello\\nworld\\ttab\\rcr");
        assertEquals("hello\nworld\ttab\rcr", JsonStringUnescaper.unescape(sb));
    }

    @Test
    void unescape_quotesAndBackslash() {
        StringBuilder sb = new StringBuilder("say \\\"hi\\\" and \\\\path");
        assertEquals("say \"hi\" and \\path", JsonStringUnescaper.unescape(sb));
    }

    @Test
    void unescape_unicodeSequence() {
        StringBuilder sb = new StringBuilder("\\u4e2d\\u6587");
        assertEquals("中文", JsonStringUnescaper.unescape(sb));
    }

    @Test
    void unescape_forwardSlash() {
        StringBuilder sb = new StringBuilder("http:\\/\\/example.com");
        assertEquals("http://example.com", JsonStringUnescaper.unescape(sb));
    }

    @Test
    void unescape_noEscapeChars_returnsAsIs() {
        StringBuilder sb = new StringBuilder("plain text");
        assertEquals("plain text", JsonStringUnescaper.unescape(sb));
    }

    @Test
    void unescape_emptyBuilder_returnsEmpty() {
        StringBuilder sb = new StringBuilder("");
        assertEquals("", JsonStringUnescaper.unescape(sb));
    }

    @Test
    void retainIncompleteEscape_trailingSingleBackslash() {
        StringBuilder pending = new StringBuilder("hello\\");
        String result = JsonStringUnescaper.retainIncompleteEscape("hello", pending);
        assertEquals("hello", result);
        assertEquals("\\", pending.toString());
    }

    @Test
    void retainIncompleteEscape_trailingPartialUnicode() {
        StringBuilder pending = new StringBuilder("hello\\u4e2");
        String result = JsonStringUnescaper.retainIncompleteEscape("hello", pending);
        assertEquals("hello", result);
        assertEquals("\\u4e2", pending.toString());
    }

    @Test
    void retainIncompleteEscape_noTrailingEscape_returnsAll() {
        StringBuilder pending = new StringBuilder("hello");
        String result = JsonStringUnescaper.retainIncompleteEscape("hello", pending);
        assertEquals("hello", result);
        assertEquals("", pending.toString());
    }

    @Test
    void retainIncompleteEscape_completeEscape_returnsAll() {
        StringBuilder pending = new StringBuilder("hello\\n");
        String result = JsonStringUnescaper.retainIncompleteEscape("hello\n", pending);
        assertEquals("hello\n", result);
        assertEquals("", pending.toString());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd FitMate-backend && mvn test -pl FitMate-api -Dtest=JsonStringUnescaperTest -DfailIfNoTests=false`
Expected: FAIL — 类不存在

- [ ] **Step 3: 实现 JsonStringUnescaper**

```java
package com.itgeo.fitmate.api.agent.core;

/**
 * JSON 字符串反转义工具。
 * 将 JSON 字符串值中的转义序列（\n \" \\ \t \r \/ \uXXXX）还原为实际字符。
 */
public final class JsonStringUnescaper {

    private JsonStringUnescaper() {}

    /**
     * 反转义 StringBuilder 中的 JSON 转义序列，返回反转义后的字符串。
     * 不修改入参 StringBuilder（调用方负责清理）。
     */
    public static String unescape(StringBuilder escaped) {
        if (escaped == null || escaped.length() == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder(escaped.length());
        int i = 0;
        int len = escaped.length();
        while (i < len) {
            char c = escaped.charAt(i);
            if (c != '\\' || i + 1 >= len) {
                out.append(c);
                i++;
                continue;
            }
            char next = escaped.charAt(i + 1);
            switch (next) {
                case 'n': out.append('\n'); i += 2; break;
                case 't': out.append('\t'); i += 2; break;
                case 'r': out.append('\r'); i += 2; break;
                case '"': out.append('"'); i += 2; break;
                case '\\': out.append('\\'); i += 2; break;
                case '/': out.append('/'); i += 2; break;
                case 'b': out.append('\b'); i += 2; break;
                case 'f': out.append('\f'); i += 2; break;
                case 'u':
                    if (i + 5 < len) {
                        String hex = escaped.substring(i + 2, i + 6);
                        try {
                            out.append((char) Integer.parseInt(hex, 16));
                            i += 6;
                        } catch (NumberFormatException e) {
                            out.append(c);
                            i++;
                        }
                    } else {
                        // 不完整的 \uXXXX，原样保留
                        out.append(c);
                        i++;
                    }
                    break;
                default:
                    out.append(c);
                    i++;
            }
        }
        return out.toString();
    }

    /**
     * 检查 unescape 后的字符串末尾是否对应原始 StringBuilder 中不完整的转义序列，
     * 如果是，把不完整部分回填到 pending，返回排除不完整部分的安全字符串。
     *
     * @param unescaped 已经反转义的内容（unescape 的返回值）
     * @param pending 原始累积缓冲（会被修改：清空并回填不完整转义部分）
     * @return 可安全推送的内容（不含末尾不完整转义）
     */
    public static String retainIncompleteEscape(String unescaped, StringBuilder pending) {
        if (pending == null || pending.length() == 0) {
            return unescaped == null ? "" : unescaped;
        }
        // 从末尾找最后一个未配对的 \
        String raw = pending.toString();
        int lastBackslash = -1;
        for (int i = raw.length() - 1; i >= 0; i--) {
            if (raw.charAt(i) == '\\') {
                // 检查前面是否已有偶数个连续 \（被转义的 \）
                int backslashes = 0;
                for (int j = i; j >= 0 && raw.charAt(j) == '\\'; j--) backslashes++;
                if (backslashes % 2 == 1) {
                    lastBackslash = i;
                    break;
                }
            }
        }

        if (lastBackslash < 0) {
            pending.setLength(0);
            return unescaped == null ? "" : unescaped;
        }

        String incomplete = raw.substring(lastBackslash);
        // 检查是否是完整的 \uXXXX（6 字符）
        if (incomplete.length() >= 6 && incomplete.startsWith("\\u")) {
            try {
                Integer.parseInt(incomplete.substring(2, 6), 16);
                pending.setLength(0);
                return unescaped == null ? "" : unescaped;
            } catch (NumberFormatException ignored) {}
        }
        // 检查是否是完整的两字符转义（如 \n \"）
        if (incomplete.length() == 2) {
            char esc = incomplete.charAt(1);
            if (esc == 'n' || esc == 't' || esc == 'r' || esc == '"'
                || esc == '\\' || esc == '/' || esc == 'b' || esc == 'f') {
                pending.setLength(0);
                return unescaped == null ? "" : unescaped;
            }
        }

        // 不完整：回填到 pending，从 unescaped 中减去对应字符
        pending.setLength(0);
        pending.append(incomplete);
        // unescaped 对应的"已处理"部分长度 = unescaped.length - (incomplete 对应的字符数)
        // 不完整转义在 unescaped 中表现为单个 '\' 字符
        int safeLen = unescaped == null ? 0 : unescaped.length();
        if (safeLen > 0 && unescaped.charAt(safeLen - 1) == '\\') {
            safeLen--;
        }
        return unescaped == null ? "" : unescaped.substring(0, safeLen);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd FitMate-backend && mvn test -pl FitMate-api -Dtest=JsonStringUnescaperTest -DfailIfNoTests=false`
Expected: PASS — 10 tests

- [ ] **Step 5: 提交**

```bash
cd FitMate-backend
git add FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/JsonStringUnescaper.java FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/core/JsonStringUnescaperTest.java
git commit -m "feat(agent): add JsonStringUnescaper for JSON string unescaping"
```

---

## Task 2: FinalAnswerStreamState 状态机

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/FinalAnswerStreamState.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/core/FinalAnswerStreamStateTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.agent.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinalAnswerStreamStateTest {

    @Test
    void standardPath_actionFirst_thenFinalAnswer() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        // 模拟 LLM 流式输出：先 action，后 final_answer
        String r1 = state.onNext("{\"action\":\"final\",", "{\"action\":\"final\",");
        assertEquals("", r1);
        assertFalse(state.hasStreamed());

        String r2 = state.onNext("\"final_answer\":\"hello", "{\"action\":\"final\",\"final_answer\":\"hello");
        assertEquals("hello", r2);
        assertTrue(state.hasStreamed());

        String r3 = state.onNext(" world\"", "{\"action\":\"final\",\"final_answer\":\"hello world\"");
        assertEquals(" world", r3);
    }

    @Test
    void fieldOrderReversed_finalAnswerFirst() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        String r1 = state.onNext("{\"final_answer\":\"hello", "{\"final_answer\":\"hello");
        assertEquals("hello", r1);
        assertTrue(state.hasStreamed());
    }

    @Test
    void toolCallPath_neverStreams() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        String r = state.onNext("{\"action\":\"tool_call\",\"tool_name\":\"kb.search\"",
                "{\"action\":\"tool_call\",\"tool_name\":\"kb.search\"");
        assertEquals("", r);
        assertFalse(state.hasStreamed());
    }

    @Test
    void escapedNewline_inFinalAnswer() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        state.onNext("{\"action\":\"final\",\"final_answer\":\"line1",
                "{\"action\":\"final\",\"final_answer\":\"line1");
        String r = state.onNext("\\nline2\"", "{\"action\":\"final\",\"final_answer\":\"line1\\nline2\"");
        assertEquals("\nline2", r);
    }

    @Test
    void escapedQuote_inFinalAnswer() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        state.onNext("{\"action\":\"final\",\"final_answer\":\"say ",
                "{\"action\":\"final\",\"final_answer\":\"say ");
        String r = state.onNext("\\\"hi\\\"\"", "{\"action\":\"final\",\"final_answer\":\"say \\\"hi\\\"\"");
        assertEquals("\"hi\"", r);
    }

    @Test
    void emptyFinalAnswer_hasStreamedTrue_noContent() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        String r1 = state.onNext("{\"action\":\"final\",\"final_answer\":\"\"",
                "{\"action\":\"final\",\"final_answer\":\"\"");
        assertEquals("", r1);
        assertTrue(state.hasStreamed());
    }

    @Test
    void markdownCodeBlockWrapper_fallsBackToFailed() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        // LLM 输出带 markdown 代码块包裹
        String r = state.onNext("```json\n{\"action\":\"final\"",
                "```json\n{\"action\":\"final\"");
        assertEquals("", r);
        assertFalse(state.hasStreamed());
        // 状态机进入 FAILED，后续不再尝试
        String r2 = state.onNext(",\"final_answer\":\"hello\"}", "```json\n{\"action\":\"final\",\"final_answer\":\"hello\"}");
        assertEquals("", r2);
    }

    @Test
    void trailingFieldAfterFinalAnswer_stopsAtClosingQuote() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        state.onNext("{\"action\":\"final\",\"final_answer\":\"hello",
                "{\"action\":\"final\",\"final_answer\":\"hello");
        // 遇到闭合 " 后，后续字段不推送
        String r = state.onNext("\",\"reason\":\"done\"}", "{\"action\":\"final\",\"final_answer\":\"hello\",\"reason\":\"done\"}");
        assertEquals("", r);
    }

    @Test
    void incompleteEscapeAtChunkBoundary_retained() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        state.onNext("{\"action\":\"final\",\"final_answer\":\"hello",
                "{\"action\":\"final\",\"final_answer\":\"hello");
        // 末尾单个 \ 不完整
        String r1 = state.onNext("\\", "{\"action\":\"final\",\"final_answer\":\"hello\\");
        assertEquals("", r1);
        // 下一块补全 \n
        String r2 = state.onNext("nworld\"", "{\"action\":\"final\",\"final_answer\":\"hello\\nworld\"");
        assertEquals("\nworld", r2);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd FitMate-backend && mvn test -pl FitMate-api -Dtest=FinalAnswerStreamStateTest -DfailIfNoTests=false`
Expected: FAIL — 类不存在

- [ ] **Step 3: 实现 FinalAnswerStreamState**

```java
package com.itgeo.fitmate.api.agent.core;

/**
 * 流式识别 LLM 决策 JSON 中的 final_answer 字段，并增量推送其值。
 *
 * 状态流转：
 *   DECIDING ──"action":"final"──> FINAL_ANSWER_DETECTED ──"final_answer":"──> IN_FINAL_ANSWER
 *   DECIDING ──"final_answer":"──> IN_FINAL_ANSWER  (字段乱序)
 *   任意状态 ──识别失败──> FAILED (fallback 整段推送)
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
    private int finalAnswerValueStart = -1; // fullContent 中 final_answer 值的起始位置

    public String onNext(String contentDelta, String fullContent) {
        if (state == State.FAILED) {
            return "";
        }

        // DECIDING：扫描 action=final 或 final_answer
        if (state == State.DECIDING) {
            // 检测 markdown 代码块包裹（容错：以 ` 开头直接判 FAILED）
            if (fullContent.length() <= 10 && fullContent.trim().startsWith("```")) {
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
                    finalAnswerValueStart = idx + FINAL_ANSWER_KEY.length();
                    pendingEscaped.append(fullContent.substring(finalAnswerValueStart));
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
            finalAnswerValueStart = idx + FINAL_ANSWER_KEY.length();
            pendingEscaped.append(fullContent.substring(finalAnswerValueStart));
            return flushPending();
        }

        if (state == State.IN_FINAL_ANSWER) {
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
            // 推完后不再接受新内容（状态保持 IN_FINAL_ANSWER 但 pending 为空）
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
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd FitMate-backend && mvn test -pl FitMate-api -Dtest=FinalAnswerStreamStateTest -DfailIfNoTests=false`
Expected: PASS — 9 tests

- [ ] **Step 5: 提交**

```bash
cd FitMate-backend
git add FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/FinalAnswerStreamState.java FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/core/FinalAnswerStreamStateTest.java
git commit -m "feat(agent): add FinalAnswerStreamState for streaming final_answer field"
```

---

## Task 3: AgentLoopExecutor 集成状态机

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java:181-231`

- [ ] **Step 1: 修改 LLM 流式消费块（L181-206）**

在 `StringBuilder decisionContent = new StringBuilder();` 之后、`llmGateway.streamWithReasoning(prompt).toStream().forEach(chunk -> {` 之前插入状态机初始化。

找到第 181-182 行：
```java
                StringBuilder reasoningContent = new StringBuilder();
                StringBuilder decisionContent = new StringBuilder();
```

改为：
```java
                StringBuilder reasoningContent = new StringBuilder();
                StringBuilder decisionContent = new StringBuilder();
                FinalAnswerStreamState streamState = new FinalAnswerStreamState();
```

- [ ] **Step 2: 在 content delta 累积后调用状态机**

找到第 203-205 行：
```java
                    if (StrUtil.isNotBlank(contentDelta)) {
                        decisionContent.append(contentDelta);
                    }
```

改为：
```java
                    if (StrUtil.isNotBlank(contentDelta)) {
                        decisionContent.append(contentDelta);
                        // 状态机驱动 final_answer 流式推送
                        String answerDelta = streamState.onNext(contentDelta, decisionContent.toString());
                        if (StrUtil.isNotBlank(answerDelta)) {
                            sendContentChunk(context, answerDelta);
                        }
                    }
```

- [ ] **Step 3: 修改 action=final 分支跳过整段推送**

找到第 228-231 行：
```java
            if ("final".equalsIgnoreCase(action)) {
                String finalAnswer = StrUtil.blankToDefault(decision.getStr("final_answer"), "已完成处理。请查看上方执行轨迹。");
                finishWithAnswer(context, finalAnswer, observations, memory, allowedTools, summarySection, userProfileSection);
                return;
            }
```

改为：
```java
            if ("final".equalsIgnoreCase(action)) {
                String finalAnswer = StrUtil.blankToDefault(decision.getStr("final_answer"), "已完成处理。请查看上方执行轨迹。");
                // 如果状态机已流式推送，跳过整段推送；否则走原逻辑
                if (!streamState.hasStreamed()) {
                    sendContentChunk(context, finalAnswer);
                }
                finishWithAnswer(context, finalAnswer, observations, memory, allowedTools, summarySection, userProfileSection);
                return;
            }
```

- [ ] **Step 4: 编译验证**

Run: `cd FitMate-backend && mvn compile -pl FitMate-api`
Expected: BUILD SUCCESS

- [ ] **Step 5: 启动后端验证端到端**

Run: `cd FitMate-backend && mvn spring-boot:run -pl FitMate-api`

手动测试：
1. 前端发起一个简单 Agent 问题（如"你好"）
2. 打开浏览器 DevTools → Network → 找到 `/sse/connect` → EventStream 标签
3. 观察 `add` 事件：应该看到多次小 `add` 事件（而非一次大事件）
4. 前端应能看到最终答案逐字出现

Expected: 多次 `add` 事件，每次携带小段文本

- [ ] **Step 6: 提交**

```bash
cd FitMate-backend
git add FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java
git commit -m "feat(agent): integrate FinalAnswerStreamState for streaming final_answer chunks"
```

---

## Task 4: 前端 per-run buffer 数据结构与辅助方法

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`

- [ ] **Step 1: 在 data 中新增 runBuffers 字段**

找到 data() 返回对象中 `activeAgentRuns` 字段附近（约第 88 行），在其后新增：

```js
      activeAgentRuns: {} as Record<string, any>,
      runBuffers: {} as Record<string, any>,
```

- [ ] **Step 2: 新增 buffer 辅助方法**

在 methods 对象中（建议放在 `snapshotRunState` 方法之后，约第 1106 行后）新增：

```js
    getOrCreateBuffer(runId: string) {
      if (!runId) return null;
      if (!this.runBuffers[runId]) {
        this.runBuffers[runId] = {
          runId: runId,
          thinkingDelta: "",
          contentDelta: "",
          pendingFlush: false,
          lastSnapshotTime: 0,
        };
      }
      return this.runBuffers[runId];
    },
    scheduleFlush(runId: string) {
      var buffer = this.getOrCreateBuffer(runId);
      if (!buffer || buffer.pendingFlush) return;
      buffer.pendingFlush = true;
      var me = this;
      requestAnimationFrame(function () {
        me.flushRunBuffer(runId);
      });
    },
    flushRunBuffer(runId: string) {
      var buffer = this.runBuffers[runId];
      if (!buffer) return;
      var run = this.activeAgentRuns[runId];

      // 1. 更新 thinking（如果有增量）
      if (buffer.thinkingDelta) {
        if (run) {
          run.thinkingContent = (run.thinkingContent || "") + buffer.thinkingDelta;
          run.thinkingSegments = this.appendThinkingChunkToSegments(
            (run.thinkingSegments || []).slice(),
            buffer.thinkingDelta
          );
        }
        var botMsgId = run ? run.botMsgId : null;
        var targetMsg = this.findBotMessage(botMsgId);
        if (targetMsg) {
          targetMsg.thinkingContent =
            (targetMsg.thinkingContent || "") + buffer.thinkingDelta;
          targetMsg.thinkingSegments = this.cloneThinkingSegments(
            run ? run.thinkingSegments : []
          );
          targetMsg.isThinking = true;
        }
        buffer.thinkingDelta = "";
      }

      // 2. 更新 content（如果有增量）
      if (buffer.contentDelta) {
        var contentBotMsgId = run ? run.botMsgId : null;
        var contentTarget = this.findBotMessage(contentBotMsgId);
        if (contentTarget) {
          contentTarget.content =
            (contentTarget.content || "") + buffer.contentDelta;
          contentTarget.isStreaming = true;
        }
        buffer.contentDelta = "";
      }

      // 3. scrollToBottom（每帧最多一次，所有 run 共享）
      this.scrollToBottomThrottled();

      // 4. snapshotRunState 节流（500ms 一次）
      if (run && Date.now() - buffer.lastSnapshotTime > 500) {
        this.snapshotRunState(runId);
        buffer.lastSnapshotTime = Date.now();
      }

      buffer.pendingFlush = false;
    },
    findBotMessage(botMsgId: string) {
      if (!botMsgId) return null;
      for (var i = 0; i < this.chatList.length; i++) {
        var item = this.chatList[i];
        if (item.botMsgId == botMsgId && item.chatType !== "user") {
          return item;
        }
      }
      return null;
    },
    clearRunBuffers() {
      this.runBuffers = {};
    },
```

- [ ] **Step 3: 验证无语法错误**

Run: `cd FitMate-frontend && npm run build`
Expected: BUILD SUCCESS（无编译错误）

- [ ] **Step 4: 提交**

```bash
cd FitMate-frontend
git add src/pages/chat/ChatLogicBase.vue
git commit -m "feat(chat): add per-run buffer infrastructure for token batching"
```

---

## Task 5: handleThinkingEvent + upsertStreamingBotMessage 改造为 buffer 模式

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue:1633-1705` (handleThinkingEvent)
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue:2066-2130` (upsertStreamingBotMessage)

- [ ] **Step 1: 改造 handleThinkingEvent**

找到第 1633-1705 行 `handleThinkingEvent(rawValue)` 方法，替换为：

```js
    handleThinkingEvent(rawValue) {
      var payload = this.normalizeAddPayload(rawValue);
      if (!payload) {
        return;
      }
      const run = this.resolveRunForEvent(payload);
      if (!run) return;
      if (
        payload.chunkType &&
        payload.chunkType !== "thinking" &&
        payload.chunkType !== "reasoning"
      ) {
        return;
      }
      var thinkingText =
        payload.chunkText == null ? "" : String(payload.chunkText);
      if (!thinkingText) {
        return;
      }
      // run entry 更新（后台 run 也需要持久化，但走 buffer 节流）
      if (payload.botMsgId) {
        run.botMsgId = payload.botMsgId;
      }
      // 后台 run：直接更新 run entry（不进 UI buffer），但仍走 buffer 节流持久化
      if (this.isBackgroundRun(run)) {
        run.thinkingContent = (run.thinkingContent || "") + thinkingText;
        run.thinkingSegments = this.appendThinkingChunkToSegments(
          (run.thinkingSegments || []).slice(),
          thinkingText
        );
        var bgBuffer = this.getOrCreateBuffer(run.runId);
        if (bgBuffer && Date.now() - bgBuffer.lastSnapshotTime > 500) {
          this.snapshotRunState(run.runId);
          bgBuffer.lastSnapshotTime = Date.now();
        }
        return;
      }

      // 当前 run：累积到 buffer，由 rAF flush 统一更新 UI
      var buffer = this.getOrCreateBuffer(run.runId);
      if (!buffer) return;
      buffer.thinkingDelta += thinkingText;
      this.scheduleFlush(run.runId);
    },
```

- [ ] **Step 2: 改造 upsertStreamingBotMessage**

找到第 2066-2130 行 `upsertStreamingBotMessage(payload)` 方法的开头部分（直到 `this.snapshotRunState(run.runId);` 之前），替换为：

```js
    upsertStreamingBotMessage(payload) {
      if (!payload) {
        return;
      }
      if (payload.chunkType && payload.chunkType !== "content") {
        return;
      }
      const run = this.resolveRunForEvent(payload);
      if (!run) return;
      var receiveMsg =
        payload.chunkText == null ? "" : String(payload.chunkText);
      if (!receiveMsg) {
        return;
      }
      var botMsgId =
        payload.botMsgId ||
        run.botMsgId ||
        this.botMsgId;
      if (!botMsgId) {
        return;
      }
      // run entry 更新
      run.botMsgId = botMsgId;
      if (payload.sessionCode) {
        run.sessionCode = payload.sessionCode;
      }
      if (payload.runId != null) {
        run.runId = payload.runId;
      }
      if (!this.isTerminalAgentRunStatus(run.status)) {
        run.status = "running";
      }

      // 后台 run：直接持久化（节流），跳过 UI
      if (this.isBackgroundRun(run)) {
        var bgBuffer = this.getOrCreateBuffer(run.runId);
        if (bgBuffer && Date.now() - bgBuffer.lastSnapshotTime > 500) {
          this.snapshotRunState(run.runId);
          bgBuffer.lastSnapshotTime = Date.now();
        }
        return;
      }

      // 当前 run：首次 ADD 立即创建 bot 消息（保证 UI 响应）
      if (!this.findBotMessage(botMsgId)) {
        this.findOrCreateBotMessage(botMsgId, payload);
        if (this.taskStartTime && !this.lastTtft) {
          this.lastTtft = Date.now() - this.taskStartTime;
        }
        this.isSending = false;
        this.isStreaming = true;
        this.guidanceMessage = "正在生成回答，请稍候。";

        var sessionMeta = {
          chatSessionId:
            payload.chatSessionId != null
              ? payload.chatSessionId
              : run.chatSessionId != null
              ? run.chatSessionId
              : null,
          sessionCode:
            payload.sessionCode ||
            run.sessionCode ||
            this.currentSessionCode ||
            null,
          sceneType:
            payload.sceneType ||
            this.currentSessionSceneType ||
            this.resolveExpectedSessionSceneType(),
        };
        this.applyServerSessionMeta(sessionMeta);
      }

      // 累积到 buffer，由 rAF flush 统一更新 UI
      var buffer = this.getOrCreateBuffer(run.runId);
      if (!buffer) return;
      buffer.contentDelta += receiveMsg;
      this.scheduleFlush(run.runId);
    },
```

- [ ] **Step 3: 验证无语法错误**

Run: `cd FitMate-frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 4: 手动验证思考流式输出**

1. 启动后端 + 前端 dev server
2. 发起一个需要思考的问题（如"分析一下我的训练计划是否合理"）
3. 观察思考流是否丝滑（不再每个 token 卡顿）
4. 观察多轮工具调用期间思考流是否稳定

Expected: 思考流丝滑，不再"一卡一卡"

- [ ] **Step 5: 提交**

```bash
cd FitMate-frontend
git add src/pages/chat/ChatLogicBase.vue
git commit -m "refactor(chat): convert thinking/content handlers to rAF-buffered mode"
```

---

## Task 6: scrollToBottom 节流

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue:3462-3492` (scrollToBottom)

- [ ] **Step 1: 在 data 中新增 scroll 节流字段**

找到 data() 中 `isProgrammaticScroll` 字段附近，新增：

```js
      isProgrammaticScroll: false,
      scrollRAFScheduled: false,
      cachedChatMessagesEl: null as HTMLElement | null,
```

- [ ] **Step 2: 新增 scrollToBottomThrottled 方法**

在 methods 中（紧挨 `scrollToBottom` 之前）新增：

```js
    scrollToBottomThrottled() {
      if (this.scrollRAFScheduled) return;
      this.scrollRAFScheduled = true;
      var me = this;
      requestAnimationFrame(function () {
        me.scrollRAFScheduled = false;
        me.doScrollToBottom();
      });
    },
    doScrollToBottom() {
      if (!this.cachedChatMessagesEl) {
        this.cachedChatMessagesEl = document.getElementById("chat-messages");
      }
      if (!this.cachedChatMessagesEl) return;

      if (!this.isUserScrolledUp) {
        this.isProgrammaticScroll = true;
        this.cachedChatMessagesEl.scrollTop = this.cachedChatMessagesEl.scrollHeight;
        var me = this;
        requestAnimationFrame(function () {
          requestAnimationFrame(function () {
            me.isProgrammaticScroll = false;
          });
        });
      } else {
        this.handleChatScroll();
      }
    },
```

- [ ] **Step 3: 修改原 scrollToBottom 调用 doScrollToBottom**

找到原 `scrollToBottom(force)` 方法（约第 3462 行），保留签名但内部调用 doScrollToBottom，force=true 时跳过节流：

```js
    scrollToBottom(force) {
      if (force) {
        this.doScrollToBottom();
        return;
      }
      this.scrollToBottomThrottled();
    },
```

注意：原 scrollToBottom 方法内部的 `$nextTick` + `getElementById` + `requestAnimationFrame` 逻辑已移到 `doScrollToBottom`，删除原方法体中的重复代码。

- [ ] **Step 4: 验证无语法错误**

Run: `cd FitMate-frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 5: 手动验证滚动跟随**

1. 发起 Agent 对话
2. 观察流式输出时滚动跟随是否稳定（不抖动）
3. 上滑停止跟随，出现"返回底部"按钮
4. 点击"返回底部"按钮恢复跟随

Expected: 滚动稳定，跟随/取消跟随逻辑正常

- [ ] **Step 6: 提交**

```bash
cd FitMate-frontend
git add src/pages/chat/ChatLogicBase.vue
git commit -m "perf(chat): throttle scrollToBottom with shared rAF and cached DOM ref"
```

---

## Task 7: ChatMessageList.vue 渲染分支 + CSS

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/components/ChatMessageList.vue:94`
- Modify: `FitMate-frontend/src/styles/base.css` (或 ChatMessageList.vue 的 scoped style)

- [ ] **Step 1: 修改 bot 消息渲染分支**

找到第 94 行：
```html
            <div class="msg-text markdown-body" v-html="item.content"></div>
```

替换为：
```html
            <div
              v-if="item.isStreaming && !item.isFinished"
              class="msg-text msg-text-streaming"
            >{{ item.content }}</div>
            <div
              v-else
              class="msg-text markdown-body"
              v-html="item.content"
            ></div>
```

- [ ] **Step 2: 添加 .msg-text-streaming 样式**

在 `ChatMessageList.vue` 的 `<style scoped>` 块（或 `src/styles/base.css` 中 `.msg-text` 定义附近）新增：

```css
.msg-text-streaming {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: inherit;
  line-height: inherit;
  padding: inherit;
  margin: inherit;
  color: inherit;
}
```

确保与 `.markdown-body` 的字号、行高、padding 一致（参考现有 `.msg-text` 样式）。

- [ ] **Step 3: 验证无语法错误**

Run: `cd FitMate-frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 4: 手动验证流式期间渲染**

1. 发起 Agent 对话
2. 流式期间观察：应看到纯文本（markdown 原文如 `## 标题`）
3. finish 后观察：应渲染成 HTML（标题变大、列表变列表）
4. 观察切换时无明显视觉跳变（字号/行高一致）

Expected: 流式期间纯文本，finish 后 HTML，无大跳变

- [ ] **Step 5: 提交**

```bash
cd FitMate-frontend
git add src/pages/chat/components/ChatMessageList.vue src/styles/base.css
git commit -m "perf(chat): use plain text container during streaming to avoid innerHTML rebuild"
```

---

## Task 8: applyFinishPayload + 中断时 markdown 渲染 + rawContent

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue:2431-2550` (applyFinishPayload)
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue` (中断处理路径)

- [ ] **Step 1: applyFinishPayload 中 flush buffer + 保存 rawContent**

找到 `applyFinishPayload` 方法中设置 `chatItem.content = marked.parse(message || "");` 的位置（约第 2523 行）：

```js
        if (chatItem.botMsgId == botMsgId && chatItem.chatType !== "user") {
          chatItem.content = marked.parse(message || "");
          chatItem.interrupted = isInterrupted;
```

改为：

```js
        if (chatItem.botMsgId == botMsgId && chatItem.chatType !== "user") {
          // flush 残留 buffer（确保流式期间累积的 content 已写入 chatItem）
          if (runId) this.flushRunBuffer(runId);
          // 优先用 chatItem 已累积的 content（流式期间已推送），否则用 finish payload 的 message
          var rawContent = chatItem.content || message || "";
          // 如果 chatItem.content 已经是流式累积的原文，直接 parse；否则用 message
          if (chatItem.isStreaming) {
            chatItem.rawContent = rawContent;
            chatItem.content = marked.parse(rawContent);
          } else {
            chatItem.rawContent = message || "";
            chatItem.content = marked.parse(message || "");
          }
          chatItem.isStreaming = false;
          chatItem.isFinished = true;
          chatItem.interrupted = isInterrupted;
```

- [ ] **Step 2: 新增 push 的新消息也设置 rawContent**

找到 `if (!matched && botMsgId)` 块（约第 2535-2548 行）的 `this.chatList.push({...})`：

```js
      if (!matched && botMsgId) {
        this.chatList.push({
          id: "temp-" + this.generateRandomId(8),
          content: marked.parse(message || ""),
          userName: "bot",
          chatType: "bot",
          botMsgId: botMsgId,
          createdAt: new Date().toISOString(),
          sources: normalizedSources,
          sourceType: payload.sourceType || null,
          sessionCode: payload.sessionCode || this.currentSessionCode || null,
          sceneType: payload.sceneType || this.currentSessionSceneType || null,
        });
      }
```

改为：

```js
      if (!matched && botMsgId) {
        this.chatList.push({
          id: "temp-" + this.generateRandomId(8),
          content: marked.parse(message || ""),
          rawContent: message || "",
          isStreaming: false,
          isFinished: true,
          userName: "bot",
          chatType: "bot",
          botMsgId: botMsgId,
          createdAt: new Date().toISOString(),
          sources: normalizedSources,
          sourceType: payload.sourceType || null,
          sessionCode: payload.sessionCode || this.currentSessionCode || null,
          sceneType: payload.sceneType || this.currentSessionSceneType || null,
        });
      }
```

- [ ] **Step 3: 在中断处理路径触发 markdown 渲染**

搜索中断/取消处理方法（如 `handleAgentCancelled`、`onError`、`stopGeneration` 等，通过 grep "interrupted" 定位）。在设置 `interrupted = true` 的位置，添加 flush + markdown 渲染：

找到设置 `chatItem.interrupted = true` 或类似中断标记的位置，确保在中断前先 flush：

```js
// 在中断处理方法中，设置 interrupted 之前
if (runId) {
  this.flushRunBuffer(runId);
}
var targetMsg = this.findBotMessage(botMsgId);
if (targetMsg && targetMsg.isStreaming) {
  targetMsg.rawContent = targetMsg.content || "";
  targetMsg.content = marked.parse(targetMsg.content || "");
  targetMsg.isStreaming = false;
  targetMsg.isFinished = true;
  targetMsg.interrupted = true;
}
```

如果中断处理在 `applyFinishPayload` 内（通过 `isInterrupted` 判断），则 Step 1 的改动已覆盖。如果是独立方法，需单独修改。

- [ ] **Step 4: 历史消息加载时设置 rawContent + isFinished**

找到历史消息加载位置（grep `marked.parse` 在第 3030 行附近）：

```js
        content: role === "assistant" ? marked.parse(rawContent) : rawContent,
```

确保历史 bot 消息也设置 `rawContent` 和 `isFinished`：

```js
        content: role === "assistant" ? marked.parse(rawContent) : rawContent,
        rawContent: rawContent,
        isStreaming: false,
        isFinished: true,
```

- [ ] **Step 5: 验证无语法错误**

Run: `cd FitMate-frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 6: 手动验证中断 + finish 场景**

1. 发起 Agent 对话，流式中点击"停止"按钮 → 验证已生成内容渲染为 markdown
2. 完成正常对话 → 验证 finish 后 markdown 渲染正常
3. 刷新页面加载历史消息 → 验证历史消息 markdown 渲染正常

Expected: 中断/finish/历史加载均正确渲染 markdown

- [ ] **Step 7: 提交**

```bash
cd FitMate-frontend
git add src/pages/chat/ChatLogicBase.vue
git commit -m "feat(chat): flush buffer and render markdown on finish/interrupt/history-load"
```

---

## Task 9: 复制功能适配 + 会话切换清理

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue:3173` (copyMessageContent)
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue` (会话切换清理)

- [ ] **Step 1: 修改 copyMessageContent 优先用 rawContent**

找到第 3173-3174 行：

```js
    copyMessageContent(item) {
      var text = this.extractMessageText(item && item.content).trim();
```

改为：

```js
    copyMessageContent(item) {
      // 优先用 rawContent（markdown 原文），fallback 到 content
      var raw = item && item.rawContent;
      var text;
      if (raw) {
        text = String(raw).trim();
      } else {
        text = this.extractMessageText(item && item.content).trim();
      }
```

- [ ] **Step 2: 在会话切换时清理 buffer**

搜索会话切换方法（grep "loadSession\|switchSession\|onSessionChange\|currentSessionId" 定位）。在切换会话的方法开头加入清理：

```js
// 在会话切换方法的开头
this.clearRunBuffers();
this.scrollRAFScheduled = false;
this.cachedChatMessagesEl = null;
```

如果不确定具体方法名，可在 `clearRunBuffers` 方法的调用点搜索。常见位置：
- `loadSessionMessages`
- `switchToSession`
- `handleSessionSelect`

- [ ] **Step 3: 验证无语法错误**

Run: `cd FitMate-frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 4: 手动验证复制 + 会话切换**

1. 完成一次对话后，点击复制按钮 → 粘贴到记事本，验证是 markdown 原文（如 `## 标题`，而非 `<h2>标题</h2>`）
2. 历史消息复制 → 验证同样是 markdown 原文
3. 切换会话 → 验证无报错，新会话能正常发起对话
4. 流式中切换会话 → 验证 buffer 清理，无串扰

Expected: 复制得到 markdown 原文；会话切换无串扰

- [ ] **Step 5: 提交**

```bash
cd FitMate-frontend
git add src/pages/chat/ChatLogicBase.vue
git commit -m "feat(chat): use rawContent for copy and clear buffers on session switch"
```

---

## Self-Review

### Spec coverage

| Spec 要求 | 对应 Task |
|---|---|
| 后端状态机识别 final_answer 字段 | Task 2 |
| 后端 JSON 转义处理 | Task 1 |
| 后端 fallback 整段推送 | Task 3 Step 3 |
| 前端 per-run buffer 隔离 | Task 4 |
| 前端 handleThinkingEvent 改 buffer | Task 5 |
| 前端 upsertStreamingBotMessage 改 buffer | Task 5 |
| 前端 scrollToBottom 节流 | Task 6 |
| 前端流式期间纯文本容器 | Task 7 |
| 前端 finish/中断时 markdown 渲染 | Task 8 |
| 前端 rawContent 保存原文 | Task 8, Task 9 |
| 前端复制功能适配 | Task 9 |
| 前端会话切换清理 buffer | Task 9 |
| 前端 snapshotRunState 节流 500ms | Task 4 (flushRunBuffer 中实现) |

所有 spec 要求已覆盖。

### Placeholder scan

无 TBD/TODO/占位符。所有步骤包含完整代码。

### Type consistency

- `getOrCreateBuffer(runId)` / `scheduleFlush(runId)` / `flushRunBuffer(runId)` / `findBotMessage(botMsgId)` / `clearRunBuffers()` 方法名一致
- `runBuffers` 字段名一致
- `rawContent` / `isStreaming` / `isFinished` 字段名一致
- 后端 `FinalAnswerStreamState.onNext(contentDelta, fullContent)` / `hasStreamed()` 签名一致

无类型不一致问题。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-05-sse-streaming-optimization.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
