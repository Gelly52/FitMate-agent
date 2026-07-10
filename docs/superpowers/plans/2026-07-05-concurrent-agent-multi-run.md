# 并发 Agent 多 run 追踪实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 ChatLogicBase.vue 的单值 `activeAgentRun` 追踪重构为 `Map<runId, RunState>`，支持后端 3 并发 slot 锁下的多 run 实时追踪与切换。

**Architecture:** 引入 `activeAgentRuns` 字典作为单一来源，`currentAgentRun` computed 按 `activeChatSessionId` 派生焦点 run。所有 per-run 状态（steps/thinkingSegments/botMsgId/tokenUsage/sessionCode 等）收入 RunState，删除全局冗余字段。SSE 事件统一经 `resolveRunForEvent` 路由。持久化按 runId 独立 key。

**Tech Stack:** Vue 2 + TypeScript，无前端单测，验证靠 `npm run dev` 编译 + 运行时手动验证。

**Spec:** `docs/superpowers/specs/2026-07-05-concurrent-agent-multi-run-design.md`

---

## 文件结构

| 文件 | 责任 | 改动类型 |
|---|---|---|
| `FitMate-frontend/src/pages/chat/ChatLogicBase.vue` | 核心逻辑，承载 RunState Map、SSE 路由、持久化 | 大改 |
| `FitMate-frontend/src/pages/chat/ChatPage.vue` | 模板，新建按钮禁用、子组件 prop 传递 | 中改 |
| `FitMate-frontend/src/pages/chat/components/ChatMessageList.vue` | 消息列表，prop 契约合并 | 小改 |

---

## Task 1: 新增 RunState 数据结构与 computed 派生层

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue` data() (L33-121) 与 computed (L123+)

**目标：** 引入 `activeAgentRuns` 字典与 computed 派生层，此阶段保留旧字段并存，不破坏现有功能。

- [ ] **Step 1: 在 data() 新增 `activeAgentRuns` 字段**

在 `ChatLogicBase.vue` data() 的 `activeAgentRun: null,` 行下方新增：

```ts
activeAgentRun: null,
// 多 run 追踪表：按 runId 索引，每个 entry 自带完整 per-run 状态
activeAgentRuns: {} as Record<string, any>,
```

- [ ] **Step 2: 在 computed 新增 `currentAgentRun` 与派生字段**

在 computed 块内（`canCompressContext` 之前）新增：

```ts
// 当前会话对应的 run；切换会话时自动重指向
currentAgentRun(): any | null {
  if (this.activeChatSessionId == null) return null;
  const runs = this.activeAgentRuns || {};
  return Object.values(runs).find(
    (r: any) => r && r.chatSessionId === this.activeChatSessionId
  ) || null;
},
// 当前会话是否有运行中 run
hasPendingRunInCurrentSession(): boolean {
  const run = this.currentAgentRun;
  return run != null && !this.isTerminalAgentRunStatus(run.status);
},
// 任意会话是否有运行中 run（用于全局提示，不再阻止操作）
hasAnyPendingRun(): boolean {
  const runs = this.activeAgentRuns || {};
  return Object.values(runs).some(
    (r: any) => r && !this.isTerminalAgentRunStatus(r.status)
  );
},
// 子组件 prop 视图：聚合当前 run + UI 状态
currentRunView(): any | null {
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
```

- [ ] **Step 3: 编译验证**

Run: `cd FitMate-frontend && npm run dev`（或项目对应命令）
Expected: 无编译错误，应用正常启动，现有功能不受影响（旧字段仍存在）

- [ ] **Step 4: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "feat(chat): 引入 activeAgentRuns Map 与 currentAgentRun computed 派生层"
```

---

## Task 2: 引入 run entry 管理方法

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue` methods（在 `clearActiveAgentRun` 方法附近，约 L943）

**目标：** 新增 `createRunEntry` / `removeRunEntry` / `resolveRunForEvent` 方法，为 SSE 路由做准备。此阶段不替换现有调用。

- [ ] **Step 1: 新增三个方法**

在 `clearActiveAgentRun` 方法（约 L943）之前新增：

```ts
/**
 * 按 runId 取 run entry；不存在时按需创建。
 * @param payload SSE 事件载荷，至少含 runId
 * @param options.createIfMissing 为 true 时，若无 runId 则降级到 currentAgentRun，若仍无则新建空骨架
 */
resolveRunForEvent(payload: any, options: { createIfMissing?: boolean } = {}): any | null {
  const runId = payload && payload.runId != null ? String(payload.runId) : null;
  if (!runId) {
    return this.currentAgentRun;
  }
  const existing = (this.activeAgentRuns || {})[runId];
  if (existing) return existing;
  if (options.createIfMissing) {
    return this.createRunEntry({ runId });
  }
  return null;
},

/**
 * 创建 run entry 并放入 Map。chatSessionId 创建时固定。
 */
createRunEntry(init: any): any {
  const runId = String(init.runId);
  const run = {
    runId: runId,
    chatSessionId: init.chatSessionId != null ? init.chatSessionId : this.activeChatSessionId,
    sessionCode: init.sessionCode != null ? String(init.sessionCode) : null,
    botMsgId: init.botMsgId != null ? String(init.botMsgId) : null,
    status: init.status || "pending",
    requestText: init.requestText || "",
    sceneType: init.sceneType || "agent",
    sourceType: init.sourceType || "chat",
    finishReceived: false,
    steps: [],
    thinkingSegments: [],
    thinkingContent: "",
    tokenUsage: null,
  };
  this.$set(this.activeAgentRuns, runId, run);
  return run;
},

/**
 * 从 Map 移除 run entry。
 */
removeRunEntry(runId: string) {
  if (!runId) return;
  this.$delete(this.activeAgentRuns, runId);
},
```

- [ ] **Step 2: 编译验证**

Run: `cd FitMate-frontend && npm run dev`
Expected: 无编译错误，应用正常启动

- [ ] **Step 3: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "feat(chat): 新增 createRunEntry/removeRunEntry/resolveRunForEvent 方法"
```

---

## Task 3: 改造 SSE 事件分发（3 处过滤点）

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`
  - `handleThinkingEvent` (约 L1424-1431)
  - `upsertStreamingBotMessage` (约 L1836-1843)
  - `handleAgentEvent` (约 L2020-2027)

**目标：** 把"非当前 run 即丢弃"改为按 runId 路由到对应 RunState entry。此阶段仍写旧字段（兼容），同时写 run entry。

- [ ] **Step 1: 改造 `handleThinkingEvent` 的 runId 过滤**

定位 `handleThinkingEvent` 中的过滤块（约 L1424-1431），原代码：

```ts
if (
  this.activeAgentRun &&
  this.activeAgentRun.runId != null &&
  String(payload.runId) !== String(this.activeAgentRun.runId)
) {
  return;
}
```

改为：

```ts
const run = this.resolveRunForEvent(payload);
if (!run) return;
```

后续方法体内所有 `this.activeAgentRun.xxx = ...` 改为 `run.xxx = ...`（涉及 L1493-1506 的反向同步字段：runId/chatSessionId/sessionCode/botMsgId）。**删除这些反向同步行**（chatSessionId 创建时固定，不再随 payload 改变），仅保留必要的 `run.thinkingContent` / `run.thinkingSegments` 写入。

同步：在写入 run 后，临时桥接 `this.activeAgentRun = run;`（仅当 run === currentAgentRun 时），保持旧字段兼容。此桥接在 Task 5 删除。

- [ ] **Step 2: 改造 `upsertStreamingBotMessage` 的 runId 过滤**

定位 `upsertStreamingBotMessage`（约 L1836-1843），同样改为：

```ts
const run = this.resolveRunForEvent(payload);
if (!run) return;
```

后续 `this.activeAgentRun.xxx` 写入改为 `run.xxx`，删除 L1884-1886 的 chatSessionId 反向同步。保留同样的临时桥接 `this.activeAgentRun = run;`。

- [ ] **Step 3: 改造 `handleAgentEvent` 的 runId 过滤**

定位 `handleAgentEvent`（约 L2020-2027），同样改为：

```ts
const run = this.resolveRunForEvent(payload);
if (!run) return;
```

后续调用 `applyAgentStepEvent(eventPayload)` 改为 `applyAgentStepEvent(eventPayload, run)`，传入 run。临时桥接 `this.activeAgentRun = run;`。

- [ ] **Step 4: 编译验证 + 运行时单 run 验证**

Run: `cd FitMate-frontend && npm run dev`
验证：
1. 发起单个 Agent 任务，实时 step/thinking 流式正常显示
2. 任务完成后状态正确，无控制台报错

- [ ] **Step 5: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "refactor(chat): SSE 事件分发改为按 runId 路由到 RunState entry"
```

---

## Task 4: 改造四个直接写入方法

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`
  - `applyAgentExecuteAck` (约 L1321-1357)
  - `applyAgentRunDetail` (约 L1212-1299)
  - `applyAgentStepEvent` (约 L2037-2197)
  - `applyFinishPayload` (约 L2201-2302)

**目标：** 四个方法显式接收 `run` 参数，所有写入改为 `run.xxx`，不再写 `this.activeAgentRun.xxx`。

- [ ] **Step 1: 改造 `applyAgentExecuteAck`**

方法签名改为 `applyAgentExecuteAck(payload: any, run?: any)`。方法体开头：

```ts
applyAgentExecuteAck(payload: any, run?: any) {
  if (!run) {
    run = this.resolveRunForEvent(payload, { createIfMissing: true });
  }
  if (!run) return false;
  // 用 ack 字段补全 run entry（chatSessionId 若 run 已有则不覆盖）
  if (run.chatSessionId == null && payload.chatSessionId != null) {
    run.chatSessionId = payload.chatSessionId;
    // 若与当前 activeChatSessionId 不一致，以 ack 为准
    if (this.activeChatSessionId !== run.chatSessionId) {
      this.activeChatSessionId = run.chatSessionId;
    }
  }
  run.sessionCode = payload.sessionCode ? String(payload.sessionCode) : run.sessionCode;
  run.botMsgId = payload.botMsgId ? String(payload.botMsgId) : run.botMsgId;
  run.status = "running";
  run.sceneType = this.resolveExpectedSessionSceneType() || "agent";
  // ... 后续逻辑（snapshot 等）改为调用 this.snapshotRunState(run.runId)
  this.activeAgentRun = run;  // 临时桥接，Task 5 删除
  return true;
}
```

删除原 L1330-1349 的 `this.activeAgentRun = { ... }` 整体覆盖。删除 L1354 的 `this.botMsgId = this.activeAgentRun.botMsgId`（全局 botMsgId 将在 Task 5 删除）。`snapshotActiveAgentRun()` 调用改为 `this.snapshotRunState(run.runId)`（方法在 Task 6 实现，此步先调用，会报错则临时保留旧调用并加注释 TODO）。

> 注：`snapshotRunState` 在 Task 6 才实现。此 Task 内先保留 `this.snapshotActiveAgentRun()` 旧调用，待 Task 6 统一替换。

- [ ] **Step 2: 改造 `applyAgentRunDetail`**

方法签名改为 `applyAgentRunDetail(detail: any, run?: any)`。开头：

```ts
applyAgentRunDetail(detail: any, run?: any) {
  if (!run) {
    run = this.resolveRunForEvent({ runId: detail.runId }, { createIfMissing: true });
  }
  if (!run) return;
  // 用 detail 补全 run 字段
  run.runId = String(detail.runId);
  if (run.chatSessionId == null && detail.chatSessionId != null) {
    run.chatSessionId = detail.chatSessionId;
  }
  run.sessionCode = detail.sessionCode ? String(detail.sessionCode) : run.sessionCode;
  run.botMsgId = detail.botMsgId ? String(detail.botMsgId) : run.botMsgId;
  run.requestText = detail.requestText || run.requestText;
  const normalizedStatus = this.normalizeAgentRunStatus(detail.status);
  run.status = normalizedStatus;
  run.sceneType = "agent";
  // steps 处理：写 run.steps 而非 this.agentSteps
  const steps = this.collectAgentTraceItems(detail);
  run.steps = Array.isArray(steps) ? steps : [];
  // ... 终态判断、guidanceMessage 等保留
  this.activeAgentRun = run;  // 临时桥接，Task 5 删除
}
```

删除原 L1272-1279 的 `this.agentSteps = ...` 与 `this.activeAgentRun.steps = ...` 双写。删除 L1283-1292 的全局字段同步（`this.activeChatSessionId` / `this.currentSessionCode` / `this.currentSessionSceneType` / `this.botMsgId`）—— 这些将在 Task 5 由 computed 派生。

- [ ] **Step 3: 改造 `applyAgentStepEvent`**

方法签名改为 `applyAgentStepEvent(eventPayload: any, run?: any)`。开头：

```ts
applyAgentStepEvent(eventPayload: any, run?: any) {
  if (!run) {
    run = this.resolveRunForEvent(eventPayload, { createIfMissing: true });
  }
  if (!run) return;
  // 补全 run 字段（不覆盖已有值）
  if (run.botMsgId == null && eventPayload.botMsgId) run.botMsgId = String(eventPayload.botMsgId);
  if (run.sessionCode == null && eventPayload.sessionCode) run.sessionCode = String(eventPayload.sessionCode);
  // steps 操作改为 run.steps
  // 原 this.agentSteps[matchedIndex] / push / slice().sort() 改为 run.steps
  // 原 L2127 this.activeAgentRun.steps = this.agentSteps.slice() 删除（run.steps 即来源）
  // 终态：run.status = "failed"/"success"
  // 临时桥接 this.activeAgentRun = run;
}
```

具体改写 L2043-2094 的 steps 操作（matchedIndex 查找、push、sort）全部从 `this.agentSteps` 改为 `run.steps`。L2158 的 `targetMsg.agentSteps = this.agentSteps.slice()` 改为 `targetMsg.agentSteps = run.steps.slice()`。L2160 的 `targetMsg.thinkingSegments` 同理改为从 run 取。

- [ ] **Step 4: 改造 `applyFinishPayload`**

方法签名改为 `applyFinishPayload(payload: any, run?: any)`。开头：

```ts
applyFinishPayload(payload: any, run?: any) {
  if (!run) {
    run = this.resolveRunForEvent(payload);
  }
  // run 可能为 null（如 finish 来自非追踪 run），降级处理
  const runId = run ? run.runId : (payload.runId != null ? String(payload.runId) : null);
  // 补全 run 字段
  if (run) {
    if (run.botMsgId == null && payload.botMsgId) run.botMsgId = String(payload.botMsgId);
    if (run.sessionCode == null && payload.sessionCode) run.sessionCode = String(payload.sessionCode);
    if (payload.chatSessionId != null && run.chatSessionId == null) run.chatSessionId = payload.chatSessionId;
    // 状态：修复 interrupted/cancelled 识别
    const normalized = this.normalizeAgentRunStatus(payload.status);
    if (normalized === "failed") run.status = "failed";
    else if (normalized === "interrupted" || normalized === "cancelled") run.status = normalized;
    else run.status = "success";
    run.finishReceived = true;
    if (payload.usage) run.tokenUsage = payload.usage;
  }
  // ... 后续 chatItem 处理、SSE 发送等保留
  // 终态后移除 entry（在 Task 6 配合 snapshotRunState 实现，此步先调用 this.removeRunEntry(runId)）
  if (runId && run && this.isTerminalAgentRunStatus(run.status)) {
    this.removeRunEntry(runId);
  }
  this.activeAgentRun = run;  // 临时桥接，Task 5 删除（注意 run 可能已移除，桥接置 null）
}
```

删除原 L2276-2283 的状态判断（已被上面的修复版替代）。删除 L2299 的 `this.botMsgId = null`（全局字段 Task 5 删除）。

- [ ] **Step 5: 编译验证 + 运行时单 run 验证**

Run: `cd FitMate-frontend && npm run dev`
验证：
1. 发起单个 Agent 任务，流式正常
2. 任务完成 → run 从 Map 移除，currentAgentRun 变 null
3. 任务取消 → interrupted 状态正确
4. 刷新历史会话 → run detail 加载正常

- [ ] **Step 6: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "refactor(chat): 四个 apply 方法改为接收 run 参数，写入 RunState entry"
```

---

## Task 5: 删除全局冗余字段，全面 computed 化

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`
  - data() (L33-121)
  - computed (新增派生字段)
  - 所有写入全局字段的位置（散落 30+ 处）

**目标：** 删除 `activeAgentRun` / `agentSteps` / `thinkingSegments` / `thinkingContent` / `botMsgId` / `tokenUsage` / `currentSessionCode` / `currentSessionSceneType` / `agentStepEventReceived` 全局字段，改为 computed 派生。

- [ ] **Step 1: computed 新增派生字段**

在 computed 块内 Task 1 新增的 `currentRunView` 之后追加：

```ts
// 派生字段（替代删除的全局字段）——读取 currentAgentRun
agentSteps(): any[] {
  const run = this.currentAgentRun;
  return run ? run.steps : [];
},
thinkingSegments(): any[] {
  const run = this.currentAgentRun;
  return run ? run.thinkingSegments : [];
},
thinkingContent(): string {
  const run = this.currentAgentRun;
  return run ? run.thinkingContent : "";
},
botMsgId(): any {
  const run = this.currentAgentRun;
  return run ? run.botMsgId : null;
},
tokenUsage(): any {
  const run = this.currentAgentRun;
  return run ? run.tokenUsage : null;
},
currentSessionCode(): any {
  const run = this.currentAgentRun;
  return run ? run.sessionCode : null;
},
currentSessionSceneType(): any {
  const run = this.currentAgentRun;
  return run ? run.sceneType : null;
},
```

- [ ] **Step 2: data() 删除全局字段**

从 data() 删除：`botMsgId` (L35), `currentSessionCode` (L43), `currentSessionSceneType` (L44), `tokenUsage` (L56), `activeAgentRun` (L74), `agentStepEventReceived` (L75), `agentSteps` (L94), `thinkingContent` (L95), `thinkingSegments` (L96-100)。

> 保留：`activeChatSessionId` (L42), `isSending`/`isStreaming`/`isThinking`/`thinkingExpanded`（UI 状态，见 §9）, `chatList`, `knowledgeSources` 等非 run 字段。

- [ ] **Step 3: 全局搜索删除所有 `this.<字段> = ...` 写入**

对每个删除的字段，全局搜索 `this.botMsgId =` / `this.agentSteps =` / `this.thinkingSegments =` / `this.thinkingContent =` / `this.tokenUsage =` / `this.currentSessionCode =` / `this.currentSessionSceneType =` / `this.activeAgentRun =` / `this.agentStepEventReceived =`，逐一处理：

- 若是写入操作：改为写 `run.xxx`（多数已在 Task 3/4 完成，此步查漏补缺）
- 若是 `clearActiveAgentRun` / `handleCreateChat` / `handleSelectChatSession` / `doChat` 中的清空操作：删除该行（computed 自动派生 null）
- 临时桥接 `this.activeAgentRun = run;` 全部删除

特别注意 `handleThinkingEvent` (L1444-1449) 的 `this.thinkingContent = ...` 和 `this.thinkingSegments = ...` 改为 `run.thinkingContent = ...` / `run.thinkingSegments = ...`。

`startThinkingSegment` (L1646-1668) / `finishThinkingSegment` (L1670-1689) 内的 `this.thinkingSegments` 改为 `run.thinkingSegments`，需要传入 run 参数（或在调用处传入）。

`handleCompressEvent` (L1974-1991) 的 `this.tokenUsage = ...` 改为 `run.tokenUsage = ...`（需先 resolveRunForEvent）。

`doChat` (L3142) 的 `this.botMsgId = botMsgId` 改为在 `applyAgentExecuteAck` 内设置 `run.botMsgId`（doChat 不再直接写）。

- [ ] **Step 4: 删除 `clearActiveAgentRun` 内对已删字段的引用**

`clearActiveAgentRun` (L943-954) 简化为（runId 参数在 Task 6 完整实现）：

```ts
clearActiveAgentRun(options?: any) {
  // Task 6 会改造为按 runId 删单个 entry；此步先清空整个 Map
  this.activeAgentRuns = {};
  this.agentStepEventReceived = false;  // 此行也删除，字段已删
}
```

实际此步直接简化为：

```ts
clearActiveAgentRun(options?: any) {
  this.activeAgentRuns = {};
}
```

- [ ] **Step 5: 编译验证 + 运行时单 run 验证**

Run: `cd FitMate-frontend && npm run dev`
验证：
1. 发起单个 Agent 任务，全流程正常（流式、step、thinking、完成）
2. 新建聊天 → 当前会话视图清空，无报错
3. 切换会话记录 → 正常加载历史
4. 控制台无 "undefined" 或响应式警告

- [ ] **Step 6: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "refactor(chat): 删除全局冗余字段，统一由 currentAgentRun computed 派生"
```

---

## Task 6: 持久化改造（per-run sessionStorage）

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`
  - `getActiveAgentRunStorageKey` (L869-875) → 改为 `getRunStorageKey` + `getRunStorageKeyPrefix`
  - `buildActiveAgentRunSnapshot` (L899-930) → 删除（RunState 即快照）
  - `snapshotActiveAgentRun` (L931-942) → 改为 `snapshotRunState(runId)`
  - `restoreActiveAgentRun` (L955-1024) → 改为 `restoreActiveAgentRuns`
  - `clearActiveAgentRun` (L943-954) → 改为支持 runId 参数
  - created() 钩子 (L213) → 改为调用 `restoreActiveAgentRuns`

**目标：** 每个 run 独立 sessionStorage key，刷新后重建整个 Map。

- [ ] **Step 1: 改造 storage key 方法**

替换 `getActiveAgentRunStorageKey` (L869-875) 为：

```ts
/**
 * 单个 run 的 sessionStorage key。
 */
getRunStorageKey(runId: string): string | null {
  const userKey = this.resolveStableUserKey();
  if (!userKey || !runId) return null;
  return "fitmate:active-run:" + String(userKey) + ":" + String(runId);
},

/**
 * 当前用户的 run storage key 前缀（用于遍历清理/恢复）。
 */
getRunStorageKeyPrefix(): string | null {
  const userKey = this.resolveStableUserKey();
  if (!userKey) return null;
  return "fitmate:active-run:" + String(userKey) + ":";
},
```

- [ ] **Step 2: 删除 `buildActiveAgentRunSnapshot`，替换 `snapshotActiveAgentRun`**

删除 `buildActiveAgentRunSnapshot` (L899-930)。`snapshotActiveAgentRun` (L931-942) 替换为：

```ts
/**
 * 写入单个 run 的快照；终态时自动删除 key。
 */
snapshotRunState(runId: string) {
  const key = this.getRunStorageKey(runId);
  if (!key || typeof window === "undefined") return;
  const run = (this.activeAgentRuns || {})[runId];
  if (!run || this.isTerminalAgentRunStatus(run.status)) {
    window.sessionStorage.removeItem(key);
    return;
  }
  window.sessionStorage.setItem(key, JSON.stringify({ version: 3, ...run }));
},
```

- [ ] **Step 3: 替换 `restoreActiveAgentRun` 为 `restoreActiveAgentRuns`**

替换 `restoreActiveAgentRun` (L955-1024) 为：

```ts
/**
 * 遍历 sessionStorage 前缀重建整个 activeAgentRuns Map。
 * 不切换 activeChatSessionId；由路由恢复流程决定。
 */
restoreActiveAgentRuns() {
  const prefix = this.getRunStorageKeyPrefix();
  if (!prefix || typeof window === "undefined") return;
  for (let i = window.sessionStorage.length - 1; i >= 0; i--) {
    const key = window.sessionStorage.key(i);
    if (!key || key.indexOf(prefix) !== 0) continue;
    const raw = window.sessionStorage.getItem(key);
    if (!raw) continue;
    try {
      const snap = JSON.parse(raw);
      if (!snap.runId || this.isTerminalAgentRunStatus(snap.status)) {
        window.sessionStorage.removeItem(key);
        continue;
      }
      // hydrate：补全字段
      const run = {
        runId: String(snap.runId),
        chatSessionId: snap.chatSessionId != null ? snap.chatSessionId : null,
        sessionCode: snap.sessionCode || null,
        botMsgId: snap.botMsgId || null,
        status: snap.status,
        requestText: snap.requestText || "",
        sceneType: snap.sceneType || "agent",
        sourceType: snap.sourceType || "chat",
        finishReceived: !!snap.finishReceived,
        steps: Array.isArray(snap.steps) ? snap.steps : (Array.isArray(snap.traceNodes) ? snap.traceNodes : []),
        thinkingSegments: Array.isArray(snap.thinkingSegments) ? snap.thinkingSegments : [],
        thinkingContent: snap.thinkingContent || "",
        tokenUsage: snap.tokenUsage || null,
      };
      this.$set(this.activeAgentRuns, run.runId, run);
      // 后台补拉最新详情（覆盖终态等）
      this.silentFetchAgentRunDetail(run.runId);
    } catch (e) {
      window.sessionStorage.removeItem(key);
    }
  }
},
```

> 注：原 `restoreActiveAgentRun` 末尾的 `silentRestoreChatSession` / `silentFetchAgentRunDetail` / `activeView="chat"` / guidanceMessage 等逻辑，迁移到路由恢复流程（`restoreChatSessionFromRoute`）中按当前会话处理。

- [ ] **Step 4: 改造 `clearActiveAgentRun` 支持 runId**

替换 `clearActiveAgentRun` 为：

```ts
/**
 * 清空 run 追踪。传 runId 删单个；不传清整个 Map（logout 用）。
 */
clearActiveAgentRun(runId?: string) {
  if (runId) {
    const key = this.getRunStorageKey(runId);
    if (key && typeof window !== "undefined") {
      window.sessionStorage.removeItem(key);
    }
    this.removeRunEntry(runId);
  } else {
    const prefix = this.getRunStorageKeyPrefix();
    if (prefix && typeof window !== "undefined") {
      for (let i = window.sessionStorage.length - 1; i >= 0; i--) {
        const key = window.sessionStorage.key(i);
        if (key && key.indexOf(prefix) === 0) {
          window.sessionStorage.removeItem(key);
        }
      }
    }
    this.activeAgentRuns = {};
  }
},
```

- [ ] **Step 5: 更新 created() 钩子与所有 snapshot 调用**

created() 中 `this.restoreActiveAgentRun();` 改为 `this.restoreActiveAgentRuns();`。

全局搜索 `snapshotActiveAgentRun()` 调用（约 7 处：L1293, L1356, L1506, L1896, L2197, L2284, L2514），全部改为 `this.snapshotRunState(run.runId)`，确保调用处 `run` 变量在作用域内（Task 3/4 已传入 run）。

- [ ] **Step 6: 编译验证 + 运行时刷新恢复验证**

Run: `cd FitMate-frontend && npm run dev`
验证：
1. 发起 Agent 任务，刷新页面 → run 状态从 sessionStorage 恢复，继续显示
2. 任务完成后刷新 → 终态 run 不再恢复（key 已删）
3. logout → 整个 Map 清空，sessionStorage 对应 key 全部删除

- [ ] **Step 7: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "refactor(chat): 持久化改为 per-run sessionStorage key，支持多 run 恢复"
```

---

## Task 7: 放开新建/切换会话限制 + UI 状态切换重置

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatPage.vue` (L18, L22)
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`
  - `handleCreateChat` (L408-430)
  - `handleSelectChatSession` (L352-396)
  - 新增 `activeChatSessionId` watcher

**目标：** 任务运行时可新建/切换会话，切换时 UI 状态重置。

- [ ] **Step 1: ChatPage.vue 移除新建按钮禁用**

L18 的 `:disabled="isSending || isStreaming || hasPendingAgentRun()"` 改为 `:disabled="isSending || isStreaming"`。

> 保留 `isSending || isStreaming` 是因为这两个是当前会话的 UI 状态（切换会话时会重置为 false，见 Step 4）。

- [ ] **Step 2: 改造 `handleCreateChat`**

L408-412 的阻止块删除。L414 的 `this.clearActiveAgentRun();` 删除（不再清整个 Map）。L420-424 的清空 `this.agentSteps = []` / `this.thinkingSegments = []` / `this.botMsgId = null` 删除（已由 computed 派生）。改为：

```ts
handleCreateChat() {
  // 不再阻止：任务运行时也可新建聊天，旧 run 在 Map 中继续追踪
  this.activeView = "chat";
  this.activeChatSessionId = null;  // 触发 currentAgentRun 重指向 null
  this.chatList = [];
  this.draftMessage = "";
  this.showBackToBottom = false;
  this.knowledgeSources = [];
  this.closeMobileDrawers();
  this.scrollToBottom(true);
},
```

> 注：`clearThinkingState()` 调用保留（清当前会话的 thinking UI 状态）。`thinkingSegments` / `agentSteps` 已是 computed，无需手动清。

- [ ] **Step 3: 改造 `handleSelectChatSession`**

L352-359 的阻止块删除。L377 的 `this.clearActiveAgentRun();` 删除。L385-387 的 `this.agentSteps = []` / `this.thinkingSegments = []` / `this.botMsgId = null` 删除。保留 `activeChatSessionId` 切换（currentAgentRun computed 自动重指向目标会话的 run）。

```ts
handleSelectChatSession(sessionId) {
  // 不再阻止：任务运行时也可切换，旧 run 在 Map 中继续追踪
  // ... targetSession 查找、mappedChatList 映射保留
  this.activeChatSessionId = sessionId;  // 触发 currentAgentRun 重指向
  // ... chatList / knowledgeSources / tokenUsage 赋值保留（tokenUsage 改为从消息恢复）
  this.activeView = "chat";
  this.scrollToBottom(true);
},
```

> `tokenUsage` 是 computed，不能直接赋值。原 L388-390 的 `this.tokenUsage = this.resolveLastUsageFromMessages(...)` 改为：从历史消息恢复的 tokenUsage 写入 currentAgentRun.tokenUsage（若 currentAgentRun 存在），或存到一个临时的 `lastRestoredTokenUsage` 字段供 computed 兜底。简化方案：若切到的会话无活跃 run，tokenUsage 显示 null（用户可在新消息时刷新）。

- [ ] **Step 4: 新增 `activeChatSessionId` watcher**

在 `watch` 块内新增：

```ts
activeChatSessionId(newVal, oldVal) {
  if (newVal !== oldVal) {
    // 切换会话：重置 UI 状态为新会话的空闲态
    const run = this.currentAgentRun;
    this.isSending = run != null && !this.isTerminalAgentRunStatus(run.status);
    this.isStreaming = false;
    this.isThinking = false;
    // 切换到无活跃 run 的会话时，清思考展开状态
    if (!run) {
      this.thinkingExpanded = true;
    }
  }
},
```

- [ ] **Step 5: 编译验证 + 运行时并发验证**

Run: `cd FitMate-frontend && npm run dev`
验证：
1. 发起任务 A → 新建聊天 → 按钮**可点击**，A 在后台继续跑
2. 在新会话发起任务 B → 两者并发跑
3. 切回 A 会话 → currentAgentRun 重指向 A，实时事件继续显示
4. 切到无任务的会话 → isSending/isStreaming 为 false，输入框可用

- [ ] **Step 6: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatPage.vue FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "feat(chat): 放开新建/切换会话限制，支持多 run 并发追踪与切换"
```

---

## Task 8: 终态修复 + 子组件 prop 契约合并

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`
  - `isTerminalAgentRunStatus` (L882-885)
- Modify: `FitMate-frontend/src/pages/chat/ChatPage.vue` (L77-95)
- Modify: `FitMate-frontend/src/pages/chat/components/ChatMessageList.vue` (prop 定义 L170-191, 模板 L17-27)

**目标：** 补全 cancelled/interrupted 终态识别；子组件 prop 合并为单个 `activeAgentRun`。

- [ ] **Step 1: 补全 `isTerminalAgentRunStatus`**

L882-885 改为：

```ts
isTerminalAgentRunStatus(status: any): boolean {
  const s = this.normalizeAgentRunStatus(status);
  return s === "success" || s === "failed" || s === "cancelled" || s === "interrupted";
},
```

- [ ] **Step 2: ChatPage.vue 合并 ChatMessageList 的 prop**

L77-95 的 `<ChatMessageList>` 标签内，删除：

```diff
- :agent-steps="agentSteps"
- :active-agent-run="activeAgentRun"
- :thinking-content="thinkingContent"
- :thinking-segments="thinkingSegments"
+ :active-agent-run="currentRunView"
```

保留 `:is-sending` / `:is-streaming` / `:is-thinking` / `:thinking-expanded` / `:show-back-to-bottom` / `:chat-list`。

- [ ] **Step 3: ChatMessageList.vue 调整 prop 定义与内部引用**

prop 定义（L170-191）删除 `agentSteps` / `thinkingContent` / `thinkingSegments` 三个独立 prop，仅保留 `activeAgentRun`。在内部 computed 或方法中从 `this.activeAgentRun` 取这些值：

```ts
computed: {
  innerSteps() { return this.activeAgentRun ? (this.activeAgentRun.steps || []) : []; },
  innerThinkingContent() { return this.activeAgentRun ? (this.activeAgentRun.thinkingContent || "") : ""; },
  innerThinkingSegments() { return this.activeAgentRun ? (this.activeAgentRun.thinkingSegments || []) : []; },
}
```

模板 L19-21 的 `:steps="agentSteps"` / `:thinking-content="thinkingContent"` / `:thinking-segments="thinkingSegments"` 改为 `:steps="innerSteps"` 等。

`shouldShowTaskCard` (L202-223) 内的 `this.agentSteps.length` 改为 `this.innerSteps.length`，`this.activeAgentRun.status` 不变。

- [ ] **Step 4: 编译验证 + 运行时验证**

Run: `cd FitMate-frontend && npm run dev`
验证：
1. AgentStepCard 任务卡片正常渲染（steps/thinking 显示）
2. ReasoningTraceBlock 历史消息思考块正常渲染
3. 任务取消 → interrupted 状态识别，run 从 Map 移除
4. 任务失败 → failed 状态识别

- [ ] **Step 5: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue FitMate-frontend/src/pages/chat/ChatPage.vue FitMate-frontend/src/pages/chat/components/ChatMessageList.vue
git commit -m "feat(chat): 补全终态识别，合并子组件 prop 契约为 currentRunView"
```

---

## Task 9: applyServerSessionMeta 收敛 + doChat 适配

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`
  - `applyServerSessionMeta` (L774-796)
  - `doChat` (L3142, L3198)
  - `stopGeneration` (L2916-2964)

**目标：** 收敛 `applyServerSessionMeta` 副作用，doChat / stopGeneration 适配新模型。

- [ ] **Step 1: `applyServerSessionMeta` 改为只在 ack 路径更新全局指针**

原 L774-796 无条件写 `this.activeChatSessionId` / `this.currentSessionCode` / `this.currentSessionSceneType`。改为接收 `options.updateGlobalSession` 参数，仅 ack/恢复路径传 true：

```ts
applyServerSessionMeta(payload: any, options: { updateGlobalSession?: boolean } = {}) {
  if (!payload) return;
  if (options.updateGlobalSession) {
    if (payload.chatSessionId != null) {
      this.activeChatSessionId = payload.chatSessionId;
    }
    // currentSessionCode / currentSessionSceneType 已是 computed，删除写入
  }
  // ... 其他逻辑保留
}
```

调用处：`applyAgentExecuteAck` 内传 `{ updateGlobalSession: true }`；`handleThinkingEvent` / `upsertStreamingBotMessage` / `applyFinishPayload` 内不传（默认 false），避免后台 run 干扰当前会话指针。

- [ ] **Step 2: `doChat` 适配**

L3142 的 `this.botMsgId = botMsgId;` 删除（botMsgId 改由 `applyAgentExecuteAck` 写入 run.botMsgId）。L3146-3147 的 `this.agentSteps = []; this.thinkingSegments = [];` 删除（computed 派生）。L3142 之前新增 `createRunEntry`（用本地生成的 botMsgId 和当前 activeChatSessionId）：

```ts
// doChat 内，发起请求前
const botMsgId = /* 原有生成逻辑 */;
// 先创建 run entry（runId 暂用 botMsgId 占位，ack 返回后由后端 runId 替换/补全）
// 实际 runId 由 ack 返回，此处不创建 entry，等 ack 到达时 createRunEntry
```

> 简化：doChat 不创建 entry，ack 到达时 `applyAgentExecuteAck` 内 `resolveRunForEvent(payload, { createIfMissing: true })` 创建。doChat 只负责发请求。

- [ ] **Step 3: `stopGeneration` 适配**

L2928 的 `var runId = (this.activeAgentRun && this.activeAgentRun.runId) || null;` 改为：

```ts
var runId = this.currentAgentRun ? this.currentAgentRun.runId : null;
```

L2931 的 `this.botMsgId = null;` 删除（computed 派生）。L2958 同理。

- [ ] **Step 4: 编译验证 + 运行时验证**

Run: `cd FitMate-frontend && npm run dev`
验证：
1. 发起任务 → ack 正常，run entry 创建
2. 取消任务 → stopGeneration 用 currentAgentRun.runId，取消成功
3. 后台 run 运行时，切到其他会话 → activeChatSessionId 不被后台 run 的事件干扰

- [ ] **Step 5: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "refactor(chat): 收敛 applyServerSessionMeta 副作用，doChat/stopGeneration 适配新模型"
```

---

## Task 10: 并发场景验证与边界修复

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`（按验证结果修补）

**目标：** 完整并发场景验证，修复发现的问题。

- [ ] **Step 1: 3 并发 run 实时追踪验证**

测试步骤：
1. 会话 A 发任务 → 切到 B 发任务 → 切到 C 发任务
2. 三个会话各有一个活跃 run
3. 轮流切回 A/B/C，确认每个会话的 step/thinking 实时更新
4. 确认 Map 中有 3 个 entry

如发现 SSE 事件错位：检查 `resolveRunForEvent` 的 runId 路由是否正确，特别是 payload.runId 缺失时的降级。

- [ ] **Step 2: 第 4 个任务被后端拒绝验证**

在 3 个 run 运行中，再发第 4 个任务。预期：后端返回 429 "当前并发任务数已达上限（3）"。前端应优雅显示错误，不创建 run entry。

如前端未处理 429：在 doChat 的 catch 分支增加对 429 的识别与提示。

- [ ] **Step 3: 取消单个 run 不影响其他验证**

3 个 run 运行中，切到 A 取消 A 的 run。预期：
- A 的 run 状态变 interrupted，从 Map 移除
- B/C 的 run 继续正常跑，实时事件不受影响

- [ ] **Step 4: 刷新恢复多 run 验证**

3 个 run 运行中刷新页面。预期：
- `restoreActiveAgentRuns` 从 sessionStorage 重建 3 个 entry
- `silentFetchAgentRunDetail` 后台补拉每个 run 最新状态
- 切到任一会话能看到该 run 的当前状态

- [ ] **Step 5: 历史消息加载兜底验证**

切到一个已完成的会话（无活跃 run）。预期：
- currentAgentRun 为 null
- 历史消息通过 `silentRestoreChatSession` 加载
- bot 消息的 thinking/steps 通过消息级字段（item.thinkingSegments 等）显示

- [ ] **Step 6: 修复发现的问题并 Commit**

按验证结果修补，每个修复独立 commit。例如：

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "fix(chat): 修复并发场景下 SSE 事件路由边界问题"
```

---

## 验证清单（对照 spec §13）

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

---

## 自审记录

**Spec 覆盖：** 逐条对照 spec §3-§10，所有决策点均有对应 Task。

**类型一致性：** `RunState` 字段名（runId/chatSessionId/sessionCode/botMsgId/status/steps/thinkingSegments/thinkingContent/tokenUsage）在所有 Task 中一致。方法名 `resolveRunForEvent` / `createRunEntry` / `removeRunEntry` / `snapshotRunState` / `restoreActiveAgentRuns` / `clearActiveAgentRun(runId?)` 全文统一。

**Placeholder 扫描：** 无 TBD/TODO，每步均有具体代码或具体操作指令。Task 9 Step 2 的 doChat 适配有明确的"不创建 entry，等 ack"决策。

**Scope 检查：** 10 个 Task 聚焦单文件 + 2 个子组件，每个 Task 独立可验证可提交。
