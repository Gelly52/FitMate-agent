# LLM Wiki 引入设计

- **状态**：Draft（待用户最终审查）
- **创建日期**：2026-07-02
- **作者**：协作设计（用户 + AI 助手）
- **关联**：参考 Karpathy LLM Wiki 模式（https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f）
- **适用范围**：FitMate-AI 后端（Spring Boot 多模块）+ 前端（Vue 3）

---

## 1. 背景与动机

### 1.1 当前 RAG 现状

FitMate-AI 已实现完整 RAG 链路（详见调研报告）：

- **存储**：Redis Vector Store（bge-m3，1024 维）+ Redis RediSearch 关键词索引
- **检索**：混合检索（向量 + 关键词 + RRF 融合 + 可选启发式 rerank）
- **隔离**：按 `userId` 在 metadata filter 层隔离
- **控制**：前端 `knowledgeSearchSelected` 开关 → 后端 `ragEnabled` 字段 → Agent 工具 `rag.search` 可见性
- **集成**：两条路径（Chat 直连 RAG 问答；Agent 预检索 + Loop 内工具调用）

RAG 的问题：**LLM 每次从原始 chunk 重新检索拼装知识，无累积、无综合、无交叉引用**。问一个需要综合 5 篇文档的问题，LLM 每次都要重新拼装。

### 1.2 引入 LLM Wiki 的目标

参考 Karpathy LLM Wiki 模式：LLM **增量构建并维护一个持久化的 wiki**——一组互链的 Markdown 页面，位于用户与原始资料之间。知识编译一次后保持更新，而非每次查询重新推导。

**核心价值**：
- 知识累积复利：每次摄入新资料，wiki 增量更新已有页面 + 交叉引用
- 综合已就绪：矛盾已标注、综合已反映所有已读资料
- LLM 维护零成本：LLM 不厌倦、不漏更新交叉引用、可一次触碰 15 个页面

### 1.3 长期愿景

引入 LLM Wiki 是为后续**用户画像**功能铺路：
- 用户已有的训练日志、身体数据、对话记忆、私有知识库内容 → 编译为结构化用户画像 wiki 页面
- 用户画像在前端展示（Dashboard）
- 画像随数据增量更新

本次设计聚焦 Wiki 知识库基础设施，用户画像作为 Phase 2 后续接入。

---

## 2. 设计方案选择

### 2.1 方案对比

| 方案 | 存储层 | 检索方式 | 优点 | 缺点 |
|---|---|---|---|---|
| **A. 纯正 LLM Wiki** | 文件系统 markdown | LLM 读 index → 定位页面 → 综合 | 忠于原模式；零向量依赖；可 git 版本化 | Java Web 多租户文件管理麻烦；难复用现有检索基础设施；前端展示不便 |
| **B. MySQL 化 + 复用向量检索（推荐）** | MySQL 存 wiki 页面 | 复用 Redis 向量+关键词+RRF+rerank | 复用现有成熟管线（最大杠杆）；事务性、多租户易；天然支持用户画像前端展示 | 偏离纯模式（失去 git 版本化优雅）；需 embedding wiki 页面 |
| **C. 混合** | 文件系统为源 + MySQL/Redis 索引 | 同 B | 兼顾版本化与检索性能 | 双写一致性复杂；实现量最大 |

### 2.2 选定方案：B

**理由**：
1. 项目已有完整 Redis 向量+关键词+RRF+rerank 管线，把 wiki 页面当作新语料复用这套管线是最高杠杆路径，避免重写检索。
2. MySQL 存储契合 Spring Boot + MyBatis-Plus 技术栈，且用户画像要在前端展示这一未来目标强烈倾向 MySQL（可查询、结构化、易分页）。
3. "Wiki 默认、RAG 开关"在方案 B 下很干净：两者都用 Redis 检索，区别只是语料（编译后的 wiki 页面 vs 原始 chunk）。
4. git 版本化是"锦上添花"，对 Web 应用非必需，后续可通过文件镜像补齐。

---

## 3. 架构概览

适配 Karpathy 三层架构到 FitMate Spring Boot 多租户场景：

### 3.1 原始资料层（不可变）
- 用户上传文档（复用现有 `t_rag_document` + 文件解析链 `DocumentParserFactory`）
- 未来扩展：训练日志、身体指标、对话记忆（Phase 2 接入）

### 3.2 Wiki 层（LLM 生成/维护，存 MySQL）

两类 wiki space：
- **全局 Wiki**（`scope=GLOBAL`）：系统级健身领域知识，全员共享检索。由管理员/系统策划摄入。
- **每用户 Wiki**（`scope=USER`, `owner_user_id`）：用户私有，编译自个人文档；Phase 2 起追加用户画像页面。

### 3.3 Schema 层（配置）
- `prompts/wiki-schema.md`：定义页面类型约定、命名规则、wikilink 约定、ingest/query/lint 工作流。
- `PromptTemplateManager` 新增编译 prompt 模板 + 查询 prompt 模板 + 改写 prompt 模板。

### 3.4 关键差异（vs 原 RAG）

RAG 检索原始 chunk；Wiki 检索 LLM 编译后的页面。**两套 Redis 索引独立**，互不干扰：
- RAG 索引：`fitmate:dev:rag:embedding:bgem3:`（原始 chunk，保持不变）
- Wiki 索引：`fitmate:wiki:embedding:`（编译后页面，新增）

Wiki 为默认检索，RAG 改为 opt-in 叠加。

---

## 4. 数据模型

新增 5 张表（遵循现有 `t_` 前缀），写入 `fitmate_init.sql`。

### 4.1 `t_wiki_space` — Wiki 空间

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | 空间主键 |
| scope_type | VARCHAR(20) | GLOBAL / USER |
| owner_user_id | BIGINT | GLOBAL=null；USER=t_user.id |
| title | VARCHAR(255) | 空间标题 |
| description | VARCHAR(500) | 空间描述 |
| status | VARCHAR(20) | ACTIVE / ARCHIVED |
| created_at / updated_at | DATETIME | 时间戳 |
| UNIQUE(scope_type, owner_user_id) | | 全局唯一 + 每用户一个 |

### 4.2 `t_wiki_page` — Wiki 页面（核心）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | 页面主键 |
| space_id | BIGINT FK→t_wiki_space | 所属空间 |
| page_type | VARCHAR(30) | INDEX / ENTITY / CONCEPT / SYNTHESIS / SOURCE_SUMMARY / LOG / PROFILE |
| title | VARCHAR(255) | 页面标题 |
| slug | VARCHAR(255) | URL 友好标识（空间内唯一） |
| content_md | LONGTEXT | Markdown 正文 |
| content_hash | VARCHAR(64) | 内容哈希（变更检测） |
| frontmatter_json | JSON | YAML frontmatter（tags/dates/source_count 等） |
| char_count | INT | 正文字符数 |
| status | VARCHAR(20) | DRAFT / PUBLISHED |
| source_doc_id | BIGINT FK→t_rag_document | 源文档（SOURCE_SUMMARY 类型用，可空） |
| created_at / updated_at / compiled_at | DATETIME | 时间戳 |
| INDEX(space_id, page_type) | | 按空间+类型查询 |
| UNIQUE(space_id, slug) | | 空间内 slug 唯一 |
| INDEX(content_hash) | | 变更检测 |

### 4.3 `t_wiki_page_link` — wikilink 关系

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | 链接主键 |
| from_page_id | BIGINT FK→t_wiki_page | 源页面 |
| to_page_id | BIGINT FK→t_wiki_page | 目标页面 |
| link_text | VARCHAR(255) | 链接显示文本 |
| created_at | DATETIME | 时间戳 |
| INDEX(from_page_id), INDEX(to_page_id) | | 双向查询 |

### 4.4 `t_wiki_compile_job` — 异步编译任务

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | 任务主键 |
| space_id | BIGINT FK→t_wiki_space | 目标空间 |
| trigger_type | VARCHAR(20) | DOC_UPLOAD / MANUAL / SCHEDULED / EVENT |
| source_doc_id | BIGINT FK→t_rag_document | 源文档（DOC_UPLOAD 用） |
| status | VARCHAR(20) | PENDING / RUNNING / SUCCESS / FAILED |
| pages_touched_json | JSON | 本次触碰的页面 ID 列表 |
| error_message | TEXT | 失败原因 |
| started_at / finished_at | DATETIME | 执行时间 |
| created_by_user_id | BIGINT FK→t_user | 发起人 |
| INDEX(space_id, status) | | 按空间+状态查询 |

### 4.5 `t_wiki_log` — 操作日志（对应 Karpathy log.md，入库可查询）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | 日志主键 |
| space_id | BIGINT FK→t_wiki_space | 所属空间 |
| entry_type | VARCHAR(20) | INGEST / QUERY / LINT / COMPILE |
| entry_summary | VARCHAR(500) | 日志摘要（如 `## [2026-07-02] ingest | 文档标题`） |
| source_ref | VARCHAR(255) | 来源引用 |
| created_at | DATETIME | 时间戳 |
| INDEX(space_id, created_at) | | 时间线查询 |

### 4.6 复用现有基础设施

- **Redis Vector Store**：新索引 `fitmate-wiki-vectorstore`，prefix `fitmate:wiki:embedding:`，metadata 字段：
  - `spaceId` (TAG)
  - `pageId` (TAG)
  - `pageType` (TAG)
  - `scope` (TAG) — GLOBAL / USER
  - `ownerUserId` (TAG) — USER 类型用，GLOBAL 为空
  - `title` (TAG)
- **Redis 关键词索引**：`fitmate-wiki-keyword-index`，prefix `fitmate:wiki:chunk:`
- **Embedding 模型**：复用 `BgeM3HttpEmbeddingModel`（1024 维，与 RAG 同模型，但独立索引）

---

## 5. Wiki 编译流程（Ingest，异步）

### 5.1 触发方式

异步后台编译。用户上传文档后立即返回，后台任务编译 wiki。

### 5.2 编译流程

1. 用户上传文档 → 复用 `DocumentServiceImpl.loadText` 解析为纯文本（复用现有 parser 链，不改）。
2. 文档入库 `t_rag_document`（保持现有逻辑，RAG 索引同步建立）。
3. 创建 `t_wiki_compile_job`（status=PENDING, trigger=DOC_UPLOAD）。
4. 异步任务（Spring `@Async` + 线程池；后续可换 MQ）：
   - a. 读 schema prompt（`wiki-schema.md` + 编译模板）。
   - b. 调 LLM（DeepSeek，复用 `ReasoningChatClient` 或新建 `WikiCompileClient`），输入：原始文本 + 当前 space 的 INDEX 页 + 相关现有页面摘要。
   - c. **LLM 输出结构化 JSON**（而非自由 markdown，便于程序解析）：
     ```json
     {
       "actions": [
         {"action": "create", "page_type": "SOURCE_SUMMARY", "title": "...", "slug": "...", "content_md": "...", "links": ["蛋白质摄入", "训练恢复"]},
         {"action": "update", "slug": "蛋白质摄入", "content_md": "..."},
         {"action": "update_index", "content_md": "..."},
         {"action": "append_log", "entry": "## [2026-07-02] ingest | 文档标题"}
       ]
     }
     ```
   - d. 解析指令 → UPSERT `t_wiki_page`（by space_id+slug）→ 更新 INDEX 页 → 追加 `t_wiki_log`。
   - e. 对每个变更页面：调 embedding → 写 Redis vector + keyword。
   - f. job → SUCCESS，记录 `pages_touched_json`。
5. 错误：job → FAILED + error_message，支持手动重试（`/wiki/recompile/{jobId}`）。

### 5.3 关键设计

**编译 prompt 强制 LLM 输出 JSON 指令而非直接写文件**，由后端程序解析并落库——保证结构一致性与可审计性。这与 Karpathy 原模式（LLM 直接写文件）的差异是 Web 多租户场景的必要适配。

### 5.4 Lint（Phase 3，本次不实现）

定时 Lint 任务：矛盾检测 / 孤儿页 / 陈旧声明 / 缺失交叉引用。

---

## 6. 检索流程

### 6.1 两层开关设计

```
知识库总开关 knowledgeBaseEnabled（默认 true）
  ├─ true  → wiki 预检索（Agent 启动前自动跑）+ 暴露 kb.search 工具
  └─ false → 不预检索、不暴露 kb.search（纯通用对话，零知识库开销）

RAG 开关 ragEnabled（默认 false，仅当 knowledgeBaseEnabled=true 时可开启）
  └─ true  → kb.search 子流程跑 wiki → rewrite → rag
  └─ false → kb.search 子流程只跑 wiki
```

### 6.2 Wiki 检索（独立子流程，复用现有管线）

```
输入：question + userId
1. 确定检索 space 集合：[GLOBAL space, 该用户的 USER space]
2. 向量召回：RedisVectorStore.similaritySearch
   filterExpression: scope == 'GLOBAL' OR ownerUserId == '{userId}'
   topK = 8
3. 关键词召回：Redis ftSearch
   filter: scope == 'GLOBAL' OR ownerUserId == '{userId}'
   topK = 8
4. RRF 融合（复用 RrfRagFusionServiceImpl）
5. 启发式 rerank（复用 RagRerankServiceImpl）→ finalTopK = 4
6. 返回 topK 页面 content_md
```

**与 RAG 检索完全隔离**：RAG 查 `fitmate:dev:rag:embedding:bgem3:`（原始 chunk），Wiki 查 `fitmate:wiki:embedding:`（编译页面）。两套索引、两套 metadata filter，互不污染。

### 6.3 串行两阶段检索（kb.search 复合工具内部逻辑）

**核心原则**：Wiki 结果复用一份（不做两份不同精简度的检索）；rewrite 由 prompt 层自处理背景提取。

```
kb.search 工具内部执行逻辑：
  1. wiki 检索（向量+关键词+RRF+rerank）→ wiki 结果
  2. if (ragEnabled):
       rewrite query = LLM(原问题, wiki 结果)   // 非流式快速调用
       rag 检索（用改写后 query）→ rag 结果
     else:
       rag 结果 = 空   // 同时跳过 rewrite 步骤
  3. 返回 observation = {wiki: [...], rag: [...]}   // 分段标注
```

### 6.4 Query Rewriting 设计

**触发条件**：仅当 `ragEnabled=true` 时执行。RAG 关闭时不触发改写。

**Rewrite Prompt 模板**：
```
你将收到用户问题与 Wiki 检索结果。请基于 Wiki 结果中与问题相关的关键背景，
将原问题改写为更精确、更贴近原始文档表述的检索 query。

## 用户问题
{question}

## Wiki 检索结果
{wiki_content}

## 输出要求
1. 只输出改写后的 query，不要解释
2. 若 Wiki 结果为空或问题已明确，直接输出原问题
3. 改写后的 query 应贴近原始文档可能使用的表述（术语、关键词）
```

**关键点**：rewrite prompt 接收完整 Wiki 结果但指示 LLM 自行提取关键背景，无需为 rewrite 单独检索精简版 Wiki。

### 6.5 上下文拼接策略

Wiki 结果与 RAG 结果合并注入回答 prompt，分段标注：

```
## 知识库 Wiki（编译后）
{wiki_content}

## 原始文档片段（RAG）
{rag_content}

## 用户问题
{question}
```

让 LLM 优先采信 Wiki（编译后的结构化知识），RAG 作为细节补充。

---

## 7. 与 Chat / Agent 集成

### 7.1 Agent Loop 集成（主路径）

**核心思路**：知识库检索作为 Agent 的一个**复合工具 `kb.search`**，LLM 在 Loop 中自主决策何时调用；调用时按 `ragEnabled` 开关跑不同子流程；结果作为 observation 回灌 Loop，LLM 继续决策下一步。

```
Agent Loop:
  LLM 决策 → 若需要知识库 → 调用 kb.search 工具
                              ├─ wiki 检索
                              ├─ if (ragEnabled) { rewrite → rag 检索 }
                              └─ 返回合并结果作为 observation
  LLM 基于 observation 决策 → 继续调其他工具 / final 回答
```

**复合工具 vs 拆分两个工具**：选择复合工具 `kb.search`。若拆成 `wiki.search` + `rag.search` 两个独立工具，LLM 可能先调 rag 再调 wiki，破坏"wiki 优先、rewrite 基于 wiki"的串行语义。复合工具把串行逻辑封装在工具内部，对 LLM 透明，开关由后端控制。

**工具注册可见性**（仿现有 `resolveAllowedTools`）：
- `knowledgeBaseEnabled=true` → `kb.search` 可见
- `knowledgeBaseEnabled=false` → `kb.search` 不出现在 Agent 可用工具列表

### 7.2 Wiki 预检索

**保留预检索机制**（替换现有 `doAgentRagSearch`）：
- Agent 启动前自动跑 wiki 检索，结果注入首轮 prompt 作为"知识库背景"区块
- 仅当 `knowledgeBaseEnabled=true` 时执行
- 非每次对话都检索——只有启用知识库时才预检索

**上下文占用评估**：
- wiki 预检索 topK=4，每段约 800 字符 → 约 3200 字符 ≈ 800-1000 token
- 注入 Agent 首轮 prompt 作为"知识库背景"区块
- DeepSeek 上下文 64K+，1000 token 占比 <2%，完全可控
- 若仍担心，预检索 topK 可降到 2-3（约 500 token）

### 7.3 SSE 事件化展示

kb.search 工具内部各子步骤发出 SSE 子事件，复用现有 `AgentStepCard` + `ReasoningTraceBlock` 展示思考过程：

```
SSE 事件序列：
  - tool_call_start: {tool: "kb.search"}
  - sub_step: {type: "wiki_search", status: "running"}
  - sub_step: {type: "wiki_search", status: "done", result_count: 4}
  - sub_step: {type: "query_rewrite", status: "running"}    // 仅 ragEnabled 时
  - sub_step: {type: "query_rewrite", status: "done", rewritten_query: "..."}
  - sub_step: {type: "rag_search", status: "running"}       // 仅 ragEnabled 时
  - sub_step: {type: "rag_search", status: "done", result_count: 3}
  - tool_call_end: {tool: "kb.search", observation: {...}}
```

用户感知："正在检索 Wiki → 正在改写查询 → 正在检索原始文档 → 生成回答"，**感知延迟大幅降低**。

### 7.4 Chat 直连路径（非 Agent）

`ChatServiceImpl` 新增：
- `doWikiSearchAuto` / `doWikiSearch`：默认每条消息先 wiki 检索 → `buildWikiPrompt` → 流式回答
- `ragEnabled=true` 时：在 wiki 上下文基础上叠加 RAG chunk（串行两阶段，同 kb.search 内部逻辑）

### 7.5 前端开关 UI

`ChatLogicBase.vue` 升级为两层开关：
- **`知识库检索` 开关**（默认开）→ 对应 `knowledgeBaseEnabled`
- **`原始文档增强 (RAG)` 开关**（默认关，依赖知识库开关开启后才可点）→ 对应 `ragEnabled`
- 现有 `knowledgeSearchSelected` 升级为这两个开关

`KnowledgePage.vue`：上传仍走 `/rag/uploadRagDoc`（复用），文案说明文档将编译进 wiki。

---

## 8. 配置

### 8.1 application.yml 新增

```yaml
fitmate:
  wiki:
    enabled: ${WIKI_ENABLED:true}                    # 知识库总开关
    compile:
      async-pool-size: ${WIKI_COMPILE_POOL_SIZE:3}
      max-retry: ${WIKI_COMPILE_MAX_RETRY:2}
    retrieval:
      default-top-k: ${WIKI_RETRIEVAL_TOP_K:4}
      max-top-k: ${WIKI_RETRIEVAL_MAX_TOP_K:10}
      vector-recall-k: ${WIKI_VECTOR_RECALL_K:8}
      keyword-recall-k: ${WIKI_KEYWORD_RECALL_K:8}
      rerank-enabled: ${WIKI_RERANK_ENABLED:true}   # wiki 默认开启 rerank
    vectorstore:
      index-name: ${WIKI_VECTORSTORE_INDEX_NAME:fitmate-wiki-vectorstore}
      prefix: ${WIKI_VECTORSTORE_PREFIX:fitmate:wiki:embedding:}
    keyword:
      index-name: ${WIKI_KEYWORD_INDEX_NAME:fitmate-wiki-keyword-index}
      key-prefix: ${WIKI_KEYWORD_KEY_PREFIX:fitmate:wiki:chunk:}
```

### 8.2 现有 RAG 配置保持不变

RAG 相关配置（`rag.*`）保持现状，仅作为 RAG 开关开启时的检索参数。

---

## 9. 关键类与文件规划

### 9.1 后端新增（FitMate-api）

**实体与 Mapper**：
- `wiki/infrastructure/entity/{WikiSpace, WikiPage, WikiPageLink, WikiCompileJob, WikiLog}.java`
- `wiki/infrastructure/mapper/{WikiSpaceMapper, WikiPageMapper, WikiPageLinkMapper, WikiCompileJobMapper, WikiLogMapper}.java`

**配置**：
- `wiki/config/WikiProperties.java`（`@ConfigurationProperties(prefix="fitmate.wiki")`）
- `wiki/config/WikiRedisVectorStoreConfig.java`（仿 `RagRedisVectorStoreConfig`，独立 Bean）

**编译服务**：
- `wiki/application/WikiCompileService.java` + `impl/WikiCompileServiceImpl.java`
- `wiki/application/impl/WikiCompileAsyncRunner.java`（`@Async` 异步执行器）

**检索服务**：
- `wiki/application/WikiSearchService.java` + `impl/WikiSearchServiceImpl.java`
- `wiki/application/QueryRewriteService.java` + `impl/QueryRewriteServiceImpl.java`

**Agent 工具**：
- `agent/tool/KbSearchToolExecutor.java`（复合工具，封装 wiki→rewrite→rag 串行逻辑）
- `agent/tool/dto/KbSearchRequest.java` / `KbSearchResult.java`

**Controller**：
- `wiki/controller/WikiController.java`（`/wiki/spaces`、`/wiki/pages`、`/wiki/compile/{jobId}`、`/wiki/rebuild`）

**Prompt**：
- `resources/prompts/wiki-schema.md`（Wiki 结构与工作流 schema）
- `PromptTemplateManager` 新增：`buildWikiCompilePrompt` / `buildWikiPrompt` / `buildQueryRewritePrompt`

### 9.2 后端修改（FitMate-api）

- `agent/core/AgentLoopExecutor.java`：`resolveAllowedTools` 增加 `kb.search` 可见性判断（基于 `knowledgeBaseEnabled`）
- `agent/tool/ToolRegistry.java`：注册 `kb.search` 工具
- `chat/application/impl/ChatServiceImpl.java`：新增 `doWikiSearchAuto` / `doWikiSearch`；预检索从 `doAgentRagSearch` 改为 `doAgentWikiSearch`
- `rag/controller/RagController.java`：`uploadRagDoc` 末尾投递 wiki 编译 job
- `ChatEntity` / 请求 DTO：新增 `knowledgeBaseEnabled` 字段（默认 true）；`ragEnabled` 语义改为 RAG 叠加开关

### 9.3 前端修改

- `pages/chat/ChatLogicBase.vue`：两层开关（`knowledgeBaseSelected` + `ragSelected`）
- `pages/chat/components/AgentStepCard.vue`：适配 `wiki_search` / `query_rewrite` / `rag_search` 子步骤事件
- `pages/knowledge/KnowledgePage.vue`：文案更新（"文档将编译进 Wiki"）
- `services/doctorApi.ts`：新增 wiki 相关 API 调用

### 9.4 SQL

- `FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql`：追加 5 张 wiki 表 DDL

### 9.5 MCP（可选，本次不实现）

`FitMate-mcpServer` 的 `rag/RagManageTool` 可扩展 wiki 管理工具，但本次 Phase 1 不做，wiki 管理走 `FitMate-api` 的 `WikiController`。

---

## 10. 分阶段规划

### Phase 1（本次实施范围）：Wiki 知识库基础设施

| 子任务 | 内容 |
|---|---|
| P1.1 数据模型 | 5 张表 DDL + 实体/Mapper（FitMate-api） |
| P1.2 存储与检索基础设施 | Redis wiki vector store + keyword index（复用 BgeM3 + 检索服务，新建 `WikiRedisVectorStoreConfig`） |
| P1.3 编译服务 | `WikiCompileService` + 异步任务池 + LLM 编译 prompt + `wiki-schema.md` |
| P1.4 摄入接入 | 文档上传触发 wiki 编译（复用 `DocumentServiceImpl.loadText`，在 `RagController.uploadRagDoc` 末尾投递编译 job） |
| P1.5 查询服务 | `WikiSearchService`（向量+关键词+RRF+rerank over wiki pages）+ `QueryRewriteService` |
| P1.6 复合工具 | `KbSearchToolExecutor`（封装 wiki→rewrite→rag 串行逻辑）+ SSE 子事件 |
| P1.7 集成与开关 | Chat/Agent 默认走 wiki；`ragEnabled` 改为 RAG 叠加开关；`knowledgeBaseEnabled` 总开关；前端两层开关 UI |
| P1.8 Wiki 管理 API | `/wiki/spaces`、`/wiki/pages`、`/wiki/compile/{jobId}`、`/wiki/rebuild` |
| P1.9 测试与验证 | 编译流程测试、检索流程测试、开关切换测试、SSE 事件测试 |

### Phase 2（后续）：用户画像

- 数据源接入：训练日志/身体指标/对话记忆变更 → 事件触发画像 wiki 编译
- 画像页面：`page_type=PROFILE`，结构化用户画像（目标/训练模式/健康趋势/偏好）
- 前端展示：Dashboard 新增用户画像页，渲染 profile wiki 页面
- 增量更新：新训练日志 → LLM 增量更新 profile 页面

### Phase 3（后续）：Lint & 高级

- 定时 Lint 任务：矛盾检测/孤儿页/陈旧声明/缺失交叉引用
- 好答案回填：Query 产生的好答案可回填为新 wiki 页面
- 图谱视图：基于 `t_wiki_page_link` 前端展示 wiki 图谱
- 文件镜像导出：wiki 页面可导出为 markdown 文件（git 版本化补齐）

---

## 11. 风险与注意事项

1. **Wiki 页面 embedding 策略**：Wiki 页面是 LLM 生成的完整 markdown，可能较长。embedding 时需决定：整页一个向量，还是页面内分块多个向量？建议整页一个向量（页面是知识单元，语义完整），若页面过长（>2000 字符）再分块。这与 RAG 的 chunk 级 embedding 不同，需在 `WikiRedisVectorStoreConfig` 中单独处理。

2. **Embedding 维度对齐**：Wiki 复用 bge-m3 1024 维，与 RAG 同模型但独立索引。若未来 Wiki 引入其他 embedding 模型，需单独索引（不同维度不能共用 Redis 索引）。

3. **编译 prompt 输出稳定性**：LLM 输出 JSON 指令需严格校验。建议：
   - prompt 中给出 JSON schema 示例
   - 后端解析失败时记录原始输出到 `error_message`，job 标记 FAILED 支持重试
   - 可考虑 JSON mode（DeepSeek 支持）强制合法 JSON

4. **多用户并发编译**：异步任务池需控制并发数（默认 3），避免 LLM API 限流。

5. **Wiki 索引初始化**：`initialize-schema(true)` 仅当索引不存在时初始化，需确保首次启动时 wiki 索引正确创建。

6. **RAG 与 Wiki 数据一致性**：用户上传文档同时进入 RAG 索引（同步）和 Wiki 编译（异步）。Wiki 编译完成前，RAG 检索可命中原始 chunk，Wiki 检索无结果。这是预期行为，无需特殊处理。

7. **Agent 工具描述**：`kb.search` 工具的 description 需清晰，让 LLM 理解何时该调用（涉及知识库/历史资料/健身领域知识时）。

---

## 12. 待确认事项

无。所有关键决策点已在协作过程中确认：

1. ✅ 方案 B（MySQL 化 + 复用向量检索）
2. ✅ 全局 + 每用户两层 wiki
3. ✅ 异步后台编译
4. ✅ 串行两阶段（wiki → rewrite → rag）+ 复用一份 wiki 结果
5. ✅ Agent Loop 中复合工具 `kb.search` + wiki 预检索
6. ✅ 两层开关（知识库总开关 + RAG 开关）
7. ✅ RAG 关闭时跳过 rewrite
8. ✅ Wiki 预检索仅启用知识库时执行（非每次对话）

---

## 13. 参考资料

- Karpathy LLM Wiki 原始 gist：https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f
- 项目 SPEC：`FitMate-backend/SPEC.md`、`FitMate-frontend/SPEC.md`、根目录 `SPEC.md`
- 现有 RAG 实现调研报告（本次设计内部调研产出）
