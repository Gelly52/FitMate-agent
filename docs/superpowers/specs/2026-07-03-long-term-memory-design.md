# FitMate 长期记忆功能设计文档

- **日期**: 2026-07-03
- **状态**: Draft
- **作者**: Brainstorming Session
- **参考**: Hermes Agent 四层记忆架构 / Codex Agent 长期记忆思路

---

## 1. 背景与目标

### 1.1 现状

FitMate Agent 当前仅有**工作记忆**（短期对话记忆 + 上下文压缩），缺乏跨会话的长期记忆能力：

| 记忆层 | Hermes 参考 | FitMate 现状 |
|---|---|---|
| 工作记忆 | 动态压缩 | ✅ 已有（`AgentMemoryService` + `ContextCompressService`，memory-window-size=20，超 80% 自动压缩） |
| 情景记忆 | 跨会话检索 | ⚠️ 仅 `t_chat_message` 按会话存储，无跨会话检索 |
| 语义记忆 | 用户偏好/事实 | ⚠️ Wiki+RAG 只存领域知识，无用户个人事实 |
| 程序性记忆 | Agent 自动生成 Skills | ❌ 无 |

Dashboard 右侧 User Profile 区域为纯占位（`DashboardPage.vue` 第 52-63 行），未调任何接口。

### 1.2 目标

为 Agent 增加长期记忆能力，使其能够：
1. **跨会话记住用户**：基础事实、偏好、关键事件、Agent 洞察
2. **记住近期状态**：训练/身体数据滚动快照
3. **个性化响应**：每轮对话自动注入用户画像
4. **Dashboard 可视化**：右侧展示图像化的用户画像

### 1.3 非目标

- 不实现 Hermes 的 Learning Loop / Skills 自动进化（程序性记忆暂不涉及）
- 不引入向量库存储记忆（v1 数据量小，用 recency+关键词检索；后续可演进）
- 不实现记忆的语义检索（v1 仅按时间+类型+标签过滤）

---

## 2. 需求基线（已与用户确认）

| 维度 | 决策 |
|---|---|
| 记忆范围 | ①基础事实与偏好 ②跨会话关键事件 ③训练/身体数据快照 ④Agent 洞察 |
| 写入触发 | 会话后自动提取（异步，额外一次 LLM 调用） |
| 读取注入 | 画像常驻首轮 prompt + 情景事件按需检索 |
| 画像展示 | 自然语言存储 + 图像化视觉展示（类知识图谱更图形化） |
| 用户控制 | 可查看 + 仅可删除 |
| Wiki 编译提取 | 仅 USER scope 文档，提取个人事实+关注领域，高阈值，复用编译 LLM 调用 |

---

## 3. 架构总览

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         记忆写入（3 个来源）                      │
│                                                                   │
│  ① 会话后自动提取        ② Wiki 编译时提取        ③ 定时聚合快照  │
│  (FACT/EPISODIC/         (FACT only)             (SNAPSHOT)      │
│   INSIGHT)               USER scope 文档          每日凌晨       │
│  LLM 提取 → 写入         编译 prompt 附加段       聚合训练/体测   │
│                         →写入                   →写入/滚动更新   │
└──────────────┬──────────────┬──────────────┬────────────────────┘
               │              │              │
               ▼              ▼              ▼
        ┌─────────────────────────────────────────┐
        │          t_user_memory (统一存储)        │
        │  memory_type: FACT/EPISODIC/SNAPSHOT/   │
        │               INSIGHT                    │
        │  status: active / archived / ignored    │
        │  content: 自然语言正文                    │
        │  metadata_json: 时间/标签/来源/重要性     │
        └────────────────────┬────────────────────┘
                             │
              ┌──────────────┴──────────────┐
              ▼                             ▼
    ┌──────────────────┐          ┌──────────────────┐
    │  画像缓存生成器    │          │  Agent 注入器     │
    │  ProfileBuilder   │          │  MemoryInjector  │
    │  (异步 LLM)       │          │                   │
    │  读全部 active →  │          │  画像常驻首轮     │
    │  生成画像文本+标签 │          │  + 事件按需检索   │
    └────────┬─────────┘          └──────────────────┘
             │
             ▼
    ┌──────────────────┐
    │ t_user_profile   │
    │ (画像缓存)        │
    │ profile_text     │
    │ profile_tags_json│
    └────────┬─────────┘
             │
             ▼
    ┌──────────────────┐
    │ Dashboard 展示    │
    │ + Agent prompt   │
    └──────────────────┘
```

### 3.2 核心组件

| 组件 | 职责 | 位置（建议） |
|---|---|---|
| `MemoryWriter` | 三个写入入口的统一收口服务 | `agent/memory/` |
| `SessionMemoryExtractor` | 会话后异步 LLM 提取 | `agent/memory/extractor/` |
| `WikiMemoryExtractor` | Wiki 编译时附加提取 | `wiki/application/impl/`（嵌入 WikiCompileServiceImpl） |
| `SnapshotAggregator` | 定时聚合训练/体测数据 | `agent/memory/scheduler/` |
| `ProfileBuilder` | 异步读全部 active 记忆 → LLM 生成画像 → 写缓存 | `agent/memory/profile/` |
| `MemoryInjector` | 会话启动时读画像注入 prompt；EPISODIC 按需检索 | `agent/memory/injector/` |
| `MemoryController` | 用户查看/删除 API | `agent/controller/` |

### 3.3 与现有系统的关系

| 现有组件 | 关系 |
|---|---|
| `AgentLoopExecutor.finishWithAnswer` | 会话结束挂钩点，触发 `SessionMemoryExtractor` |
| `AgentPromptBuilder.buildDecisionPrompt` | 画像注入点，新增 `userProfileSection` 参数 |
| `WikiCompileServiceImpl.executeCompile` | Wiki 编译挂钩点，编译后调 `WikiMemoryExtractor` |
| `t_training_log` / `t_body_metrics` | `SnapshotAggregator` 的数据源 |
| `t_context_summary` | 短期记忆，与长期记忆独立，不合并 |
| `t_wiki_page.page_type` 的 `PROFILE` 枚举 | **删除**（Wiki 专注知识库，画像走独立表） |

---

## 4. 数据模型

### 4.1 表 1：`t_user_memory`（记忆主表）

```sql
CREATE TABLE IF NOT EXISTS `t_user_memory` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         BIGINT       NOT NULL COMMENT '用户ID',
    `memory_type`     VARCHAR(20)  NOT NULL COMMENT 'FACT|EPISODIC|SNAPSHOT|INSIGHT',
    `content`         TEXT         NOT NULL COMMENT '自然语言正文',
    `metadata_json`   JSON         NULL     COMMENT '结构化元数据：时间范围/标签/重要性/关联实体等',
    `source`          VARCHAR(100) NULL     COMMENT '来源：session:{id} / wiki_compile:{pageId} / schedule',
    `content_hash`    VARCHAR(64)  NULL     COMMENT '内容hash，用于去重和ignored标记',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT 'active|archived|ignored',
    `expired_at`      DATETIME     NULL     COMMENT '过期时间（SNAPSHOT 滚动更新时旧记录归档）',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_type_status` (`user_id`, `memory_type`, `status`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_content_hash` (`user_id`, `content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户长期记忆';
```

### 4.2 各记忆类型的 metadata_json 约定

| 类型 | metadata 示例 | 说明 |
|---|---|---|
| FACT | `{"category":"body_condition","tags":["腰椎间盘突出","力量举"]}` | 稳定事实，无过期 |
| EPISODIC | `{"occurred_at":"2026-06-28","tags":["训练计划调整"],"importance":"high","session_id":123}` | 带时间戳的事件片段 |
| SNAPSHOT | `{"period":"2026-06-29~2026-07-02","metrics":{"training_days":3,"total_volume":12000,"avg_weight":78.2}}` | 滚动窗口，旧的归档 |
| INSIGHT | `{"category":"training_style","tags":["适合推拉腿分化"],"confidence":0.85}` | Agent 总结的洞察 |

### 4.3 表 2：`t_user_profile`（画像缓存）

```sql
CREATE TABLE IF NOT EXISTS `t_user_profile` (
    `id`                BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`           BIGINT   NOT NULL UNIQUE COMMENT '用户ID',
    `profile_text`      TEXT     NOT NULL COMMENT 'LLM 生成的自然语言画像',
    `profile_tags_json` JSON     NULL     COMMENT '关键标签数组，供可视化',
    `memory_version`    INT      NOT NULL DEFAULT 0 COMMENT '生成时基于的记忆版本号',
    `generated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像缓存';
```

### 4.4 profile_tags_json 结构

```json
[
  {"label": "力量举训练者", "weight": 0.95, "category": "identity"},
  {"label": "减脂期", "weight": 0.80, "category": "goal"},
  {"label": "腰椎间盘突出", "weight": 0.90, "category": "condition"},
  {"label": "推拉腿分化", "weight": 0.75, "category": "preference"},
  {"label": "近期疲劳偏高", "weight": 0.65, "category": "status"}
]
```

- `label`：标签文本
- `weight`（0-1）：权重，用于前端节点大小映射
- `category`：类别，用于前端颜色映射。约定值：`identity`（身份）/ `goal`（目标）/ `condition`（身体条件）/ `preference`（偏好）/ `status`（近期状态）

### 4.5 设计要点

1. **记忆与画像分离**：记忆是原始数据（可增删），画像是派生缓存（可重生）
2. **content_hash + status=ignored**：实现"用户删除后不被重复提取"——删除时标记为 ignored，提取时跳过相同 hash
3. **SNAPSHOT 滚动窗口**：用 `expired_at` + 归档实现，保留历史可追溯，而非 delete 旧记录
4. **profile_tags_json**：为 Dashboard 图形化展示预留结构化数据，不依赖纯文本解析

---

## 5. 写入流程

### 5.1 写入来源一：会话后自动提取

#### 5.1.1 触发时机

挂钩点：`AgentLoopExecutor.finishWithAnswer`（[agent/core/AgentLoopExecutor.java#L281-327](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java#L281)）第 325 行 `markRunSuccess` 之后。

**注意**：仅在 Agent 成功结束时触发，失败/取消路径（`AgentCancelledException`）不提取记忆。

#### 5.1.2 执行流程

```
finishWithAnswer (AgentLoopExecutor, 同步线程)
  ├─ 现有逻辑：finishAssistantMessage + markRunSuccess + SSE FINISH
  └─ 新增：提取 userId + sessionId + 完整对话历史
       ↓
  @Async("memoryTaskExecutor")
  SessionMemoryExtractor.extract(userId, sessionId, conversation)
       ├─ 跳过过短对话（< 3 轮或 < 100 字）
       ├─ 构建 extract prompt（含 schema 约定 + 对话内容）
       ├─ chatModel.call(prompt) → 解析 JSON 输出
       │   输出格式：{"memories": [{"type":"FACT|EPISODIC|INSIGHT","content":"...","metadata":{...}}]}
       ├─ 对每条 memory：
       │    ├─ 计算 content_hash（SHA-256 of content）
       │    ├─ 查询是否已存在相同 hash 且 status=ignored → 跳过
       │    ├─ 查询是否已存在相同 hash 且 status=active → 跳过（去重）
       │    └─ 否则插入 t_user_memory (source="session:{sessionId}")
       └─ 若有新记忆写入 → 触发 ProfileBuilder.asyncRebuild(userId)
```

#### 5.1.3 提取 prompt 设计

模板位置：`classpath:prompts/memory-extract.md`（新建）

prompt 核心指令：
- 角色：你是用户画像分析助手，从对话中提取值得长期记住的用户信息
- 提取范围（高阈值）：
  - FACT：用户明确表达的训练目标、身体条件、饮食偏好、伤病史、训练历史、个人条件（年龄/性别/身高/体重等）
  - EPISODIC：带时间戳的关键决策点（如"本周改为推拉腿分化"、"暂停深蹲因为腰伤"）
  - INSIGHT：Agent 在对话中得出的分析结论（如"该用户适合高频率低容量训练"）
- **不提取**：客套话、临时性需求、已在记忆中的重复信息、通用知识
- 输出格式：严格 JSON，无值得提取的内容时返回 `{"memories": []}`

#### 5.1.4 过短对话过滤

为避免无意义提取（如用户只说"你好"），跳过条件：
- 对话总轮数 < 3（user + assistant）
- 或对话总字符数 < 100

### 5.2 写入来源二：Wiki 编译时提取

#### 5.2.1 触发时机

挂钩点：`WikiCompileServiceImpl.executeCompile`（[wiki/application/impl/WikiCompileServiceImpl.java#L104-162](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/impl/WikiCompileServiceImpl.java#L104)）编译成功后。

**仅对 USER scope 文档触发**：编译入口通过 `job.getSpaceId() → WikiSpace → scopeType` 判断，`scopeType == "USER"` 才提取。

#### 5.2.2 实现方式：复用编译 LLM 调用

**不增加额外 LLM 调用**，而是在现有 Wiki 编译 prompt 的输出 schema 中附加一个 `memory_extraction` 字段：

修改 `prompts/wiki-schema.md` 的输出格式约定：
```json
{
  "actions": [...],
  "memory_extraction": [
    {"type": "FACT", "content": "用户目标减脂到15%体脂", "metadata": {"category": "goal", "tags": ["减脂"]}}
  ]
}
```

prompt 附加指令（写入 `WIKI_COMPILE_PROMPT_TEMPLATE`）：
```
## 记忆提取（可选）
如果原始资料中明确包含用户本人的训练目标、身体条件、饮食偏好、伤病史、训练历史、
或用户明显关注的训练领域，请在 memory_extraction 字段中提取。
高阈值：仅在明确涉及用户个人化信息时提取，通用健身知识不要提取。
无个人化信息时返回空数组。
```

#### 5.2.3 执行流程

```
WikiCompileServiceImpl.executeCompile (异步线程 wikiCompileExecutor)
  ├─ 现有逻辑：LLM 编译 → 解析 actions → 落库 t_wiki_page
  ├─ 解析 llmOutput 中的 memory_extraction 字段
  └─ 若 space.scopeType == "USER" 且 memory_extraction 非空：
       对每条 memory：
       ├─ 计算 content_hash
       ├─ 查询是否已存在相同 hash 且 status=ignored → 跳过
       ├─ 查询是否已存在相同 hash 且 status=active → 跳过
       └─ 否则插入 t_user_memory
            (source="wiki_compile:{sourceDocId}", memory_type="FACT")
       若有新记忆写入 → 触发 ProfileBuilder.asyncRebuild(userId)
```

#### 5.2.4 userId 获取

`WikiCompileServiceImpl` 在异步线程执行，不能使用 `UserContextHolder`。userId 从 `WikiSpace.ownerUserId` 获取（USER scope 空间必有 ownerUserId）。

### 5.3 写入来源三：定时聚合快照

#### 5.3.1 触发时机

新增定时任务：每日凌晨 02:00 执行。

需新增 `@EnableScheduling` 配置类（项目当前无定时任务基础设施）。

#### 5.3.2 执行流程

```
@Scheduled(cron = "0 0 2 * * *")
SnapshotAggregator.aggregateSnapshots()
  ├─ 查询所有有训练/体测记录的用户（DISTINCT user_id from t_training_log + t_body_metrics）
  └─ 对每个用户：
       ├─ 查询最近 N 天（默认 14 天）的训练记录 + 体测记录
       ├─ 聚合计算：训练天数、总训练量、平均体重、体重变化、平均疲劳、主要肌群分布
       ├─ 生成自然语言摘要（模板拼接，不用 LLM）：
       │   "近14天训练5次，总训练量24000kg，平均体重78.2kg（下降0.5kg），
       │    疲劳水平中等，主要训练胸/背/腿"
       ├─ 查询该用户最近的 SNAPSHOT 记忆：
       │    ├─ 若存在 → 将旧的归档（status=archived, expired_at=now）
       │    └─ 插入新的 SNAPSHOT (source="schedule", expired_at=now+14天)
       └─ 触发 ProfileBuilder.asyncRebuild(userId)
```

#### 5.3.3 滚动窗口策略

- 窗口大小：14 天（可配置 `fitmate.memory.snapshot.window-days`）
- 保留策略：仅保留最新的 1 条 active SNAPSHOT，旧的归档
- 归档的 SNAPSHOT 不删除（保留历史可追溯），但 `ProfileBuilder` 和 `MemoryInjector` 只读 active

### 5.4 异步线程池配置

新建 `MemoryAsyncConfig`，配置独立的记忆任务线程池：

```yaml
# application.yml 新增
fitmate:
  memory:
    async-pool-size: 2
    snapshot:
      window-days: 14
    extract:
      min-conversation-turns: 3
      min-conversation-chars: 100
```

```java
// agent/memory/config/MemoryAsyncConfig.java
@Bean("memoryTaskExecutor")
public ThreadPoolTaskExecutor memoryTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(memoryProperties.getAsyncPoolSize());        // 默认 2
    executor.setMaxPoolSize(executor.getCorePoolSize() * 2);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("memory-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    return executor;
}
```

**独立线程池理由**：避免与 `agentTaskExecutor`（Agent Loop 主池，core=2）竞争资源；记忆提取是低优先级后台任务，不应阻塞 Agent 执行。

---

## 6. 读取与注入流程

### 6.1 画像常驻注入

#### 6.1.1 触发时机

在 `AgentLoopExecutor.run` 第 109 行附近（构造 `summarySection` 时），同步加载画像并构造 `userProfileSection`。

#### 6.1.2 执行流程

```
AgentLoopExecutor.run (异步线程 agentTaskExecutor)
  ├─ 第 109 行：buildSummaryPromptSection(...)
  ├─ 新增：userProfileSection = memoryInjector.loadProfileSection(userId)
  │         ├─ 查询 t_user_profile WHERE user_id = ? 
  │         ├─ 若存在且未过期（updated_at 在 24h 内）→ 直接用 profile_text
  │         ├─ 若不存在或过期 → 返回空串（不阻塞 Agent，异步触发重建）
  │         └─ 返回 "## 用户画像\n{profile_text}" 或 ""
  ├─ 第 116 行：doAgentWikiSearch(...)
  └─ buildDecisionPrompt(context, memory, observations, tools, wikiContext, 
                         summarySection, userProfileSection)  ← 新增参数
```

#### 6.1.3 prompt 区块插入位置

在 `AgentPromptBuilder.buildDecisionPrompt` 中，**插在 `summarySection` 之后、`## 最近对话` 之前**：

```
1. [系统提示词]
2. ## 可用工具
3. [summarySection]              ← 历史摘要
4. ## 用户画像                    ← 新增区块
5. ## 最近对话
6. [wikiContext]
7. ## 已获得的工具观察结果
8. ## 当前用户问题
9. [固定收尾]
```

**理由**："用户画像 + 历史摘要"都属于"用户上下文背景"，与"最近对话"（短期记忆）形成分层。

#### 6.1.4 画像缓存过期策略

- 缓存有效期：24 小时（`updated_at` 距今 > 24h 视为过期）
- 过期时：返回空串（不阻塞 Agent 启动），异步触发 `ProfileBuilder.asyncRebuild`
- 首次使用（无缓存）：返回空串，异步触发 `ProfileBuilder.asyncRebuild`，下次会话起生效

#### 6.1.5 总开关闭合

当 `fitmate.memory.enabled=false` 时：
- `MemoryInjector.loadProfileSection` 直接返回空串，不查询数据库
- `SessionMemoryExtractor` / `WikiMemoryExtractor` / `SnapshotAggregator` 全部短路不执行
- `MemoryController` 的写入/删除接口返回 403
- `GET /memory/profile` 返回空画像
- Agent prompt 不包含 `## 用户画像` 区块，行为与无记忆功能时完全一致

### 6.2 情景事件按需检索

#### 6.2.1 实现方式：Agent 工具

新增 Agent 工具 `memory.search`，加入 `fitmate.agent.enabled-tools` 白名单。

```
工具名：memory.search
描述：检索用户的历史记忆（关键事件、过往决策、Agent 洞察）。当需要回顾用户之前
      发生过什么、做过什么决定时调用。
输入：{"query": "关键词或主题", "limit": 5}
输出：{"memories": [{"type":"EPISODIC","content":"...","occurred_at":"2026-06-28"}, ...]}
```

#### 6.2.2 检索逻辑（v1：recency + 关键词）

```java
// 简单检索：按 memory_type IN (EPISODIC, INSIGHT) + content LIKE %query% + recency 排序
List<UserMemory> search(Long userId, String query, int limit) {
    return memoryMapper.selectList(new LambdaQueryWrapper<UserMemory>()
        .eq(UserMemory::getUserId, userId)
        .eq(UserMemory::getStatus, "active")
        .in(UserMemory::getMemoryType, "EPISODIC", "INSIGHT")
        .and(w -> w.like(UserMemory::getContent, query)
                   .or().apply("JSON_EXTRACT(metadata_json, '$.tags') LIKE {0}", "%" + query + "%"))
        .orderByDesc(UserMemory::getCreatedAt)
        .last("LIMIT " + limit));
}
```

**v2 演进**：若后续需要语义检索，给 `t_user_memory.content` 加 embedding 列或独立向量索引。

#### 6.2.3 工具白名单更新

```yaml
# application.yml
fitmate:
  agent:
    enabled-tools: date.now, kb.search, rag.search, body_metrics.query, training_log.query, web.search, web.fetch, memory.search  # 新增 memory.search
```

### 6.3 画像生成（ProfileBuilder）

#### 6.3.1 触发时机

- 记忆写入后自动触发（`SessionMemoryExtractor` / `WikiMemoryExtractor` / `SnapshotAggregator` 写入后）
- 画像缓存过期被访问时触发（`MemoryInjector` 检测到过期）
- 用户删除记忆后触发

#### 6.3.2 执行流程

```
@Async("memoryTaskExecutor")
ProfileBuilder.asyncRebuild(Long userId)
  ├─ 加分布式锁（防并发，key=profile:rebuild:{userId}）
  ├─ 查询 t_user_memory WHERE user_id=? AND status='active'，按类型分组
  ├─ 构建 profile prompt：
  │    - FACT 列表
  │    - 最近 5 条 EPISODIC
  │    - 最新 1 条 SNAPSHOT
  │    - INSIGHT 列表
  │    - 指令：生成自然语言画像 + 5-8 个关键标签（带 weight/category）
  ├─ chatModel.call(prompt) → 解析 JSON
  │   输出格式：{"profile_text":"...","tags":[{"label":"...","weight":0.9,"category":"..."}]}
  ├─ upsert t_user_profile (memory_version = 当前最大 memory.id)
  └─ 释放锁
```

#### 6.3.3 画像 prompt 设计

模板位置：`classpath:prompts/profile-build.md`（新建）

prompt 核心指令：
- 角色：你是用户画像生成助手，基于用户的长期记忆生成简洁的用户画像
- 输出要求：
  - `profile_text`：100-200 字的自然语言画像，涵盖身份/目标/条件/偏好/近期状态
  - `tags`：5-8 个关键标签，每个带 weight（0-1，反映确定性/重要性）和 category
- category 约定值：identity / goal / condition / preference / status

---

## 7. Dashboard 画像展示

### 7.1 接口

新增接口：`GET /memory/profile` → 返回 `t_user_profile` 的 `profile_text` + `profile_tags_json`

```json
{
  "code": 200,
  "data": {
    "profileText": "28岁男性，力量举训练者，目标减脂到15%体脂。有腰椎间盘突出史，适合推拉腿分化训练。近14天训练5次，疲劳水平中等偏高...",
    "profileTags": [
      {"label": "力量举训练者", "weight": 0.95, "category": "identity"},
      {"label": "减脂期", "weight": 0.80, "category": "goal"},
      {"label": "腰椎间盘突出", "weight": 0.90, "category": "condition"},
      {"label": "推拉腿分化", "weight": 0.75, "category": "preference"},
      {"label": "近期疲劳偏高", "weight": 0.65, "category": "status"}
    ],
    "generatedAt": "2026-07-03T10:30:00",
    "memoryVersion": 42
  }
}
```

### 7.2 前端展示

替换 `DashboardPage.vue` 第 52-63 行的占位区域。

**展示形式**：图像化标签云（类知识图谱但更图形化）+ 底部自然语言画像摘要。

具体视觉设计在 `frontend-design` 阶段细化，本设计文档仅约定数据契约：
- 节点 = 标签，节点大小 = `weight`，节点颜色 = `category`
- 节点可悬浮显示
- 底部显示 `profileText` 摘要（截断 + 展开查看全文）

### 7.3 空状态处理

- 用户无任何记忆时：显示"暂无画像，开始对话或上传文档后自动生成"
- 画像生成中（首次使用）：显示"正在生成用户画像..." + loading 动画

---

## 8. 用户控制

### 8.1 接口

| 接口 | 方法 | 用途 |
|---|---|---|
| `GET /memory/list?type=&page=&size=` | GET | 分页查看记忆列表（可按类型过滤） |
| `DELETE /memory/{id}` | DELETE | 删除单条记忆（标记为 ignored，不物理删除） |
| `DELETE /memory/all` | DELETE | 清空所有记忆（全部标记为 ignored） |
| `GET /memory/profile` | GET | 获取画像缓存（Dashboard 用） |
| `POST /memory/profile/rebuild` | POST | 手动触发画像重建 |

### 8.2 删除语义

- 删除 = `status` 改为 `ignored`，不物理删除
- `content_hash` 保留，提取时跳过相同内容
- 删除后触发 `ProfileBuilder.asyncRebuild`

### 8.3 前端入口

在 Settings 页面新增"记忆管理"区块（或独立子页面），列出全部记忆条目，支持按类型过滤和单条删除。

具体 UI 在实现阶段细化。

---

## 9. 配置项

### 9.1 application.yml 新增

```yaml
fitmate:
  memory:
    enabled: true                                          # 总开关
    async-pool-size: 2                                     # 记忆任务线程池大小
    profile:
      cache-ttl-hours: 24                                  # 画像缓存有效期
    snapshot:
      window-days: 14                                      # 快照滚动窗口
      cron: "0 0 2 * * *"                                  # 快照聚合定时
    extract:
      min-conversation-turns: 3                            # 会话提取最小轮数
      min-conversation-chars: 100                          # 会话提取最小字符数
```

### 9.2 enabled-tools 更新

```yaml
fitmate:
  agent:
    enabled-tools: date.now, kb.search, rag.search, body_metrics.query, training_log.query, web.search, web.fetch, memory.search
```

---

## 10. Wiki PROFILE 枚举清理

### 10.1 背景

`t_wiki_page.page_type` 当前预留了 `PROFILE` 枚举值（注释"用户画像页 Phase 2 用"），但长期记忆采用独立表方案，不走 Wiki。为避免混淆，删除该预留。

### 10.2 改动清单

| 文件 | 改动 |
|---|---|
| [fitmate_init.sql#L319](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/FitMate-backend/FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql#L319) | 注释改为 `INDEX/ENTITY/CONCEPT/SYNTHESIS/SOURCE_SUMMARY/LOG` |
| [wiki-schema.md#L13](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/FitMate-backend/FitMate-api/src/main/resources/prompts/wiki-schema.md#L13) | 删除 `- PROFILE：用户画像页（Phase 2 用）` 这一行 |
| [WikiPage.java#L15](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/infrastructure/entity/WikiPage.java#L15) | 注释改为 `INDEX/ENTITY/CONCEPT/SYNTHESIS/SOURCE_SUMMARY/LOG` |
| 现有数据库 | 手动 ALTER：`ALTER TABLE t_wiki_page MODIFY COLUMN page_type VARCHAR(30) NOT NULL COMMENT 'INDEX/ENTITY/CONCEPT/SYNTHESIS/SOURCE_SUMMARY/LOG';` |

---

## 11. 错误处理与降级

| 场景 | 处理 |
|---|---|
| 会话后提取 LLM 调用失败 | 记录日志，不影响会话已完成的回答 |
| Wiki 编译时 memory_extraction 字段解析失败 | 记录日志，不影响 Wiki 编译结果 |
| 快照聚合定时任务失败 | 记录日志，下次定时重试 |
| 画像生成 LLM 调用失败 | 记录日志，画像缓存保持旧值或空值 |
| 画像缓存过期且重建未完成 | `MemoryInjector` 返回空串，Agent 正常运行（降级为无画像） |
| 记忆删除后画像重建失败 | 画像缓存保持旧值，下次重建时更新 |

**核心原则**：长期记忆是增强能力，任何失败不应阻塞 Agent 主流程。

---

## 12. 验收标准

1. **会话后提取**：完成一段含个人信息的对话后，`t_user_memory` 出现对应 FACT/EPISODIC/INSIGHT 记录
2. **Wiki 编译提取**：上传含个人信息的 USER scope 文档后，`t_user_memory` 出现对应 FACT 记录；上传通用知识文档不产生记忆
3. **快照聚合**：定时任务执行后，有训练/体测记录的用户出现 SNAPSHOT 记录；旧的 SNAPSHOT 被归档
4. **画像注入**：有记忆的用户开启新会话，Agent prompt 中包含 `## 用户画像` 区块
5. **事件检索**：Agent 调用 `memory.search` 工具能返回匹配的 EPISODIC/INSIGHT 记录
6. **Dashboard 展示**：Dashboard 右侧显示图像化标签 + 画像文本
7. **用户控制**：Settings 页面可查看全部记忆、可删除单条、删除后画像重建
8. **降级**：关闭 `fitmate.memory.enabled` 后，Agent 正常运行无画像注入
9. **PROFILE 清理**：Wiki 相关文件/表/枚举不再出现 PROFILE

---

## 13. 演进路径

| 阶段 | 内容 |
|---|---|
| v1（本设计） | 单表 + recency/关键词检索 + 画像缓存 |
| v2 | 给 `t_user_memory.content` 加 embedding，EPISODIC 支持语义检索 |
| v3 | 引入 Hermes 式 Learning Loop，Agent 自动生成 Skills（程序性记忆） |
