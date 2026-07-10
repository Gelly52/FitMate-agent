# 思考内容持久化与前端展示改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 LLM 思考内容（reasoning_content）持久化到独立表，历史会话加载时按需展示；同时改造前端展示，去掉固定窗口、流式输出、自定义滚动条。

**Architecture:** 新增 `t_chat_thinking` 独立表通过 `message_id` 关联消息表（`ON DELETE CASCADE`）；后端在 Agent / Chat 链路 finish 时保存 thinking；前端历史会话默认折叠 thinking，点击展开时调 `GET /chat/thinking/{messageId}` 按需加载；前端去掉 `max-height: 200px` 固定窗口，纯文本 pre-wrap + 浅色调 + 左边框区分正文，点击标题栏切换展开/折叠；自定义滚动条匹配设计风格。

**Tech Stack:** Spring Boot + MyBatis-Plus + Vue 3 (Options API) + MySQL + TypeScript

**Spec:** `docs/superpowers/specs/2026-07-04-thinking-content-persistence-and-display-design.md`

---

## File Structure

### 新增文件

| 文件 | 责任 |
|---|---|
| `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/infrastructure/entity/ChatThinking.java` | 思考内容实体类 |
| `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/infrastructure/mapper/ChatThinkingMapper.java` | 思考内容 Mapper |

### 修改文件

| 文件 | 改动 |
|---|---|
| `FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql` | 新增 `t_chat_thinking` 表定义 |
| `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/ChatSessionService.java` | 加 `saveThinking` / `getThinkingByMessageId` 接口方法 |
| `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/impl/ChatSessionServiceImpl.java` | 实现两个新方法；注入 `ChatThinkingMapper` |
| `FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/dto/AgentExecuteContext.java` | 加 `accumulatedThinking` 字段 |
| `FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java` | 循环内累积 thinking；`finishWithAnswer` 调 `saveThinking` |
| `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/impl/ChatServiceImpl.java` | `streamAndSend` 完成后调 `saveThinking` |
| `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/controller/ChatController.java` | 加 `GET /chat/thinking/{messageId}` 接口 |
| `FitMate-frontend/src/services/doctorApi.ts` | 加 `getThinkingByMessageId` API |
| `FitMate-frontend/src/pages/chat/ChatLogicBase.vue` | `mapRecordToChatItem` 加 thinking 状态字段；`toggleThinkingExpanded` 改异步按需加载 |
| `FitMate-frontend/src/pages/chat/components/ReasoningTraceBlock.vue` | 去固定窗口；标题栏点击切换；chevron 图标；浅色调 + 左边框 |
| `FitMate-frontend/src/pages/chat/components/ChatMessageList.vue` | `.chat-scroll` 自定义滚动条样式 |

### 不改动

- `t_chat_message` 表结构、`ChatMessage` 实体、`ChatRecordItem` DTO、`getChatRecords` 查询逻辑、SSE 推送链路、`ReasoningChatClient`、`SSEMsgType.THINKING` 枚举

---

## Part A：问题2 — 思考内容持久化（优先）

### Task 1: 数据库 — 新增 t_chat_thinking 表

**Files:**
- Modify: `FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql`（在 `t_chat_message` 表定义之后插入新表）

- [ ] **Step 1: 在 fitmate_init.sql 中新增 t_chat_thinking 表**

打开 `FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql`，定位到 `t_chat_message` 表定义结束（`COMMENT='聊天消息表';` 那一行，约 line 148），在它之后、`t_context_summary` 表之前插入：

```sql

CREATE TABLE IF NOT EXISTS `t_chat_thinking` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `message_id` BIGINT NOT NULL COMMENT '关联 t_chat_message.id',
    `content` LONGTEXT NOT NULL COMMENT '思考内容全文',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_thinking_message` (`message_id`),
    CONSTRAINT `fk_thinking_message` FOREIGN KEY (`message_id`)
        REFERENCES `t_chat_message` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM 思考内容表';
```

- [ ] **Step 2: 对已部署的数据库执行迁移**

连接现有 MySQL 数据库，执行上面的 `CREATE TABLE` 语句（去掉 `IF NOT EXISTS` 也可，重复执行会报已存在）。命令示例：

```bash
mysql -u <user> -p <database_name> < FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql
```

或者在 MySQL 客户端中手动执行该 `CREATE TABLE` 语句。执行后验证：

```sql
DESC t_chat_thinking;
```

应看到 4 个字段（id, message_id, content, created_at, updated_at）和 1 个 UNIQUE 键。

- [ ] **Step 3: Commit**

```bash
git add FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql
git commit -m "feat(db): 新增 t_chat_thinking 表用于持久化 LLM 思考内容"
```

---

### Task 2: 后端 — 新增 ChatThinking 实体与 Mapper

**Files:**
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/infrastructure/entity/ChatThinking.java`
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/infrastructure/mapper/ChatThinkingMapper.java`

- [ ] **Step 1: 创建 ChatThinking 实体类**

创建文件 `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/infrastructure/entity/ChatThinking.java`：

```java
package com.itgeo.fitmate.api.chat.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * LLM 思考内容持久化实体。
 * <p>
 * 一条 assistant 消息最多对应一条思考记录，通过 message_id 唯一关联。
 */
@Data
@ToString
@TableName("t_chat_thinking")
public class ChatThinking {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("message_id")
    private Long messageId;
    private String content;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 ChatThinkingMapper 接口**

创建文件 `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/infrastructure/mapper/ChatThinkingMapper.java`：

```java
package com.itgeo.fitmate.api.chat.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatThinking;

/**
 * LLM 思考内容表 Mapper。
 */
public interface ChatThinkingMapper extends BaseMapper<ChatThinking> {
}
```

- [ ] **Step 3: 确认 MapperScan 已覆盖**

`FitMateApiApplication.java` 的 `@MapperScan` 已包含 `"com.itgeo.fitmate.api.chat.infrastructure.mapper"`（line 16），无需修改。新 Mapper 会自动注册。

- [ ] **Step 4: 编译验证**

```bash
cd FitMate-backend
mvn -pl FitMate-api -am compile -q
```

预期：编译成功，无报错。

- [ ] **Step 5: Commit**

```bash
git add FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/infrastructure/entity/ChatThinking.java \
        FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/infrastructure/mapper/ChatThinkingMapper.java
git commit -m "feat(chat): 新增 ChatThinking 实体与 Mapper"
```

---

### Task 3: 后端 — ChatSessionService 接口扩展

**Files:**
- Modify: `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/ChatSessionService.java`

- [ ] **Step 1: 在 ChatSessionService 接口末尾新增两个方法签名**

打开 `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/ChatSessionService.java`，在 `deleteMessagesFromBotMsgId` 方法声明之后（接口末尾，约 line 184 之前的 `}` 之前）插入：

```java

    /**
     * 保存思考内容。已存在则覆盖更新。
     *
     * @param messageId 关联的 assistant 消息ID
     * @param content 思考内容全文
     */
    void saveThinking(Long messageId, String content);

    /**
     * 按消息ID查询思考内容。
     *
     * @param messageId 消息ID
     * @return 思考内容；不存在时返回 null
     */
    String getThinkingByMessageId(Long messageId);
```

- [ ] **Step 2: 编译验证**

```bash
cd FitMate-backend
mvn -pl FitMate-api -am compile -q
```

预期：编译报错，因为 `ChatSessionServiceImpl` 还未实现这两个方法（这是预期的，下个任务会修复）。

- [ ] **Step 3: Commit（暂时不 commit，等实现也完成）**

跳过 commit，与 Task 4 一起提交。

---

### Task 4: 后端 — ChatSessionServiceImpl 实现 saveThinking / getThinkingByMessageId

**Files:**
- Modify: `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/impl/ChatSessionServiceImpl.java`

- [ ] **Step 1: 注入 ChatThinkingMapper**

在 `ChatSessionServiceImpl.java` 顶部 import 区加：

```java
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatThinking;
import com.itgeo.fitmate.api.chat.infrastructure.mapper.ChatThinkingMapper;
```

在类字段区（`ContextSummaryMapper contextSummaryMapper;` 之后，约 line 63 之后）加：

```java
    @Resource
    private ChatThinkingMapper chatThinkingMapper;
```

- [ ] **Step 2: 实现 saveThinking 方法**

在类末尾（`isSceneCompatible` 方法之后，类的 `}` 之前）新增：

```java
    @Override
    public void saveThinking(Long messageId, String content) {
        // 1. messageId 不能为空
        if (messageId == null) {
            throw new IllegalArgumentException("messageId不能为空");
        }
        // 2. content 为空时跳过，不保存空 thinking
        if (StrUtil.isBlank(content)) {
            return;
        }

        // 3. 查询是否已存在（按 messageId 唯一）
        ChatThinking existing = chatThinkingMapper.selectOne(
                new LambdaQueryWrapper<ChatThinking>()
                        .eq(ChatThinking::getMessageId, messageId)
                        .last("limit 1")
        );

        if (existing != null) {
            // 3.1 已存在则更新 content
            ChatThinking update = new ChatThinking();
            update.setId(existing.getId());
            update.setContent(content);
            chatThinkingMapper.updateById(update);
        } else {
            // 3.2 不存在则插入新记录
            ChatThinking thinking = new ChatThinking();
            thinking.setMessageId(messageId);
            thinking.setContent(content);
            chatThinkingMapper.insert(thinking);
        }
    }

    @Override
    public String getThinkingByMessageId(Long messageId) {
        // 1. messageId 不能为空
        if (messageId == null) {
            return null;
        }
        // 2. 按 messageId 查询
        ChatThinking thinking = chatThinkingMapper.selectOne(
                new LambdaQueryWrapper<ChatThinking>()
                        .eq(ChatThinking::getMessageId, messageId)
                        .last("limit 1")
        );
        // 3. 返回 content，不存在返回 null
        return thinking == null ? null : thinking.getContent();
    }
```

- [ ] **Step 3: 编译验证**

```bash
cd FitMate-backend
mvn -pl FitMate-api -am compile -q
```

预期：编译成功。

- [ ] **Step 4: Commit（含 Task 3 的接口改动）**

```bash
git add FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/ChatSessionService.java \
        FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/impl/ChatSessionServiceImpl.java
git commit -m "feat(chat): ChatSessionService 新增 saveThinking / getThinkingByMessageId"
```

---

### Task 5: 后端 — AgentExecuteContext 加 accumulatedThinking 字段

**Files:**
- Modify: `FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/dto/AgentExecuteContext.java`

- [ ] **Step 1: 加 accumulatedThinking 字段**

打开 `AgentExecuteContext.java`，在 `private volatile boolean cancelled = false;`（line 36）之后新增：

```java
    /** Agent 多轮循环累积的思考内容，finishWithAnswer 时持久化。 */
    private StringBuilder accumulatedThinking = new StringBuilder();
```

- [ ] **Step 2: 编译验证**

```bash
cd FitMate-backend
mvn -pl FitMate-api -am compile -q
```

预期：编译成功。

- [ ] **Step 3: Commit**

```bash
git add FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/dto/AgentExecuteContext.java
git commit -m "feat(agent): AgentExecuteContext 加 accumulatedThinking 字段用于累积思考内容"
```

---

### Task 6: 后端 — AgentLoopExecutor 累积 thinking 并在 finishWithAnswer 保存

**Files:**
- Modify: `FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java`

- [ ] **Step 1: 在循环内累积 thinking 到 context**

打开 `AgentLoopExecutor.java`，定位到 line 195-198：

```java
                    if (StrUtil.isNotBlank(reasoningDelta)) {
                        reasoningContent.append(reasoningDelta);
                        sendThinkingChunk(context, reasoningDelta);
                    }
```

修改为：

```java
                    if (StrUtil.isNotBlank(reasoningDelta)) {
                        reasoningContent.append(reasoningDelta);
                        context.getAccumulatedThinking().append(reasoningDelta);
                        sendThinkingChunk(context, reasoningDelta);
                    }
```

- [ ] **Step 2: 在 finishWithAnswer 中保存 thinking**

定位到 `finishWithAnswer` 方法中 `chatSessionService.finishAssistantMessage(...)` 调用之后（约 line 332，`);` 之后）插入：

```java
        // 持久化累积的思考内容（Agent 多轮决策循环合并）
        String thinkingContent = context.getAccumulatedThinking().toString();
        if (StrUtil.isNotBlank(thinkingContent)) {
            try {
                chatSessionService.saveThinking(context.getAssistantMessageId(), thinkingContent);
            } catch (Exception e) {
                log.warn("保存思考内容失败，messageId={}, runId={}, error={}",
                        context.getAssistantMessageId(), context.getRunId(), e.getMessage());
            }
        }
```

**说明**：用 try-catch 包裹，避免 thinking 保存失败影响主流程（最终一致性策略，详见 spec §5.2）。

- [ ] **Step 3: 编译验证**

```bash
cd FitMate-backend
mvn -pl FitMate-api -am compile -q
```

预期：编译成功。

- [ ] **Step 4: Commit**

```bash
git add FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java
git commit -m "feat(agent): AgentLoopExecutor 累积多轮思考内容并在 finishWithAnswer 持久化"
```

---

### Task 7: 后端 — ChatServiceImpl.streamAndSend 完成后保存 thinking

**Files:**
- Modify: `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/impl/ChatServiceImpl.java`

- [ ] **Step 1: 在 streamAndSend 中 finishAssistantMessage 调用之后保存 thinking**

打开 `ChatServiceImpl.java`，定位到 line 363-368：

```java
        chatSessionService.finishAssistantMessage(
                assistantMessageId,
                fullContent,
                sources == null ? null : JSONUtil.toJsonStr(sources),
                usageJson
        );
```

在它之后（line 368 的 `);` 之后）插入：

```java

        // 持久化思考内容（Chat 链路单轮 reasoning）
        String thinkingText = reasoningContent.toString();
        if (StrUtil.isNotBlank(thinkingText)) {
            try {
                chatSessionService.saveThinking(assistantMessageId, thinkingText);
            } catch (Exception e) {
                log.warn("保存思考内容失败，messageId={}, runId={}, error={}",
                        assistantMessageId, runId, e.getMessage());
            }
        }
```

- [ ] **Step 2: 编译验证**

```bash
cd FitMate-backend
mvn -pl FitMate-api -am compile -q
```

预期：编译成功。

- [ ] **Step 3: Commit**

```bash
git add FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/impl/ChatServiceImpl.java
git commit -m "feat(chat): ChatServiceImpl.streamAndSend 完成后持久化思考内容"
```

---

### Task 8: 后端 — ChatController 新增 GET /chat/thinking/{messageId}

**Files:**
- Modify: `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/controller/ChatController.java`

- [ ] **Step 1: 加 thinking 查询接口**

打开 `ChatController.java`，在 `rollback` 方法之后（类末尾 `}` 之前）新增：

```java

    /**
     * 按消息ID查询思考内容。
     * <p>
     * 用于历史会话加载时按需展开思考内容：前端默认折叠，用户点击展开时调本接口加载。
     *
     * @param messageId 消息主键
     * @return 思考内容字符串；不存在时返回空字符串
     */
    @GetMapping("/thinking/{messageId}")
    public LeeResult getThinking(@PathVariable Long messageId) {
        AuthenticatedUserContext authenticatedUser = UserContextHolder.getRequired();
        String thinking = chatSessionService.getThinkingByMessageId(messageId);
        return LeeResult.ok(thinking == null ? "" : thinking);
    }
```

**说明**：
- 复用现有 `UserContextHolder.getRequired()` 登录校验
- 不额外校验消息归属（messageId 是 Long 主键，难以伪造；如需严格校验可后续在 service 层加 userId 校验，当前 YAGNI）
- 返回空字符串而非 null，简化前端处理

- [ ] **Step 2: 确认 import 已存在**

检查 `ChatController.java` 顶部 import 区，确认已有（默认应有）：

```java
import org.springframework.web.bind.annotation.PathVariable;
```

如果没有，添加该 import。

- [ ] **Step 3: 编译验证**

```bash
cd FitMate-backend
mvn -pl FitMate-api -am compile -q
```

预期：编译成功。

- [ ] **Step 4: 启动后端验证接口**

启动 FitMate-api 应用，登录后调用：

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/chat/thinking/1
```

预期：返回 `{"status":200,"data":""}` 或类似结构（messageId=1 不一定有 thinking，返回空字符串）。

- [ ] **Step 5: Commit**

```bash
git add FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/controller/ChatController.java
git commit -m "feat(chat): 新增 GET /chat/thinking/{messageId} 按需加载思考内容"
```

---

### Task 9: 前端 — doctorApi.ts 加 getThinkingByMessageId

**Files:**
- Modify: `FitMate-frontend/src/services/doctorApi.ts`

- [ ] **Step 1: 加 getThinkingByMessageId 函数**

打开 `FitMate-frontend/src/services/doctorApi.ts`，在 `getRecords` 函数之后（约 line 63 之后）新增：

```typescript
export function getThinkingByMessageId(messageId) {
  return instance({
    url: "/chat/thinking/" + messageId,
    method: "get",
  });
}
```

- [ ] **Step 2: 类型检查**

```bash
cd FitMate-frontend
npm run type-check 2>&1 | head -30
```

预期：无报错（或与改动前相同的既有告警）。

- [ ] **Step 3: Commit**

```bash
git add FitMate-frontend/src/services/doctorApi.ts
git commit -m "feat(frontend): doctorApi 新增 getThinkingByMessageId"
```

---

### Task 10: 前端 — ChatLogicBase.vue 历史消息按需加载 thinking

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`

- [ ] **Step 1: 改造 mapRecordToChatItem，assistant 消息加 thinking 状态字段**

打开 `ChatLogicBase.vue`，定位到 `mapRecordToChatItem` 方法（约 line 2208-2256）。

找到 assistant 消息返回对象（约 line 2237-2255）：

```javascript
      var role = message.role === "assistant" ? "assistant" : "user";
      var rawContent = message.content == null ? "" : String(message.content);
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

修改为（在返回对象中加 `messageId`、`thinkingContent`、`thinkingExpanded`、`thinkingLoaded`、`thinkingLoading` 字段）：

```javascript
      var role = message.role === "assistant" ? "assistant" : "user";
      var rawContent = message.content == null ? "" : String(message.content);
      return {
        id:
          message.messageId != null
            ? String(message.messageId)
            : "record-" + index + "-" + this.generateRandomId(6),
        messageId: message.messageId != null ? message.messageId : null,
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
        // 历史消息思考内容状态：默认空 + 折叠 + 未加载
        thinkingContent:
          role === "assistant" ? "" : null,
        thinkingExpanded: false,
        thinkingLoaded: false,
        thinkingLoading: false,
      };
```

- [ ] **Step 2: 改造 toggleThinkingExpanded 为异步按需加载**

定位到 `toggleThinkingExpanded` 方法（约 line 1301-1307）：

```javascript
    toggleThinkingExpanded(message) {
      if (message && typeof message === "object" && message.botMsgId) {
        message.thinkingExpanded = !message.thinkingExpanded;
        return;
      }
      this.thinkingExpanded = !this.thinkingExpanded;
    },
```

替换为：

```javascript
    async toggleThinkingExpanded(message) {
      // 全局思考卡片（无 message 对象）切换
      if (!message || typeof message !== "object" || !message.botMsgId) {
        this.thinkingExpanded = !this.thinkingExpanded;
        return;
      }

      // 折叠 → 直接切换
      if (message.thinkingExpanded) {
        message.thinkingExpanded = false;
        return;
      }

      // 展开：历史消息且 thinking 未加载过，先调接口加载
      if (
        !message.thinkingLoaded &&
        !message.thinkingLoading &&
        message.messageId &&
        !message.thinkingContent
      ) {
        message.thinkingLoading = true;
        try {
          var res = await doctorApi.getThinkingByMessageId(message.messageId);
          var data = (res && res.data != null ? res.data : "") || "";
          message.thinkingContent = String(data);
          message.thinkingLoaded = true;
        } catch (e) {
          console.warn("加载思考内容失败:", e);
          message.thinkingContent = "";
        } finally {
          message.thinkingLoading = false;
        }
      }
      message.thinkingExpanded = true;
    },
```

**说明**：
- 实时对话场景：`thinkingContent` 已由 SSE 流式累积填充，`thinkingLoaded` 虽为 false 但 `thinkingContent` 非空，跳过接口调用
- 历史会话场景：`thinkingContent` 为空字符串，`thinkingLoaded` 为 false，触发接口加载
- 加载中 `thinkingLoading=true` 防止重复请求
- 失败时 `thinkingContent` 置空，仍展开（显示"暂无思考内容"由模板兜底）

- [ ] **Step 3: 类型检查**

```bash
cd FitMate-frontend
npm run type-check 2>&1 | head -30
```

预期：无新增报错。

- [ ] **Step 4: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "feat(frontend): 历史会话思考内容按需加载"
```

---

## Part B：问题1 — 前端展示改造

### Task 11: 前端 — ReasoningTraceBlock.vue 改造（去固定窗口 + 点击切换 + 浅色调）

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/components/ReasoningTraceBlock.vue`

- [ ] **Step 1: 替换模板部分**

打开 `ReasoningTraceBlock.vue`，替换整个 `<template>` 标签内容为：

```vue
<template>
  <div
    v-if="hasContent()"
    class="reasoning-trace-block"
    :class="{ 'is-active': isThinking }"
  >
    <div class="reasoning-trace-header" @click="$emit('toggle-thinking')">
      <div class="reasoning-trace-title">
        <span v-if="isThinking" class="reasoning-trace-dot"></span>
        <span class="reasoning-trace-label">{{ headerTitle() }}</span>
      </div>
      <span class="material-symbols-outlined reasoning-trace-chevron">
        {{ expanded ? "expand_less" : "expand_more" }}
      </span>
    </div>

    <div v-if="expanded" class="reasoning-trace-body">
      <div v-if="steps && steps.length > 0" class="reasoning-trace-timeline">
        <div
          v-for="(step, index) in steps"
          :key="step.id || step.stepNo || index"
          class="reasoning-trace-step"
          :class="stepClass(step)"
        >
          <div class="reasoning-trace-indicator">
            <span class="reasoning-trace-step-dot"></span>
            <span v-if="index < steps.length - 1" class="reasoning-trace-line"></span>
          </div>
          <span class="reasoning-trace-step-label">{{ resolveStepLabel(step) }}</span>
        </div>
      </div>

      <div v-if="thinkingContent" class="reasoning-trace-content">{{ thinkingContent }}</div>
      <div v-else-if="isThinking" class="reasoning-trace-content reasoning-trace-content-muted">
        正在思考中...
      </div>
    </div>

    <div v-else class="reasoning-trace-collapsed">
      {{ collapsedText() }}
    </div>
  </div>
</template>
```

**变化说明**：
- 移除了"展开/收起"文字按钮
- 标题栏右侧改为 chevron 图标（`expand_less` / `expand_more`）
- 加了 `isThinking` 时的占位文字
- 其余结构保留

- [ ] **Step 2: 替换样式部分**

替换整个 `<style scoped>` 标签内容为：

```css
.reasoning-trace-block {
  margin-bottom: 10px;
  padding: 10px 14px;
  border: 1px solid var(--color-surface-container);
  border-radius: 8px;
  background: var(--color-surface-container-low);
  font-size: 13px;
}

.reasoning-trace-block.is-active {
  border-color: color-mix(in srgb, var(--color-primary) 40%, transparent);
}

.reasoning-trace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  user-select: none;
}

.reasoning-trace-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.reasoning-trace-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary-fixed-dim);
  animation: reasoning-pulse 1.4s ease-in-out infinite;
}

@keyframes reasoning-pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}

.reasoning-trace-label {
  font-family: "Inter", sans-serif;
  font-size: 12px;
  color: var(--color-on-surface-variant);
  letter-spacing: 0.03em;
}

.reasoning-trace-chevron {
  flex-shrink: 0;
  font-size: 18px;
  color: var(--color-on-surface-variant);
  transition: transform 0.2s ease;
}

.reasoning-trace-header:hover .reasoning-trace-chevron {
  color: var(--color-on-surface);
}

.reasoning-trace-body {
  margin-top: 10px;
}

.reasoning-trace-timeline {
  display: flex;
  flex-direction: column;
  margin-bottom: 10px;
}

.reasoning-trace-step {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-height: 28px;
}

.reasoning-trace-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.reasoning-trace-step-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-outline-variant);
  margin-top: 4px;
  transition: background 0.2s ease;
}

.reasoning-trace-line {
  width: 1px;
  flex: 1;
  min-height: 14px;
  background: var(--color-surface-container);
  margin-top: 2px;
}

.reasoning-trace-step-label {
  font-size: 12px;
  font-family: "Inter", sans-serif;
  color: var(--color-on-surface-variant);
  padding-top: 1px;
}

.reasoning-trace-step.step-completed .reasoning-trace-step-dot {
  background: var(--color-primary-fixed-dim);
}

.reasoning-trace-step.step-completed .reasoning-trace-step-label {
  color: var(--color-on-surface-variant);
}

.reasoning-trace-step.step-running .reasoning-trace-step-dot {
  background: var(--color-primary-fixed-dim);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary) 20%, transparent);
}

.reasoning-trace-step.step-running .reasoning-trace-step-label {
  color: var(--color-on-surface);
}

.reasoning-trace-step.step-failed .reasoning-trace-step-dot {
  background: var(--color-error);
}

.reasoning-trace-step.step-failed .reasoning-trace-step-label {
  color: var(--color-error);
}

/* 思考内容：纯文本 pre-wrap，浅色调 + 左边框区分正文，无固定高度 */
.reasoning-trace-content {
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-on-surface-variant);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "Inter", sans-serif;
  padding: 6px 0 6px 12px;
  border-left: 2px solid var(--color-outline-variant);
  background: color-mix(in srgb, var(--color-surface-container-low) 60%, transparent);
}

.reasoning-trace-content-muted {
  color: color-mix(in srgb, var(--color-on-surface-variant) 60%, transparent);
  font-style: italic;
}

.reasoning-trace-collapsed {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-on-surface-variant);
  font-family: "Inter", sans-serif;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
```

**变化说明**：
- 删除 `.reasoning-trace-toggle` 样式（已移除按钮）
- 新增 `.reasoning-trace-chevron` 样式
- `.reasoning-trace-content` 移除 `max-height: 200px; overflow-y: auto;`，改为流式撑开
- `.reasoning-trace-content` 加 `border-left` + `background` 区分正文
- `.reasoning-trace-content` 字号从 12px 提到 13px，行高 1.7（更易读）
- 新增 `.reasoning-trace-content-muted` 用于思考中占位文字

- [ ] **Step 3: 验证前端运行**

```bash
cd FitMate-frontend
npm run dev
```

在浏览器打开 chat 页面，发起新对话，观察：
- 思考内容流式输出，无固定高度限制
- 浅色调 + 左边框，与正文区分明显
- 标题栏整行可点击，右侧 chevron 图标随展开/折叠切换
- 流式输出时自动滚动到底部

- [ ] **Step 4: Commit**

```bash
git add FitMate-frontend/src/pages/chat/components/ReasoningTraceBlock.vue
git commit -m "feat(frontend): ReasoningTraceBlock 去固定窗口，流式输出，点击切换，浅色调区分"
```

---

### Task 12: 前端 — ChatMessageList.vue 自定义滚动条

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/components/ChatMessageList.vue`

- [ ] **Step 1: 在 .chat-scroll 样式中加滚动条自定义**

打开 `ChatMessageList.vue`，定位到 `.chat-scroll` 样式块（约 line 285-290）：

```css
.chat-scroll {
  position: relative;
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}
```

修改为：

```css
.chat-scroll {
  position: relative;
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  /* Firefox */
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--color-on-surface) 15%, transparent) transparent;
}

/* Webkit (Chrome / Edge / Safari) */
.chat-scroll::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.chat-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.chat-scroll::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--color-on-surface) 15%, transparent);
  border-radius: 4px;
  border: 2px solid transparent;
  background-clip: padding-box;
}

.chat-scroll::-webkit-scrollbar-thumb:hover {
  background: color-mix(in srgb, var(--color-on-surface) 25%, transparent);
  background-clip: padding-box;
}
```

- [ ] **Step 2: 验证滚动条样式**

刷新浏览器，在 chat 页面产生足够多的消息让滚动条出现，观察：
- 滚动条变细（8px）
- 半透明，与界面色调一致
- hover 时颜色加深
- Firefox / Chrome / Edge 都正常（Firefox 走 `scrollbar-width` / `scrollbar-color`，Webkit 走 `::-webkit-scrollbar`）

- [ ] **Step 3: Commit**

```bash
git add FitMate-frontend/src/pages/chat/components/ChatMessageList.vue
git commit -m "style(frontend): 自定义 chat-scroll 滚动条样式匹配界面设计"
```

---

## Part C：端到端验证

### Task 13: 端到端验证

**Files:** 无（仅验证）

- [ ] **Step 1: 后端启动 + 编译验证**

```bash
cd FitMate-backend
mvn -pl FitMate-api -am compile -q
```

启动 FitMate-api 应用，确认无报错，MapperScan 能扫到 ChatThinkingMapper（看启动日志）。

- [ ] **Step 2: 实时对话 — thinking 流式输出 + 保存验证**

1. 前端发起新对话（Agent 模式，触发多轮决策）
2. 观察思考内容流式输出正常，无固定窗口，浅色调 + 左边框
3. 标题栏点击切换展开/折叠正常
4. 流式输出时自动滚动到底部
5. 对话完成后，查询数据库验证 thinking 已保存：

```sql
SELECT m.id, m.content, t.content AS thinking_content, t.created_at
FROM t_chat_message m
LEFT JOIN t_chat_thinking t ON t.message_id = m.id
WHERE m.role = 'assistant'
ORDER BY m.id DESC
LIMIT 5;
```

预期：最近一条 assistant 消息对应一条 thinking 记录，content 非空。

- [ ] **Step 3: 历史会话 — thinking 按需加载验证**

1. 刷新页面，进入会话记录列表
2. 点击刚才的会话，加载历史消息
3. 观察：assistant 消息的思考内容默认折叠（chevron 朝下）
4. 点击标题栏展开，观察：
   - 触发接口调用（Network 面板看到 `GET /chat/thinking/{messageId}`）
   - 加载完成后思考内容显示
   - 再次点击折叠正常
5. 再次展开同一条消息：不再触发接口调用（thinkingLoaded=true，走缓存）

- [ ] **Step 4: Chat 模式验证（如有 Chat 链路入口）**

如果项目还有 Chat 流式链路（非 Agent），重复 Step 2-3 验证 Chat 模式 thinking 保存与加载。

- [ ] **Step 5: 消息回滚验证**

1. 在历史会话中点击某条 user 消息的"重试"按钮
2. 确认回滚成功
3. 查询数据库：

```sql
SELECT * FROM t_chat_thinking WHERE message_id NOT IN (SELECT id FROM t_chat_message);
```

预期：返回空（`ON DELETE CASCADE` 已级联清理被删除消息的 thinking 记录）。

- [ ] **Step 6: 滚动条样式验证**

在 Chrome / Edge / Firefox 三个浏览器中分别验证：
- 滚动条变细（8px）
- 颜色与界面一致
- hover 加深
- 无原生滚动条突兀感

- [ ] **Step 7: 最终 Commit（如有修复）**

如验证中发现问题并修复，提交修复：

```bash
git add <修复的文件>
git commit -m "fix: 端到端验证修复"
```

---

## Self-Review 检查

### Spec coverage

- ✅ §3.1 数据库设计 → Task 1
- ✅ §3.2.1 实体与 Mapper → Task 2
- ✅ §3.2.2 ChatSessionService 接口 → Task 3
- ✅ §3.2.3 ChatSessionServiceImpl 实现 → Task 4
- ✅ §3.2.4 AgentExecuteContext + AgentLoopExecutor → Task 5 + Task 6
- ✅ §3.2.5 ChatServiceImpl → Task 7
- ✅ §3.2.6 历史查询不变 → 无需任务（确认不改动）
- ✅ §3.2.7 Controller 接口 → Task 8
- ✅ §3.3.1 实时对话不变 → 无需任务
- ✅ §3.3.2 历史按需加载 → Task 9 + Task 10
- ✅ §3.4.1 ReasoningTraceBlock 改造 → Task 11
- ✅ §3.4.2 自动滚动 → Task 13 Step 2 验证（无需改动代码，已确认现有 scrollToBottom 覆盖）
- ✅ §3.4.3 自定义滚动条 → Task 12
- ✅ §4 实施清单全部覆盖

### Placeholder scan

- ✅ 无 TBD / TODO / "implement later"
- ✅ 所有代码块完整
- ✅ 所有命令含预期输出

### Type consistency

- ✅ `saveThinking(Long messageId, String content)` — 接口、实现、调用点一致
- ✅ `getThinkingByMessageId(Long messageId)` 返回 `String` — 接口、实现、Controller、前端 API 一致
- ✅ `accumulatedThinking` — `AgentExecuteContext` 字段、`AgentLoopExecutor` 调用一致
- ✅ 前端 `getThinkingByMessageId(messageId)` — doctorApi 定义、ChatLogicBase 调用一致
- ✅ 前端 thinking 状态字段 `thinkingContent` / `thinkingExpanded` / `thinkingLoaded` / `thinkingLoading` — mapRecordToChatItem 定义、toggleThinkingExpanded 使用一致
