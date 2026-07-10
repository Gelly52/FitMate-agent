# FitMate-AI 全局代码规范 SPEC

版本：v0.4  
适用范围：FitMate-AI 前端、后端、MCP 工具服务、配置、数据库脚本与文档。  
目标读者：开发者、维护者、代码评审者。

---

## 1. 项目概述

FitMate-AI 是面向健身场景的 AI 助手系统，提供用户认证、Agent 聊天、长期记忆与用户画像、Wiki 知识库、RAG 知识库、联网搜索、Sub-Agent 编排、训练日志、身体指标记录和 MCP 工具调用等能力。

代码规范目标：

- 项目命名统一使用 `FitMate-AI`、`FitMate`、`fitmate`。
- 后端模块按部署边界划分，Java 包按业务能力划分。
- `common` 只承载横切通用能力，不承载具体业务模块。
- 接口、配置、数据库、日志和文档命名保持一致。
- 保持代码简单、清晰、可测试、可维护。

---

## 2. 技术栈

### 2.1 前端

- Vue 3
- TypeScript
- Vite
- Vue Router（Hash 模式）
- Axios
- Tailwind CSS（Material Design 3 token 体系）
- marked
- npm

### 2.2 后端

- Java 21
- Spring Boot 3.5.x
- Spring AI 1.x
- MyBatis-Plus
- Maven 多模块工程
- SSE
- MCP Client / MCP Server

### 2.3 数据与基础设施

- MySQL 8
- Redis
- Redis Stack / Redis Vector Store
- SearXNG
- OpenAI 兼容模型服务或 Spring AI 支持的模型服务
- Docker Compose

---

## 3. 模块架构

### 3.1 目录结构

```text
FitMate-AI/
├─ FitMate-frontend/       # Vue 3 + TypeScript 前端
├─ FitMate-backend/        # Java 后端 Maven 父工程
│  ├─ pom.xml
│  ├─ FitMate-api/         # 主业务 API 服务
│  ├─ FitMate-mcpServer/   # MCP 工具服务
│  └─ FitMate-common/      # 统一响应、异常、错误码与通用工具
├─ docs/                   # 设计文档、计划与 spec
├─ docker-compose.yml
├─ .env.example
├─ .gitignore
├─ LICENSE
└─ SPEC.md
```

### 3.2 Maven 模块

| 目录名 | artifactId | 职责 |
| --- | --- | --- |
| `FitMate-api` | `fitmate-api` | 面向前端暴露 REST/SSE API，承载认证、聊天、记忆、Agent、RAG、Wiki、搜索、训练、身体指标等业务能力。 |
| `FitMate-mcpServer` | `fitmate-mcp-server` | 暴露 MCP 工具，承载邮件、RAG 管理等工具能力。 |
| `FitMate-common` | `fitmate-common` | 放置统一响应、异常、错误码、常量、枚举等横切能力。 |

> 当前不存在独立 `FitMate-domain` 模块。领域逻辑下沉到各功能包的 `domain` 子包内。

父工程：

```xml
<groupId>com.itgeo</groupId>
<artifactId>fitmate-ai</artifactId>
<version>0.1.0-SNAPSHOT</version>
<packaging>pom</packaging>
```

### 3.3 功能模块

| 模块 | 包名片段 | 职责 |
| --- | --- | --- |
| 用户认证 | `auth` | 验证码、登录、退出、token、用户上下文、用户资料、用户偏好。 |
| 浏览器 SSE | `sse` | SSE ticket、连接管理、事件推送。 |
| 聊天 | `chat` | 对话会话、消息持久化、思考内容存储。 |
| Agent | `agent` | 任务受理、run/step 状态、异步 workflow、SSE 回传、工具调用编排。 |
| Agent 记忆 | `agent.memory` | 上下文压缩、长期记忆、用户画像、记忆提取与召回。 |
| Sub-Agent | `agent`（内嵌） | 由主 Agent 派生子 Agent 执行子任务。 |
| RAG | `rag` | 文档上传、解析、分块、embedding、检索、rerank、benchmark。 |
| Wiki | `wiki` | LLM 知识库空间、页面、编译任务、向量与关键词检索。 |
| 联网搜索 | `search` | SearXNG 搜索、WebFetch 抓取。 |
| 训练日志 | `fitness.training` | 训练日志、动作明细、肌肉群字典。 |
| 身体指标 | `fitness.metrics` | 体重、体脂、睡眠、疲劳度等指标。 |
| 有氧训练 | `fitness.cardio` | 有氧训练日志与 MET 字典。 |
| 心率 | `fitness.heartrate` | 心率记录。 |
| 饮食 | `fitness.diet` | 饮食日志与明细。 |
| MCP 工具 | `mcp`（mcpServer 模块） | 邮件、RAG 管理工具。 |
| 技能 | `skill` | 技能列表渐进式加载。 |

`common` 不得包含上述业务模块的核心逻辑。

---

## 4. Java 包命名

### 4.1 根包

所有 Java 代码根包统一为：

```java
com.itgeo.fitmate
```

禁止新增以下包名：

```text
com.itgeo.pojo
com.itgeo.service
com.itgeo.mapper
com.itgeo.FitMate-xxx
```

### 4.2 模块包

```text
com.itgeo.fitmate.api
com.itgeo.fitmate.mcp
com.itgeo.fitmate.common
```

### 4.3 API 服务包结构

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

### 4.4 MCP 服务包结构

```text
com.itgeo.fitmate.mcp
├─ FitMateMcpServerApplication.java
├─ email             # 邮件工具
└─ rag               # RAG 管理工具
   └─ infrastructure # 实体与 Mapper
```

### 4.5 功能内分层

每个功能包内部优先采用以下结构：

```text
feature/
├─ controller/          # HTTP API 入口
├─ application/         # 应用服务与流程编排
│  └─ impl/             # 服务实现
│  └─ scheduler/        # 定时任务
├─ domain/              # 领域对象、领域规则
├─ infrastructure/      # 数据库、Redis、外部 HTTP、MCP、文件系统
│  ├─ entity/
│  └─ mapper/
├─ dto/                 # 请求、响应、命令、查询对象
└─ config/              # 功能内配置
```

小功能可以合并少量层级，但不得把所有类平铺到根包。`controller` 不直接访问数据库、Redis、模型 SDK 或 MCP SDK。`infrastructure` 只表达技术实现，不承载主要业务流程。

---

## 5. 类命名规范

### 5.1 启动类

```java
FitMateApiApplication
FitMateMcpServerApplication
```

### 5.2 常见类型命名

| 类型 | 命名规则 | 示例 |
| --- | --- | --- |
| Controller | `{Feature}Controller` | `ChatController` |
| 应用服务 | `{Feature}Service` | `RagService` |
| 应用服务实现 | `{Feature}ServiceImpl` | `RagServiceImpl` |
| 领域服务 | `{Feature}DomainService` | `TrainingDomainService` |
| 请求 DTO | `{Action}Request` | `LoginRequest` |
| 响应 DTO | `{Action}Response` | `LoginResponse` |
| 内部命令 | `{Action}Command` | `ExecuteAgentCommand` |
| 查询对象 | `{Feature}Query` | `TrainingLogQuery` |
| 实体 | `{Domain}` 或 `{Domain}Entity` | `User`、`RagDocumentEntity` |
| Mapper | `{Domain}Mapper` | `TrainingLogMapper` |
| Repository | `{Domain}Repository` | `RagDocumentRepository` |
| 配置类 | `{Feature}Config` | `RedisConfig` |
| 配置属性 | `{Feature}Properties` | `OpenAiProperties` |
| MCP Tool | `{Capability}Tool` | `TrainingLogTool` |
| 工具执行器 | `{Capability}ToolExecutor` | `WebSearchToolExecutor` |
| 外部客户端 | `{Provider}Client` | `SearxngClient` |
| LLM 网关 | `{Provider}Gateway` | `LlmGateway` |
| 转换器 | `{Source}To{Target}Converter` | `DocumentToChunkConverter` |
| 定时任务 | `{Feature}CleanupTask` / `{Feature}Scheduler` / `{Feature}Aggregator` | `WikiPageCleanupTask` |
| 异常 | `{Scene}Exception` | `AuthenticationException` |

### 5.3 基础命名规则

- 类名使用 UpperCamelCase。
- 方法、变量、参数使用 lowerCamelCase。
- 常量使用 UPPER_SNAKE_CASE。
- 包名全部小写，不使用短横线、下划线、中文或大写字母。
- 布尔变量使用明确语义，如 `enabled`、`authenticated`、`expired`。
- 集合变量使用复数，如 `documents`、`messages`。
- 避免无意义缩写，如 `Mgr`、`Svc`、`Obj`、`Tmp`。
- 普通业务类不强制添加 `FitMate` 前缀，避免类名冗长。

---

## 6. API 规范

### 6.1 路径命名

- 现有接口使用领域前缀，例如 `/user/...`、`/chat/...`、`/agent/...`、`/rag/...`、`/wiki/...`。
- 路径使用小写短横线或明确的领域名。
- 路径中使用名词表达资源，动作用 HTTP 方法表达。
- 特殊动作接口可使用语义清晰的动词片段。

示例：

```text
POST   /user/code
POST   /user/login
POST   /user/logout
GET    /chat/records
POST   /agent/execute
GET    /agent/runs/{runId}
POST   /rag/uploadRagDoc
DELETE /rag/docs/{docId}
GET    /wiki/spaces
POST   /wiki/rebuild/{jobId}
DELETE /wiki/pages/{pageId}
POST   /training/log
GET    /body-metrics/recent
POST   /memory/profile/rebuild
```

### 6.2 Controller

- Controller 只负责参数接收、校验、调用应用服务、返回响应。
- 入参使用 DTO，并使用 Jakarta Validation 注解校验。
- 不在 Controller 中写复杂业务逻辑。
- 不在 Controller 中直接访问 Mapper、RedisTemplate、模型 SDK、MCP SDK。
- 文件下载、SSE 等特殊接口可以不使用统一响应包装。

统一响应示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

### 6.3 SSE

- 浏览器 SSE 与 MCP SSE 必须在命名和文档中明确区分。
- 浏览器 SSE 事件名使用 lowerCamelCase，如 `messageDelta`、`agentStep`、`done`、`error`。
- SSE 的 data 使用结构化 JSON。
- 服务端必须处理客户端断开连接，避免资源泄漏。
- 错误事件必须包含可读错误信息和必要的业务标识。

---

## 7. 数据库与缓存规范

### 7.1 表名

数据库表统一使用 `t_` 前缀，表名使用 snake_case：

```text
t_user
t_user_login_session
t_user_preference
t_user_memory
t_user_profile
t_chat_session
t_chat_message
t_chat_thinking
t_context_summary
t_agent_run
t_agent_step
t_rag_document
t_rag_config
t_rag_benchmark_run
t_wiki_space
t_wiki_page
t_wiki_page_link
t_wiki_compile_job
t_wiki_log
t_training_log
t_training_exercise
t_body_metrics
t_cardio_log
t_heart_rate
t_diet_log
t_diet_item
t_dashboard_summary
```

### 7.2 字段名

- 字段名使用 snake_case。
- 主键统一命名为 `id`。
- 创建时间使用 `created_at`。
- 更新时间使用 `updated_at`。
- 逻辑删除字段统一使用 `deleted` 或 `deleted_at`，同一项目保持一致。
- 状态字段使用明确枚举值，不使用魔法数字。
- JSON 类型字段使用 `_json` 后缀，例如 `frontmatter_json`、`pages_touched_json`、`llm_config_json`。

### 7.3 Mapper 与 SQL

- Mapper 与实体按功能模块归档。
- `@MapperScan` 在启动类上显式列举 Mapper 包路径，不使用通配符。
- SQL 中禁止拼接未校验的用户输入。
- 分页查询必须明确排序字段。
- 跨表查询应返回专用 DTO，不复用实体类承载聚合结果。

### 7.4 Redis Key

Redis Key 使用冒号分隔：

```text
fitmate:{env}:{domain}:{purpose}:{id}
```

示例：

```text
fitmate:dev:auth:sms-code:13800138000
fitmate:dev:auth:email-code:user@example.com
fitmate:dev:sse:ticket:abc123
fitmate:dev:agent:lock:session:{sessionId}:slot:{1|2|3}
fitmate:dev:memory:session:extracted-count:{sessionId}
fitmate:dev:rag:embedding:bgem3:{chunkId}
fitmate:dev:rag:chunk:{docId}:{seq}
fitmate:wiki:embedding:{pageId}
fitmate:wiki:chunk:{pageId}
```

规则：

- 所有临时 Key 必须设置 TTL。
- 分布式锁使用多 slot Key，例如 Agent 任务锁使用 3 个 slot：`fitmate:dev:agent:lock:session:{sessionId}:slot:{1|2|3}`。
- Redis Vector Store 的 index name、key prefix、embedding 维度必须可配置。
- 集中管理 Key 常量到 `RedisKeyConstants`，避免散落在各 Service 类中。

### 7.5 向量检索 ID 约定

- RAG 文档分块使用 `{docId}:{seq}` 作为向量文档 ID。
- Wiki 页面使用 `pageId` 作为向量文档 ID，便于精确删除。

---

## 8. 配置规范

### 8.1 配置文件

配置文件命名：

```text
application.yml              # 主配置（通用项）
application-dev.yml          # 开发环境（端口、数据源等）
application-prod.yml.example # 生产环境模板（仅示例，不生效）
```

规则：

- Spring Boot 只加载 `application.yml` 和 `application-{profile}.yml`，`.example` 后缀文件不生效。
- 包含真实密钥、密码、Token、私有地址的配置文件不得提交。
- 生产配置通过环境变量、容器编排或密钥管理系统注入。
- 主配置与 profile 配置中环境变量名必须一致，防止静默失败。

### 8.2 配置项前缀

业务配置统一使用 `fitmate` 前缀：

```yaml
fitmate:
  llm:
    encryption-key: ${LLM_ENCRYPTION_KEY}
  agent:
    max-iterations: 20
    cleanup-enabled: true
    retention-days: 30
    cleanup-cron: "0 0 3 * * *"
  memory:
    enabled: true
    snapshot:
      cron: "0 0 2 * * *"
  wiki:
    enabled: true
    retention-months: 3
```

顶层配置段顺序约定：

```text
server → website → spring → reasoning → rag → internet → fitmate → logging → mybatis-plus
```

YAML 文件使用 2 空格缩进，顶层段之间空 1 行，段内不空行。

### 8.3 端口约定

| 服务 | 端口 | 环境变量 |
| --- | --- | --- |
| FitMate-api | 7070 | `MCP_CLIENT_PORT` |
| FitMate-mcpServer | 9070 | `MCP_SERVER_PORT` |

### 8.4 环境变量

环境变量统一使用 `FITMATE_` 前缀：

```text
FITMATE_DB_HOST
FITMATE_DB_PORT
FITMATE_REDIS_HOST
FITMATE_MCP_SERVER_URL
FITMATE_OPENAI_BASE_URL
FITMATE_OPENAI_API_KEY
```

第三方标准变量可保留惯用名称，但项目内部读取应集中映射到配置属性类。

### 8.5 Prompt 模板

- Prompt 模板必须作为独立文件存放在 `resources/prompts/` 目录下。
- 技能相关 Prompt 放在 `prompts/skills/` 子目录。
- 禁止在 Java 代码中拼接大段 Prompt 文本。

当前 Prompt 文件：

```text
prompts/
├─ agent-system.md
├─ context-compress.md
├─ memory-extract.md
├─ profile-build.md
├─ wiki-compile.md
├─ wiki-context.md
├─ wiki-schema.md
└─ skills/
   ├─ analyze-weekly-training.md
   ├─ generate-weekly-report.md
   └─ recovery-assessment.md
```

---

## 9. Java 代码风格

### 9.1 基础规则

- 文件编码：UTF-8。
- 缩进：4 个空格。
- 行宽建议不超过 120 字符。
- 禁止通配符 import。
- import 分组保持 IDE 默认或项目统一格式。
- 单个方法只表达一个清晰意图。
- 避免过早抽象和无意义工具类。

### 9.2 Lombok

允许使用：

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
```

规则：

- 实体类和复杂领域对象谨慎使用 `@Data`。
- 业务代码禁止使用 `@SneakyThrows`。
- 构造注入优先使用 `@RequiredArgsConstructor`。

### 9.3 空值与 Optional

- 公共方法参数需要明确是否允许为 `null`。
- Controller 入参通过 Validation 保证基本合法性。
- `Optional` 可作为返回值，不作为实体字段或 DTO 字段。
- 业务状态使用枚举，不使用 `null` 表达状态。

### 9.4 枚举

枚举名使用 UpperCamelCase，枚举值使用 UPPER_SNAKE_CASE：

```java
public enum AgentRunStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELED
}
```

涉及数据库存储时，必须明确 code 与 description，避免依赖 ordinal。

---

## 10. 异常、日志与安全

### 10.1 异常

- 使用统一业务异常，例如 `BusinessException`。
- 使用统一错误码，例如 `ErrorCode`。
- 使用全局异常处理器，例如 `GlobalExceptionHandler`。
- 禁止吞掉异常后返回 `null` 或空字符串。
- 不能处理的异常交给上层统一处理。

### 10.2 日志

- 使用 SLF4J，不使用 `System.out.println`。
- 日志应包含关键业务标识，如 `userId`、`runId`、`documentId`、`jobId`。
- 禁止打印密码、验证码、API Key、Token、Cookie、Authorization Header。
- 异常日志保留堆栈。
- 高频业务失败日志需要控制级别和频率。
- Wiki 编译等异步长流程必须记录关键节点日志：开始、LLM 调用、LLM 返回、完成。

### 10.3 安全

- 所有外部输入都必须校验。
- 文件上传必须校验大小、类型和文件名。
- 外部 URL 请求需要避免 SSRF 风险。
- 认证接口需要限制验证码频率和登录尝试频率。
- 敏感配置不得写死在代码或提交到仓库。
- Agent 调用工具时，用户身份必须由后端可信上下文注入。
- 删除操作必须验证用户归属权限：RAG 删除验证用户 ID 归属，Wiki 删除验证空间归属。

---

## 11. AI、记忆、RAG、Agent、Wiki 与 MCP

### 11.1 AI 调用

- 模型配置集中在配置类中，通过 `LlmGateway` 抽象。
- Prompt 模板独立存放在 `resources/prompts/` 目录，由 `PromptTemplateManager` 加载。
- 对模型返回的结构化结果必须做解析失败处理。
- 流式输出必须可中断、可记录关键错误。
- `JsonStringUnescaper` 处理 LLM 返回 JSON 中的转义字符。

### 11.2 记忆系统

- 上下文压缩与长期记忆统一放在 `agent.memory` 包下。
- 短期上下文压缩：`ContextCompressService`，当对话超出窗口时触发摘要。
- 长期记忆：`MemoryWriter`、`MemoryReader`、`MemoryExtractCounter`。
- 用户画像：`ProfileBuilder` 定期生成与缓存。
- 会话快照：`SnapshotAggregator` 每日 2:00 聚合用户训练与体测数据。
- 记忆类型枚举：`FACT`、`EPISODIC`、`SNAPSHOT`、`INSIGHT`。
- 删除会话时同步失效思考内容缓存。

### 11.3 RAG

RAG 推荐结构：

```text
rag/
├─ controller/
├─ application/
│  └─ impl/
├─ config/
├─ dto/
└─ infrastructure/
   ├─ chunking/
   ├─ embedding/
   ├─ parser/
   ├─ entity/
   └─ mapper/
```

规则：

- 文档解析、分块、向量化、检索、生成拆成可测试步骤。
- 知识库名称、向量索引、embedding 模型必须可配置。
- 文档服务命名使用 `RagDocumentService` 或 `RagIngestionService`。
- benchmark 结果保留输入、输出、评分和运行时间。
- 检索链路中的 vector recall、keyword recall、fusion、rerank 应边界清晰。
- RAG 分块使用 `{docId}:{seq}` 作为向量文档 ID，删除时按文档批量清理。

### 11.4 Agent

Agent 推荐结构：

```text
agent/
├─ controller/
├─ application/
│  ├─ impl/
│  └─ scheduler/
├─ core/               # 取消注册表、循环执行器、流式缓冲
├─ config/
├─ llm/                # LLM 网关与 JSON 清洗
├─ mcp/                # MCP 客户端池与工具注册
├─ memory/             # 上下文压缩与长期记忆
│  └─ longterm/
├─ prompt/
├─ tool/               # 工具执行器与路由
├─ trace/              # 执行轨迹
├─ infrastructure/
│  ├─ entity/
│  └─ mapper/
└─ dto/
```

规则：

- Agent run 与 step 状态必须可持久化、可查询、可通过 SSE 回传。
- 并发策略使用多 slot Redis 锁，每会话最多 3 个并发 run。
- AgentStep、AgentRun、ChatThinking 数据有 30 天 TTL，每日 3:00 清理。
- workflow 步骤名、状态枚举、失败原因统一命名。
- MCP 工具调用参数必须由后端校验。
- Sub-Agent 由主 Agent 派生，通过 `subRuns` 字段序列化存储。

### 11.5 Wiki

Wiki 推荐结构：

```text
wiki/
├─ controller/
├─ application/
│  ├─ impl/
│  └─ scheduler/       # WikiPageCleanupTask
├─ config/
├─ dto/
└─ infrastructure/
   ├─ entity/
   └─ mapper/
```

规则：

- Wiki 编译为异步任务，通过 LLM 生成页面。
- Wiki 页面有 3 个月 TTL，每日 3:30 清理，并级联清理 Redis 向量与关键词索引。
- Wiki 页面使用 `pageId` 作为向量文档 ID。
- Wiki 删除独立于 RAG 删除，不级联。
- LLM 返回的编译动作必须对 `page_type`、`title` 等字段设置默认值，防止入库失败。
- 编译任务必须记录 `pages_touched_json`，页面必须记录 `frontmatter_json`。
- 编译日志必须包含：开始、LLM 调用、LLM 返回、完成四个关键节点。

### 11.6 知识库检索流程

当知识库开关启用时：

1. 先执行 Wiki 检索（默认开启）。
2. 若 RAG 也启用，先执行查询改写，再执行 RAG 检索。
3. 结果按顺序返回给 Agent 循环。

RAG 与联网搜索可同时启用，不互斥。

### 11.7 MCP

- `FitMate-api` 中的 `integration.mcp` 与 `agent.mcp` 只负责发现与调用工具。
- `FitMate-mcpServer` 只负责暴露和执行工具能力。
- Tool 名称使用 lowerCamelCase，例如 `createTrainingLog`。
- Tool 入参和出参必须是结构化 DTO。
- Tool 描述必须说明能力边界、必填参数和返回含义。
- MCP 工具按领域组织：`email`、`rag`。

---

## 12. 前端代码规范

### 12.1 目录结构

```text
src/
├─ main.ts                # 应用入口
├─ App.vue                # 根组件
├─ router/                # 路由配置与鉴权守卫
├─ layouts/               # 页面布局
├─ components/            # 跨页面通用组件
├─ pages/                 # 页面级模块
│  ├─ chat/
│  │  ├─ ChatLogicBase.vue
│  │  ├─ ChatPage.vue
│  │  └─ components/
│  ├─ dashboard/
│  ├─ knowledge/
│  ├─ login/
│  ├─ metrics/
│  ├─ settings/
│  │  └─ components/
│  ├─ training/
│  └─ wiki/
├─ services/              # HTTP、SSE、业务 API、缓存、主题
├─ config/                # 运行时配置
├─ utils/                 # 纯工具函数
├─ types/                 # 全局或共享类型声明
└─ styles/                # 全局样式与 token
```

### 12.2 Vue 规范

- Vue 单文件组件使用 `<script setup lang="ts">`。
- 组件名使用 UpperCamelCase，例如 `ChatPanel.vue`。
- 页面组件放在 `pages`，通用组件放在 `components`，页面私有组件放在页面目录的 `components/` 下。
- 组合函数命名为 `useXxx`。
- 组件中不直接拼接后端 URL，统一从 `services` 层调用。
- 复杂类型定义放在 `types` 中。

### 12.3 API 调用

- Axios 实例集中在 `services/http.ts`，配置 baseURL、超时、拦截器。
- API 方法集中在 `services/doctorApi.ts`，按业务方法组织。
- 专用 API（memory、wiki、llmConfig）可拆分独立服务文件。
- 所有后端响应都应有 TypeScript 类型。
- SSE 逻辑封装在 `services/sseService.ts`。

### 12.4 认证约定

- Cookie：`user_token`、`user_info`
- Header：`headerUserToken`、`headerUserId`
- 页面不直接拼接认证 Header。

---

## 13. 定时任务

定时任务通过 `@EnableScheduling`（位于 `MemoryAsyncConfig`）+ `@Scheduled` 实现。

| 任务 | 执行时间 | 职责 |
| --- | --- | --- |
| `SnapshotAggregator` | 每日 2:00 | 聚合用户近 N 天训练与体测数据，生成 SNAPSHOT 记忆 |
| `AgentRunCleanupScheduler` | 每日 3:00 | 清理过期 AgentStep、AgentRun、ChatThinking（30 天 TTL） |
| `WikiPageCleanupTask` | 每日 3:30 | 清理过期 Wiki 页面（3 个月 TTL），级联清理向量与关键词索引 |

---

## 14. 文档与测试

### 14.1 文档

建议维护以下文档：

```text
SPEC.md                    # 根全局规范
FitMate-backend/SPEC.md    # 后端规范
FitMate-frontend/SPEC.md   # 前端规范
docs/                      # 设计文档与计划
```

文档要求：

- 接口文档包含路径、方法、请求、响应、错误码、认证要求。
- 配置文档说明默认端口、环境变量、依赖服务。
- 示例不得包含真实密钥。

### 14.2 后端测试

- 单元测试使用 JUnit 5。
- 应用服务、文档解析、RAG 分块、DTO 转换、错误码映射优先写单元测试。
- 只有需要 Spring 容器时才使用 `@SpringBootTest`。
- Mapper 或外部依赖测试应标识为集成测试。
- Mockito mock 测试可绕过容器扫描，需通过运行时 Spring Boot 启动验证 Mapper Bean 注入。

推荐命令：

```bash
mvn test
mvn -pl FitMate-api -am test
mvn -pl FitMate-mcpServer -am test
```

### 14.3 前端测试

至少保证：

```bash
npm run build
```

---

## 15. 命名速查

| 场景 | 规范示例 |
| --- | --- |
| 项目根目录 | `FitMate-AI` |
| 前端目录 | `FitMate-frontend` |
| 后端父目录 | `FitMate-backend` |
| 主业务 API 目录 | `FitMate-api` |
| MCP Server 目录 | `FitMate-mcpServer` |
| 通用模块目录 | `FitMate-common` |
| Maven 父 artifactId | `fitmate-ai` |
| API artifactId | `fitmate-api` |
| MCP Server artifactId | `fitmate-mcp-server` |
| common artifactId | `fitmate-common` |
| Java 根包 | `com.itgeo.fitmate` |
| API 包 | `com.itgeo.fitmate.api` |
| MCP 包 | `com.itgeo.fitmate.mcp` |
| common 包 | `com.itgeo.fitmate.common` |
| RAG 包 | `com.itgeo.fitmate.api.rag` |
| 记忆包 | `com.itgeo.fitmate.api.agent.memory` |
| Agent 包 | `com.itgeo.fitmate.api.agent` |
| Wiki 包 | `com.itgeo.fitmate.api.wiki` |
| 训练包 | `com.itgeo.fitmate.api.fitness.training` |
| 身体指标包 | `com.itgeo.fitmate.api.fitness.metrics` |
| 数据库表前缀 | `t_` |
| Redis Key 前缀 | `fitmate:{env}:...` |
| 环境变量前缀 | `FITMATE_` |
| Docker 资源前缀 | `fitmate-ai-` |
| API 端口 | `7070` |
| MCP Server 端口 | `9070` |
| 前端开发端口 | `5500` |

---

## 16. 代码评审检查清单

- [ ] 项目、模块、包、配置、数据库命名是否统一？
- [ ] Java 包名是否全部位于 `com.itgeo.fitmate` 下？
- [ ] RAG、记忆、Agent、Wiki、搜索、fitness 是否有独立 package？
- [ ] `common` 是否只包含横切通用能力？
- [ ] Controller 是否只做入口职责？
- [ ] 业务流程是否放在 application/service 层？
- [ ] 外部依赖调用是否放在 infrastructure 层？
- [ ] DTO、Entity、Mapper、Tool 命名是否符合规范？
- [ ] Prompt 模板是否放在 `resources/prompts/` 而非 Java 代码中？
- [ ] 配置是否可通过环境变量覆盖？
- [ ] 是否避免提交真实密钥和本地私有配置？
- [ ] Redis Key 是否设置 TTL？
- [ ] 删除操作是否验证了用户归属权限？
- [ ] 是否运行了必要的构建或测试？
