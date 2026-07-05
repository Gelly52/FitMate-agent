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
| `FitMate-api` | 面向前端暴露 REST/SSE API，承载认证、聊天、记忆、Agent、RAG、搜索、训练、身体指标等业务流程。 |
| `FitMate-mcpServer` | 暴露 MCP 工具能力，承载邮件、RAG 管理等工具实现。 |
| `FitMate-common` | 放置统一响应、异常、错误码、枚举、常量、基础工具方法等横切通用能力。 |

`common` 不承载具体业务模块核心逻辑；业务流程应放在 `FitMate-api` 或 `FitMate-mcpServer` 对应功能包内。

---

## 2. 包命名与分层

所有 Java 代码根包统一为：

```text
com.itgeo.fitmate
```

推荐模块包：

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

功能包内部优先采用以下分层：

```text
feature/
├─ controller/          # HTTP API 入口
├─ application/         # 应用服务与流程编排
├─ domain/              # 领域对象与领域规则
├─ infrastructure/      # 数据库、Redis、外部 HTTP、MCP、文件系统等技术实现
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
- `FitMate-api` 与 `FitMate-mcpServer` 独立启动、独立配置端口。
- 需要初始化的业务数据通过 SQL、迁移脚本或明确的初始化组件完成，不写在启动类中。
- `@MapperScan`、`@EnableScheduling`、异步配置等全局能力应集中放置，避免散落在业务类中。

---

## 4. 功能包职责

推荐功能包与职责：

| 功能 | 包名片段 | 职责 |
| --- | --- | --- |
| 用户认证 | `auth`、`user` | 验证码、登录、退出、token、用户上下文。 |
| 浏览器 SSE | `sse` | SSE ticket、连接管理、事件推送。 |
| 聊天 | `chat` | 普通对话、流式回复、prompt 管理、聊天入口。 |
| 记忆 | `memory` | 会话历史、ChatMemory、摘要、裁剪与召回。 |
| Agent | `agent` | 任务受理、run/step 状态、异步 workflow、SSE 回传。 |
| RAG | `rag` | 文档上传、解析、分块、embedding、检索、rerank、benchmark。 |
| 联网搜索 | `search` | SearXNG 搜索与搜索增强问答。 |
| 训练日志 | `fitness.training` | 训练日志、动作明细、训练摘要。 |
| 身体指标 | `fitness.metrics` | 体重、体脂、睡眠、疲劳度等指标。 |
| MCP 工具 | `mcp` | 时间、邮件、训练、身体指标、RAG 管理工具。 |

规则：

- Controller 只做入参接收、校验、调用应用服务、返回响应。
- Application Service 负责编排流程、事务边界、权限上下文与外部依赖调用顺序。
- Domain 层表达稳定业务概念，不依赖 HTTP、Redis、数据库 Mapper 或模型 SDK。
- Infrastructure 层封装 MyBatis、Redis、HTTP Client、文件系统、模型 SDK、MCP SDK 等技术细节。

---

## 5. 类命名规范

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
| Repository | `{Domain}Repository` | `RagDocumentRepository` |
| 配置类 | `{Feature}Config` | `RedisConfig` |
| 配置属性 | `{Feature}Properties` | `OpenAiProperties` |
| MCP Tool | `{Capability}Tool` | `TrainingLogTool` |
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

## 6. API 与 Controller 规范

- 新接口推荐使用 `/api/v1/...` 前缀。
- 路径使用小写短横线或明确领域名。
- 路径中使用名词表达资源，动作用 HTTP 方法表达。
- 入参使用 DTO，并通过 Jakarta Validation 做基础校验。
- Controller 不直接访问 Mapper、RedisTemplate、模型 SDK、MCP SDK。
- 文件下载、浏览器 SSE 等特殊接口可以不使用统一响应包装，但必须在文档中说明。

统一响应建议：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

---

## 7. SSE 规范

- 浏览器 SSE 与 MCP SSE 必须在命名和文档中明确区分。
- 浏览器 SSE 事件名使用 lowerCamelCase，例如 `messageDelta`、`agentStep`、`done`、`error`。
- SSE 的 data 使用结构化 JSON。
- 服务端必须处理客户端断开连接，避免资源泄漏。
- 错误事件必须包含可读错误信息和必要业务标识。
- Agent run、step、chat delta 等长流程事件应带可追踪 id。

---

## 8. 数据库与 MyBatis 规范

- 新表使用 `fitmate_` 前缀，表名和字段名使用 snake_case。
- 主键统一命名为 `id`。
- 创建时间使用 `created_at`，更新时间使用 `updated_at`。
- 逻辑删除字段统一使用 `deleted` 或 `deleted_at`，同一项目保持一致。
- 状态字段使用明确枚举值，不使用魔法数字。
- Mapper 与 XML 路径按功能模块归档，并与 Mapper 包结构保持一致。
- SQL 中禁止拼接未校验的用户输入。
- 分页查询必须明确排序字段。
- 跨表查询应返回专用 DTO，不复用实体类承载聚合结果。

---

## 9. Redis 规范

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

规则：

- 所有临时 Key 必须设置 TTL。
- 分布式锁必须明确锁粒度、过期时间和失败策略。
- Redis Vector Store 的 index name、key prefix、embedding 维度必须可配置。
- 不在业务代码中散落硬编码 Redis Key，优先集中到常量或配置属性。

---

## 10. 配置规范

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
- 业务配置统一使用 `fitmate` 前缀。
- 环境变量统一使用 `FITMATE_` 前缀。
- 第三方惯用变量可以保留，但项目内部读取应集中映射到配置属性类。

---

## 11. 异常、日志与安全

### 11.1 异常

- 使用统一业务异常，例如 `BusinessException`。
- 使用统一错误码，例如 `ErrorCode`。
- 使用全局异常处理器，例如 `GlobalExceptionHandler`。
- 禁止吞掉异常后返回 `null` 或空字符串。
- 不能处理的异常交给上层统一处理。

### 11.2 日志

- 使用 SLF4J，不使用 `System.out.println`。
- 日志应包含关键业务标识，如 `userId`、`runId`、`documentId`。
- 禁止打印密码、验证码、API Key、Token、Cookie、Authorization Header。
- 异常日志保留堆栈。
- 高频业务失败日志需要控制级别和频率。

### 11.3 安全

- 所有外部输入都必须校验。
- 文件上传必须校验大小、类型和文件名。
- 外部 URL 请求需要避免 SSRF 风险。
- 认证接口需要限制验证码频率和登录尝试频率。
- 敏感配置不得写死在代码或提交到仓库。
- Agent 调用工具时，用户身份必须由后端可信上下文注入。

---

## 12. AI、RAG、Agent 与 MCP 规范

### 12.1 AI 调用

- 模型配置集中在配置类中。
- Prompt 模板独立存放，避免在 Service 中拼接大段文本。
- 对模型返回的结构化结果必须做解析失败处理。
- 流式输出必须可中断、可记录关键错误。

### 12.2 RAG

- 文档解析、分块、向量化、检索、生成拆成可测试步骤。
- 知识库名称、向量索引、embedding 模型必须可配置。
- 文档服务命名使用 `RagDocumentService` 或 `RagIngestionService`。
- benchmark 结果保留输入、输出、评分和运行时间。
- 检索链路中的 vector recall、keyword recall、fusion、rerank 应边界清晰。

### 12.3 Agent

- Agent run 与 step 状态必须可持久化、可查询、可通过 SSE 回传。
- 并发策略必须明确，例如 Redis 锁、幂等键或队列。
- workflow 步骤名、状态枚举、失败原因统一命名。
- MCP 工具调用参数必须由后端校验。

### 12.4 MCP

- `FitMate-api` 中的 `integration.mcp` 只负责发现与调用工具。
- `FitMate-mcpServer` 只负责暴露和执行工具能力。
- Tool 名称使用 lowerCamelCase，例如 `createTrainingLog`。
- Tool 入参和出参必须是结构化 DTO。
- Tool 描述必须说明能力边界、必填参数和返回含义。
- MCP 工具按领域组织：`time`、`email`、`fitness.training`、`fitness.metrics`、`rag`。

---

## 13. 测试规范

- 单元测试使用 JUnit 5。
- 应用服务、文档解析、RAG 分块、DTO 转换、错误码映射优先写单元测试。
- 只有需要 Spring 容器时才使用 `@SpringBootTest`。
- Mapper 或外部依赖测试应标识为集成测试。
- 新增 bug 修复优先补充能复现问题的测试。

推荐命令：

```bash
mvn test
mvn -pl FitMate-api -am test
mvn -pl FitMate-mcpServer -am test
```

---

## 14. 变更检查清单

- [ ] 新代码包名是否位于 `com.itgeo.fitmate` 下？
- [ ] 新业务是否放在对应 feature 包中，而不是塞入 `common`？
- [ ] Controller 是否只做入口职责？
- [ ] 应用服务是否承载流程编排和事务边界？
- [ ] Mapper、Redis、HTTP、模型 SDK 是否封装在 infrastructure 或专用 client 中？
- [ ] DTO、实体、查询对象、命令对象命名是否清晰？
- [ ] Redis Key、数据库表名、配置项是否符合统一命名？
- [ ] 日志是否避免输出密钥、Token、验证码等敏感信息？
- [ ] RAG、Agent、MCP 变更是否有清晰边界和必要测试？
- [ ] 修改后是否运行相关 Maven 测试或说明未运行原因？
