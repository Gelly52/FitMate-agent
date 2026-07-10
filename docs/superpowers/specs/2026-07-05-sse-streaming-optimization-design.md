# SSE 流式输出性能优化设计

## 概述

### 目标

解决当前 Agent-based chat 的两个核心体验问题：

1. **思考流式输出"一卡一卡的、一大片一大片返回"** — 由前端每个 token 同步触发响应式更新 + DOM 重建 + sessionStorage 写入导致
2. **长对话 + 多工具调用页面崩溃** — 由前端 O(n²) 处理链路 + DOM 无限累积导致
3. **Agent 最终答案一次性整段推送** — 由后端 `AgentLoopExecutor` 在 LLM 决策 JSON 解析完成后才推送 finalAnswer 导致

### 范围

本设计包含两部分：

- **前端**：token 批处理（rAF 队列合并）+ 流式期间禁用 markdown 重渲染
- **后端**：final_answer 字段流式分片推送（轻量级状态机 + JSON 转义处理）

### 不在范围内

以下优化项不在本次设计内，未来可单独处理：

- 虚拟滚动（vue-virtual-scroller）
- shallowRef / markRaw 响应式优化
- mergedTimeline computed 缓存
- Agent 线程池扩容
- SseEmitter 异步发送队列
- SseEmitter 超时与心跳
- thinking chunk 时间窗口批处理
- 工具调用期间心跳事件

### 背景

当前 SSE 链路分析（详见 2026-07-05 会话）：

- 后端 `AgentLoopExecutor.java:331` `sendContentChunk(context, finalAnswer)` 一次性推送整段答案
- 前端 `ChatLogicBase.vue` 每个 thinking token 触发 `cloneThinkingSegments` + `snapshotRunState`（JSON.stringify 整个 run）+ `scrollToBottom`（$nextTick + 双 rAF + getElementById）
- 前端 `ChatMessageList.vue:94` `v-html="item.content"` 流式期间每个 token 触发整段 innerHTML 重建（而 content 是未解析的 markdown 原文，v-html 无渲染意义）

## 整体架构

### 数据流

```
后端 AgentLoopExecutor.forEach:
  ├─ reasoning delta → sendThinkingChunk (不变)
  └─ content delta:
     ├─ 累积到 decisionContent (不变)
     └─ 状态机识别 final_answer 字段 → sendContentChunk 增量推送 (新增)

前端 SSE 事件:
  ├─ onThinking → per-run buffer → rAF flush → 响应式状态更新
  └─ onAdd → per-run buffer → rAF flush → 响应式状态更新
       ├─ 流式中: 纯文本容器 {{ item.content }} (新增, 不触发 innerHTML 重建)
       └─ finish/中断时: marked.parse + v-html 渲染 (现有逻辑)
```

### 改动文件清单

| 文件 | 改动类型 | 说明 |
|---|---|---|
| `FitMate-backend/.../agent/core/AgentLoopExecutor.java` | 修改 | LLM 流式消费块加入状态机驱动 final_answer 分片 |
| `FitMate-backend/.../agent/core/FinalAnswerStreamState.java` | 新增 | 状态机类，封装字段边界识别与转义处理 |
| `FitMate-backend/.../agent/core/JsonStringUnescaper.java` | 新增 | JSON 字符串反转义工具 |
| `FitMate-frontend/src/pages/chat/ChatLogicBase.vue` | 修改 | handleThinkingEvent、upsertStreamingBotMessage 改为 buffer 模式；scrollToBottom 节流；中断时触发 markdown 渲染 |
| `FitMate-frontend/src/pages/chat/components/ChatMessageList.vue` | 修改 | bot 消息渲染分支：流式期间纯文本，finish 后 v-html |

### 关键设计决策

1. **前端 buffer 按 runId 隔离** — 避免多并发 Agent run 串扰，每个 run 独立 rAF 调度
2. **后端状态机只在 action=final 路径触发分片** — tool_call 路径完全不变，保证工具调用逻辑零影响
3. **后端 fallback** — 状态机识别失败时退回整段推送，保证健壮性
4. **前端 markdown 渲染时机** — `onFinish` 或 `onError` 或中断检测时统一触发
5. **scrollToBottom 全局共享一个 rAF** — 多并发 run 时避免多个 scroll 调度风暴
6. **snapshotRunState 节流到 500ms** — 从每 token 降到 500ms 一次，丢失最多 500ms 状态（刷新后从后端补全）

## 后端详细设计

### 改动位置

`AgentLoopExecutor.java` 第 181-206 行的 LLM 流式消费块。

### 状态机设计

新增 `FinalAnswerStreamState` 类，封装识别与转义逻辑，保持 `AgentLoopExecutor` 干净。

#### 状态流转

```
                                 "action":"final"
              DECIDING ───────────────────────────> FINAL_ANSWER_DETECTED
                  │                                            │
                  │ "final_answer":"                           │ "final_answer":"
                  └──────────────────────────────┐             v
                                                   ──────> IN_FINAL_ANSWER
                                                                        │
   任意时刻识别失败 ─────────> FAILED (fallback 到整段推送) <────────────┘
```

支持两条进入 `IN_FINAL_ANSWER` 的路径：
- 标准路径：`DECIDING → FINAL_ANSWER_DETECTED → IN_FINAL_ANSWER`（action 字段在前）
- 字段乱序路径：`DECIDING → IN_FINAL_ANSWER`（final_answer 字段在前，跳过 action 校验。安全因为 tool_call 格式不含 final_answer 字段）

| 状态 | 含义 | content delta 处理 |
|---|---|---|
| `DECIDING` | 还没识别到 action=final | 只累积到 decisionContent，不推送 |
| `FINAL_ANSWER_DETECTED` | 已识别 action=final，等待 final_answer 字段 | 只累积，扫描 `"final_answer":"` 边界 |
| `IN_FINAL_ANSWER` | 已进入 final_answer 字段值 | 反转义后推送，遇字段结束符 `"` 停止 |
| `FAILED` | 状态机识别失败（格式异常） | 不再尝试推送，由 parseDecision 后的兜底逻辑整段推送 |

#### 核心伪代码

```java
// AgentLoopExecutor.java forEach 内
FinalAnswerStreamState streamState = new FinalAnswerStreamState();

llmGateway.streamWithReasoning(prompt).toStream().forEach(chunk -> {
    // ... 取消检查、usage 处理、reasoning 推送均不变 ...

    if (StrUtil.isNotBlank(contentDelta)) {
        decisionContent.append(contentDelta);
        // 新增：状态机驱动 final_answer 流式推送
        String answerDelta = streamState.onNext(contentDelta, decisionContent.toString());
        if (StrUtil.isNotBlank(answerDelta)) {
            sendContentChunk(context, answerDelta);
        }
    }
});

// forEach 结束后逻辑不变：
// - parseDecision 解析完整 decisionContent
// - 如果 action=final，取出 finalAnswer
// - 如果 streamState.hasStreamed()，sendContentChunk 跳过（避免重复）
// - 如果 !streamState.hasStreamed()，sendContentChunk 整段推送（原逻辑）
```

#### FinalAnswerStreamState 设计

```java
class FinalAnswerStreamState {
    enum State { DECIDING, FINAL_ANSWER_DETECTED, IN_FINAL_ANSWER, FAILED }

    private State state = State.DECIDING;
    private StringBuilder pendingEscaped = new StringBuilder(); // 待反转义的缓冲
    private boolean hasStreamed = false; // 是否已推送过任何增量

    /**
     * 处理新 delta，返回可推送的已反转义内容。
     * @param contentDelta 本次新增的 content 片段
     * @param fullContent 截至当前累积的完整 decisionContent
     */
    String onNext(String contentDelta, String fullContent) {
        if (state == State.FAILED) return "";

        if (state == State.DECIDING) {
            // 扫描 "action":"final"
            if (containsActionFinal(fullContent)) {
                state = State.FINAL_ANSWER_DETECTED;
            } else {
                return "";
            }
        }

        if (state == State.FINAL_ANSWER_DETECTED) {
            int idx = findFinalAnswerStart(fullContent);
            if (idx < 0) return "";
            state = State.IN_FINAL_ANSWER;
            // 把 idx 之后已累积的内容作为初始推送
            pendingEscaped.append(fullContent.substring(idx));
        }

        if (state == State.IN_FINAL_ANSWER) {
            pendingEscaped.append(contentDelta);
            // 反转义并返回（处理 \n \" \\ \t \uXXXX 等）
            String unescaped = JsonStringUnescaper.unescape(pendingEscaped);
            // 保留末尾不完整的转义序列（如单个 \）
            String safe = JsonStringUnescaper.retainIncompleteEscape(unescaped, pendingEscaped);
            hasStreamed = true;
            return safe;
        }
        return "";
    }

    boolean hasStreamed() { return hasStreamed; }
}
```

#### 转义处理

JSON 字符串中 `final_answer` 的值可能含 `\n`、`\"`、`\\`、`\t`、`\uXXXX` 等。需要反转义后再推送，否则前端看到字面量 `\n`。

```java
class JsonStringUnescaper {
    /**
     * 反转义 JSON 字符串内容。
     * 支持的转义序列：\n \t \r \" \\ \/ \uXXXX
     */
    static String unescape(StringBuilder escaped) {
        // 逐字符扫描，遇到 \ 做转义处理
    }

    /**
     * 末尾如果是不完整的转义序列（如单个 \，或 \u 未满 4 位），
     * 保留到下次处理，避免推送错误字符。
     */
    static String retainIncompleteEscape(String unescaped, StringBuilder pending) {
        // 检查末尾是否有未完成的 \xxx，回填到 pending
    }
}
```

### Fallback 逻辑

`AgentLoopExecutor.java` 第 228-231 行改造：

```java
if ("final".equalsIgnoreCase(action)) {
    String finalAnswer = StrUtil.blankToDefault(decision.getStr("final_answer"), "已完成处理。请查看上方执行轨迹。");
    // 新增：如果状态机已流式推送，跳过整段推送；否则走原逻辑
    if (!streamState.hasStreamed()) {
        sendContentChunk(context, finalAnswer);
    }
    finishWithAnswer(context, finalAnswer, observations, memory, allowedTools, summarySection, userProfileSection);
    return;
}
```

### 边界情况覆盖

| 场景 | 处理 |
|---|---|
| `final_answer` 值为空 `""` | 状态机进入 IN_FINAL_ANSWER 但无内容可推送，hasStreamed=true，跳过整段推送 |
| LLM 输出带 markdown 代码块包裹 ` ```json {...} ``` ` | 状态机识别失败 → FAILED → fallback 整段推送 |
| LLM 字段顺序变化（`final_answer` 在 `action` 前） | DECIDING 状态先扫描 `final_answer`，如果先出现则直接进入 IN_FINAL_ANSWER |
| final_answer 含代码块（多行 `\n`） | 正常反转义推送，前端批处理合并 |
| 流式中途取消 | forEach 内取消检查不变，抛 `AgentCancelledException` |
| final_answer 后还有其他字段（如 `reason`） | IN_FINAL_ANSWER 遇到未转义的 `"` 字符停止，后续字段不推送 |
| LLM 输出 `tool_call` 而非 `final` | 状态机停在 DECIDING，不推送任何 content |

### 对现有功能的影响

| 功能 | 影响 |
|---|---|
| 决策解析 | 不影响。完整 `decisionContent` 仍传给 `parseDecision` |
| 工具调用路径 | 不影响。action=tool_call 时状态机停在 DECIDING |
| trace 事件 | 不影响。`llm_started`/`llm_finished` 触发时机不变 |
| 持久化 | 不影响。`finishAssistantMessage` 仍用完整 finalAnswer |
| 中断/取消 | 不影响。取消检查仍在 forEach 内 |
| 前端 onAdd | 天然兼容。前端已是增量拼接 `content += delta` |

## 前端详细设计

### 改动位置

- `ChatLogicBase.vue` — token 批处理逻辑、中断时 markdown 渲染
- `ChatMessageList.vue` 第 94 行 — 流式期间渲染分支

### 1. per-run buffer 数据结构

```ts
interface RunFlushBuffer {
  runId: string;
  thinkingDelta: string;       // 累积的 thinking token
  contentDelta: string;        // 累积的 content token
  pendingFlush: boolean;       // 是否已安排 rAF
  lastSnapshotTime: number;    // snapshotRunState 节流时间戳
}

// data 中新增
runBuffers: new Map<string, RunFlushBuffer>()
```

约束：按 runId 隔离，多并发 run 各自独立 buffer 与 rAF 调度，互不串扰。

### 2. handleThinkingEvent 改造

原逻辑（`ChatLogicBase.vue` 第 1638-1689 行）：每个 token 立即更新 `run.thinkingContent` + `thinkingSegments` + `snapshotRunState` + `scrollToBottom`

新逻辑：

```ts
handleThinkingEvent(data) {
  // 1. 解析 payload，找到 run（逻辑不变）
  const run = this.activeAgentRuns[runId];
  if (!run) return;

  // 2. 累积到 buffer，不立即更新响应式状态
  const buffer = this.getOrCreateBuffer(runId);
  buffer.thinkingDelta += thinkingText;

  // 3. 安排 rAF flush
  this.scheduleFlush(runId);
}
```

### 3. upsertStreamingBotMessage（onAdd）改造

原逻辑（第 2104-2120 行）：每个 token 立即 `content += delta` + `snapshotRunState` + `scrollToBottom`

新逻辑：

```ts
upsertStreamingBotMessage(payload) {
  // 1. 首次 ADD：仍立即创建 bot 消息（保证 UI 响应）
  if (!this.findBotMsg(payload.botMsgId)) {
    this.createBotMessage(payload);  // 原创建逻辑
    this.isStreaming = true;
  }

  // 2. 后续 ADD：累积到 buffer
  const buffer = this.getOrCreateBuffer(payload.runId);
  buffer.contentDelta += receiveMsg;
  this.scheduleFlush(payload.runId);
}
```

### 4. flush 逻辑（核心）

```ts
private flushRunBuffer(runId: string) {
  const buffer = this.runBuffers.get(runId);
  if (!buffer) return;

  // 1. 更新 thinking（如果有增量）
  if (buffer.thinkingDelta) {
    const run = this.activeAgentRuns[runId];
    run.thinkingContent += buffer.thinkingDelta;
    run.thinkingSegments = this.appendThinkingChunkToSegments(
      (run.thinkingSegments || []).slice(),
      buffer.thinkingDelta
    );

    const targetMsg = this.findBotMsg(run.botMsgId);
    if (targetMsg) {
      targetMsg.thinkingContent += buffer.thinkingDelta;
      targetMsg.thinkingSegments = this.cloneThinkingSegments(run.thinkingSegments);
      targetMsg.isThinking = true;
    }
    buffer.thinkingDelta = "";
  }

  // 2. 更新 content（如果有增量）
  if (buffer.contentDelta) {
    const targetMsg = this.findBotMsg(run.botMsgId);
    if (targetMsg) {
      targetMsg.content = (targetMsg.content || "") + buffer.contentDelta;
      targetMsg.isStreaming = true;   // 标记流式中，控制渲染分支
    }
    buffer.contentDelta = "";
  }

  // 3. scrollToBottom（每帧最多一次，所有 run 共享）
  this.scrollToBottomThrottled();

  // 4. snapshotRunState 节流（500ms 一次）
  if (Date.now() - buffer.lastSnapshotTime > 500) {
    this.snapshotRunState(runId);
    buffer.lastSnapshotTime = Date.now();
  }

  buffer.pendingFlush = false;
}

private scheduleFlush(runId: string) {
  const buffer = this.runBuffers.get(runId);
  if (!buffer || buffer.pendingFlush) return;
  buffer.pendingFlush = true;
  requestAnimationFrame(() => this.flushRunBuffer(runId));
}
```

### 5. scrollToBottom 节流

```ts
private scrollRAFScheduled = false;
private cachedChatMessagesEl: HTMLElement | null = null;

private scrollToBottomThrottled() {
  if (this.scrollRAFScheduled) return;
  this.scrollRAFScheduled = true;
  requestAnimationFrame(() => {
    this.scrollRAFScheduled = false;
    this.doScrollToBottom();
  });
}

private doScrollToBottom() {
  if (!this.cachedChatMessagesEl) {
    this.cachedChatMessagesEl = document.getElementById("chat-messages");
  }
  if (!this.cachedChatMessagesEl) return;

  if (!this.isUserScrolledUp) {
    this.isProgrammaticScroll = true;
    this.cachedChatMessagesEl.scrollTop = this.cachedChatMessagesEl.scrollHeight;
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        this.isProgrammaticScroll = false;
      });
    });
  } else {
    this.handleChatScroll();
  }
}
```

优化点：
- 缓存 `chat-messages` DOM 引用，避免每 token `getElementById`
- 所有 run 共享一个 scroll rAF，避免多并发 run 时多个 scroll 调度风暴

### 6. ChatMessageList.vue 渲染分支

原逻辑（第 94 行）：

```html
<div class="msg-text markdown-body" v-html="item.content"></div>
```

新逻辑：

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

样式约束：`.msg-text-streaming` 与 `.markdown-body` 共享字号、行高、padding、`white-space: pre-wrap`，确保切换时无视觉跳变。

### 7. 中断/finish 时触发 markdown 渲染

```ts
// onFinish 时
applyFinishPayload(payload) {
  const targetMsg = this.findBotMsg(payload.botMsgId);
  if (targetMsg) {
    // 先 flush 残留 buffer
    this.flushRunBuffer(payload.runId);

    // 保留 rawContent 用于复制
    targetMsg.rawContent = targetMsg.content;

    // 解析 markdown
    targetMsg.content = marked.parse(targetMsg.content || "");
    targetMsg.isFinished = true;
    targetMsg.isStreaming = false;
  }
  // ... 其他 finish 逻辑不变 ...
}

// 中断/错误时（关键：必须也触发 markdown 渲染）
handleInterrupted(runId) {
  this.flushRunBuffer(runId);  // 强制 flush 残留
  const run = this.activeAgentRuns[runId];
  const targetMsg = this.findBotMsg(run?.botMsgId);
  if (targetMsg && targetMsg.isStreaming) {
    targetMsg.rawContent = targetMsg.content;
    targetMsg.content = marked.parse(targetMsg.content || "");
    targetMsg.isFinished = true;
    targetMsg.isStreaming = false;
    targetMsg.interrupted = true;
  }
}
```

### 8. 复制功能适配

finish 后 `item.content` 变成 HTML 字符串，复制按钮应复制 markdown 原文。新增 `rawContent` 字段保存原文：

```ts
// 复制按钮逻辑
handleCopyMessage(item) {
  const text = item.rawContent || item.content;
  navigator.clipboard.writeText(text);
}
```

历史消息加载时，后端返回的是 markdown 原文，直接赋值给 `rawContent`，`content` 走 `marked.parse`。

### 9. 历史消息加载（不受影响）

历史消息从后端加载时，直接设置 `isFinished=true`、`isStreaming=false`，走 v-html 路径，不进 buffer。

### 10. 会话切换/清理

```ts
// 切换会话时清理所有 buffer
onSessionChange() {
  this.runBuffers.clear();
  this.scrollRAFScheduled = false;
  this.cachedChatMessagesEl = null;  // 重新查询
  // ... 其他清理 ...
}
```

## 边界情况与风险

### 前端边界情况

| 场景 | 处理 |
|---|---|
| 首次 ADD | 立即创建 bot 消息，不进 buffer（保证 UI 响应） |
| 多并发 run | per-run 独立 buffer + 独立 rAF，scroll 共享一个 rAF |
| 中断/取消 | 强制 flush 残留 buffer + 触发 markdown 渲染，不丢内容 |
| 会话切换 | 清理所有 buffer + 重置 scroll rAF 标志 + 清除 DOM 引用缓存 |
| 历史消息加载 | 直接 isFinished=true，不进 buffer |
| snapshotRunState 频率 | 从每 token 降到 500ms 一次，最多丢 500ms 状态（刷新后从后端补全） |
| 流式期间视觉 | 纯文本插值 `{{ item.content }}`，无 DOM 重建 |
| finish 时跳变 | CSS 让 `.msg-text-streaming` 与 `.markdown-body` 样式一致 |
| 复制功能 | 新增 `rawContent` 字段保存原文，复制时优先用 rawContent |

### 后端边界情况

见后端详细设计章节的"边界情况覆盖"。

### 潜在风险

1. **状态机识别失败率**：依赖 LLM 严格遵守 `agent-system.md` 约定的 JSON 格式。如果 LLM 偶尔输出带 markdown 代码块包裹的 JSON，状态机会 fallback 到整段推送，体验退化为"一大片"。需通过日志监控 fallback 频率，必要时增强状态机容错。

2. **转义处理复杂性**：JSON 转义序列（特别是 `\uXXXX`）的反转义逻辑需要严格测试。错误的反转义会导致前端显示乱码。

3. **多并发 run 的 rAF 调度**：每个 run 独立 rAF，极端情况下 3 个并发 run 各自一个 rAF，每帧 3 次 flush。性能上仍远优于每 token 更新，但需验证。

4. **sessionStorage 5MB 上限**：长会话 + 多并发 run 时，500ms 节流后仍可能累积较大数据。需监控 sessionStorage 使用量，必要时进一步降低频率或压缩数据。

## 对现有功能的影响汇总

| 功能 | 影响 | 严重度 |
|---|---|---|
| 会话刷新恢复 | snapshotRunState 降到 500ms，最多丢 500ms（后端有完整数据补全） | 低 |
| 多并发 Agent run | per-run buffer 隔离，不串扰 | 低（需测试验证） |
| 中断/取消 | 强制 flush + markdown 渲染，不丢内容 | 低（需测试验证） |
| 滚动跟随 | 反而更稳定（合并到 rAF） | 正向 |
| 历史消息加载 | 不受影响 | 无 |
| 复制/引用 | 新增 rawContent 字段保存原文 | 低（需测试复制功能） |
| 决策解析（后端） | 不影响 | 无 |
| 工具调用路径（后端） | 不影响 | 无 |
| 持久化（后端） | 不影响 | 无 |

## 测试策略

### 后端测试

1. **状态机单元测试**：
   - 标准 `{"action":"final","final_answer":"..."}` 路径
   - 字段顺序变化（`final_answer` 在 `action` 前）
   - final_answer 含转义字符（`\n`、`\"`、`\\`、`\uXXXX`）
   - final_answer 为空字符串
   - LLM 输出带 markdown 代码块包裹（fallback）
   - LLM 输出 tool_call（状态机停在 DECIDING）

2. **转义工具单元测试**：
   - 各种转义序列的反转义
   - 末尾不完整转义序列的保留
   - 无转义字符的字符串

3. **集成测试**：
   - 端到端 Agent run，验证前端收到多次 ADD 事件
   - 中断场景，验证已推送的内容不丢
   - 多并发 run，验证不串扰

### 前端测试

1. **buffer 隔离测试**：
   - 2 个并发 run 同时推送 token，验证不串扰
   - 切换会话时 buffer 清理

2. **markdown 渲染时机测试**：
   - 流式期间验证 `{{ item.content }}` 渲染（无 innerHTML 重建）
   - finish 后验证 `v-html` 渲染
   - 中断后验证 markdown 渲染触发

3. **复制功能测试**：
   - finish 后复制验证是 markdown 原文
   - 历史消息复制验证

4. **滚动跟随测试**：
   - 流式期间滚动跟随稳定
   - 用户上滑停止跟随 + 返回底部按钮
   - 多并发 run 时 scroll 调度无风暴

5. **会话刷新恢复测试**：
   - 流式中刷新，验证从后端补全的内容正确
   - snapshotRunState 节流后刷新，验证最多丢 500ms 数据

### 性能验证

1. **思考流卡顿**：复杂问题 + 多轮工具调用，观察思考流是否丝滑
2. **长对话崩溃**：单次回答 2000 字 + 5 次工具调用，观察页面是否稳定
3. **多并发**：3 个并发 run，观察是否串扰
4. **最终答案逐字显示**：验证 Agent 模式下最终答案逐字出现
