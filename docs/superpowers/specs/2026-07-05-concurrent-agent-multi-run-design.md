# 并发 Agent 多 run 追踪设计

日期：2026-07-05
关联：后端 slot 锁（`AgentExecuteServiceImpl.MAX_CONCURRENT_AGENTS_PER_SESSION = 3`）

## 1. 背景与目标

后端已放开同一登录会话最多 3 个 Agent 任务并发（多槽位锁）。前端当前以"全局单值 `activeAgentRun` + 多个游离的全局数组/字段"追踪单个 run，导致：

- 任务运行时新建聊天/切换会话被禁用
- 强行切换会丢失旧 run 的实时追踪
- `agentSteps` / `thinkingSegments` / `botMsgId` / `tokenUsage` / `currentSessionCode` 等全局字段与 `activeAgentRun.*` 双写冗余，同步点散落 10+ 处

**目标**：把单值追踪重构为 `Map<runId, RunState>`，每个 run 自带完整状态；全局只保留"当前会话对应的 run"派生视图。支持并发新建/切换/取消，旧 run 在后台继续被实时追踪，切回时立即可见。

## 2. 当前问题清单

| 问题 | 位置 |
|---|---|
| 新建聊天按钮 `:disabled` 含 `hasPendingAgentRun()` | ChatPage.vue L18 |
| `handleCreateChat` 二次阻止 | ChatLogicBase.vue L408-412 |
| `handleSelectChatSession` 阻止切换 | L352-359 |
| `activeAgentRun` 单值，切换会话被 `clearActiveAgentRun()` 清掉 | L377, L414, L948 |
| SSE 3 处 runId 过滤"非当前 run 即丢弃" | L1424, L1836, L2020 |
| `agentSteps` ↔ `activeAgentRun.steps` 双写，同步点 L2127 | 全文 |
| `thinkingSegments` / `thinkingContent` 游离全局，未挂 run | L96-100 |
| `botMsgId` 全局 ↔ `activeAgentRun.botMsgId` 双写 + fallback | L35, 多处 |
| `tokenUsage` 全局单值，未挂 run | L56 |
| `currentSessionCode` / `currentSessionSceneType` 双写 | L43-44 |
| `agentStepEventReceived` 死字段（只写不读 0 处读取） | L75 |
| sessionStorage 单 key 只能存一个 run | L869-875 |
| `isTerminalAgentRunStatus` 不识别 cancelled/interrupted | L882-885 |

## 3. 新数据模型

### 3.1 RunState 结构（单一来源）

每个 run 是一个独立对象，自带所有 per-run 状态：

```ts
interface RunState {
  runId: string | number;
  chatSessionId: number | null;   // 创建时固定，不再随 payload 反向同步
  sessionCode: string | null;
  botMsgId: string | null;
  status: string;                  // pending/running/success/failed/cancelled/interrupted
  requestText: string;
  sceneType: string;               // 默认 "agent"
  sourceType: string;              // 默认 "chat"
  finishReceived: boolean;
  steps: AgentStep[];              // 原 agentSteps
  thinkingSegments: ThinkingSegment[];
  thinkingContent: string;         // 原 thinkingContent
  tokenUsage: TokenUsage | null;   // 原 tokenUsage
}
```

### 3.2 data() 改造

```ts
data() {
  return {
    // 新增：run 追踪表，按 runId 索引
    activeAgentRuns: {} as Record<string, RunState>,
    // 保留：当前会话指针（用于路由 SSE 事件、决定焦点）
    activeChatSessionId: null as number | null,
    // 保留：UI 状态（见 §10）
    isSending: false,
    isStreaming: false,
    isThinking: false,
    thinkingExpanded: false,
    chatList: [],
    // ... 其他与 run 无关的字段保留
  };
}
```

### 3.3 删除清单（不再保留为全局字段）

| 字段 | 替代 |
|---|---|
| `activeAgentRun` | computed `currentAgentRun` |
| `agentSteps` | `currentAgentRun?.steps ?? []` |
| `thinkingSegments` | `currentAgentRun?.thinkingSegments ?? []` |
| `thinkingContent` | `currentAgentRun?.thinkingContent ?? ""` |
| `botMsgId` | `currentAgentRun?.botMsgId ?? null` |
| `tokenUsage` | `currentAgentRun?.tokenUsage ?? null` |
| `currentSessionCode` | `currentAgentRun?.sessionCode ?? null` |
| `currentSessionSceneType` | `currentAgentRun?.sceneType ?? null` |
| `agentStepEventReceived` | 删除（死字段） |

## 4. computed 派生视图

```ts
computed: {
  // 当前会话对应的非终态 run；切换会话时自动重指向
  currentAgentRun(): RunState | null {
    if (this.activeChatSessionId == null) return null;
    return Object.values(this.activeAgentRuns).find(
      r => r.chatSessionId === this.activeChatSessionId
    ) || null;
  },
  // 当前会话是否有运行中 run
  hasPendingRunInCurrentSession(): boolean {
    return this.currentAgentRun != null
      && !this.isTerminalAgentRunStatus(this.currentAgentRun.status);
  },
  // 任意会话是否有运行中 run（用于全局提示，不再阻止操作）
  hasAnyPendingRun(): boolean {
    return Object.values(this.activeAgentRuns).some(
      r => !this.isTerminalAgentRunStatus(r.status)
    );
  },
  // 子组件 prop 视图：聚合当前 run + UI 状态
  currentRunView() {
    const run = this.currentAgentRun;
    if (!run) return null;
    return {
      ...run,
      isSending: this.isSending,
      isStreaming: this.isStreaming,
      isThinking: this.isThinking,
      thinkingExpanded: this.thinkingExpanded,
    };
  },
  // 派生字段（替代删除的全局字段）
  agentSteps() { return this.currentAgentRun?.steps ?? []; },
  thinkingSegments() { return this.currentAgentRun?.thinkingSegments ?? []; },
  thinkingContent() { return this.currentAgentRun?.thinkingContent ?? ""; },
  botMsgId() { return this.currentAgentRun?.botMsgId ?? null; },
  tokenUsage() { return this.currentAgentRun?.tokenUsage ?? null; },
  currentSessionCode() { return this.currentAgentRun?.sessionCode ?? null; },
  currentSessionSceneType() { return this.currentAgentRun?.sceneType ?? null; },
}
```

> 注：`agentSteps` 等改为 computed 后，模板和子组件 prop 绑定无需改动（Vue computed 可作为响应式属性被读取）。所有原本写 `this.agentSteps = ...` 的位置必须改为写 `currentAgentRun.steps`（通过方法路由，见 §5）。

## 5. SSE 事件分发统一入口

引入统一路由方法，所有 SSE handler 先按 `payload.runId` 找到（或创建）对应 RunState，再调用纯函数 apply 到该 run：

```ts
methods: {
  // 路由：按 runId 取 run，不存在时按需创建
  resolveRunForEvent(payload: { runId?: any }, options: { createIfMissing?: boolean } = {}): RunState | null {
    const runId = payload?.runId != null ? String(payload.runId) : null;
    if (!runId) return this.currentAgentRun;  // 无 runId 时降级到当前会话
    const existing = this.activeAgentRuns[runId];
    if (existing) return existing;
    if (options.createIfMissing && this.currentAgentRun == null) {
      // 仅 ack/step 事件允许新建 run entry
      return this.createRunEntry({ runId });
    }
    return null;  // 未知 run 的事件丢弃
  },

  createRunEntry(init: Partial<RunState>): RunState {
    const run: RunState = {
      runId: String(init.runId),
      chatSessionId: init.chatSessionId ?? this.activeChatSessionId,
      sessionCode: init.sessionCode ?? null,
      botMsgId: init.botMsgId ?? null,
      status: init.status ?? "pending",
      requestText: init.requestText ?? "",
      sceneType: init.sceneType ?? "agent",
      sourceType: init.sourceType ?? "chat",
      finishReceived: false,
      steps: [],
      thinkingSegments: [],
      thinkingContent: "",
      tokenUsage: null,
    };
    this.$set(this.activeAgentRuns, run.runId, run);
    return run;
  },

  removeRunEntry(runId: string) {
    this.$delete(this.activeAgentRuns, runId);
  },
}
```

### 5.1 三个 SSE 过滤位置改造

| 位置 | 旧逻辑 | 新逻辑 |
|---|---|---|
| `handleThinkingEvent` L1424 | `runId !== activeAgentRun.runId → return` | `const run = resolveRunForEvent(payload); if (!run) return;` 后续写 `run.thinkingSegments` |
| `upsertStreamingBotMessage` L1836 | 同上 | 同上，写 `run.botMsgId` 等 |
| `handleAgentEvent` L2020 | 同上 | 同上 |

### 5.2 四个直接写入方法改造

`applyAgentStepEvent` / `applyFinishPayload` / `applyAgentRunDetail` / `applyAgentExecuteAck` 显式接收 `run: RunState` 参数（或从 eventPayload 提取 runId 后调用 `resolveRunForEvent`），所有写入改为 `run.xxx = ...`，不再写 `this.activeAgentRun.xxx`。

### 5.3 chatSessionId 创建时固定

`createRunEntry` 时确定 `chatSessionId` 后，**不再**因后续 payload 反向同步（原 L1497-1499, L1884-1886, L2117-2119, L2267-2269 全部删除反向同步逻辑）。payload 中的 chatSessionId 仅用于首次创建 entry 时兜底。

## 6. 持久化（per-run）

### 6.1 key 规则

```
旧: fitmate:active-run:<userKey>           （单 key）
新: fitmate:active-run:<userKey>:<runId>    （每 run 独立 key）
```

### 6.2 方法改造

```ts
// 写入单个 run 的快照
snapshotRunState(runId: string) {
  const key = this.getRunStorageKey(runId);
  if (!key) return;
  const run = this.activeAgentRuns[runId];
  if (!run || this.isTerminalAgentRunStatus(run.status)) {
    window.sessionStorage.removeItem(key);
    return;
  }
  window.sessionStorage.setItem(key, JSON.stringify({ version: 3, ...run }));
},

// 恢复：遍历前缀重建整个 Map
restoreActiveAgentRuns() {
  const prefix = this.getRunStorageKeyPrefix();
  if (!prefix || typeof window === "undefined") return;
  for (let i = window.sessionStorage.length - 1; i >= 0; i--) {
    const key = window.sessionStorage.key(i);
    if (!key || !key.startsWith(prefix)) continue;
    const raw = window.sessionStorage.getItem(key);
    if (!raw) continue;
    try {
      const snap = JSON.parse(raw);
      if (!snap.runId || this.isTerminalAgentRunStatus(snap.status)) {
        window.sessionStorage.removeItem(key);
        continue;
      }
      this.$set(this.activeAgentRuns, String(snap.runId), this.hydrateRunState(snap));
    } catch (e) { window.sessionStorage.removeItem(key); }
  }
  // 不在此处切换 activeChatSessionId；由路由恢复流程决定
},

// 清空：传 runId 删单个；不传清整个 Map（logout 用）
clearActiveAgentRun(runId?: string) {
  if (runId) {
    const key = this.getRunStorageKey(runId);
    if (key) window.sessionStorage.removeItem(key);
    this.$delete(this.activeAgentRuns, runId);
  } else {
    const prefix = this.getRunStorageKeyPrefix();
    if (prefix && typeof window !== "undefined") {
      for (let i = window.sessionStorage.length - 1; i >= 0; i--) {
        const key = window.sessionStorage.key(i);
        if (key && key.startsWith(prefix)) window.sessionStorage.removeItem(key);
      }
    }
    this.activeAgentRuns = {};
  }
},
```

### 6.3 snapshot 写入调用点改造

原 7 处 `snapshotActiveAgentRun()` 调用全部改为 `snapshotRunState(run.runId)`，由 apply 方法显式传入 runId。

## 7. 用户操作行为

| 操作 | 新行为 |
|---|---|
| **新建聊天** | 去掉 `:disabled` 中的 `hasPendingAgentRun()`；`handleCreateChat` 不再 `clearActiveAgentRun()`，只重置当前会话视图（chatList/activeChatSessionId=null 等），保留 Map 中其他 run |
| **切换会话记录** | 去掉阻止；加载目标会话历史；`activeChatSessionId` 切换后 `currentAgentRun` computed 自动重指向该会话的 run（若有）；若无 Map entry 但服务器侧仍在跑，按需 `silentFetchAgentRunDetail` 重建 |
| **发新消息** | 不再被 `hasPendingAgentRun()` 阻止；后端 slot 锁兜底，达上限返回 429 |
| **取消（stopGeneration）** | 取消 `currentAgentRun.runId`（当前会话焦点 run），不影响其他 run |
| **logout** | `clearActiveAgentRun()` 清整个 Map |

## 8. 子组件 prop 契约变更

### 8.1 ChatPage.vue → ChatMessageList.vue

```diff
- :agent-steps="agentSteps"
- :active-agent-run="activeAgentRun"
- :thinking-content="thinkingContent"
- :thinking-segments="thinkingSegments"
+ :active-agent-run="currentRunView"
```

`ChatMessageList.vue` 内部从 `activeAgentRun` prop 取 `steps` / `thinkingContent` / `thinkingSegments`，删除对应的 4 个独立 prop。

### 8.2 ChatPage.vue → ChatInput.vue

`tokenUsage` 改为 `currentAgentRun?.tokenUsage`，prop 名不变。

### 8.3 ChatMessageList.vue → AgentStepCard.vue / ReasoningTraceBlock.vue

`AgentStepCard` 接收 `steps` / `thinkingContent` / `thinkingSegments`，来源从 `activeAgentRun` prop 内取，不变。

`ReasoningTraceBlock` 接收消息级 `item.thinkingSegments` 等，不变（消息级状态保留）。

## 9. UI 状态（isSending / isStreaming / isThinking）处理

这三个字段控制输入框禁用、流式光标等，是"当前会话视图"的 UI 状态，不属于单个 run。

**保留为全局字段**，但在 `currentAgentRun` 变化时（切换会话）按需重置：

```ts
watch: {
  activeChatSessionId(newVal, oldVal) {
    if (newVal !== oldVal) {
      // 切换会话：重置 UI 状态为新会话的空闲态
      const run = this.currentAgentRun;
      this.isSending = run != null && !this.isTerminalAgentRunStatus(run.status);
      this.isStreaming = false;  // 切换后流式状态由后续 SSE 事件恢复
      this.isThinking = false;
    }
  },
},
```

> 取舍：`isSending`/`isStreaming`/`isThinking` 完全 per-run 化会触及 SSE handler 和模板 20+ 处，收益有限。保留为全局 UI 状态 + 切换会话时重置，是更小的改动面。若切换回的 run 仍在流式，SSE 事件会立即把 `isStreaming` 设回 true。

## 10. 终态与清理

### 10.1 `isTerminalAgentRunStatus` 补全

```ts
isTerminalAgentRunStatus(status) {
  const s = this.normalizeAgentRunStatus(status);
  return s === "success" || s === "failed" || s === "cancelled" || s === "interrupted";
},
```

### 10.2 `applyFinishPayload` 修复 interrupted

原 L2276-2283 仅判 `failed` 否则一律 `success`，改为：

```ts
const normalized = this.normalizeAgentRunStatus(payload.status);
if (normalized === "failed") run.status = "failed";
else if (normalized === "interrupted" || normalized === "cancelled") run.status = normalized;
else run.status = "success";
run.finishReceived = true;
```

### 10.3 终态副作用

终态后**立即**从 Map 移除并删 sessionStorage key，依赖历史消息加载兜底显示最终结果（`refreshChatRecordsIfNeeded` 已存在）。

- `snapshotRunState(run.runId)` → 检测终态自动删 sessionStorage key
- `removeRunEntry(run.runId)` → 从 Map 移除
- 若被移除的是 currentAgentRun，computed 自动重指向 null
- `refreshChatRecordsIfNeeded()` 刷新侧栏会话列表

## 11. 实施阶段

每阶段独立可验证，按顺序执行：

### 阶段 1：数据结构与 computed 派生（不破坏现有功能）
- 新增 `RunState` interface（注释或 d.ts）
- 新增 `activeAgentRuns` data 字段
- 新增 computed：`currentAgentRun` / `hasPendingRunInCurrentSession` / `hasAnyPendingRun` / `currentRunView` / 派生的 `agentSteps` 等
- 暂保留 `activeAgentRun` 等旧字段，与 computed 并存（此阶段旧字段仍是写入目标）
- 验证：现有功能不受影响

### 阶段 2：SSE 事件分发改造
- 引入 `resolveRunForEvent` / `createRunEntry` / `removeRunEntry`
- 改造 3 处过滤 + 4 处直接写入方法，全部走 `resolveRunForEvent`
- 此阶段 `activeAgentRun` 旧字段由 `currentAgentRun` 别名同步（临时桥接）
- 验证：单 run 场景行为不变

### 阶段 3：写入路径迁移
- 所有 `this.agentSteps = ...` / `this.thinkingSegments = ...` / `this.botMsgId = ...` / `this.tokenUsage = ...` / `this.currentSessionCode = ...` 改为写 `run.xxx`
- 删除 `agentSteps` / `thinkingSegments` / `botMsgId` / `tokenUsage` / `currentSessionCode` / `currentSessionSceneType` / `activeAgentRun` / `agentStepEventReceived` 全局字段
- 验证：单 run 场景行为不变

### 阶段 4：持久化改造
- `getRunStorageKey(runId)` / `getRunStorageKeyPrefix()`
- `snapshotRunState` / `restoreActiveAgentRuns` / `clearActiveAgentRun(runId?)`
- 替换所有 `snapshotActiveAgentRun` 调用
- 验证：刷新页面后 run 状态恢复

### 阶段 5：用户操作放开
- ChatPage.vue L18 移除 `:disabled` 中的 `hasPendingAgentRun()`
- `handleCreateChat` / `handleSelectChatSession` 移除阻止与 `clearActiveAgentRun()`
- 验证：任务运行时可新建/切换

### 阶段 6：终态修复 + 子组件契约
- `isTerminalAgentRunStatus` 补全 cancelled/interrupted
- `applyFinishPayload` 修复 interrupted
- ChatMessageList prop 合并为 `activeAgentRun`（接收 `currentRunView`）
- 验证：取消/中断状态正确；子组件渲染正常

### 阶段 7：并发验证
- 3 个并发 run 实时追踪
- 切换会话焦点切换正确
- 取消单个 run 不影响其他
- 历史消息加载兜底

## 12. 风险与边界

1. **响应式**：`activeAgentRuns` 是普通对象，新增/删除属性必须用 `this.$set` / `this.$delete`（Vue 2）保证响应式。已在新方法中体现。
2. **SSE 事件乱序**：run entry 创建前可能收到 step 事件。`resolveRunForEvent` 在 `createIfMissing` 时兜底创建空骨架，后续 ack/detail 事件会补全字段。
3. **chatSessionId 创建时固定**：`createRunEntry` 时用 ack 的 chatSessionId 确定，之后不再因后续 payload 改变（删除原反向同步逻辑）。若 ack 的 chatSessionId 与前端 `activeChatSessionId` 不一致，以 ack 为准并切换 `activeChatSessionId`。
4. **刷新恢复**：`restoreActiveAgentRuns` 重建 Map 后，若服务器侧 run 已终态，由 `silentFetchAgentRunDetail` 拉取最新状态覆盖。
5. **内存**：终态 run 立即从 Map 移除，最多同时存在 3 个 entry（后端 slot 上限）。
6. **applyServerSessionMeta**（L774）：原无条件写全局 `activeChatSessionId`，改为仅在 ack/恢复路径调用，避免后台 run 的事件干扰当前会话指针。

## 13. 验证清单

- [ ] 单 run 发起 → 实时 step/thinking 流式正常
- [ ] 单 run 完成 → 状态 success，Map 移除，历史消息刷新
- [ ] 单 run 取消 → 状态 cancelled/interrupted，Map 移除
- [ ] 单 run 失败 → 状态 failed，Map 移除
- [ ] 任务运行时新建聊天 → 不再禁用，新会话可发新任务
- [ ] A 会话发任务 → 切到 B 会话发任务 → 两者并发跑，互不影响
- [ ] 切回 A 会话 → currentAgentRun 重指向 A 的 run，实时事件继续显示
- [ ] 3 个并发 → 第 4 个被后端拒绝（429）
- [ ] 刷新页面 → 多 run 状态从 sessionStorage 恢复
- [ ] logout → 整个 Map 清空
- [ ] 子组件 AgentStepCard / ReasoningTraceBlock 渲染正常
