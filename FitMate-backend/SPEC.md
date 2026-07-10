# FitMate-AI 后端代码规范 SPEC

适用范围：`FitMate-backend` Maven 多模块后端工程，包括主业务 API 服务、MCP 工具服务、通用能力、配置、数据库脚本与测试代码。  
目录结构、模块命名、包名与 artifactId 遵照项目根目录 `SPEC.md` 中的全局规范。

---

## 1. 后端模块边界

后端工程按部署边界与稳定复用边界划分模块：

```text
FitMate-backend/
├─ pom.xml
├─ FitMate-api/         # 主业务 API 服务
├─ FitMate-mcpServer/   # MCP 工具服务
└─ FitMate-common/      # 统一响应、异常、错误码、枚举、常量与通用工具
```

模块职责：

| 模块 | 职责 |
| --- | --- |
| `FitMate-api` | 面向前端暴露 REST/SSE API，承载认证、聊天、记忆、Agent、RAG、Wiki、搜索、训练、身体指标等业务流程。 |
| `FitMate-mcpServer` | 暴露 MCP 工具能力，承载邮件、RAG 管理等工具实现。 |
| `FitMate-common` | 放置统一响应、异常、错误码、枚举、常量、基础工具方法等横切通用能力。 |

`common` 不承载具体业务模块核心逻辑；业务流程应放在 `FitMate-api` 或 `FitMate-mcpServer` 对应功能包内。

> 当前不存在独立 `FitMate-domain` 模块。领域逻辑（如 `MuscleGroupDictionary`、`CardioMetTable`、`SSEMsgType`）下沉到各功能包的 `domain` 子包内。

---

## 2. 包命名与分层

所有 Java 代码根包统一为：

```text
com.itgeo.fitmate
```

模块包：

```text
com.itgeo.fitmate.api
com.itgeo.fitmate.mcp
com.itgeo.fitmate.common
```

禁止新增以下旧包名或不规范包名：

```text
com.itgeo.pojo
com.itgeo.service
com.itgeo.mapper
com.itgeo.FitMate-xxx
```

### 2.1 FitMate-api 包结构

```text
com.itgeo.fitmate.api
├─ FitMateApiApplication.java
├─ auth              # 用户认证、用户资料、用户偏好
├─ chat              # 聊天会话与消息
├─ sse               # 浏览器 SSE
├─ agent             # Agent 执行、工具、LLM、MCP、轨迹、记忆
│  └─ memory         # 上下文压缩与长期记忆
├─ rag               # RAG 文档管理与检索
├─ search            # 联网搜索与 WebFetch
├─ wiki              # LLM Wiki 知识库
├─ fitness
│  ├─ training       # 训练日志
│  ├─ metrics        # 身体指标
│  ├─ cardio         # 有氧训练
│  ├─ heartrate      # 心率
│  └─ diet           # 饮食
├─ skill             # 技能列表
├─ integration       # 外部集成（OKHttp 等）
├─ prompt            # Prompt 模板管理
├─ config            # 顶层通用配置
└─ debug             # 调试接口
```

### 2.2 FitMate-mcpServer 包结构

```text
com.itgeo.fitmate.mcp
├─ FitMateMcpServerApplication.java
├─ email             # 邮件工具
└─ rag               # RAG 管理工具
   └─ infrastructure # 实体与 Mapper
```

### 2.3 FitMate-common 包结构

```text
com.itgeo.fitmate.common
├─ constant           # RedisKeyConstants
├─ enums              # ListSortEnum
├─ exception          # BusinessException、ErrorCode
└─ response           # LeeResult
```

### 2.4 功能内分层

功能包内部优先采用以下分层：

```text
feature/
├─ controller/          # HTTP API 入口
├─ application/         # 应用服务与流程编排
│  └─ impl/             # 服务实现
│  └─ scheduler/        # 定时任务
├─ domain/              # 领域对象与领域规则
├─ infrastructure/      # 数据库、Redis、外部 HTTP、MCP、文件系统等技术实现
│  ├─ entity/
│  └─ mapper/
├─ dto/                 # 请求、响应、命令、查询对象
└─ config/              # 功能内配置
```

小功能可以减少层级，但不得把 Controller、Mapper、外部 SDK 调用和复杂业务流程全部平铺到同一包内。

---

## 3. 启动类与模块入口

启动类命名遵照全局规范：

```text
FitMateApiApplication
FitMateMcpServerApplication
```

规则：

- 启动类只负责应用启动与必要注解，不写业务初始化逻辑。
- `FitMateApiApplication` 标注 `@EnableAsync`，但不直接标注 `@EnableScheduling`。
- `@EnableScheduling` 位于 `agent.memory.longterm.config.MemoryAsyncConfig`，同时定义 `memoryTaskExecutor` 线程池 Bean。
- `@MapperScan` 在启动类上显式列举 Mapper 包路径（当前 11 个），不使用通配符。
- `FitMate-api` 与 `FitMate-mcpServer` 独立启动、独立配置端口。
- 需要初始化的业务数据通过 SQL 或迁移脚本完成，不写在启动类中。

---

## 4. 功能包职责

| 功能 | 包名片段 | 职责 |
| --- | --- | --- |
| 用户认证 | `auth` | 验证码、登录、退出、token、用户上下文、用户资料、用户偏好。 |
| 浏览器 SSE | `sse` | SSE ticket、连接管理、事件推送。 |
| 聊天 | `chat` | 对话会话、消息持久化、思考内容存储。 |
| Agent | `agent` | 任务受理、run/step 状态、异步 workflow、SSE 回传、工具调用编排。 |
| Agent 记忆 | `agent.memory` | 上下文压缩、长期记忆、用户画像、记忆提取与召回。 |
| RAG | `rag` | 文档上传、解析、分块、embedding、检索、rerank、benchmark。 |
| Wiki | `wiki` | LLM 知识库空间、页面、编译任务、检索。 |
| 联网搜索 | `search` | SearXNG 搜索与 WebFetch 抓取。 |
| 训练日志 | `fitness.training` | 训练日志、动作明细、肌肉群字典。 |
| 身体指标 | `fitness.metrics` | 体重、体脂、睡眠、疲劳度等指标。 |
| 有氧训练 | `fitness.cardio` | 有氧训练日志与 MET 字典。 |
| 心率 | `fitness.heartrate` | 心率记录。 |
| 饮食 | `fitness.diet` | 饮食日志与明细。 |
| MCP 工具 | `mcp`（mcpServer） | 邮件、RAG 管理工具。 |
| 技能 | `skill` | 技能列表渐进式加载。 |

规则：

- Controller 只做入参接收、校验、调用应用服务、返回响应。
- Application Service 负责编排流程、事务边界、权限上下文与外部依赖调用顺序。
- Domain 层表达稳定业务概念，不依赖 HTTP、Redis、数据库 Mapper 或模型 SDK。
- Infrastructure 层封装 MyBatis、Redis、HTTP Client、文件系统、模型 SDK、MCP SDK 等技术细节。

---

## 5. Agent 包详细结构

`agent` 是最复杂的功能包，内部结构：

```text
agent/
├─ controller/                       # AgentController
├─ application/
│  ├─ impl/                          # AgentAsyncServiceImpl 等
│  └─ scheduler/                     # AgentRunCleanupScheduler
├─ core/                             # 取消注册表、循环执行器、流式缓冲、JSON 清洗
├─ config/                           # AgentProperties、SubAgentProperties、ContextCompressProperties
├─ llm/                              # LlmGateway、SpringAiLlmGateway、LlmJsonSanitizer
├─ mcp/                              # McpClientPool、McpToolExecutorBridge、McpToolRegistry
├─ memory/
│  ├─ dto/                           # CompressEventPayload、MemoryLoadResult
│  ├─ longterm/
│  │  ├─ application/
│  │  │  ├─ extractor/               # SessionMemoryExtractor
│  │  │  ├─ scheduler/               # SnapshotAggregator
│  │  │  └─ MemoryReader、MemoryWriter、ProfileBuilder
│  │  ├─ config/                     # MemoryAsyncConfig、MemoryProperties
│  │  ├─ controller/                 # MemoryController
│  │  ├─ infrastructure/             # UserMemory、UserProfile 实体与 Mapper
│  │  └─ tool/                       # MemoryRecordToolExecutor、MemorySearchToolExecutor
│  └─ AgentMemoryService、ContextCompressService
├─ prompt/                           # AgentPromptBuilder
├─ tool/                             # 24 个工具执行器与 ToolRouter
├─ trace/                            # AgentTraceService
├─ infrastructure/
│  ├─ entity/                        # AgentRun、AgentStep
│  └─ mapper/                        # AgentRunMapper、AgentStepMapper
└─ dto/                              # AgentExecuteAckResponse 等
```

---

## 6. 类命名规范

| 类型 | 命名规则 | 示例 |
| --- | --- | --- |
| Controller | `{Feature}Controller` | `ChatController` |
| 应用服务接口 | `{Feature}Service` | `RagService` |
| 应用服务实现 | `{Feature}ServiceImpl` | `RagServiceImpl` |
| 领域服务 | `{Feature}DomainService` | `TrainingDomainService` |
| 请求 DTO | `{Action}Request` | `LoginRequest` |
| 响应 DTO | `{Action}Response` | `LoginResponse` |
| 内部命令 | `{Action}Command` | `ExecuteAgentCommand` |
| 查询对象 | `{Feature}Query` | `TrainingLogQuery` |
| 实体 | `{Domain}` 或 `{Domain}Entity` | `User`、`RagDocumentEntity` |
| Mapper | `{Domain}Mapper` | `TrainingLogMapper` |
| 配置类 | `{Feature}Config` | `RedisConfig` |
| 配置属性 | `{Feature}Properties` | `AgentProperties` |
| MCP Tool | `{Capability}Tool` | `RagManageTool` |
| 工具执行器 | `{Capability}ToolExecutor` | `WebSearchToolExecutor` |
| LLM 网关 | `{Provider}Gateway` | `LlmGateway` |
| 定时任务 | `{Feature}CleanupTask` / `{Feature}Scheduler` / `{Feature}Aggregator` | `WikiPageCleanupTask` |
| 外部客户端 | `{Provider}Client` | `SearxngClient` |
| 转换器 | `{Source}To{Target}Converter` | `DocumentToChunkConverter` |
| 异常 | `{Scene}Exception` | `AuthenticationException` |

基础规则：

- 类名使用 UpperCamelCase。
- 方法、变量、参数使用 lowerCamelCase。
- 常量使用 UPPER_SNAKE_CASE。
- 包名全部小写，不使用短横线、下划线、中文或大写字母。
- 避免无意义缩写，如 `Mgr`、`Svc`、`Obj`、`Tmp`。

---

## 7. API 与 Controller 规范

- 现有接口使用领域前缀，例如 `/user/...`、`/chat/...`、`/agent/...`、`/rag/...`、`/wiki/...`。
- 路径使用小写短横线或明确领域名。
- 路径中使用名词表达资源，动作用 HTTP 方法表达。
- 入参使用 DTO，并通过 Jakarta Validation 做基础校验。
- Controller 不直接访问 Mapper、RedisTemplate、模型 SDK、MCP SDK。
- 文件下载、浏览器 SSE 等特殊接口可以不使用统一响应包装，但必须在文档中说明。

统一响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

---

## 8. SSE 规范

- 浏览器 SSE 与 MCP SSE 必须在命名和文档中明确区分。
- 浏览器 SSE 事件名使用 lowerCamelCase，例如 `messageDelta`、`agentStep`、`done`、`error`。
- SSE 的 data 使用结构化 JSON。
- 服务端必须处理客户端断开连接，避免资源泄漏。
- 错误事件必须包含可读错误信息和必要业务标识。
- Agent run、step、chat delta 等长流程事件应带可追踪 id。

---

## 9. 数据库与 MyBatis 规范

- 数据库表统一使用 `t_` 前缀，表名和字段名使用 snake_case。
- 主键统一命名为 `id`。
- 创建时间使用 `created_at`，更新时间使用 `updated_at`。
- 逻辑删除字段统一使用 `deleted` 或 `deleted_at`，同一项目保持一致。
- 状态字段使用明确枚举值，不使用魔法数字。
- JSON 类型字段使用 `_json` 后缀，例如 `frontmatter_json`、`pages_touched_json`、`llm_config_json`、`mcp_config_json`。
- Mapper 与 XML 路径按功能模块归档，并与 Mapper 包结构保持一致。
- `@MapperScan` 在启动类上显式列举 Mapper 包路径，不使用通配符。
- SQL 中禁止拼接未校验的用户输入。
- 分页查询必须明确排序字段。
- 跨表查询应返回专用 DTO，不复用实体类承载聚合结果。

当前数据库共 27 张表，详见 `FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql`。

---

## 10. Redis 规范

Redis Key 使用冒号分隔：

```text
fitmate:{env}:{domain}:{purpose}:{id}
```

当前业务 Key 模式：

| 用途 | Key 模式 | TTL |
| --- | --- | --- |
| 短信验证码 | `fitmate:dev:auth:sms-code:{phone}` | 300s |
| 邮箱验证码 | `fitmate:dev:auth:email-code:{email}` | 300s |
| 发送冷却 | `fitmate:dev:auth:email-code:cooldown:{email}` | — |
| SSE 票据 | `fitmate:dev:sse:ticket:{ticket}` | 会话级 |
| Agent 任务锁 | `fitmate:dev:agent:lock:session:{sessionId}:slot:{1\|2\|3}` | run 级 |
| 记忆提取计数 | `fitmate:dev:memory:session:extracted-count:{sessionId}` | — |
| RAG 向量前缀 | `fitmate:dev:rag:embedding:bgem3:` | 永久 |
| RAG 关键词前缀 | `fitmate:dev:rag:chunk:{docId}:{seq}` | 永久 |
| Wiki 向量前缀 | `fitmate:wiki:embedding:{pageId}` | 永久 |
| Wiki 关键词前缀 | `fitmate:wiki:chunk:{pageId}` | 永久 |

规则：

- 所有临时 Key 必须设置 TTL。
- 分布式锁使用多 slot Key，每会话最多 3 个并发 Agent run。
- Redis Vector Store 的 index name、key prefix、embedding 维度必须可配置。
- Key 常量应集中到 `RedisKeyConstants`，避免散落在各 Service 类中。

向量检索 ID 约定：

- RAG 分块：`{docId}:{seq}`
- Wiki 页面：`pageId`

---

## 11. 配置规范

配置文件：

```text
application.yml              # 主配置（通用项）
application-dev.yml          # 开发环境（端口、数据源等）
application-prod.yml.example # 生产环境模板（仅示例，不生效）
```

规则：

- Spring Boot 只加载 `application.yml` 和 `application-{profile}.yml`，`.example` 后缀文件不生效。
- 主配置与 profile 配置中环境变量名必须一致，防止静默失败。
- 包含真实密钥、密码、Token、私有地址的配置文件不得提交。
- 生产配置通过环境变量、容器编排或密钥管理系统注入。
- 业务配置统一使用 `fitmate` 前缀。
- 环境变量统一使用 `FITMATE_` 前缀。
- 第三方惯用变量可以保留，但项目内部读取应集中映射到配置属性类。

顶层配置段顺序约定：

```text
server → website → spring → reasoning → rag → internet → fitmate → logging → mybatis-plus
```

YAML 文件使用 2 空格缩进，顶层段之间空 1 行，段内不空行。

端口约定：

| 服务 | 端口 | 环境变量 |
| --- | --- | --- |
| FitMate-api | 7070 | `MCP_CLIENT_PORT` |
| FitMate-mcpServer | 9070 | `MCP_SERVER_PORT` |

Prompt 模板存放在 `resources/prompts/`，由 `PromptTemplateManager` 加载。

---

## 12. 定时任务

定时任务通过 `@EnableScheduling`（位于 `MemoryAsyncConfig`）+ `@Scheduled` 实现。执行时间错峰：

| 任务类 | 所在包 | 执行时间 | 职责 |
| --- | --- | --- | --- |
| `SnapshotAggregator` | `agent.memory.longterm.application.scheduler` | 每日 2:00 | 聚合用户近 N 天训练与体测数据，生成 SNAPSHOT 记忆 |
| `AgentRunCleanupScheduler` | `agent.application.scheduler` | 每日 3:00 | 清理过期 AgentStep、AgentRun、ChatThinking（30 天 TTL） |
| `WikiPageCleanupTask` | `wiki.application.scheduler` | 每日 3:30 | 清理过期 Wiki 页面（3 个月 TTL），级联清理向量与关键词索引 |

清理任务受配置开关控制：

- `fitmate.agent.cleanup-enabled`（默认 true）
- `fitmate.agent.retention-days`（默认 30）
- `fitmate.wiki.retention-months`（默认 3）

---

## 13. 异常、日志与安全

### 13.1 异常

- 使用统一业务异常 `BusinessException`。
- 使用统一错误码 `ErrorCode`。
- 使用全局异常处理器 `GlobalExceptionHandler`。
- 禁止吞掉异常后返回 `null` 或空字符串。
- 不能处理的异常交给上层统一处理。

### 13.2 日志

- 使用 SLF4J，不使用 `System.out.println`。
- 日志应包含关键业务标识，如 `userId`、`runId`、`documentId`、`jobId`。
- 禁止打印密码、验证码、API Key、Token、Cookie、Authorization Header。
- 异常日志保留堆栈。
- 高频业务失败日志需要控制级别和频率。
- Wiki 编译等异步长流程必须记录关键节点日志：开始、LLM 调用、LLM 返回、完成。

### 13.3 安全

- 所有外部输入都必须校验。
- 文件上传必须校验大小、类型和文件名。
- 外部 URL 请求需要避免 SSRF 风险。
- 认证接口需要限制验证码频率和登录尝试频率。
- 敏感配置不得写死在代码或提交到仓库。
- Agent 调用工具时，用户身份必须由后端可信上下文注入。
- 删除操作必须验证用户归属权限：RAG 删除验证用户 ID 归属，Wiki 删除验证空间归属。

---

## 14. AI、RAG、Agent、Wiki 与 MCP 规范

### 14.1 AI 调用

- 模型配置集中在配置类中，通过 `LlmGateway` 抽象。
- Prompt 模板独立存放在 `resources/prompts/`，由 `PromptTemplateManager` 加载。
- 对模型返回的结构化结果必须做解析失败处理。
- 流式输出必须可中断、可记录关键错误。
- `JsonStringUnescaper` 处理 LLM 返回 JSON 中的转义字符。
- LLM 返回的 Wiki 编译动作必须对 `page_type`、`title` 等字段设置默认值，防止入库失败。

### 14.2 记忆系统

- 上下文压缩与长期记忆统一放在 `agent.memory` 包下。
- 短期上下文压缩：`ContextCompressService`，当对话超出窗口时触发摘要。
- 长期记忆：`MemoryWriter`、`MemoryReader`、`MemoryExtractCounter`。
- 用户画像：`ProfileBuilder` 定期生成与缓存。
- 会话快照：`SnapshotAggregator` 每日 2:00 聚合。
- 记忆类型枚举：`FACT`、`EPISODIC`、`SNAPSHOT`、`INSIGHT`。
- 删除会话时同步失效思考内容缓存。

### 14.3 RAG

- 文档解析、分块、向量化、检索、生成拆成可测试步骤。
- 知识库名称、向量索引、embedding 模型必须可配置。
- 文档服务命名使用 `RagDocumentService` 或 `RagIngestionService`。
- benchmark 结果保留输入、输出、评分和运行时间。
- 检索链路中的 vector recall、keyword recall、fusion、rerank 应边界清晰。
- RAG 分块使用 `{docId}:{seq}` 作为向量文档 ID，删除时按文档批量清理。
- RAG 删除需验证用户 ID 归属，只清理 RAG 侧，不级联 Wiki。

### 14.4 Agent

- Agent run 与 step 状态必须可持久化、可查询、可通过 SSE 回传。
- 并发策略使用多 slot Redis 锁，每会话最多 3 个并发 run。
- AgentStep、AgentRun、ChatThinking 数据有 30 天 TTL，每日 3:00 清理。
- workflow 步骤名、状态枚举、失败原因统一命名。
- MCP 工具调用参数必须由后端校验。
- Sub-Agent 由主 Agent 派生，通过 `subRuns` 字段序列化存储。
- 思考缓存 schema 版本 v=2，含 `subRuns` 字段。

### 14.5 Wiki

- Wiki 编译为异步任务，通过 LLM 生成页面。
- Wiki 页面有 3 个月 TTL，每日 3:30 清理，并级联清理 Redis 向量与关键词索引。
- Wiki 页面使用 `pageId` 作为向量文档 ID。
- Wiki 删除独立于 RAG 删除，不级联。
- 编译任务必须记录 `pages_touched_json`，页面必须记录 `frontmatter_json`。
- 编译日志必须包含：开始、LLM 调用、LLM 返回、完成四个关键节点。

### 14.6 知识库检索流程

当知识库开关启用时：

1. 先执行 Wiki 检索（默认开启）。
2. 若 RAG 也启用，先执行查询改写，再执行 RAG 检索。
3. 结果按顺序返回给 Agent 循环。
4. RAG 与联网搜索可同时启用，不互斥。

### 14.7 MCP

- `FitMate-api` 中的 `integration.mcp` 与 `agent.mcp` 只负责发现与调用工具。
- `FitMate-mcpServer` 只负责暴露和执行工具能力。
- Tool 名称使用 lowerCamelCase，例如 `createTrainingLog`。
- Tool 入参和出参必须是结构化 DTO。
- Tool 描述必须说明能力边界、必填参数和返回含义。
- MCP 工具按领域组织：`email`、`rag`。

---

## 15. 测试规范

- 单元测试使用 JUnit 5。
- 应用服务、文档解析、RAG 分块、DTO 转换、错误码映射优先写单元测试。
- 只有需要 Spring 容器时才使用 `@SpringBootTest`。
- Mapper 或外部依赖测试应标识为集成测试。
- Mockito mock 测试可绕过容器扫描，需通过运行时 Spring Boot 启动验证 Mapper Bean 注入。
- 新增 bug 修复优先补充能复现问题的测试。

推荐命令：

```bash
mvn test
mvn -pl FitMate-api -am test
mvn -pl FitMate-mcpServer -am test
```

---

## 16. 变更检查清单

- [ ] 新代码包名是否位于 `com.itgeo.fitmate` 下？
- [ ] 新业务是否放在对应 feature 包中，而不是塞入 `common`？
- [ ] Controller 是否只做入口职责？
- [ ] 应用服务是否承载流程编排和事务边界？
- [ ] Mapper、Redis、HTTP、模型 SDK 是否封装在 infrastructure 或专用 client 中？
- [ ] DTO、实体、查询对象、命令对象命名是否清晰？
- [ ] Redis Key、数据库表名、配置项是否符合统一命名？
- [ ] Redis Key 是否设置 TTL 且常量集中到 `RedisKeyConstants`？
- [ ] 日志是否避免输出密钥、Token、验证码等敏感信息？
- [ ] Prompt 模板是否放在 `resources/prompts/` 而非 Java 代码中？
- [ ] 定时任务是否遵循错峰执行（2:00 / 3:00 / 3:30）？
- [ ] 删除操作是否验证了用户归属权限？
- [ ] RAG、Agent、Wiki、MCP 变更是否有清晰边界和必要测试？
- [ ] 修改后是否运行相关 Maven 测试或说明未运行原因？
