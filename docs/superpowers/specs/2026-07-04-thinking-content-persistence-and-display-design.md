# 思考内容持久化与前端展示改造设计

**日期**: 2026-07-04
**状态**: 已确认，待实施

---

## 1. 背景与目标

### 1.1 现状

当前系统已实现 LLM 思考内容（reasoning_content）的实时 SSE 流式推送，但存在两个问题：

1. **思考内容未持久化**：`t_chat_message` 表无 thinking 字段，`ChatMessage` 实体无该字段，`finishAssistantMessage` 不接收 thinking 参数。历史会话加载时无法看到思考内容。
2. **前端展示问题**：
   - `ReasoningTraceBlock.vue` 把 thinking 限定在 `max-height: 200px` 的固定滚动窗口，与 deepseek 风格不一致
   - thinking 与 agentSteps（执行轨迹）混在同一卡片
   - 使用浏览器原生滚动条，与界面设计风格不一致
   - 流式输出时滚动行为未完全覆盖 thinking 增长场景

### 1.2 目标

- **问题2（优先）**：思考内容支持保存，从历史会话加载时也能看到
- **问题1**：参考 https://chat.deepseek.com 改造思考内容展示
  - 去掉固定窗口，thinking 与正文一样自然流式输出
  - 纯文本展示，浅色调与正文区分
  - 点击标题栏展开/折叠（不用按钮）
  - 流式输出自动滚动到新行
  - 自定义滚动条样式匹配界面设计

---

## 2. 设计决策

### 2.1 存储方式：独立表 `t_chat_thinking`

**决策**：思考内容存独立表，通过 `message_id` 关联，不污染主消息表。

**理由**：
- 主消息表 `t_chat_message` 是高频查询表，保持轻量
- thinking 内容可能比正文还长（Agent 多轮合并后 5-50KB / 条），独立表隔离存储压力
- 未来可单独清理老数据而不动消息表
- 事务性良好（仍是数据库），实现复杂度适中
- **为未来缓存铺路**：独立表支持细粒度缓存，thinking 与正文可独立配置 TTL / 淘汰策略 / 多级层级

### 2.2 加载策略：按需加载

**决策**：历史会话加载时不返回 thinking，用户点击展开时单独调接口加载。

**理由**：
- 与 deepseek 行为一致（历史会话 thinking 默认折叠，展开才显示）
- 减少历史会话列表的传输量
- 对未来缓存最友好（按 messageId 维度缓存 thinking）

### 2.3 Agent 多轮 thinking 合并格式：直接拼接

**决策**：Agent 多轮决策循环中，每轮的 reasoning_content 直接拼接，不加分隔符或轮次标签。

**理由**：
- 接近 deepseek 连续流式体验
- 工具调用步骤已在 `agentSteps` 中展示，thinking 不需要重复标注轮次
- 纯文本阅读流畅

### 2.4 前端展示：去掉固定窗口，流式输出

**决策**：
- 移除 `max-height: 200px` 限制，thinking 自然撑开
- 纯文本 `pre-wrap`，不渲染 markdown
- 浅色调 + 左侧细边框区分正文
- 点击标题栏整行切换展开/折叠（不用按钮）
- 正文仍用 markdown 渲染

---

## 3. 技术设计

### 3.1 数据库设计

#### 新增表 `t_chat_thinking`

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

**要点**：
- `message_id` 唯一索引：一条消息最多一份 thinking
- `ON DELETE CASCADE`：消息回滚/删除时 thinking 自动清理（复用现有 `deleteMessagesFromBotMsgId` 链路，无需额外改逻辑）
- 主消息表 `t_chat_message` 完全不动

#### 迁移脚本

更新 `fitmate_init.sql`（新部署用），不提供单独 ALTER 脚本（因为是新增表，不是加列）。

### 3.2 后端持久化设计

#### 3.2.1 新增实体与 Mapper

**`ChatThinking.java`**（新增）：
- 位置：`com.itgeo.fitmate.api.chat.infrastructure.entity`
- 字段：`id`, `messageId`, `content`, `createdAt`, `updatedAt`
- `@TableName("t_chat_thinking")`

**`ChatThinkingMapper.java`**（新增）：
- 位置：`com.itgeo.fitmate.api.chat.infrastructure.mapper`
- 继承 `BaseMapper<ChatThinking>`，无自定义方法

#### 3.2.2 ChatSessionService 接口扩展

新增两个方法：

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

#### 3.2.3 ChatSessionServiceImpl 实现

**`saveThinking`**：
- 查询是否已存在（按 messageId）
- 存在则 update content
- 不存在则 insert 新记录
- content 为空时跳过（不保存空 thinking）

**`getThinkingByMessageId`**：
- `selectOne` 按 messageId 查询
- 返回 content，不存在返回 null

#### 3.2.4 Agent 链路改造（`AgentLoopExecutor`）

**`AgentExecuteContext` 加字段**：
```java
/** Agent 多轮循环累积的思考内容，finishWithAnswer 时持久化。 */
private StringBuilder accumulatedThinking = new StringBuilder();
```

**`AgentLoopExecutor.execute` 循环内**（当前 line 178-202）：
- 保留局部 `reasoningContent` 变量（用于 trace 事件记录）
- 每个 reasoning 分片到达时，除了 `sendThinkingChunk(context, reasoningDelta)` 推送 SSE，还要 `context.getAccumulatedThinking().append(reasoningDelta)`

**`finishWithAnswer` 方法**（当前 line 302-332）：
- 在调用 `chatSessionService.finishAssistantMessage(...)` 之后，新增：
  ```java
  String thinkingContent = context.getAccumulatedThinking().toString();
  if (StrUtil.isNotBlank(thinkingContent)) {
      chatSessionService.saveThinking(context.getAssistantMessageId(), thinkingContent);
  }
  ```

#### 3.2.5 Chat 链路改造（`ChatServiceImpl.streamAndSend`）

在 line 363-368 调用 `finishAssistantMessage` 之后，新增：
```java
String thinkingText = reasoningContent.toString();
if (StrUtil.isNotBlank(thinkingText)) {
    chatSessionService.saveThinking(assistantMessageId, thinkingText);
}
```

#### 3.2.6 历史会话查询不变

- `getChatRecords` / `buildChatRecordItem` 不返回 thinking
- `ChatRecordItem` DTO 不加 thinkingContent 字段
- 历史消息列表保持轻量

#### 3.2.7 新增 Controller 接口

`ChatController` 加：

```java
@GetMapping("/thinking/{messageId}")
public ApiResult<String> getThinking(@PathVariable Long messageId) {
    return ApiResult.success(chatSessionService.getThinkingByMessageId(messageId));
}
```

- 权限：复用现有登录校验
- 返回：thinking 内容字符串，不存在时返回空字符串

### 3.3 前端持久化对接设计

#### 3.3.1 实时对话（不变）

- SSE 流式推送 thinking 不变
- 前端 `handleThinkingEvent` 累积 `chatItem.thinkingContent` 实时展示
- 后端在 finish 时自动保存 thinking，前端无需回传

#### 3.3.2 历史会话加载

**`mapRecordToChatItem`**（`ChatLogicBase.vue` line 2208-2256）：
- assistant 消息：`chatItem.thinkingContent = null`（默认空，等按需加载）
- `chatItem.thinkingExpanded = false`（历史消息默认折叠）
- `chatItem.thinkingLoaded = false`（标记 thinking 尚未从后端加载）

**新增 API**（`doctorApi.ts`）：
```typescript
getThinkingByMessageId(messageId: number | string): Promise<string>
```

**展开时按需加载**（`toggleThinkingExpanded` 方法改造）：
```typescript
async toggleThinkingExpanded(message) {
  if (message.thinkingExpanded) {
    message.thinkingExpanded = false;
    return;
  }
  // 历史消息且 thinking 未加载过，先调接口加载
  if (!message.thinkingLoaded && message.messageId && !message.thinkingContent) {
    message.thinkingLoading = true;
    try {
      const thinking = await doctorApi.getThinkingByMessageId(message.messageId);
      message.thinkingContent = thinking || "";
      message.thinkingLoaded = true;
    } finally {
      message.thinkingLoading = false;
    }
  }
  message.thinkingExpanded = true;
}
```

**注意**：`mapRecordToChatItem` 当前用 `message.messageId` 作为 chatItem.id，需确认前端能拿到 messageId 用于调接口。当前代码 line 2238-2241：
```javascript
id: message.messageId != null
  ? String(message.messageId)
  : "record-" + index + "-" + this.generateRandomId(6),
```
chatItem.id 在历史消息场景就是 messageId 字符串，可直接用。但为清晰起见，建议同时保留 `messageId` 字段。

### 3.4 前端展示改造设计（问题1）

#### 3.4.1 `ReasoningTraceBlock.vue` 改造

**模板调整**：
- 移除"展开/收起"文字按钮
- 标题栏整行可点击切换 expanded，右侧显示 chevron 图标（`expand_more` / `expand_less`）
- 折叠时：标题栏 + 首行预览（截断 + 省略号）
- 展开时：标题栏 + thinking 内容（纯文本 pre-wrap，无高度限制）
- thinking 内容区无 `overflow-y: auto`，自然撑开

**样式调整**：
- `.reasoning-trace-content` 移除 `max-height: 200px; overflow-y: auto;`
- thinking 文字颜色：`var(--color-on-surface-variant)`（比正文浅）
- 左侧边框：`border-left: 2px solid var(--color-outline-variant)`
- 左侧 padding：`padding-left: 12px`
- 轻微背景：`background: color-mix(in srgb, var(--color-surface-container-low) 60%, transparent)`

**agentSteps 与 thinking 关系**：
- 当前模板同时显示 agentSteps（执行轨迹时间线）和 thinkingContent
- 保留 agentSteps 在 thinking 上方（决策步骤可视化）
- thinking 在下方流式输出
- 两者都在同一个 ReasoningTraceBlock 内，但视觉上分离（agentSteps 是时间线，thinking 是文本块）

#### 3.4.2 自动滚动

**现状**：
- `handleThinkingEvent` 末尾已有 `this.scrollToBottom()`（line 1294）
- `upsertStreamingBotMessage` 末尾已有 `this.scrollToBottom()`（line 1480）
- `scrollToBottom` 逻辑（line 2722-2741）：检测是否在底部附近（120px 内），是则强制滚动，否则不滚

**确认**：thinking 增长时已触发 scrollToBottom，无需额外改造。但需验证 `scrollToBottom` 的"nearBottom"判断在 thinking 撑开高度后仍正确工作。

#### 3.4.3 自定义滚动条

**`ChatMessageList.vue` 的 `.chat-scroll`** 新增滚动条样式：

```css
.chat-scroll {
  /* 现有样式不变 */
  scrollbar-width: thin;
  scrollbar-color: var(--color-outline-variant) transparent;
}

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

**要点**：
- Firefox 用 `scrollbar-width: thin` + `scrollbar-color`
- Webkit 用 `::-webkit-scrollbar` 系列
- 颜色用设计 token，与界面风格一致
- 8px 宽度，半透明，hover 加深
- `background-clip: padding-box` + 2px border 让滚动条不贴边

---

## 4. 实施清单

### 问题2（思考内容持久化）— 优先

| 序号 | 文件 | 改动类型 | 内容 |
|---|---|---|---|
| 1 | `FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql` | 新增表 | 添加 `t_chat_thinking` 表定义 |
| 2 | `ChatThinking.java` | 新增 | 实体类 |
| 3 | `ChatThinkingMapper.java` | 新增 | Mapper 接口 |
| 4 | `ChatSessionService.java` | 修改 | 加 `saveThinking` / `getThinkingByMessageId` 方法签名 |
| 5 | `ChatSessionServiceImpl.java` | 修改 | 实现两个新方法 |
| 6 | `AgentExecuteContext.java` | 修改 | 加 `accumulatedThinking` 字段 |
| 7 | `AgentLoopExecutor.java` | 修改 | 循环内累积 thinking；`finishWithAnswer` 调 `saveThinking` |
| 8 | `ChatServiceImpl.java` | 修改 | `streamAndSend` 完成后调 `saveThinking` |
| 9 | `ChatController.java` | 修改 | 加 `GET /chat/thinking/{messageId}` 接口 |
| 10 | `doctorApi.ts` | 修改 | 加 `getThinkingByMessageId` API |
| 11 | `ChatLogicBase.vue` | 修改 | `mapRecordToChatItem` 加 thinkingLoading/thinkingLoaded 字段；`toggleThinkingExpanded` 改异步按需加载 |

### 问题1（前端展示改造）

| 序号 | 文件 | 改动类型 | 内容 |
|---|---|---|---|
| 12 | `ReasoningTraceBlock.vue` | 修改 | 去固定窗口；标题栏点击切换；chevron 图标；浅色调 + 左边框；移除文字按钮 |
| 13 | `ChatMessageList.vue` | 修改 | `.chat-scroll` 自定义滚动条样式 |

### 不需要改动的部分

- `t_chat_message` 表结构（不动）
- `ChatMessage` 实体（不加字段）
- `ChatRecordItem` DTO（不加字段）
- `getChatRecords` / `buildChatRecordItem` 查询逻辑（不返回 thinking）
- SSE 推送链路（实时推送不变）
- `ReasoningChatClient` 对 DeepSeek reasoning_content 的解析（不变）
- `SSEMsgType.THINKING` 枚举（已存在）

---

## 5. 关键设计考量

### 5.1 为未来缓存铺路

独立表设计支持未来细粒度缓存：

- **接口边界清晰**：`getThinkingByMessageId(messageId)` 可单独加 `@Cacheable`，不影响消息列表查询
- **冷热分离**：thinking（冷数据，大）与正文（热数据，小）分开缓存，避免大字段污染缓存
- **缓存失效点明确**：消息更新只失效消息缓存，thinking 重新生成只失效 thinking 缓存
- **多级缓存友好**：未来可 Caffeine（本地热数据）+ Redis（分布式温数据），thinking 走单独通道

当前实现不引入缓存，但接口设计已为未来铺路。

### 5.2 事务一致性

- `saveThinking` 在 `finishAssistantMessage` 之后调用，两者非同一事务
- 若 `saveThinking` 失败：消息已保存但 thinking 丢失，不影响主流程，日志告警即可
- 若需强一致，可加 `@Transactional` 包裹两个操作，但 thinking 丢失影响小，当前选择最终一致性

### 5.3 消息回滚与删除

- 现有 `deleteMessagesFromBotMsgId` 删除消息时，`t_chat_thinking` 因 `ON DELETE CASCADE` 自动清理
- 无需额外改删除逻辑

### 5.4 历史消息的 messageId 传递

- `ChatRecordItem.messageId` 已是 Long 类型字段
- 前端 `mapRecordToChatItem` 用 `message.messageId` 作为 chatItem.id
- 调 `getThinkingByMessageId` 时直接用 `message.messageId`（数字）或 `message.id`（字符串转数字）
- 建议在 `mapRecordToChatItem` 显式保留 `messageId` 字段，避免 id 类型混淆

---

## 6. 风险与验证

### 6.1 风险

1. **Agent thinking 累积内存**：多轮循环累积 thinking 到 context，长会话可能占用较多内存。但单次 Agent 运行 thinking 通常 < 100KB，可接受。
2. **按需加载延迟**：历史会话展开 thinking 时有网络延迟（一次 API 调用）。deepseek 也是此行为，可接受。
3. **滚动条浏览器兼容**：`::-webkit-scrollbar` 不支持 Firefox，但已用 `scrollbar-width` / `scrollbar-color` 兜底。

### 6.2 验证点

1. 实时对话：thinking 流式输出正常，finish 后后端有 thinking 记录
2. 历史会话：加载时 thinking 折叠，点击展开调接口加载，内容正确显示
3. Agent 模式：多轮 thinking 合并保存，历史加载可查看完整推理链
4. Chat 模式：单轮 thinking 保存，历史加载可查看
5. 消息回滚：删除消息后 thinking 也被级联删除
6. 前端展示：thinking 无固定窗口、浅色调、点击标题栏展开/折叠、流式自动滚动
7. 滚动条：自定义样式在 Chrome/Edge/Firefox 均正常
