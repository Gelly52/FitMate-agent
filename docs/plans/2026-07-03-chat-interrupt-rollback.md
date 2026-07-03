# 对话输出打断与输入历史回滚 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为现有 Agent 对话增加「输出打断」和「输入历史回滚」两个功能，支持用户主动停止正在生成的回复，以及通过重试按钮回滚到某条用户消息并重新输入。

**Architecture:** 前后端联动取消方案。后端新增 `AgentCancellationRegistry` 维护 runId→取消标志映射，Agent Loop 在每次 LLM 分片迭代时检查标志并抛出 `AgentCancelledException`；前端发送按钮在流式期间切换为停止按钮，点击后调用 `/agent/cancel` 接口。回滚功能复用打断逻辑（串行：先打断再删除），后端新增消息删除接口按 botMsgId 定位并删除该用户消息及之后全部消息。

**Tech Stack:** Spring Boot + MyBatis-Plus（后端），Vue 3 Options API + EventSource SSE（前端）

---

## 已确认的决策

| 决策项 | 选择 |
|--------|------|
| 打断深度 | 前后端联动取消（彻底） |
| 打断后 assistant 消息 | 保留已生成部分内容并标注「已中断」 |
| 回滚删除范围 | 含该用户消息及之后全部消息 |
| 打断按钮位置 | 发送按钮在流式期间切换为停止按钮 |
| 打断与回滚时序 | 串行（先打断等完成，再删除） |

## 关键约束

1. 回滚通过 `botMsgId` 定位：前端在用户消息对象上保存 `botMsgId`，后端通过 botMsgId 找到 assistant 消息的 seqNo，再删除 seqNo >= (assistantSeqNo - 1) 的所有消息（即 user 消息及之后全部）
2. 当前前端 `mapRecordToChatItem`（[ChatLogicBase.vue:2107-2124](file:///d:\Applications\Java\A%20Learning\FitMate-AI-0\FitMate-frontend\src\pages\chat\ChatLogicBase.vue#L2107-L2124)）**没有保留 seqNo**，但保留了 botMsgId，所以用 botMsgId 定位
3. 后端 `doChat` 创建用户消息时（[ChatLogicBase.vue:2433-2442](file:///d:\Applications\Java\A%20Learning\FitMate-AI-0\FitMate-frontend\src\pages\chat\ChatLogicBase.vue#L2433-L2442)）**没有保存 botMsgId 到用户消息对象**，需要补充
4. Agent Loop 中 LLM 流式输出是阻塞式迭代（`.toStream().forEach()`），在 forEach lambda 内部检查取消标志即可中断
5. LLM HTTP 请求用 `httpClient.send()`（同步阻塞），首字等待期间无法立即中断，需等首个分片到达后在 forEach 中检查标志——这个延迟（通常几秒）可接受

## 文件结构

### 后端新增文件
| 文件 | 职责 |
|------|------|
| `FitMate-api/.../agent/core/AgentCancellationRegistry.java` | 取消注册表，维护 runId→取消标志映射 |
| `FitMate-api/.../agent/core/AgentCancelledException.java` | 取消异常，区分取消与失败 |

### 后端修改文件
| 文件 | 改动 |
|------|------|
| `FitMate-api/.../agent/dto/AgentExecuteContext.java` | 添加 `volatile boolean cancelled` 字段 |
| `FitMate-api/.../agent/core/AgentLoopExecutor.java` | 在 forEach 中检查取消标志；捕获后在 finishWithAnswer 前中断 |
| `FitMate-api/.../agent/application/impl/AgentAsyncServiceImpl.java` | 捕获 AgentCancelledException，回填部分内容+标注，推送 interrupted FINISH |
| `FitMate-api/.../agent/controller/AgentController.java` | 新增 `POST /agent/cancel` 接口 |
| `FitMate-api/.../agent/application/AgentRunService.java` | 新增 `markRunCancelled` 方法声明 |
| `FitMate-api/.../agent/application/impl/AgentRunServiceImpl.java` | 实现 `markRunCancelled` |
| `FitMate-api/.../chat/application/ChatSessionService.java` | 新增 `deleteMessagesFromBotMsgId` 方法声明 |
| `FitMate-api/.../chat/application/impl/ChatSessionServiceImpl.java` | 实现删除消息+清理压缩摘要+更新 lastBotMsgId |
| `FitMate-api/.../chat/controller/ChatController.java` | 新增 `POST /chat/rollback` 接口 |

### 前端修改文件
| 文件 | 改动 |
|------|------|
| `FitMate-frontend/src/services/doctorApi.ts` | 新增 `cancelAgent` 和 `rollbackMessage` API 函数 |
| `FitMate-frontend/src/pages/chat/ChatLogicBase.vue` | 新增 `stopGeneration`、`rollbackChatMessages`；改造 `retryUserMessage`、`doChat`、`applyFinishPayload` |
| `FitMate-frontend/src/pages/chat/components/ChatInput.vue` | 发送按钮在流式期间切换为停止按钮，emit `stop` 事件 |
| `FitMate-frontend/src/pages/chat/components/ChatMessageList.vue` | bot 消息添加「已中断」标注展示 |

---

## 任务列表

### Task 1: 创建 AgentCancelledException

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentCancelledException.java`

- [ ] **Step 1: 创建异常类**

```java
package com.itgeo.fitmate.api.agent.core;

/**
 * Agent 执行被用户主动取消时抛出。
 * 用于区分取消与普通失败，使异步执行壳能执行不同的收尾逻辑。
 */
public class AgentCancelledException extends RuntimeException {
    /** 已生成的部分内容，用于回填 assistant 消息。 */
    private final String partialContent;

    public AgentCancelledException(String partialContent) {
        super("Agent执行已被用户取消");
        this.partialContent = partialContent;
    }

    public String getPartialContent() {
        return partialContent;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd FitMate-backend && mvn compile -pl FitMate-api -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentCancelledException.java
git commit -m "feat: 新增 AgentCancelledException 用于区分取消与失败"
```

---

### Task 2: 创建 AgentCancellationRegistry

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentCancellationRegistry.java`

- [ ] **Step 1: 创建取消注册表**

```java
package com.itgeo.fitmate.api.agent.core;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Agent 取消注册表。
 * 维护 runId → 取消标志的映射，供取消接口设置标志、Agent Loop 检查标志。
 */
@Component
public class AgentCancellationRegistry {

    private final ConcurrentHashMap<Long, Boolean> cancelFlags = new ConcurrentHashMap<>();

    /**
     * 注册一个 run，初始为未取消。
     */
    public void register(Long runId) {
        if (runId != null) {
            cancelFlags.put(runId, Boolean.FALSE);
        }
    }

    /**
     * 标记指定 run 为已取消。
     */
    public boolean cancel(Long runId) {
        if (runId == null) {
            return false;
        }
        return cancelFlags.replace(runId, Boolean.FALSE, Boolean.TRUE)
                || cancelFlags.putIfAbsent(runId, Boolean.TRUE) == null;
    }

    /**
     * 检查指定 run 是否已取消。
     */
    public boolean isCancelled(Long runId) {
        return runId != null && Boolean.TRUE.equals(cancelFlags.get(runId));
    }

    /**
     * run 结束后清理标志。
     */
    public void unregister(Long runId) {
        if (runId != null) {
            cancelFlags.remove(runId);
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd FitMate-backend && mvn compile -pl FitMate-api -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentCancellationRegistry.java
git commit -m "feat: 新增 AgentCancellationRegistry 取消注册表"
```

---

### Task 3: AgentExecuteContext 添加取消标志

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/dto/AgentExecuteContext.java`

- [ ] **Step 1: 添加 cancelled 字段**

在 `AgentExecuteContext.java` 的 `accumulatedUsage` 字段后添加：

```java
    /** 用户主动取消标志，由 AgentCancellationRegistry 设置，Agent Loop 每次迭代检查。 */
    private volatile boolean cancelled = false;
```

由于 `@AllArgsConstructor` 会包含所有字段，需确认 `AgentExecuteServiceImpl.java:166-175` 构造调用处补上 `false` 参数。

- [ ] **Step 2: 更新 AgentExecuteServiceImpl 的构造调用**

在 `AgentExecuteServiceImpl.java` 第 166-175 行，`new AgentExecuteContext(...)` 调用末尾添加 `false`：

```java
            AgentExecuteContext context = new AgentExecuteContext(
                    runId,
                    session.getId(),
                    assistantMessageId,
                    lockKey,
                    lockOwner,
                    authenticatedUser,
                    chatEntity,
                    new TokenUsage(),
                    false
            );
```

- [ ] **Step 3: 编译验证**

Run: `cd FitMate-backend && mvn compile -pl FitMate-api -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/dto/AgentExecuteContext.java
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/application/impl/AgentExecuteServiceImpl.java
git commit -m "feat: AgentExecuteContext 添加 cancelled 取消标志"
```

---

### Task 4: AgentLoopExecutor 支持取消检查

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java`

- [ ] **Step 1: 注入 AgentCancellationRegistry**

在类字段区域（约第 96 行 `WikiProperties` 字段后）添加：

```java
    @Resource
    private AgentCancellationRegistry cancellationRegistry;
```

- [ ] **Step 2: run() 方法开头注册取消标志**

在 `run()` 方法第 99 行 `Instant runStarted = Instant.now();` 之前添加：

```java
        cancellationRegistry.register(context.getRunId());
```

- [ ] **Step 3: 在 Agent Loop 主循环每次迭代开头检查取消**

在 `run()` 方法第 131 行（超时检查之后）添加取消检查：

```java
            if (cancellationRegistry.isCancelled(context.getRunId())) {
                throw new AgentCancelledException(extractPartialContent(context));
            }
```

- [ ] **Step 4: 在 LLM 流式 forEach 内部检查取消**

在 `run()` 方法第 152 行的 `llmGateway.streamWithReasoning(prompt).toStream().forEach(chunk -> {` lambda 体最前面添加取消检查：

```java
                llmGateway.streamWithReasoning(prompt).toStream().forEach(chunk -> {
                    if (cancellationRegistry.isCancelled(context.getRunId())) {
                        throw new AgentCancelledException(extractPartialContent(context));
                    }
                    if (chunk == null) {
                        return;
                    }
                    // ... 原有逻辑不变
```

- [ ] **Step 5: 在工具调用前检查取消**

在 `run()` 方法第 209 行 `ToolCall toolCall = toToolCall(decision);` 之前添加：

```java
            if (cancellationRegistry.isCancelled(context.getRunId())) {
                throw new AgentCancelledException(extractPartialContent(context));
            }
```

- [ ] **Step 6: 在 finishWithAnswer 的 sendContentChunk 前检查取消**

在 `finishWithAnswer()` 方法第 280 行 `sendContentChunk(context, finalAnswer);` 之前添加：

```java
        if (cancellationRegistry.isCancelled(context.getRunId())) {
            throw new AgentCancelledException(extractPartialContent(context));
        }
```

- [ ] **Step 7: 添加 extractPartialContent 辅助方法**

在类末尾添加：

```java
    /**
     * 提取已生成的部分内容用于回填。
     * 当前实现返回空字符串，因为 Agent Loop 中最终答案是一次性生成的，
     * 取消时通常没有完整 finalAnswer；thinking 内容已通过 SSE 推送给前端。
     */
    private String extractPartialContent(AgentExecuteContext context) {
        return "";
    }
```

- [ ] **Step 8: 确认 import**

`AgentCancellationRegistry` 和 `AgentCancelledException` 都在 `com.itgeo.fitmate.api.agent.core` 包，而 `AgentLoopExecutor` 也在同一包下，因此**不需要额外 import**。

- [ ] **Step 9: 编译验证**

Run: `cd FitMate-backend && mvn compile -pl FitMate-api -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java
git commit -m "feat: AgentLoopExecutor 支持取消检查"
```

---

### Task 5: AgentAsyncServiceImpl 处理取消异常

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/application/impl/AgentAsyncServiceImpl.java`

- [ ] **Step 1: 注入 AgentCancellationRegistry**

在类字段区域（约第 72 行 `StringRedisTemplate` 字段后）添加：

```java
    @Resource
    private AgentCancellationRegistry cancellationRegistry;
```

- [ ] **Step 2: 在 executeAsync 的 try-catch 中新增 AgentCancelledException 分支**

将 `executeAsync` 方法第 93-104 行的 try-catch-finally 改为：

```java
        try {
            agentRunService.markRunRunning(context.getRunId());
            agentLoopExecutor.run(context);
        } catch (AgentCancelledException e) {
            log.info("Agent执行被用户取消, runId={}", context.getRunId());
            handleCancellation(context, e.getPartialContent());
        } catch (Exception e) {
            log.error("Agent异步执行失败, runId={}", context.getRunId(), e);
            String failedMessage = "任务执行失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage());
            agentRunService.markRunFailed(context.getRunId(), failedMessage);
            sendFailureFinish(context, failedMessage);
        } finally {
            cancellationRegistry.unregister(context.getRunId());
            stopRenewTask(renewFuture, renewExecutor);
            releaseLock(context.getLockKey(), context.getLockOwner());
        }
```

- [ ] **Step 3: 添加 handleCancellation 方法**

在 `sendFailureFinish` 方法之后添加：

```java
    private void handleCancellation(AgentExecuteContext context, String partialContent) {
        // 1. 回填 assistant 消息：部分内容 + "已中断"标注
        String displayContent = (partialContent == null ? "" : partialContent);
        if (displayContent.isBlank()) {
            displayContent = "> ⚠️ **已中断** — 用户主动停止了生成。";
        } else {
            displayContent = displayContent + "\n\n> ⚠️ **已中断** — 用户主动停止了生成。";
        }
        try {
            chatSessionService.finishAssistantMessage(
                    context.getAssistantMessageId(),
                    displayContent,
                    null
            );
        } catch (Exception ex) {
            log.warn("回填中断消息失败, runId={}", context.getRunId(), ex);
        }

        // 2. 标记 run 为 cancelled
        agentRunService.markRunCancelled(context.getRunId(), "用户主动取消");

        // 3. 推送 interrupted FINISH 事件给前端
        AgentFinishResponse interrupted = new AgentFinishResponse(
                displayContent,
                context.getChatEntity() == null ? null : context.getChatEntity().getBotMsgId(),
                context.getRunId(),
                "interrupted",
                null,
                context.getChatSessionId(),
                context.getChatEntity() == null ? null : context.getChatEntity().getSessionCode(),
                context.getAccumulatedUsage()
        );
        SSEServer.sendMsg(
                context.getAuthenticatedUser().getSseClientId(),
                JSONUtil.toJsonStr(interrupted),
                SSEMsgType.FINISH
        );
    }
```

- [ ] **Step 4: 添加 import**

文件顶部添加：

```java
import com.itgeo.fitmate.api.agent.core.AgentCancellationRegistry;
import com.itgeo.fitmate.api.agent.core.AgentCancelledException;
```

- [ ] **Step 5: 编译验证**

Run: `cd FitMate-backend && mvn compile -pl FitMate-api -am -q`
Expected: BUILD SUCCESS（注意：markRunCancelled 尚未实现，此步编译会失败，需先完成 Task 6）

- [ ] **Step 6: Commit（在 Task 6 完成后一起提交）**

---

### Task 6: AgentRunService 新增 markRunCancelled

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/application/AgentRunService.java`
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/application/impl/AgentRunServiceImpl.java`

- [ ] **Step 1: 在 AgentRunService 接口添加方法声明**

在 `markRunFailed` 方法声明之后添加：

```java
    /**
     * 将 run 状态更新为 cancelled，并记录取消原因。
     *
     * @param runId run 主记录ID
     * @param reason 取消原因
     */
    void markRunCancelled(Long runId, String reason);
```

- [ ] **Step 2: 在 AgentRunServiceImpl 实现该方法**

在 `markRunFailed` 实现方法之后添加（参照 `markRunFailed` 的实现模式，将状态设为 `"cancelled"`）：

```java
    @Override
    public void markRunCancelled(Long runId, String reason) {
        if (runId == null) {
            return;
        }
        AgentRun update = new AgentRun();
        update.setId(runId);
        update.setStatus("cancelled");
        update.setErrorMessage(reason);
        update.setFinishedAt(java.time.LocalDateTime.now());
        agentRunMapper.updateById(update);
    }
```

注意：需确认 `AgentRun` 实体有 `errorMessage` 和 `finishedAt` 字段。如果没有 `finishedAt`，参照 `markRunFailed` 的实现省略该字段。实现时先读 `AgentRunServiceImpl.markRunFailed` 确认字段名。

- [ ] **Step 3: 编译验证**

Run: `cd FitMate-backend && mvn compile -pl FitMate-api -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit（与 Task 5 一起提交）**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/application/AgentRunService.java
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/application/impl/AgentRunServiceImpl.java
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/application/impl/AgentAsyncServiceImpl.java
git commit -m "feat: AgentAsyncService 处理取消异常并回填中断标注"
```

---

### Task 7: 新增 POST /agent/cancel 接口

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/controller/AgentController.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/application/AgentCancelService.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/application/impl/AgentCancelServiceImpl.java`

- [ ] **Step 1: 创建 AgentCancelService 接口**

```java
package com.itgeo.fitmate.api.agent.application;

/**
 * Agent 取消服务契约。
 */
public interface AgentCancelService {
    /**
     * 取消指定 runId 的 Agent 执行。
     *
     * @param userId 当前用户ID（权限校验用）
     * @param runId 要取消的 run ID
     * @return true 表示已成功设置取消标志
     */
    boolean cancel(Long userId, Long runId);
}
```

- [ ] **Step 2: 创建 AgentCancelServiceImpl**

```java
package com.itgeo.fitmate.api.agent.application.impl;

import com.itgeo.fitmate.api.agent.application.AgentCancelService;
import com.itgeo.fitmate.api.agent.application.AgentRunService;
import com.itgeo.fitmate.api.agent.core.AgentCancellationRegistry;
import com.itgeo.fitmate.api.agent.infrastructure.entity.AgentRun;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AgentCancelServiceImpl implements AgentCancelService {

    @Resource
    private AgentCancellationRegistry cancellationRegistry;

    @Resource
    private AgentRunService agentRunService;

    @Override
    public boolean cancel(Long userId, Long runId) {
        if (userId == null || runId == null) {
            return false;
        }
        // 权限校验：确认 run 归属当前用户
        AgentRun run = agentRunService.findByIdAndUserId(userId, runId);
        if (run == null) {
            log.warn("取消Agent失败: run不存在或无权访问, userId={}, runId={}", userId, runId);
            return false;
        }
        // 仅 running/pending 状态可取消
        String status = run.getStatus();
        if (!"running".equals(status) && !"pending".equals(status)) {
            log.info("取消Agent跳过: run已结束, runId={}, status={}", runId, status);
            return false;
        }
        cancellationRegistry.cancel(runId);
        return true;
    }
}
```

注意：需确认 `AgentRunService` 是否有 `findByIdAndUserId` 方法。如果没有，需在 `AgentRunService` 中新增：
```java
    AgentRun findByIdAndUserId(Long userId, Long runId);
```
并在 `AgentRunServiceImpl` 中实现（通过 `agentRunMapper` 查询 `userId` 和 `id`）。实现时先检查是否已有此方法。

- [ ] **Step 3: 在 AgentController 添加 cancel 接口**

在 `AgentController.java` 中添加字段注入和接口方法：

```java
    @Resource
    private AgentCancelService agentCancelService;

    /**
     * 取消正在执行的 Agent 任务。
     *
     * @param runId 要取消的 run ID
     * @return 取消结果
     */
    @PostMapping("/cancel")
    public LeeResult cancel(@RequestParam Long runId) {
        try {
            AuthenticatedUserContext authenticatedUser = UserContextHolder.getRequired();
            boolean cancelled = agentCancelService.cancel(authenticatedUser.getUserId(), runId);
            return LeeResult.ok(cancelled);
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("取消Agent任务失败, runId={}", runId, e);
            return LeeResult.errorException("取消Agent任务失败");
        }
    }
```

- [ ] **Step 4: 编译验证**

Run: `cd FitMate-backend && mvn compile -pl FitMate-api -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/
git commit -m "feat: 新增 POST /agent/cancel 接口支持取消正在执行的Agent任务"
```

---

### Task 8: ChatSessionService 新增消息删除方法

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/ChatSessionService.java`
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/impl/ChatSessionServiceImpl.java`

- [ ] **Step 1: 在 ChatSessionService 接口添加方法声明**

在接口末尾添加：

```java
    /**
     * 删除指定 botMsgId 对应的用户消息及其之后的所有消息（含 assistant 回复）。
     * 同时清理覆盖该范围的上下文压缩摘要，并更新会话的 lastBotMsgId。
     *
     * @param userId 用户ID（权限校验）
     * @param sessionId 会话ID
     * @param botMsgId 机器人消息ID，用于定位 assistant 消息的 seqNo
     * @return 实际删除的消息条数
     */
    int deleteMessagesFromBotMsgId(Long userId, Long sessionId, String botMsgId);
```

- [ ] **Step 2: 在 ChatSessionServiceImpl 实现该方法**

在类中添加（需补充 `ContextSummaryMapper` 的删除操作和 `ChatSessionMapper` 的更新操作）：

```java
    @Override
    public int deleteMessagesFromBotMsgId(Long userId, Long sessionId, String botMsgId) {
        // 1. 校验会话归属
        if (userId == null || sessionId == null || StrUtil.isBlank(botMsgId)) {
            throw new IllegalArgumentException("参数不能为空");
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new IllegalArgumentException("会话不存在或无权访问");
        }

        // 2. 通过 botMsgId 找到 assistant 消息，获取其 seqNo
        ChatMessage assistantMsg = chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getBotMsgId, botMsgId.trim())
                        .last("limit 1")
        );
        if (assistantMsg == null) {
            return 0;
        }
        int assistantSeqNo = assistantMsg.getSeqNo();
        // 用户消息的 seqNo = assistantSeqNo - 1（受理时先写 user 再写 assistant 占位）
        int rollbackFromSeqNo = assistantSeqNo - 1;

        // 3. 删除 seqNo >= rollbackFromSeqNo 的所有消息
        int deletedMessages = chatMessageMapper.delete(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .ge(ChatMessage::getSeqNo, rollbackFromSeqNo)
        );

        // 4. 清理覆盖回滚范围的上下文压缩摘要
        //    compressedToSeq >= rollbackFromSeqNo 的摘要需要删除（它们压缩的范围已被部分删除）
        contextSummaryMapper.delete(
                new LambdaQueryWrapper<ContextSummary>()
                        .eq(ContextSummary::getSessionId, sessionId)
                        .ge(ContextSummary::getCompressedToSeq, rollbackFromSeqNo)
        );

        // 5. 更新会话的 lastBotMsgId：找到剩余消息中最后一条 assistant 消息的 botMsgId
        ChatMessage lastAssistant = chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getRole, "assistant")
                        .orderByDesc(ChatMessage::getSeqNo)
                        .last("limit 1")
        );
        String newLastBotMsgId = lastAssistant != null ? lastAssistant.getBotMsgId() : null;
        ChatSession sessionUpdate = new ChatSession();
        sessionUpdate.setId(sessionId);
        sessionUpdate.setLastBotMsgId(newLastBotMsgId);
        chatSessionMapper.updateById(sessionUpdate);

        return deletedMessages;
    }
```

注意：
- 需确认 `ChatSession` 实体有 `userId` 字段（用于归属校验）。实现时先读 `ChatSession.java` 确认。
- 需确认 `ContextSummary` 实体有 `sessionId` 和 `compressedToSeq` 字段。实现时先读 `ContextSummary.java` 确认字段名。
- 需添加 import: `com.itgeo.fitmate.api.chat.infrastructure.entity.ContextSummary`

- [ ] **Step 3: 编译验证**

Run: `cd FitMate-backend && mvn compile -pl FitMate-api -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/ChatSessionService.java
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/impl/ChatSessionServiceImpl.java
git commit -m "feat: ChatSessionService 新增按 botMsgId 回滚删除消息方法"
```

---

### Task 9: 新增 POST /chat/rollback 接口

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/controller/ChatController.java`

- [ ] **Step 1: 在 ChatController 添加 rollback 接口**

```java
    /**
     * 回滚：删除指定 botMsgId 对应的用户消息及其之后的所有消息。
     * 用于「重试」功能——把用户消息内容回填输入框后，删除该消息及之后的历史。
     *
     * @param body 请求体，包含 sessionId 和 botMsgId
     * @return 删除的消息条数
     */
    @PostMapping("/rollback")
    public LeeResult rollback(@RequestBody RollbackRequest body) {
        try {
            AuthenticatedUserContext user = UserContextHolder.getRequired();
            if (body == null || body.getSessionId() == null || body.getBotMsgId() == null) {
                return LeeResult.errorMsg("sessionId和botMsgId不能为空");
            }
            int deleted = chatSessionService.deleteMessagesFromBotMsgId(
                    user.getUserId(),
                    body.getSessionId(),
                    body.getBotMsgId()
            );
            return LeeResult.ok(deleted);
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("回滚消息失败", e);
            return LeeResult.errorException("回滚消息失败");
        }
    }
```

- [ ] **Step 2: 创建 RollbackRequest DTO**

在 `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/dto/` 下创建：

```java
package com.itgeo.fitmate.api.chat.dto;

import lombok.Data;

/**
 * 回滚请求体。
 */
@Data
public class RollbackRequest {
    /** 会话ID。 */
    private Long sessionId;
    /** 机器人消息ID，用于定位要回滚的位置。 */
    private String botMsgId;
}
```

- [ ] **Step 3: 编译验证**

Run: `cd FitMate-backend && mvn compile -pl FitMate-api -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/controller/ChatController.java
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/dto/RollbackRequest.java
git commit -m "feat: 新增 POST /chat/rollback 接口支持消息回滚"
```

---

### Task 10: 前端 doctorApi.ts 新增 API 函数

**Files:**
- Modify: `FitMate-frontend/src/services/doctorApi.ts`

- [ ] **Step 1: 添加 cancelAgent 和 rollbackMessage 函数**

在 `compressContext` 函数之后添加：

```ts
export function cancelAgent(runId: number | string) {
  return instance({
    url: "/agent/cancel",
    method: "post",
    params: { runId },
  });
}

export function rollbackMessage(sessionId: number | string, botMsgId: string) {
  return instance({
    url: "/chat/rollback",
    method: "post",
    data: { sessionId, botMsgId },
  });
}
```

- [ ] **Step 2: 在 doctorApi 对象中注册**

在 `doctorApi` 对象（约第 226 行）中添加：

```ts
const doctorApi = {
  // ... 已有项
  cancelAgent,
  rollbackMessage,
};
```

- [ ] **Step 3: Commit**

```bash
git add FitMate-frontend/src/services/doctorApi.ts
git commit -m "feat: doctorApi 新增 cancelAgent 和 rollbackMessage API"
```

---

### Task 11: ChatInput.vue 发送按钮切换为停止按钮

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/components/ChatInput.vue`

- [ ] **Step 1: 修改发送按钮模板**

将第 50-61 行的发送按钮改为：

```html
      <button
        v-if="!(isSending || isStreaming)"
        type="button"
        class="chat-send-btn"
        :aria-label="'发送任务'"
        @click="$emit('send')"
      >
        <span class="material-symbols-outlined">arrow_upward</span>
      </button>
      <button
        v-else
        type="button"
        class="chat-stop-btn"
        :aria-label="'停止生成'"
        @click="$emit('stop')"
      >
        <span class="material-symbols-outlined">stop_circle</span>
      </button>
```

- [ ] **Step 2: 添加停止按钮样式**

在 `<style>` 区域的 `.chat-send-btn` 样式之后添加：

```css
.chat-stop-btn {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: #ef4444;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}
.chat-stop-btn:hover {
  background: #dc2626;
}
.chat-stop-btn .material-symbols-outlined {
  font-size: 20px;
}
```

- [ ] **Step 3: 在 props 中添加 stop 不需要额外声明（$emit 自动传递）**

确认 `ChatPage.vue` 中 `<ChatInput>` 组件的 `@send` 旁边添加 `@stop` 监听。

- [ ] **Step 4: Commit**

```bash
git add FitMate-frontend/src/pages/chat/components/ChatInput.vue
git commit -m "feat: ChatInput 发送按钮在流式期间切换为停止按钮"
```

---

### Task 12: ChatPage.vue 连接 stop 事件

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatPage.vue`

- [ ] **Step 1: 在 ChatInput 组件上添加 @stop 监听**

找到 `<ChatInput` 标签，在 `@send="doChat"` 旁边添加 `@stop="stopGeneration"`：

```html
      <ChatInput
        ...
        @send="doChat"
        @stop="stopGeneration"
        ...
      />
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatPage.vue
git commit -m "feat: ChatPage 连接 stop 事件到 stopGeneration"
```

---

### Task 13: ChatLogicBase.vue 新增 stopGeneration 方法

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`

- [ ] **Step 1: 添加 stopGeneration 方法**

在 `retryUserMessage` 方法之前（约第 2277 行）添加：

```js
    async stopGeneration() {
      // 获取当前 runId
      var runId =
        (this.activeAgentRun && this.activeAgentRun.runId) || null;
      if (!runId) {
        // 没有 runId 时仅做前端状态重置
        this.botMsgId = null;
        this.isSending = false;
        this.isStreaming = false;
        this.isThinking = false;
        this.guidanceMessage = "已停止生成。";
        return;
      }

      this.guidanceMessage = "正在停止生成…";
      try {
        await doctorApi.cancelAgent(runId);
      } catch (e) {
        console.error("取消Agent请求失败:", e);
      }

      // 前端状态立即重置（后端会推送 interrupted FINISH 事件做最终收尾）
      // 但如果 SSE 通道已断开，FINISH 可能收不到，所以这里也做兜底重置
      // 注意：不完全重置 isStreaming，等 FINISH 事件或超时后再重置
      // 设置一个超时兜底：5秒后如果没收到 FINISH，强制重置
      var me = this;
      if (this._stopTimeout) {
        clearTimeout(this._stopTimeout);
      }
      this._stopTimeout = setTimeout(function () {
        if (me.isStreaming || me.isSending) {
          console.warn("停止超时，强制重置状态");
          me.botMsgId = null;
          me.isSending = false;
          me.isStreaming = false;
          me.isThinking = false;
          me.guidanceMessage = "已停止生成。";
        }
      }, 5000);
    },
```

- [ ] **Step 2: 在 data() 中添加 _stopTimeout 不需要声明（非响应式）**

`_stopTimeout` 是非响应式的实例属性，直接用 `this._stopTimeout` 赋值即可，无需在 `data()` 中声明。

- [ ] **Step 3: 在 beforeUnmount 中清理超时**

在 `beforeUnmount` 钩子中添加清理：

```js
    beforeUnmount() {
      if (this._stopTimeout) {
        clearTimeout(this._stopTimeout);
      }
      // ... 已有的清理逻辑
    },
```

- [ ] **Step 4: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "feat: ChatLogicBase 新增 stopGeneration 方法"
```

---

### Task 14: ChatLogicBase.vue 改造 applyFinishPayload 处理 interrupted 状态

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`

- [ ] **Step 1: 在 applyFinishPayload 中识别 interrupted 状态**

在 `applyFinishPayload` 方法（约第 1598 行）开头添加中断标志提取和停止超时清理：

```js
    applyFinishPayload(payload) {
      var isInterrupted = payload && payload.status === "interrupted";

      // 清理停止超时（stopGeneration 设置的 5 秒兜底）
      if (this._stopTimeout) {
        clearTimeout(this._stopTimeout);
        this._stopTimeout = null;
      }

      // ... 已有的 usage 解析等逻辑保持不变
```

然后在遍历 `chatList` 更新 bot 消息的地方（约第 1621-1633 行），给匹配的 bot 消息添加 `interrupted` 字段。无论是否中断，content 都统一用 `marked.parse` 渲染（后端返回的中断消息也是 Markdown 文本）：

```js
          if (this.chatList[i].botMsgId == finishBotMsgId) {
            this.chatList[i].content = marked.parse(message);
            this.chatList[i].interrupted = isInterrupted;
            // ... 已有的 sources、sourceType 等赋值保持不变
          }
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "feat: applyFinishPayload 支持 interrupted 状态标注"
```

---

### Task 15: ChatLogicBase.vue 改造 retryUserMessage（核心）

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`

- [ ] **Step 1: 改造 retryUserMessage 方法**

将第 2277-2287 行的 `retryUserMessage` 替换为：

```js
    async retryUserMessage(item) {
      var text = this.extractMessageText(item && item.content).trim();
      if (!text) {
        this.showUiMessage("error", "暂无可重试内容");
        return;
      }

      // 如果正在输出，先打断（串行：等打断完成再回滚）
      if (this.isSending || this.isStreaming) {
        this.guidanceMessage = "正在停止当前生成，请稍候…";
        await this.stopGeneration();

        // 等待 interrupted FINISH 事件到达（stopGeneration 已设置 5 秒超时兜底）
        // 这里用轮询等待 isStreaming 变为 false
        var waitCount = 0;
        while ((this.isStreaming || this.isSending) && waitCount < 60) {
          await new Promise(function (resolve) { setTimeout(resolve, 100); });
          waitCount++;
        }
      }

      // 执行回滚删除
      var botMsgId = item && item.botMsgId;
      var sessionId = this.activeChatSessionId;
      if (botMsgId && sessionId) {
        try {
          await doctorApi.rollbackMessage(sessionId, botMsgId);
        } catch (e) {
          console.error("回滚消息失败:", e);
          this.showUiMessage("error", "回滚失败，请稍后重试");
          return;
        }

        // 从前端 chatList 中移除该用户消息及之后的所有消息
        var rollbackIndex = -1;
        for (var i = 0; i < this.chatList.length; i++) {
          if (this.chatList[i].botMsgId === botMsgId) {
            // 找到 botMsgId 对应的 assistant 消息，用户消息在它前一条
            rollbackIndex = i - 1;
            break;
          }
        }
        if (rollbackIndex < 0) {
          // 兜底：如果没找到 botMsgId，尝试按消息内容匹配
          for (var j = 0; j < this.chatList.length; j++) {
            if (this.chatList[j].chatType === "user"
                && this.extractMessageText(this.chatList[j].content).trim() === text) {
              rollbackIndex = j;
              break;
            }
          }
        }
        if (rollbackIndex >= 0) {
          this.chatList.splice(rollbackIndex);
        }
      }

      // 回填输入框
      this.draftMessage = text;
      this.guidanceMessage = "已将历史任务填回输入框，可直接调整后再次发送。";
      this.focusInputPanel(true);
    },
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "feat: retryUserMessage 支持流式时先打断再回滚删除"
```

---

### Task 16: ChatLogicBase.vue doChat 保存 botMsgId 到用户消息

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`

- [ ] **Step 1: 在 doChat 的用户消息对象中添加 botMsgId**

在第 2433-2442 行的用户消息 push 对象中添加 `botMsgId` 字段：

```js
      this.chatList.push({
        id: "user-" + this.generateRandomId(8),
        content: pendingMsg,
        userName: currentUserName || "用户",
        chatType: "user",
        createdAt: new Date().toISOString(),
        sessionCode: this.currentSessionCode || null,
        sceneType: expectedSceneType,
        sourceType: currentSourceType,
        botMsgId: botMsgId,
      });
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "feat: doChat 用户消息对象保存 botMsgId 用于回滚定位"
```

---

### Task 17: ChatMessageList.vue 添加「已中断」标注展示

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/components/ChatMessageList.vue`

- [ ] **Step 1: 在 bot 消息气泡中添加中断标注**

在第 89 行 `<div class="msg-text markdown-body" v-html="item.content"></div>` 之后、`<source-card-list>` 之前添加：

```html
            <div v-if="item.interrupted" class="msg-interrupted-badge">
              <span class="material-symbols-outlined">pause_circle</span>
              <span>已中断</span>
            </div>
```

- [ ] **Step 2: 添加标注样式**

在 `<style>` 区域添加：

```css
.msg-interrupted-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  font-size: 12px;
}
.msg-interrupted-badge .material-symbols-outlined {
  font-size: 14px;
}
```

- [ ] **Step 3: Commit**

```bash
git add FitMate-frontend/src/pages/chat/components/ChatMessageList.vue
git commit -m "feat: ChatMessageList bot 消息添加已中断标注展示"
```

---

### Task 18: mapRecordToChatItem 保留 interrupted 标志

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`

- [ ] **Step 1: 在 mapRecordToChatItem 中识别中断消息**

在第 2107-2124 行的返回对象中添加 `interrupted` 字段。中断消息在后端回填时 content 包含 `> ⚠️ **已中断**`，可通过此特征识别：

```js
      return {
        id:
          message.messageId != null
            ? String(message.messageId)
            : "record-" + index + "-" + this.generateRandomId(6),
        content: role === "assistant" ? marked.parse(rawContent) : rawContent,
        userName: role === "assistant" ? "bot" : this.currentUserName || "用户",
        chatType: role === "assistant" ? "bot" : "user",
        botMsgId: message.botMsgId || null,
        createdAt: message.createdAt || new Date().toISOString(),
        sessionCode: message.sessionCode || null,
        sceneType: message.sceneType || null,
        sourceType: message.sourceType || null,
        sources:
          role === "assistant"
            ? this.parseRecordSources(message.sourcesJson)
            : [],
        interrupted: role === "assistant" && rawContent.indexOf("已中断") >= 0,
      };
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "feat: mapRecordToChatItem 识别并保留中断消息标志"
```

---

## 技术决策说明

### 1. 后端取消信号传播方式
采用「前端调取消接口 → 设置取消标志 → Agent Loop 检查标志」方案，而非「前端关闭 SSE 连接 → 后端回调感知」。原因：
- 调接口更明确可靠，前端能获得取消确认
- SSE 断开回调不携带 runId 信息，需要额外映射
- SSE 连接断开后前端无法接收 interrupted FINISH 事件

### 2. LLM 首字等待期间的取消
`ReasoningChatClient.stream()` 内部用 `httpClient.send()`（同步阻塞），在 HTTP 请求发出后、首个分片到达前无法中断。取消标志在 `forEach` 迭代时检查，因此首字等待期间（通常几秒）的取消会有延迟。这个限制可接受，不需要改造为 `sendAsync`。

### 3. 回滚定位用 botMsgId 而非 seqNo
前端 `mapRecordToChatItem` 没有保留 seqNo，但保留了 botMsgId。且 `doChat` 中用户消息原本没有 botMsgId（Task 16 补上）。用 botMsgId 统一定位，后端通过 botMsgId 找到 assistant 消息的 seqNo，再删除 seqNo >= (assistantSeqNo - 1) 的所有消息。

### 4. 回滚时清理压缩摘要
`t_context_summary` 中 `compressedToSeq >= 回滚点` 的摘要记录会被删除，因为它们压缩的范围已被部分删除，保留会导致数据不一致。

### 5. 中断后 assistant 消息内容
后端在 `handleCancellation` 中回填 content 为 `"> ⚠️ **已中断** — 用户主动停止了生成。"`（Markdown 格式），前端 `marked.parse` 渲染为引用块。如果有部分内容则拼接在前面。

### 6. 串行打断与回滚的等待机制
`retryUserMessage` 中用轮询等待 `isStreaming` 变为 false（每 100ms 检查一次，最多等 6 秒）。`stopGeneration` 内部也有 5 秒超时兜底。双保险确保不会无限等待。

---

## 风险和注意事项

1. **AgentRun 实体字段确认**：Task 6 的 `markRunCancelled` 需确认 `AgentRun` 有 `errorMessage` 和 `finishedAt` 字段。实现时先读 `AgentRun.java`。
2. **AgentRunService.findByIdAndUserId**：Task 7 的取消服务需要此方法。如果不存在，需新增。
3. **ChatSession.userId 字段**：Task 8 的归属校验需要此字段。实现时先读 `ChatSession.java` 确认。
4. **ContextSummary 字段名**：Task 8 需确认 `ContextSummary` 实体的 `sessionId` 和 `compressedToSeq` 字段名。
5. **并发安全**：如果用户在停止后立即重新发送，`doChat` 的 `isSending || isStreaming` 守卫会阻止。需确保 `stopGeneration` 正确重置这些标志。
6. **前端轮询等待的边界**：`retryUserMessage` 中最多等 6 秒（60 × 100ms），如果后端取消超过 6 秒，回滚仍会执行（因为超时后 `isStreaming` 已被 `stopGeneration` 的 5 秒超时兜底重置为 false）。
7. **中断消息的 Markdown 渲染**：后端回填的中断消息是 Markdown 文本，前端 `applyFinishPayload` 统一用 `marked.parse` 渲染。

---

## 验证步骤

### 后端验证
1. `mvn compile -pl FitMate-api -am -q` 编译通过
2. 启动后端服务

### 功能验证 — 输出打断
1. 发送一条消息，在模型输出期间点击停止按钮
2. 确认：流式输出停止、bot 消息显示「已中断」标注、输入框恢复可用状态
3. 确认：数据库中 assistant 消息 content 包含「已中断」标注

### 功能验证 — 输入历史回滚（非流式）
1. 完成一轮对话后，点击某条用户消息的重试按钮
2. 确认：该用户消息及之后的 bot 回复从列表消失
3. 确认：输入框回填该用户消息内容
4. 确认：数据库中对应消息已删除
5. 编辑内容后重新发送，确认新消息正常创建

### 功能验证 — 输入历史回滚（流式中）
1. 发送消息，在模型输出期间点击重试按钮
2. 确认：先停止生成（显示「正在停止」），再执行回滚删除
3. 确认：输入框回填内容、消息列表已截断

### 持久化验证
1. 回滚后刷新页面/切换会话再切回
2. 确认：被回滚的消息没有重新出现
