# FitMate-AI 全局代码规范 SPEC

版本：v0.3  
适用范围：FitMate-AI 前端、后端、MCP 工具服务、配置、数据库脚本与文档。  
目标读者：开发者、维护者、代码评审者。

---

## 1. 项目概述

FitMate-AI 是面向健身场景的 AI 助手系统，提供用户认证、实时聊天、记忆系统、Agent 执行、RAG 知识库、联网搜索、训练日志、身体指标记录和 MCP 工具调用等能力。

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
- Vue Router
- Axios
- Tailwind CSS 或项目统一样式体系
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
│  ├─ FitMate-domain/      # 领域实体、枚举与值对象
│  └─ FitMate-common/      # 统一响应、异常、错误码与通用工具
├─ datasets/               # RAG 数据集与 benchmark 数据
├─ docs/                   # 接口、数据库、部署、能力说明
├─ docker-compose.yml
├─ .env.example
├─ README.md
├─ README_CN.md
└─ SPEC.md
```

### 3.2 Maven 模块

| 目录名 | artifactId | 职责 |
| --- | --- | --- |
| `FitMate-api` | `fitmate-api` | 面向前端暴露 REST/SSE API，承载认证、聊天、记忆、Agent、RAG、搜索、训练、身体指标等业务能力。 |
| `FitMate-mcpServer` | `fitmate-mcp-server` | 暴露 MCP 工具，承载时间、邮件、训练、身体指标、RAG 管理等工具能力。 |
| `FitMate-domain` | `fitmate-domain` | 放置跨模块稳定复用的领域实体、枚举、值对象。 |
| `FitMate-common` | `fitmate-common` | 放置统一响应、异常、错误码、基础工具方法等横切能力。 |

父工程建议：

```xml
<groupId>com.itgeo</groupId>
<artifactId>fitmate-ai</artifactId>
<version>0.1.0-SNAPSHOT</version>
<packaging>pom</packaging>
```

### 3.3 功能模块

| 模块 | 包名片段 | 职责 |
| --- | --- | --- |
| 用户认证 | `auth`、`user` | 验证码、登录、退出、token、用户上下文。 |
| 浏览器 SSE | `sse` | SSE ticket、连接管理、事件推送。 |
| 聊天 | `chat` | 普通对话、流式回复、prompt 管理、聊天入口。 |
| 记忆 | `memory` | ChatMemory、会话历史、消息持久化、记忆裁剪与摘要。 |
| Agent | `agent` | 任务受理、run/step 状态、异步 workflow、SSE 回传。 |
| RAG | `rag` | 文档上传、解析、分块、embedding、检索、rerank、benchmark。 |
| 联网搜索 | `search` | SearXNG 搜索、搜索增强问答。 |
| 训练日志 | `fitness.training` | 训练日志、动作明细、训练摘要。 |
| 身体指标 | `fitness.metrics` | 体重、体脂、睡眠、疲劳度等指标。 |
| MCP 工具 | `mcp` | 时间、邮件、训练、身体指标、RAG 管理工具。 |

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
com.itgeo.fitmate.domain
com.itgeo.fitmate.common
```

### 4.3 API 服务包结构

```text
com.itgeo.fitmate.api
├─ FitMateApiApplication.java
├─ auth
├─ user
├─ sse
├─ chat
├─ memory
├─ agent
├─ rag
├─ search
├─ fitness
│  ├─ training
│  └─ metrics
└─ integration
   └─ mcp
```

### 4.4 MCP 服务包结构

```text
com.itgeo.fitmate.mcp
├─ FitMateMcpServerApplication.java
├─ time
├─ email
├─ fitness
│  ├─ training
│  └─ metrics
└─ rag
```

### 4.5 功能内分层

每个功能包内部优先采用以下结构：

```text
feature/
├─ controller/          # HTTP API 入口
├─ application/         # 应用服务与流程编排
├─ domain/              # 领域对象、领域规则
├─ infrastructure/      # 数据库、Redis、外部 HTTP、MCP、文件系统
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
| 外部客户端 | `{Provider}Client` | `SearxngClient` |
| 转换器 | `{Source}To{Target}Converter` | `DocumentToChunkConverter` |
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

- 新接口推荐使用 `/api/v1/...` 前缀。
- 路径使用小写短横线或明确的领域名。
- 路径中使用名词表达资源，动作用 HTTP 方法表达。
- 特殊动作接口可使用语义清晰的动词片段。

示例：

```text
POST /api/v1/auth/code
POST /api/v1/auth/login
POST /api/v1/auth/logout
GET  /api/v1/chat/stream
POST /api/v1/agent/runs
GET  /api/v1/agent/runs/{runId}
POST /api/v1/rag/documents
GET  /api/v1/rag/documents
POST /api/v1/rag/query
POST /api/v1/fitness/training/logs
GET  /api/v1/fitness/metrics/recent
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
- SSE 事件名使用 lowerCamelCase，如 `messageDelta`、`agentStep`、`done`、`error`。
- SSE 的 data 使用结构化 JSON。
- 服务端必须处理客户端断开连接，避免资源泄漏。
- 错误事件必须包含可读错误信息和必要的业务标识。

---

## 7. 数据库与缓存规范

### 7.1 表名

新表使用 `fitmate_` 前缀，表名使用 snake_case：

```text
fitmate_user
fitmate_user_login_session
fitmate_chat_session
fitmate_chat_message
fitmate_agent_run
fitmate_agent_step
fitmate_rag_document
fitmate_rag_chunk
fitmate_rag_benchmark_run
fitmate_training_log
fitmate_training_exercise
fitmate_body_metrics
```

### 7.2 字段名

- 字段名使用 snake_case。
- 主键统一命名为 `id`。
- 创建时间使用 `created_at`。
- 更新时间使用 `updated_at`。
- 逻辑删除字段统一使用 `deleted` 或 `deleted_at`，同一项目保持一致。
- 状态字段使用明确枚举值，不使用魔法数字。

### 7.3 Mapper 与 SQL

- Mapper 与实体按功能模块归档。
- XML 路径与 Mapper 包结构保持一致。
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
fitmate:dev:sse:ticket:abc123
fitmate:dev:agent:lock:session123
fitmate:dev:rag:index:fitness-v2
```

所有临时 Key 必须设置 TTL。

---

## 8. 配置规范

### 8.1 配置文件

推荐配置文件：

```text
application.yml
application-local.yml
application-dev.example.yml
application-prod.example.yml
```

规则：

- `application*.example.yml` 可以提交，用于说明配置项。
- 包含真实密钥、密码、Token、私有地址的配置文件不得提交。
- 生产配置通过环境变量、容器编排或密钥管理系统注入。

### 8.2 配置项前缀

业务配置统一使用 `fitmate` 前缀：

```yaml
fitmate:
  auth:
    code-ttl-seconds: 300
  rag:
    default-knowledge-base: fitness-v2
  mcp:
    server-url: http://127.0.0.1:9070
```

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
    SUCCEEDED,
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
- 日志应包含关键业务标识，如 `userId`、`runId`、`documentId`。
- 禁止打印密码、验证码、API Key、Token、Cookie、Authorization Header。
- 异常日志保留堆栈。
- 高频业务失败日志需要控制级别和频率。

### 10.3 安全

- 所有外部输入都必须校验。
- 文件上传必须校验大小、类型和文件名。
- 外部 URL 请求需要避免 SSRF 风险。
- 认证接口需要限制验证码频率和登录尝试频率。
- 敏感配置不得写死在代码或提交到仓库。
- Agent 调用工具时，用户身份必须由后端可信上下文注入。

---

## 11. AI、记忆、RAG、Agent 与 MCP

### 11.1 AI 调用

- 模型配置集中在配置类中。
- Prompt 模板独立存放，避免在 Service 中拼接大段文本。
- 对模型返回的结构化结果必须做解析失败处理。
- 流式输出必须可中断、可记录关键错误。

### 11.2 记忆系统

- 记忆系统统一命名为 `memory`。
- `chat` 负责对话流程，`memory` 负责上下文存储、召回、裁剪和摘要。
- ChatMemory 的 conversation id 必须与用户、会话或业务场景绑定。
- 长期记忆、短期会话记忆、聊天记录展示应分别命名。
- 记忆裁剪、过期、摘要策略必须可配置。

### 11.3 RAG

RAG 推荐结构：

```text
rag/
├─ controller/
├─ application/
├─ domain/
├─ infrastructure/
│  ├─ parser/
│  ├─ chunking/
│  ├─ embedding/
│  ├─ keyword/
│  ├─ vectorstore/
│  ├─ fusion/
│  ├─ rerank/
│  └─ repository/
├─ benchmark/
└─ dto/
```

规则：

- 文档解析、分块、向量化、检索、生成拆成可测试步骤。
- 知识库名称、向量索引、embedding 模型必须可配置。
- 文档服务命名使用 `RagDocumentService` 或 `RagIngestionService`。
- benchmark 结果保留输入、输出、评分和运行时间。

### 11.4 Agent

Agent 推荐结构：

```text
agent/
├─ controller/
├─ application/
├─ workflow/
├─ domain/
├─ infrastructure/
└─ dto/
```

规则：

- Agent run 与 step 状态必须可持久化、可查询、可通过 SSE 回传。
- 并发策略必须明确，例如 Redis 锁、幂等键或队列。
- workflow 步骤名、状态枚举、失败原因统一命名。
- MCP 工具调用参数必须由后端校验。

### 11.5 MCP

- `FitMate-api` 中的 `integration.mcp` 只负责发现与调用工具。
- `FitMate-mcpServer` 只负责暴露和执行工具能力。
- Tool 名称使用 lowerCamelCase，例如 `createTrainingLog`。
- Tool 入参和出参必须是结构化 DTO。
- Tool 描述必须说明能力边界、必填参数和返回含义。
- MCP 工具按领域组织：`time`、`email`、`fitness.training`、`fitness.metrics`、`rag`。

---

## 12. 前端代码规范

### 12.1 目录结构

```text
src/
├─ api/
├─ assets/
├─ components/
├─ composables/
├─ pages/
├─ router/
├─ stores/
├─ types/
└─ utils/
```

### 12.2 Vue 规范

- Vue 单文件组件使用 `<script setup lang="ts">`。
- 组件名使用 UpperCamelCase，例如 `ChatPanel.vue`。
- 页面组件放在 `pages`，通用组件放在 `components`。
- 组合函数命名为 `useXxx`，例如 `useSseChat`。
- 组件中不直接拼接后端 URL，统一从 `api` 层调用。
- 复杂类型定义放在 `types` 中。

### 12.3 API 调用

- Axios 实例集中配置 baseURL、超时、错误处理。
- API 方法按领域拆分，例如 `authApi.ts`、`chatApi.ts`、`ragApi.ts`。
- 所有后端响应都应有 TypeScript 类型。
- SSE 逻辑封装为 composable 或 service。

---

## 13. 文档与测试

### 13.1 文档

建议维护以下文档：

```text
README.md
README_CN.md
SPEC.md
docs/API.md
docs/DATABASE.md
docs/MCP_TOOLS.md
docs/RAG.md
docs/DEPLOYMENT.md
```

文档要求：

- 接口文档包含路径、方法、请求、响应、错误码、认证要求。
- 配置文档说明默认端口、环境变量、依赖服务。
- 示例不得包含真实密钥。

### 13.2 后端测试

- 单元测试使用 JUnit 5。
- 应用服务、文档解析、RAG 分块、DTO 转换、错误码映射优先写单元测试。
- 只有需要 Spring 容器时才使用 `@SpringBootTest`。
- Mapper 或外部依赖测试应标识为集成测试。

推荐命令：

```bash
mvn test
mvn -pl FitMate-api -am test
mvn -pl FitMate-mcpServer -am test
```

### 13.3 前端测试

至少保证：

```bash
npm run build
```

如保留类型检查脚本，同时运行：

```bash
npm run type-check
```

---

## 14. 命名速查

| 场景 | 规范示例 |
| --- | --- |
| 项目根目录 | `FitMate-AI` |
| 前端目录 | `FitMate-frontend` |
| 后端父目录 | `FitMate-backend` |
| 主业务 API 目录 | `FitMate-api` |
| MCP Server 目录 | `FitMate-mcpServer` |
| Maven 父 artifactId | `fitmate-ai` |
| API artifactId | `fitmate-api` |
| MCP Server artifactId | `fitmate-mcp-server` |
| Java 根包 | `com.itgeo.fitmate` |
| API 包 | `com.itgeo.fitmate.api` |
| MCP 包 | `com.itgeo.fitmate.mcp` |
| RAG 包 | `com.itgeo.fitmate.api.rag` |
| 记忆包 | `com.itgeo.fitmate.api.memory` |
| Agent 包 | `com.itgeo.fitmate.api.agent` |
| 训练包 | `com.itgeo.fitmate.api.fitness.training` |
| 身体指标包 | `com.itgeo.fitmate.api.fitness.metrics` |
| 数据库表前缀 | `fitmate_` |
| Redis Key 前缀 | `fitmate:{env}:...` |
| 环境变量前缀 | `FITMATE_` |
| Docker 资源前缀 | `fitmate-ai-` |

---

## 15. 代码评审检查清单

- [ ] 项目、模块、包、配置、数据库命名是否统一？
- [ ] Java 包名是否全部位于 `com.itgeo.fitmate` 下？
- [ ] RAG、记忆、Agent、搜索、fitness 是否有独立 package？
- [ ] `common` 是否只包含横切通用能力？
- [ ] Controller 是否只做入口职责？
- [ ] 业务流程是否放在 application/service 层？
- [ ] 外部依赖调用是否放在 infrastructure 层？
- [ ] DTO、Entity、Mapper、Tool 命名是否符合规范？
- [ ] 配置是否可通过环境变量覆盖？
- [ ] 是否避免提交真实密钥和本地私有配置？
- [ ] 是否运行了必要的构建或测试？
