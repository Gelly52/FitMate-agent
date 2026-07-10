# 思考内容 sessionStorage 缓存实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为历史会话的思考内容增加 sessionStorage 层 TTL 缓存，缓存命中时默认展开，未命中保持折叠懒加载；流式生成完成后写入缓存；删除会话/登出时失效。

**Architecture:** 新增独立工具模块 `thinkingCache.ts`（纯函数 + sessionStorage，无 Vue 依赖），ChatLogicBase.vue 在三处调用：读取（`toggleThinkingExpanded`）、写入（`applyFinishPayload` 终态时）、应用（会话加载后遍历 chatList 命中即填充并默认展开）、失效（`handleDeleteSession` / `handleLogout`）。Key 格式 `fitmate:thinking-cache:{userKey}:{sessionId}:{botMsgId}`，TTL 1 小时，schema 版本字段 `v: 1`。

**Tech Stack:** Vue 2 Options API + TypeScript，无前端单测，验证靠 `npm run dev` 编译 + 浏览器 DevTools 手动验证 sessionStorage 与 TTL 行为。

**Scope:** Phase 1 仅缓存思考内容（thinkingContent + thinkingSegments + agentSteps）。正文懒加载改造留待 Phase 2。

---

## 文件结构

| 文件 | 责任 | 改动类型 |
|---|---|---|
| `FitMate-frontend/src/services/thinkingCache.ts` | 缓存工具模块：get/set/invalidate，TTL 校验，schema 版本 | 新建 |
| `FitMate-frontend/src/pages/chat/ChatLogicBase.vue` | 集成点：toggleThinkingExpanded 读缓存、applyFinishPayload 写缓存、silentRestoreChatSession/switchSession 后应用缓存、handleDeleteSession/handleLogout 失效 | 中改 |

---

## Task 1: 新建 thinkingCache.ts 工具模块

**Files:**
- Create: `FitMate-frontend/src/services/thinkingCache.ts`

**目标：** 提供独立的思考内容缓存原语，无 Vue 依赖，可被任意组件/服务调用。所有读写均带 TTL 校验。

- [ ] **Step 1: 创建 thinkingCache.ts 文件**

写入以下内容：

```ts
/**
 * 思考内容 sessionStorage 缓存。
 *
 * 设计要点：
 * - Key: `fitmate:thinking-cache:{userKey}:{sessionId}:{botMsgId}`
 *   - userKey 隔离用户
 *   - sessionId 便于按会话前缀失效
 *   - botMsgId 唯一标识一条 assistant 消息（流式与历史记录都持有）
 * - TTL: 1 小时；读取时若超时则视为未命中并清理对应 key
 * - schema 版本字段 v：未来字段升级时整体失效
 *
 * 该模块无 Vue 依赖，纯函数 + sessionStorage。
 */

export interface ThinkingCacheEntry {
  v: 1;
  cachedAt: number;
  thinkingContent: string;
  thinkingSegments: any[];
  agentSteps: any[];
}

const TTL_MS = 60 * 60 * 1000; // 1 小时
const KEY_PREFIX = "fitmate:thinking-cache:";

function buildKey(userKey: string, sessionId: string | number, botMsgId: string): string {
  return KEY_PREFIX + String(userKey) + ":" + String(sessionId) + ":" + String(botMsgId);
}

function buildUserPrefix(userKey: string): string {
  return KEY_PREFIX + String(userKey) + ":";
}

function buildSessionPrefix(userKey: string, sessionId: string | number): string {
  return KEY_PREFIX + String(userKey) + ":" + String(sessionId) + ":";
}

function safeParse(raw: string | null): ThinkingCacheEntry | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object") return null;
    if (parsed.v !== 1) return null; // schema 不匹配，视为未命中
    if (typeof parsed.cachedAt !== "number") return null;
    return parsed as ThinkingCacheEntry;
  } catch (e) {
    return null;
  }
}

function isExpired(entry: ThinkingCacheEntry, now: number = Date.now()): boolean {
  return now - entry.cachedAt > TTL_MS;
}

/**
 * 读取缓存。命中且未过期则返回 entry；过期或不存在返回 null（并清理过期项）。
 */
export function getThinking(
  userKey: string,
  sessionId: string | number,
  botMsgId: string
): ThinkingCacheEntry | null {
  if (!userKey || sessionId == null || !botMsgId) return null;
  const key = buildKey(userKey, sessionId, botMsgId);
  let raw: string | null = null;
  try {
    raw = sessionStorage.getItem(key);
  } catch (e) {
    return null;
  }
  const entry = safeParse(raw);
  if (!entry) return null;
  if (isExpired(entry)) {
    try {
      sessionStorage.removeItem(key);
    } catch (e) {
      /* ignore */
    }
    return null;
  }
  return entry;
}

/**
 * 写入缓存。任意字段缺失则跳过。
 */
export function setThinking(
  userKey: string,
  sessionId: string | number,
  botMsgId: string,
  data: {
    thinkingContent: string;
    thinkingSegments: any[];
    agentSteps: any[];
  }
): void {
  if (!userKey || sessionId == null || !botMsgId) return;
  if (!data) return;
  const entry: ThinkingCacheEntry = {
    v: 1,
    cachedAt: Date.now(),
    thinkingContent: String(data.thinkingContent || ""),
    thinkingSegments: Array.isArray(data.thinkingSegments) ? data.thinkingSegments : [],
    agentSteps: Array.isArray(data.agentSteps) ? data.agentSteps : [],
  };
  const key = buildKey(userKey, sessionId, botMsgId);
  try {
    sessionStorage.setItem(key, JSON.stringify(entry));
  } catch (e) {
    // sessionStorage 满 or 不可用：静默降级
    console.warn("thinkingCache.setThinking 写入失败:", e);
  }
}

/**
 * 失效单条消息缓存。
 */
export function invalidateMessage(
  userKey: string,
  sessionId: string | number,
  botMsgId: string
): void {
  if (!userKey || sessionId == null || !botMsgId) return;
  try {
    sessionStorage.removeItem(buildKey(userKey, sessionId, botMsgId));
  } catch (e) {
    /* ignore */
  }
}

/**
 * 失效某会话下所有消息缓存。遍历该用户前缀下所有 key，删除匹配 session 前缀的项。
 */
export function invalidateSession(
  userKey: string,
  sessionId: string | number
): void {
  if (!userKey || sessionId == null) return;
  const prefix = buildSessionPrefix(userKey, sessionId);
  try {
    const keysToRemove: string[] = [];
    for (let i = 0; i < sessionStorage.length; i++) {
      const k = sessionStorage.key(i);
      if (k && k.indexOf(prefix) === 0) {
        keysToRemove.push(k);
      }
    }
    for (const k of keysToRemove) {
      sessionStorage.removeItem(k);
    }
  } catch (e) {
    /* ignore */
  }
}

/**
 * 失效当前用户的所有思考缓存（登出/换号时调用）。
 */
export function invalidateUser(userKey: string): void {
  if (!userKey) return;
  const prefix = buildUserPrefix(userKey);
  try {
    const keysToRemove: string[] = [];
    for (let i = 0; i < sessionStorage.length; i++) {
      const k = sessionStorage.key(i);
      if (k && k.indexOf(prefix) === 0) {
        keysToRemove.push(k);
      }
    }
    for (const k of keysToRemove) {
      sessionStorage.removeItem(k);
    }
  } catch (e) {
    /* ignore */
  }
}

/**
 * 清空所有用户的思考缓存（仅用于调试/强制清理）。
 */
export function clearAllThinking(): void {
  try {
    const keysToRemove: string[] = [];
    for (let i = 0; i < sessionStorage.length; i++) {
      const k = sessionStorage.key(i);
      if (k && k.indexOf(KEY_PREFIX) === 0) {
        keysToRemove.push(k);
      }
    }
    for (const k of keysToRemove) {
      sessionStorage.removeItem(k);
    }
  } catch (e) {
    /* ignore */
  }
}
```

- [ ] **Step 2: 验证 TypeScript 编译通过**

Run: `cd FitMate-frontend && npx vue-tsc --noEmit`
Expected: 无新增报错（若项目本身已有报错，本次新增不应增加）。

- [ ] **Step 3: Commit**

```bash
git add FitMate-frontend/src/services/thinkingCache.ts
git commit -m "feat: 新增 thinkingCache.ts 思考内容 sessionStorage 缓存工具模块"
```

---

## Task 2: 在 toggleThinkingExpanded 中读取缓存

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue:1872-1952` (toggleThinkingExpanded)
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue` 顶部 import 区

**目标：** 历史消息展开时优先查缓存，命中则跳过两个 API 调用直接填充字段并展开。未命中走原逻辑（拉接口），拉完写入缓存。

- [ ] **Step 1: 在 ChatLogicBase.vue 顶部 import thinkingCache**

在文件已有 import 区（`import doctorApi from "../../services/doctorApi"` 附近，约第 13 行）新增一行。注意项目约定用相对路径而非 `@/` 别名：

```ts
import {
  getThinking as getThinkingCache,
  setThinking as setThinkingCache,
  invalidateSession as invalidateThinkingCacheBySession,
  invalidateUser as invalidateThinkingCacheByUser,
} from "../../services/thinkingCache";
```

说明：`invalidateMessage`（单条失效）当前无调用点，重新生成场景由 `applyFinishPayload` 终态时覆盖写入隐式处理（同一 botMsgId 直接覆盖；不同 botMsgId 的旧条目由 TTL 自然清理）。如未来需要单条失效再按需导入。

- [ ] **Step 2: 修改 toggleThinkingExpanded 增加 cache 读取分支**

定位到 `ChatLogicBase.vue:1885` 处的展开加载条件块（`if (!message.thinkingLoaded && !message.thinkingLoading && ...)`）。在该 `if` 块**之前**插入缓存读取分支：

```ts
      // 展开：先查 sessionStorage 缓存，命中则直接填充并展开，跳过接口
      if (
        !message.thinkingLoaded &&
        !message.thinkingLoading &&
        message.botMsgId
      ) {
        var userKey = this.resolveStableUserKey();
        var sessionId = this.activeChatSessionId;
        if (userKey && sessionId != null) {
          var cached = getThinkingCache(
            String(userKey),
            String(sessionId),
            String(message.botMsgId)
          );
          if (cached) {
            message.thinkingContent = cached.thinkingContent;
            message.thinkingSegments = cached.thinkingSegments;
            message.agentSteps = cached.agentSteps;
            message.thinkingLoaded = true;
            message.thinkingExpanded = true;
            return;
          }
        }
      }
```

注意：插入位置在原 `if (!message.thinkingLoaded && !message.thinkingLoading && message.messageId && !message.thinkingContent)` **之前**，且不影响其后原有的接口加载分支。

- [ ] **Step 3: 在 toggleThinkingExpanded 接口加载成功后写入缓存**

定位到 `ChatLogicBase.vue:1946` 处 `message.thinkingLoaded = true;` 这一行。在其**之前**（即 segments 已赋值之后）插入缓存写入：

```ts
          // 写入 sessionStorage 缓存，便于下次会话加载时默认展开
          var writeUserKey = this.resolveStableUserKey();
          var writeSessionId = this.activeChatSessionId;
          if (writeUserKey && writeSessionId != null && message.botMsgId) {
            setThinkingCache(
              String(writeUserKey),
              String(writeSessionId),
              String(message.botMsgId),
              {
                thinkingContent: String(thinkingText),
                thinkingSegments: message.thinkingSegments,
                agentSteps: message.agentSteps,
              }
            );
          }
          message.thinkingLoaded = true;
```

- [ ] **Step 4: 验证编译通过**

Run: `cd FitMate-frontend && npx vue-tsc --noEmit`
Expected: 无新增报错。

- [ ] **Step 5: 启动 dev server 手动验证读路径**

Run: `cd FitMate-frontend && npm run dev`

操作步骤：
1. 浏览器打开应用，登录
2. 进入一个含历史 assistant 消息的会话
3. 点击一条消息的思考块展开 → 应正常加载（首次未命中缓存）
4. 折叠该思考块
5. 再次点击展开 → 应**无网络请求**（打开 DevTools Network 面板确认无 `/chat/thinking/...` 与 `/agent/runs/by-bot-msg/...` 调用），瞬间展开
6. 在 DevTools → Application → Session Storage 中应能看到 key 形如 `fitmate:thinking-cache:{userKey}:{sessionId}:{botMsgId}`

Expected: 第二次展开无网络请求，缓存 key 存在。

- [ ] **Step 6: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "feat: toggleThinkingExpanded 优先读思考内容缓存，未命中再拉接口并写入缓存"
```

---

## Task 3: 在 SSE 完成时写入缓存

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue:2376-2511` (applyFinishPayload)

**目标：** 流式生成完成后，将本轮 run 累积的 thinkingSegments 与 steps 写入缓存，使下次重进会话时该消息默认展开。

- [ ] **Step 1: 在 applyFinishPayload 末尾写入缓存**

定位到 `ChatLogicBase.vue:2510` 处 `this.scrollToBottom();`（applyFinishPayload 方法的最后一行，紧邻 `},` 结束符）。在其**之前**插入缓存写入逻辑：

```ts
      // 写入思考内容缓存：终态后 run.thinkingSegments 与 run.steps 已最终化
      if (run && botMsgId) {
        var finishUserKey = this.resolveStableUserKey();
        var finishSessionId =
          (run.chatSessionId != null ? run.chatSessionId : this.activeChatSessionId);
        if (finishUserKey && finishSessionId != null) {
          // 终态且非中断时才缓存；interrupted/cancelled 不写入，避免缓存半截内容
          var finishStatus = this.normalizeAgentRunStatus(run.status);
          if (finishStatus === "success") {
            setThinkingCache(
              String(finishUserKey),
              String(finishSessionId),
              String(botMsgId),
              {
                // thinkingContent 在流式路径下没有显式累积，这里用 segments.content 拼接做兜底
                thinkingContent: (run.thinkingSegments || [])
                  .map(function (s) {
                    return (s && s.content) || "";
                  })
                  .join("\n"),
                thinkingSegments: run.thinkingSegments || [],
                agentSteps: run.steps || [],
              }
            );
          }
        }
      }
      this.scrollToBottom();
```

说明：
- 仅 `success` 终态写入；`interrupted`/`cancelled`/`failed` 跳过
- `botMsgId` 与 `run.chatSessionId`（或回退到 `activeChatSessionId`）做 key
- thinkingContent 流式路径下未单独累积，用 segments.content 拼接做兜底（与历史加载路径下 `getThinkingByMessageId` 返回值语义一致）

- [ ] **Step 2: 验证编译通过**

Run: `cd FitMate-frontend && npx vue-tsc --noEmit`
Expected: 无新增报错。

- [ ] **Step 3: 手动验证流式生成后缓存写入**

Run: `cd FitMate-frontend && npm run dev`

操作步骤：
1. 登录后发起新一轮 agent 对话
2. 等待流式生成完成（看到"本轮任务已完成"提示）
3. 打开 DevTools → Application → Session Storage
4. 应能看到一个新的 `fitmate:thinking-cache:{userKey}:{sessionId}:{botMsgId}` key
5. 解析其 value，应包含 `v: 1`、`cachedAt`、`thinkingSegments` 数组、`agentSteps` 数组

Expected: 流式完成后缓存 key 立即出现，内容完整。

- [ ] **Step 4: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "feat: SSE finish 终态后写入思考内容缓存，便于重进会话默认展开"
```

---

## Task 4: 会话加载后遍历 chatList 应用缓存命中

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue:2908-2964` (mapRecordToChatItem)
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue` 新增方法 `applyThinkingCacheToChatList`
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue:441` 与 `:1280` 两处 `this.chatList = mappedChatList;` 之后

**目标：** 重新进入会话时，对 chatList 中每条 assistant 消息查缓存，命中则填充三个字段并置 `thinkingExpanded = true`（默认展开）；未命中保持折叠懒加载。

- [ ] **Step 1: 新增 applyThinkingCacheToChatList 方法**

在 `mapRecordToChatItem` 方法**之后**（`ChatLogicBase.vue:2964` 行 `},` 之后）新增方法：

```ts
    /**
     * 遍历 chatList，对 assistant 消息查思考内容缓存。
     * 命中则填充 thinkingContent/thinkingSegments/agentSteps，
     * 置 thinkingLoaded=true 与 thinkingExpanded=true（默认展开）。
     * 未命中保持原状（折叠 + 未加载）。
     */
    applyThinkingCacheToChatList(sessionId) {
      if (!Array.isArray(this.chatList) || sessionId == null) return;
      var userKey = this.resolveStableUserKey();
      if (!userKey) return;
      for (var i = 0; i < this.chatList.length; i++) {
        var item = this.chatList[i];
        if (!item || item.chatType !== "bot") continue;
        if (!item.botMsgId) continue;
        if (item.thinkingLoaded) continue; // 已加载过则跳过
        var cached = getThinkingCache(
          String(userKey),
          String(sessionId),
          String(item.botMsgId)
        );
        if (cached) {
          item.thinkingContent = cached.thinkingContent;
          item.thinkingSegments = cached.thinkingSegments;
          item.agentSteps = cached.agentSteps;
          item.thinkingLoaded = true;
          item.thinkingExpanded = true;
        }
      }
    },
```

- [ ] **Step 2: 在 switchSession 加载完成后调用**

定位 `ChatLogicBase.vue:441` 处 `this.chatList = mappedChatList;`，在其**之后**新增一行：

```ts
      this.chatList = mappedChatList;
      // 应用思考内容缓存：命中则默认展开
      this.applyThinkingCacheToChatList(targetSession.sessionId);
```

- [ ] **Step 3: 在 silentRestoreChatSession 加载完成后调用**

定位 `ChatLogicBase.vue:1280` 处 `me.chatList = mappedChatList;`，在其**之后**新增一行：

```ts
          me.chatList = mappedChatList;
          // 应用思考内容缓存：命中则默认展开
          me.applyThinkingCacheToChatList(targetSession.sessionId);
```

- [ ] **Step 4: 验证编译通过**

Run: `cd FitMate-frontend && npx vue-tsc --noEmit`
Expected: 无新增报错。

- [ ] **Step 5: 手动验证默认展开行为**

Run: `cd FitMate-frontend && npm run dev`

操作步骤：
1. 进入会话 A，展开一条历史消息的思考块（触发缓存写入）
2. 触发一次新的 agent 对话并等待完成（流式写入缓存）
3. 切换到会话 B，再切回会话 A
4. 会话 A 中之前展开过的消息与新完成的消息应**默认展开**，无需点击
5. DevTools Network 面板应**无** `/chat/thinking/...` 与 `/agent/runs/by-bot-msg/...` 请求
6. 从未展开过的消息仍保持折叠（懒加载行为不变）

Expected: 缓存命中的消息自动展开，未命中的保持折叠。

- [ ] **Step 6: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "feat: 会话加载后应用思考内容缓存，命中消息默认展开"
```

---

## Task 5: 删除会话与登出时失效缓存

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue:474-527` (handleDeleteSession)
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue:2512-2547` (handleLogout)

**目标：** 删除会话时清理该会话下所有思考缓存；登出/换号时清理当前用户的所有思考缓存，避免数据残留与泄露。

- [ ] **Step 1: 在 handleDeleteSession 成功删除后失效该会话缓存**

定位 `ChatLogicBase.vue:496` 处 `await doctorApi.deleteChatSession(sessionId);` 之后的逻辑。在 `// 从列表中移除` 这一行**之前**（即 API 成功后、UI 更新前）插入：

```ts
      // 失效该会话下的思考内容缓存（无论是否为当前活动会话）
      var delUserKey = this.resolveStableUserKey();
      if (delUserKey) {
        invalidateThinkingCacheBySession(String(delUserKey), String(sessionId));
      }
      // 从列表中移除
```

- [ ] **Step 2: 在 handleLogout 清理本地态时失效当前用户全部缓存**

定位 `ChatLogicBase.vue:2538-2546` 的 `.finally(...)` 块。在 `this.clearActiveAgentRun();` 行**之前**插入：

```ts
        .finally(
          function () {
            this.teardownSSE({ clearPending: true });
            // 失效当前用户的所有思考内容缓存，避免账号间泄露
            var logoutUserKey = this.resolveStableUserKey();
            if (logoutUserKey) {
              invalidateThinkingCacheByUser(String(logoutUserKey));
            }
            this.clearActiveAgentRun();
            clearUserSession();
            this.currentUserInfo = null;
            this.currentUserName = null;
            this.isLoggingOut = false;
            this.$emit("logout-success");
          }.bind(this)
        )
```

- [ ] **Step 3: 验证编译通过**

Run: `cd FitMate-frontend && npx vue-tsc --noEmit`
Expected: 无新增报错。

- [ ] **Step 4: 手动验证删除会话失效**

Run: `cd FitMate-frontend && npm run dev`

操作步骤：
1. 进入会话 A，展开一条思考块（写入缓存）
2. DevTools 中确认 `fitmate:thinking-cache:...:A` key 存在
3. 在会话列表中删除会话 A
4. DevTools → Session Storage 中应不再有该会话对应的 key
5. （可选）登出当前账号，确认所有 `fitmate:thinking-cache:` 前缀的 key 全部消失

Expected: 删除会话清理对应 key；登出清理当前用户全部 key。

- [ ] **Step 5: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "feat: 删除会话与登出时失效思考内容缓存"
```

---

## Task 6: TTL 过期与边界场景手动验证

**Files:** 无代码改动，仅验证

**目标：** 确认 TTL 行为、并发安全、空数据兜底等边界场景正常。

- [ ] **Step 1: 验证 TTL 过期清理**

Run: `cd FitMate-frontend && npm run dev`

操作步骤：
1. 进入会话 A，展开一条思考块（写入缓存），记录 DevTools 中该 key 的 `cachedAt` 值
2. 在 DevTools Console 中执行：`const k = "fitmate:thinking-cache:{实际userKey}:{实际sessionId}:{实际botMsgId}"; const e = JSON.parse(sessionStorage.getItem(k)); e.cachedAt = Date.now() - 61 * 60 * 1000; sessionStorage.setItem(k, JSON.stringify(e));` （把 cachedAt 改为 61 分钟前）
3. 刷新页面或切换会话再切回
4. 该消息应**不再默认展开**（缓存已过期视为未命中）
5. DevTools 中该 key 应已被 `getThinking` 读取时清理掉

Expected: TTL 过期后回到懒加载行为，过期 key 被自动清理。

- [ ] **Step 2: 验证并发点击不重复请求**

操作步骤：
1. 找一条**未缓存**的历史消息
2. 快速连续点击其思考块头部 3-5 次
3. DevTools Network 面板应只看到**一组** `/chat/thinking/...` 与 `/agent/runs/by-bot-msg/...` 请求

Expected: `thinkingLoading` 标志阻止并发加载，与现有逻辑一致。

- [ ] **Step 3: 验证 sessionStorage 不可用时不崩**

操作步骤：
1. 在 DevTools 中（如果浏览器支持）禁用 sessionStorage，或在 Console 中重写：`const orig = sessionStorage.getItem; sessionStorage.getItem = () => { throw new Error("mock unavailable"); };`
2. 刷新页面，进入会话
3. 应用不应崩溃；思考块保持折叠懒加载行为（缓存读取失败兜底为 null）
4. 恢复：`sessionStorage.getItem = orig;`

Expected: sessionStorage 异常时降级为无缓存行为，不抛错。

- [ ] **Step 4: 验证 interrupted/cancelled 状态不写入缓存**

操作步骤：
1. 发起新 agent 对话
2. 在流式生成过程中点击"停止"按钮（触发 interrupted）
3. DevTools → Session Storage 中应**没有**对应 botMsgId 的缓存 key

Expected: 仅 success 终态写入缓存；中断/失败不写入。

- [ ] **Step 5: 全量回归**

操作步骤：
1. 完整走一遍：登录 → 进入历史会话 → 展开思考块 → 折叠 → 再展开（无请求）→ 切换会话 → 切回（默认展开）→ 发起新对话 → 完成（自动缓存）→ 切走再切回（新消息默认展开）→ 删除会话（缓存清理）→ 登出（全清理）
2. 全程无 console error，无重复请求

Expected: 全链路顺畅。

- [ ] **Step 6: Commit（如有微调）**

如本任务中发现 bug 并修复，单独 commit：

```bash
git add -A
git commit -m "fix: TTL 与边界场景微调"
```

否则跳过。

---

## 自检清单（实施完成后回看）

- [ ] `thinkingCache.ts` 无 Vue 依赖，纯函数 + sessionStorage
- [ ] TTL 1 小时，读取时校验并清理过期项
- [ ] schema 版本字段 `v: 1` 已加，未来升级可整体失效
- [ ] 历史消息展开优先读缓存，未命中再拉接口并写入缓存
- [ ] 流式 success 终态写入缓存；interrupted/cancelled/failed 不写入
- [ ] 重新进入会话时缓存命中的消息默认展开，未命中的保持折叠
- [ ] 删除会话清理该会话所有思考缓存
- [ ] 登出清理当前用户所有思考缓存
- [ ] 并发点击不重复请求（thinkingLoading 守卫保留）
- [ ] sessionStorage 异常时静默降级，不影响主流程
