# Sub-Agent 编排设计文档

**日期**: 2026-07-07
**状态**: 待审核

---

## 1. 背景与目标

### 1.1 现状

当前 FitMate Agent 采用单层 ReAct 循环（推理→工具→再推理），所有任务复杂度由一个主 Agent 处理。对于简单问题（如"今天训练了什么"）绰绰有余，但对于复杂任务（如"分析最近一周训练数据并给出营养建议"），主 Agent 需要在多个领域间切换推理，导致：

- 单轮决策负担重，LLM 输出质量下降
- 推理链路过长，thinking 内容冗长难以阅读
- 无法对不同子任务使用不同工具集和推理预算

### 1.2 目标

引入 Sub-Agent 编排机制：

- **智能调度**：主 Agent 根据任务复杂度自主决定是否派生 Sub-Agent（LLM ReAct 决策中新增 `spawn_subagent` action）
- **通用 Sub-Agent**：Sub-Agent 使用与主 Agent 相同的 system prompt（保证 KV cache 命中），但工具集不包含 spawn 能力（防止递归）
- **串行执行**：单次最多 1 个 Sub-Agent，主 Agent 等待其完成后再决定下一步（MVP 最小侵入）
- **独立上下文**：Sub-Agent 只看到主 Agent 分配的任务描述，不继承主 Agent 完整对话历史
- **嵌套显示**：前端以嵌套折叠块展示 Sub-Agent 的推理过程，复用现有 ReasoningTraceBlock 视觉风格
- **KV cache 复用**：Sub-Agent prompt 前缀与主 Agent 一致（system prompt + user profile），最大化 KV cache 命中率

### 1.3 非目标

- 不支持多 Sub-Agent 并行执行（MVP 串行）
- 不支持 Sub-Agent 递归派生 Sub-Agent（最多一层）
- 不引入异步 CompletableFuture / Flux 并行调度
- 不修改 ToolRouter / ToolExecutor 接口（spawn 不走工具路由）
- 不改变现有流式渲染逻辑（v-text streaming / v-html markdown 切换完全保留）
- 不新增 SSE 事件类型（仅在 AGENT_STEP 内新增 2 个 eventType 子类型）
- 不做 Sub-Agent 预定义角色（通用 Sub-Agent，由主 Agent 在 spawn 时指定任务和工具子集）

---

## 2. 整体架构与数据流

### 2.1 核心流程

```
┌─ 主 Agent run (runId=R1, parent_run_id=null) ─────────────────────┐
│                                                                    │
│  iter 1: LLM 决策 → action: spawn_subagent                         │
│           ├─ 创建 subagent run (runId=R2, parent_run_id=R1)        │
│           ├─ AGENT_STEP: subagent_started (subagentRunId=R2,       │
│           │   subTask="分析最近一周训练数据")                       │
│           ├─ subagent.runLoop(R2 context)         ← 同步阻塞       │
│           │   ├─ AGENT_STEP: run_started (runId=R2)                │
│           │   ├─ iter 1: LLM → tool_call → 工具执行                │
│           │   │   ├─ THINKING/ADD chunk (runId=R2)                 │
│           │   │   └─ AGENT_STEP: llm_*/tool_call_* (runId=R2)      │
│           │   ├─ iter 2: LLM → final                               │
│           │   │   └─ AGENT_STEP: final_answer (runId=R2)           │
│           │   └─ (不推 FINISH，结果写入 context.subAgentResult)    │
│           ├─ AGENT_STEP: subagent_finished (subagentRunId=R2)      │
│           └─ subagent 结果 → observation → 加入主 agent 上下文      │
│                                                                    │
│  iter 2: LLM 决策（看到 subagent 结果）→ action: final              │
│  └─ FINISH 推送（仅主 agent 推 FINISH）                              │
└────────────────────────────────────────────────────────────────────┘
```

主 Agent 在收到 Sub-Agent 结果后，可以继续 spawn 另一个 Sub-Agent、调用工具、或 final。唯一限制是同一时间最多一个 Sub-Agent 在跑（串行），不限制总 spawn 次数。

### 2.2 SSE 事件路由原理

Sub-Agent 的所有事件（AGENT_STEP/THINKING/ADD）都携带 `runId=R2`（Sub-Agent 自己的 runId），而非 `parentRunId`。前端通过 `activeAgentRuns[runId]` 自然路由——`subagent_started` 事件先创建子 runEntry，后续 runId=R2 的事件自动路由到子 runEntry。这与现有多 run 并发追踪机制一致，无需额外路由字段。

### 2.3 主 Agent 与 Sub-Agent 边界

| 维度 | 主 Agent | Sub-Agent |
|---|---|---|
| runId | 独立 R1 | 独立 R2 |
| parent_run_id | NULL | R1 |
| sseClientId | 共享 | 共享 |
| AuthenticatedUser | 共享 | 共享（同线程 ThreadLocal） |
| system prompt | agent-system.md | 相同（KV cache 命中） |
| user profile | 加载 | 相同前缀（KV cache 命中） |
| iteration 预算 | `fitmate.agent.max-iterations` | `fitmate.agent.sub-agent.max-iterations`（配置文件可调） |
| tool-call 预算 | `fitmate.agent.max-tool-calls` | `fitmate.agent.sub-agent.max-tool-calls`（配置文件可调） |
| run 时长 | `fitmate.agent.max-run-duration-seconds` | `fitmate.agent.sub-agent.max-run-duration-seconds`（配置文件可调） |
| 工具超时 | `fitmate.agent.tool-timeout-seconds` | `fitmate.agent.sub-agent.tool-timeout-seconds`（配置文件可调） |
| allowedTools | 全量（含 spawn_subagent 能力） | 子集（不含 spawn，由主 agent 在 spawn 时指定） |
| ChatMessage | 创建（正式回复） | 不创建（Sub-Agent 不是对用户的回复） |
| usage 累加器 | 独立 | 独立（完成后汇总到主 agent） |
| thinking 累加器 | 独立 | 独立（持久化到 t_chat_thinking，虚拟 botMsgId） |
| slot 锁 | 占用 1 个 | 共享主 agent slot（不单独申请） |
| 被动压缩 | run() 入口执行 | 跳过 |
| Wiki/RAG 预检索 | runLoop 前执行 | 跳过（Sub-Agent 需要时自行调工具） |
| 历史消息 | 加载最近 N 条 | 只构造一条 task 消息 |
| 记忆提取 | finishWithAnswer 触发 | 跳过 |
| FINISH 推送 | 推送 | 不推送 |
| 取消 | 用户主动 | 级联取消 |

### 2.4 不变量（保护现有功能）

1. **SSE 事件类型不变**：仍只有 ADD/THINKING/FINISH/AGENT_STEP/CUSTOM_EVENT/ERROR/DONE/MESSAGE
2. **AGENT_STEP 仅新增 2 个 eventType**：`subagent_started`、`subagent_finished`；Sub-Agent 复用现有 `run_started`/`llm_started`/`tool_call_*`/`final_answer` 等 eventType
3. **FINISH 只在主 Agent 推送一次**：Sub-Agent 完成时不推 FINISH
4. **ChatStreamChunkResponse 不新增字段**：Sub-Agent 的 THINKING/ADD chunk 携带 runId=R2，通过现有 runId 路由机制到子 runEntry
5. **主循环同步结构不变**：Sub-Agent 在主 Agent 线程内同步执行，不引入异步
6. **ToolRouter 不改**：spawn 是主循环独立分支，不走工具路由
7. **流式渲染逻辑不变**：前端 v-if streaming → v-text + cursor / v-else → v-html markdown 完全保留
8. **rAF 批处理不变**：Sub-Agent 的 chunk 同样走 rAF 批处理缓冲（子 runEntry 独立 buffer）
9. **ThreadLocal 安全**：Sub-Agent 在同一线程执行，UserContextHolder/KbSearchContextHolder 不需重置
10. **历史数据兼容**：新增字段 nullable，旧数据 parent_run_id=NULL 不影响现有功能

---

## 3. 后端改造

### 3.1 数据模型变更

#### `t_agent_run` 表新增字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `parent_run_id` | BIGINT, nullable | NULL | 父 run ID，主 agent 为 NULL |

DDL：
```sql
ALTER TABLE t_agent_run ADD COLUMN parent_run_id BIGINT NULL COMMENT '父AgentRun ID' AFTER bot_msg_id;
```

#### `t_agent_step` 表新增字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `subagent_run_id` | BIGINT, nullable | NULL | 若此 step 派生了 subagent，记录 subagent 的 runId |

DDL：
```sql
ALTER TABLE t_agent_step ADD COLUMN subagent_run_id BIGINT NULL COMMENT '派生的SubAgent Run ID' AFTER tool_call_id;
```

不需要 `parent_step_id` 字段。Sub-Agent 步骤通过 `t_agent_run.parent_run_id` 找到父 run，再通过父 run 中 `subagent_run_id=R2` 的 subagent_started step 关联。前端构建树时同样通过此配对关系。

#### t_chat_thinking 兼容

Sub-Agent 的 thinking 内容持久化到 `t_chat_thinking` 表，使用虚拟 botMsgId 格式 `sub:{subRunId}`。该表的 bot_msg_id 是字符串类型，不做外键约束，因此不会冲突。TTL 清理按 created_at 清理，这些虚拟记录也会在 30 天后自动清理。

#### 新增 SubAgentProperties 配置类

```yaml
fitmate:
  agent:
    sub-agent:
      max-iterations: 8
      max-tool-calls: 20
      max-run-duration-seconds: 600
      tool-timeout-seconds: 30
```

所有值在 `application.yml` 中配置，代码中不硬编码。配置类注册为 Spring `@ConfigurationProperties("fitmate.agent.sub-agent")`。

### 3.2 AgentExecuteContext 扩展

新增字段（仅 Sub-Agent context 使用）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `isSubAgent` | boolean | 是否为 subagent context |
| `parentRunId` | Long | 父 runId |
| `subAgentTask` | String | 主 agent 给 subagent 的任务描述 |
| `subAgentAllowedTools` | Set\<String\> | subagent 可用工具白名单（不含 spawn 能力） |
| `subAgentResult` | String | subagent 执行结果文本（完成后回传主 agent） |
| `activeSubAgentRunId` | Long | 当前活跃的 subagent runId（主 agent 用，用于取消级联） |

新增静态工厂方法：
- `AgentExecuteContext.forSubAgent(parentContext, subRunId, task, allowedTools)` — 创建 subagent context，继承 parentContext 的共享字段（sseClientId、authenticatedUser、chatEntity、kbEnabled/ragEnabled/internetEnabled、UserContextHolder 状态），独立初始化 accumulatedUsage、accumulatedThinking、stepNoCounter、SSE buffers。Sub-Agent 不需要 subAssistantMsgId（不创建 ChatMessage），thinking 持久化使用虚拟 botMsgId。

### 3.3 AgentLoopExecutor 重构

#### 3.3.1 方法拆分

将现有 `run(context)` 拆分为：

- `run(context)` — 入口方法，保持现有签名。执行初始化（被动压缩、加载历史、加载用户画像、发出 run_started），然后调用 `runLoop(context, false)`
- `runLoop(context, isSubAgent)` — 提取的核心循环，包含 for iteration 循环、LLM 决策、action 分发、预算检查。被 `run()` 和 spawn 分支共同调用。

重构是纯提取：`runLoop` 的代码就是原来 `run` 方法中从 for iteration 开始到 final answer 结束的部分，不改逻辑。

#### 3.3.2 runLoop 的 isSubAgent 分支

进入 runLoop 后，根据 isSubAgent 做差异化初始化：

| 初始化项 | isSubAgent=false（主 Agent） | isSubAgent=true（Sub-Agent） |
|---|---|---|
| 被动压缩 | run() 已执行，跳过 | 跳过 |
| Wiki/RAG 预检索 | 执行 | 跳过 |
| 历史消息加载 | 加载最近 N 条 + summary | 构造一条 task 消息 |
| userProfileSection | 加载 | 加载（与主 Agent 前缀一致，KV cache 命中） |
| 起始事件 | run_started（由 run() 发出） | 发出 run_started（runId=R2，复用现有 eventType） |
| 预算配置 | AgentProperties | SubAgentProperties |
| allowedTools | resolveAllowedTools（含 spawn） | subAgentAllowedTools（不含 spawn） |

Sub-Agent 的 prompt 构造：
```
[system prompt]（与主 agent 完全相同，KV cache 命中）
[user profile section]（与主 agent 相同，KV cache 命中）

Task: {subAgentTask}

Available tools: {subAgentAllowedTools}
```

Sub-Agent 的 observations 初始为空（不继承主 Agent 的工具调用结果），从空白开始根据 task 自行决定调用工具。

#### 3.3.3 新增 spawn_subagent 分支

在 `runLoop` 的 action switch 中新增 case `"spawn_subagent"`：

```
case "spawn_subagent":
    if isSubAgent → 视为异常决策（subagent 不能再 spawn），走 default 兜底
    if context.activeSubAgentRunId != null → 错误 observation，continue（串行约束）
    解析 task 和可选 tools（来自 LLM 决策 JSON）
    确定 subagent 工具集：
        - 若 LLM 指定了 tools → 取指定工具 ∩ 主 agent 当前 allowedTools（去除 spawn 能力）
        - 若未指定 → 取主 agent 当前 allowedTools，去除 spawn 能力
    创建 subagent run（agentRunService.createRun，parentRunId=当前 runId）
    发出 subagent_started AGENT_STEP（含 subagentRunId=R2、inputJson.task）
    构造 subagent context（forSubAgent）
    context.activeSubAgentRunId = R2
    try:
        runLoop(subContext, true)  ← 同步递归调用
    catch AgentCancelledException:
        包装为失败 observation（"Sub-Agent 被取消"）
    catch Exception:
        包装为失败 observation（"Sub-Agent 执行失败: " + e.getMessage()）
    finally:
        context.activeSubAgentRunId = null
    发出 subagent_finished AGENT_STEP（含 subagentRunId=R2、durationMs）
    将 subAgentResult（或错误信息）包装为 observation 加入 observations
    汇总 subContext.accumulatedUsage 到 context.accumulatedUsage
    continue  // 进入下一轮迭代
```

spawn_subagent 的 JSON 格式（在 agent-system.md 中定义）：
```json
{"action":"spawn_subagent","task":"具体任务描述","tools":["工具名1","工具名2"],"reason":"为什么需要派生"}
```
`tools` 字段可选，不传则继承主 agent 可用工具集（spawn 能力除外）。

#### 3.3.4 Sub-Agent 的 final 处理

`finishWithAnswer` 改造：增加 `isSubAgent` 参数。

Sub-Agent 完成时（isSubAgent=true）：
1. 将 finalAnswer 写入 `context.subAgentResult`（字符串）
2. 持久化 subagent 自己的 thinking 内容到 t_chat_thinking（botMsgId = `sub:{subRunId}`）
3. 标记 subagent run 为 success（result_json 存 AgentFinishResponse）
4. **不推 FINISH**
5. **不触发记忆提取**
6. **不创建/更新 ChatMessage**（Sub-Agent 不是对用户的回复）
7. 正常发出 final_answer AGENT_STEP（runId=R2，前端据此知道 Sub-Agent 完成）

主 Agent 完成时（isSubAgent=false）：走现有逻辑，不变。

#### 3.3.5 预算检查

在 runLoop 的每轮迭代中，通过 `isSubAgent` 标志选择配置：
- 迭代上限：isSubAgent ? subAgentProperties.getMaxIterations() : agentProperties.getMaxIterations()
- 工具调用上限：isSubAgent ? subAgentProperties.getMaxToolCalls() : agentProperties.getMaxToolCalls()
- 运行时长：isSubAgent ? subAgentProperties.getMaxRunDurationSeconds() : agentProperties.getMaxRunDurationSeconds()
- 工具超时：isSubAgent ? subAgentProperties.getToolTimeoutSeconds() : agentProperties.getToolTimeoutSeconds()

### 3.4 AGENT_STEP 事件扩展

#### 新增 eventType（仅 2 个）

| eventType | 说明 | 推送方 | runId |
|---|---|---|---|
| `subagent_started` | 主 Agent 派生 Sub-Agent | 主 Agent（spawn 分支，runLoop 调用前） | R1（主 run） |
| `subagent_finished` | Sub-Agent 执行完成 | 主 Agent（spawn 分支，runLoop 返回后） | R1（主 run） |

Sub-Agent 自己的步骤使用现有 eventType（run_started、llm_started/finished、tool_call_started/finished/failed、final_answer、run_failed），但 runId=R2。

#### AgentStepEvent DTO 新增字段

| 字段 | 类型 | 说明 | 何时有值 |
|---|---|---|---|
| `subagentRunId` | Long, nullable | subagent_started/subagent_finished 携带的 subagent runId | 仅这两个 eventType |

**不新增 parentRunId 字段**。Sub-Agent 的 AGENT_STEP 事件已携带 runId=R2，前端通过 subagent_started 事件建立父子关系后，R2 的事件自然路由到子 runEntry。

#### SSE Chunk：不新增字段

ChatStreamChunkResponse 不新增 parentRunId 字段。Sub-Agent 的 THINKING/ADD chunk 携带 runId=R2（Sub-Agent 自己的 runId），前端通过 `activeAgentRuns[runId]` 自然路由。

### 3.5 AgentTraceService 扩展

- `startEvent`/`finishEvent`/`failEvent` 增加重载，支持传入 `subagentRunId`（用于 subagent_started/subagent_finished step）
- Sub-Agent 调用 startEvent/finishEvent 时，AgentStepEvent 的 runId 自然设置为 Sub-Agent 的 runId（通过 context.runId 获取）
- THINKING/ADD chunk 的 SSE 推送不需要改——Sub-Agent context 的 runId=R2，chunk 自动携带 runId=R2

### 3.6 取消机制

`AgentCancellationRegistry` 扩展：
- `AgentExecuteContext` 新增 `activeSubAgentRunId` 字段（主 Agent 记录当前活跃的 subagent runId）
- 取消主 Agent run 时，检查 `activeSubAgentRunId`，若存在则一并注册取消（`cancellationRegistry.cancel(subRunId)`）
- Sub-Agent 的 runLoop 在每次迭代前检查 `cancellationRegistry.isCancelled(context.runId)`（复用现有检查逻辑，因为 Sub-Agent 有自己的 runId）
- Sub-Agent 被取消时抛出 AgentCancelledException，被主 Agent 的 spawn 分支 catch，包装为失败 observation
- 级联取消路径：用户点"停止"→取消主 runId=R1→AgentAsyncServiceImpl 检测到 activeSubAgentRunId=R2→取消 R2→Sub-Agent 在迭代检查时抛出 AgentCancelledException

### 3.7 Agent 异步执行与锁

- Sub-Agent 不独立申请 slot 锁（共享主 Agent 的 slot）
- Sub-Agent 在主 Agent 的异步线程内同步执行，不占用额外线程池资源
- 锁续期由主 Agent 的续期任务统一负责（Sub-Agent 不创建续期任务）
- `AgentAsyncServiceImpl.executeAsync` 的 finally 块释放锁逻辑不变

### 3.8 AgentRunService 与历史加载

- `createRun` 方法支持传入 `parentRunId`
- Sub-Agent run 创建时设置 `parentRunId`
- `markRunSuccess`/`markRunFailed` 逻辑不变
- **历史查询扩展**：`/agent/runs/by-bot-msg/{botMsgId}` 和 `/agent/runs/{runId}` 接口在返回 run 详情时，需要递归查询子 run（WHERE parent_run_id = R1）及其 steps，组装到响应中（子 run 挂在对应 subagent_started step 下）。这样历史消息加载时前端能拿到完整的嵌套结构。

### 3.9 prompt 改造（agent-system.md）

在现有 prompt 的 JSON 决策说明中新增 spawn_subagent 格式和规则：

```markdown
## 派生 Sub-Agent

当任务涉及多个独立步骤需要深度推理、或需要在不同领域间切换分析时，可以派生一个 Sub-Agent 来完成子任务。Sub-Agent 是一个独立的 Agent 循环，有自己的工具调用和推理过程，完成后将结果返回给你。

输出格式：
{"action":"spawn_subagent","task":"<给Sub-Agent的具体任务描述，必须清晰、明确、包含所有必要信息>","tools":["<可选，指定工具名白名单>"],"reason":"<为什么需要派生>"}

规则：
- 单次只能派生一个 Sub-Agent，等其完成后再决定下一步
- Sub-Agent 不能再派生 Sub-Agent
- 简单任务（1-2步即可完成）不要派生 Sub-Agent，直接调用工具或回答
- task 描述必须自包含，Sub-Agent 看不到之前的对话历史，只能看到你给的 task
- tools 不传则 Sub-Agent 继承当前可用工具集；你可以限制工具集让 Sub-Agent 专注
- Sub-Agent 的结果会作为 observation 返回，你需要根据结果决定最终答案或下一步行动
```

### 3.10 后端文件变更清单

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `fitmate_init.sql` | 修改 | 新增 DDL 字段（parent_run_id、subagent_run_id） |
| `AgentRun.java` (entity) | 修改 | 新增 parentRunId 字段 |
| `AgentStep.java` (entity) | 修改 | 新增 subagentRunId 字段 |
| `AgentRunMapper.java` + XML | 修改 | 新增 parentRunId 查询（按 parent_run_id 查子 run） |
| `AgentStepMapper.java` + XML | 修改 | 新增 subagentRunId 相关查询 |
| `SubAgentProperties.java` | 新增 | Sub-Agent 配置类 |
| `AgentExecuteContext.java` | 修改 | 新增 isSubAgent/parentRunId/subAgentTask/subAgentResult/activeSubAgentRunId + forSubAgent 工厂方法 |
| `AgentLoopExecutor.java` | 重构+新增 | 提取 runLoop 方法；新增 spawn_subagent 分支；Sub-Agent 初始化/预算/finish 差异 |
| `AgentTraceService.java` | 修改 | startEvent/finishEvent 支持 subagentRunId |
| `AgentStepEvent.java` (DTO) | 修改 | 新增 subagentRunId 字段（仅 subagent_started/finished 使用） |
| `AgentCancellationRegistry.java` | 修改 | 级联取消支持（cancel 时检查 context.activeSubAgentRunId） |
| `AgentRunService.java` / impl | 修改 | createRun 支持 parentRunId；查询接口递归加载子 run |
| `agent-system.md` (prompt) | 修改 | 新增 spawn_subagent 格式和规则 |
| `application.yml` / `application-dev.yml` | 修改 | 新增 sub-agent 配置段 |
| `LlmConfigBeanConfig.java` | 修改 | 注册 SubAgentProperties |
| `SSEMsgType.java` | 不改 | 不新增 SSE 消息类型，仅在 AGENT_STEP 内新增 eventType 子类型 |

---

## 4. 前端改造

### 4.1 类型定义扩展（types/agent.ts）

```typescript
// AgentStepEvent 新增字段
interface AgentStepEvent {
  // ... 现有字段保持不变
  subagentRunId?: string;  // 仅 subagent_started/subagent_finished 有值
}

// AgentTraceEvent（前端内部）新增字段
interface AgentTraceEvent {
  // ... 现有字段
  subagentRunId?: string;
  subSteps?: AgentTraceEvent[];         // 嵌套 Sub-Agent 步骤（实时同步自子 runEntry）
  subThinkingSegments?: ThinkingSegment[]; // 嵌套 Sub-Agent 思考段
  subTask?: string;                      // Sub-Agent 任务描述
}

// RunState 新增字段
interface RunState {
  // ... 现有字段
  parentRunId?: string;
  childrenRunIds?: string[];
}
```

注意：不使用已预留但未消费的 `nodeId`/`parentId` 字段（语义不明确），改用 `subagentRunId`/`parentRunId` 等语义清晰的字段。

### 4.2 SSE 事件适配器（utils/agentEventAdapter.ts）

- 解析 `subagentRunId` 字段，透传到 AgentTraceEvent
- 不改变现有事件解析逻辑，新字段为可选

### 4.3 ChatLogicBase.vue 状态管理改造

#### 4.3.1 runEntry 扩展

现有 `activeAgentRuns: Record<string, runEntry>` 的每个 runEntry 新增：
- `parentRunId?: string` — Sub-Agent 的父 runId
- `childrenRunIds?: string[]` — 主 Agent 记录子 runId 列表

#### 4.3.2 createRunEntry 扩展

`createRunEntry` 方法增加 `parentRunId` 可选参数。

#### 4.3.3 SSE 事件路由改造

**AGENT_STEP 事件路由逻辑**：

所有 AGENT_STEP 事件携带 runId（现有字段），前端按以下逻辑路由：

1. **eventType === 'subagent_started'**（runId=R1，主 run）：
   - 从 event.subagentRunId 拿到 R2
   - 创建子 runEntry（parentRunId=R1, runId=R2），加入 activeAgentRuns
   - 将 R2 加入父 runEntry.childrenRunIds
   - 在父 runEntry.steps 中找到/创建 subagent_started step，设置 step.subagentRunId=R2、step.subTask=task
   - **关键**：将 step.subSteps 指向子 runEntry.steps 数组引用，step.subThinkingSegments 指向子 runEntry.thinkingSegments 数组引用。这样子 runEntry.steps 的实时更新会自动反映到父 step.subSteps 上（Vue 响应式）

2. **eventType === 'subagent_finished'**（runId=R1，主 run）：
   - 在父 runEntry.steps 中找到 subagent_started step（subagentRunId=R2）
   - 更新该 step 状态为 success，设置 durationMs
   - 子 runEntry 标记为终态（后续可能保留用于历史记录，但不再接收新事件）

3. **其他 eventType（runId=R2）**：
   - 按 runId=R2 找到子 runEntry，走现有 applyAgentStepEvent 逻辑更新子 runEntry.steps
   - 由于 step.subSteps 已指向子 runEntry.steps（同一数组引用），更新自动反映到父 step

**THINKING/ADD chunk 路由**：
- 所有 chunk 携带 runId（现有字段）
- runId=R1 → 路由到主 runEntry buffer（现有逻辑）
- runId=R2 → 路由到子 runEntry buffer（runBuffers 按 runId 索引，子 run 有独立 buffer）
- flush 时写入对应 runEntry 的 thinkingSegments/content

**SSE 事件顺序保证**：由于 SSE 是单连接顺序协议，subagent_started 事件一定在 Sub-Agent 的第一个 R2 事件之前到达前端（subagent_started 在 runLoop 调用前发出，runLoop 内的事件在其后才开始产生），子 runEntry 一定先于 R2 事件创建。

#### 4.3.4 流式数据实时同步到父 step

核心机制：在处理 subagent_started 事件时，将父 step 的 `subSteps` 和 `subThinkingSegments` 设置为子 runEntry 的对应数组引用。由于 Vue 响应式系统对数组突变（push/splice）和替换（不可变更新返回新数组）都能追踪，子 runEntry 的步骤和思考段更新会自动触发 SubAgentTraceBlock 重新渲染。

对于不可变更新（现有代码中 startThinkingSegment/finishThinkingSegment 等返回新数组），需要在子 runEntry 更新时同步更新父 step 的引用。实现方式：在更新子 runEntry.steps/thinkingSegments 后，检查如果该 runEntry 有 parentRunId，找到父 runEntry 中对应的 subagent_started step，同步更新 step.subSteps/subThinkingSegments 引用。

#### 4.3.5 取消级联

`handleCancelAgent` 方法扩展：
- 取消主 run 时，遍历主 runEntry.childrenRunIds，对每个子 runId 也发送取消请求

#### 4.3.6 run 快照扩展（snapshotRunState）

sessionStorage 快照扩展：保存 runEntry 时递归保存 childrenRunIds 及对应子 runEntry。快照恢复时递归重建。由于 Sub-Agent 生命周期短（通常在主 run 一次迭代内完成），快照主要用于页面刷新兜底。

#### 4.3.7 finalizeStreamingChatItem 改造

终态处理（applyFinishPayload）时：
- 主 run 收到 FINISH 后，遍历 childrenRunIds，将每个子 runEntry 的最终 steps/thinkingSegments 快照合并到主消息对应 subagent_started step 的 subSteps/subThinkingSegments（脱离对子 runEntry 的引用，转为持久化数据）
- 清理子 runEntry（从 activeAgentRuns 中删除）
- 保存主消息 thinking 到 thinkingCache（仅主 Agent thinkingContent，不含 Sub-Agent）

#### 4.3.8 历史消息加载

加载历史消息时，后端返回的 run 详情已包含子 run 和 steps（嵌套结构），前端按以下逻辑恢复：
- 主 run 的 steps 中，subagent_started step 已携带 subSteps/subThinkingSegments/subTask
- 不需要重建 activeAgentRuns 中的子 runEntry（历史数据是终态的，直接用嵌套结构渲染）

### 4.4 ReasoningTraceBlock 递归渲染

#### 4.4.1 策略：新增 SubAgentTraceBlock.vue 组件

不改动 ReasoningTraceBlock 的主时间线计算逻辑（mergedTimeline），而是：
1. 新建 `SubAgentTraceBlock.vue`，视觉风格复用 ReasoningTraceBlock（圆点、连接线、斜体思考、折叠动画）
2. ReasoningTraceBlock 的时间线 step 渲染处，当 step.eventType === 'subagent_started' 时，渲染 SubAgentTraceBlock 作为该 step 的嵌套内容
3. SubAgentTraceBlock 内部有自己的 mergedTimeline 计算，渲染子步骤和思考内容

#### 4.4.2 SubAgentTraceBlock 视觉设计

```
├─ ◇ 调用 Sub-Agent: "分析最近一周训练数据"    ← 主时间线中的 subagent_started step
│   ┌─────────────────────────────────────────┐
│   │ ◇ Sub-Agent: 分析最近一周训练数据         │ ← SubAgentTraceBlock header
│   │ ├─ ● 第1轮 LLM 决策                      │
│   │ │   ├─ 斜体思考内容...                    │
│   │ │   └─ 决策: tool_call training_log.query│
│   │ ├─ □ 调用工具: training_log.query         │
│   │ ├─ ● 第2轮 LLM 决策                      │
│   │ │   └─ 决策: final                       │
│   │ └─ ◇ Sub-Agent 完成 (3.2s)               │
│   └─────────────────────────────────────────┘
```

样式细节：
- 外层容器：`border: 1px solid var(--outline-variant)`，`border-radius: 8px`，`margin: 4px 0`，`overflow: hidden`
- 内层背景：`background: color-mix(in srgb, var(--primary) 3%, transparent)`
- Sub-Agent header：Material icon `smart_tools` + 任务描述文本 + 耗时 meta，`padding: 8px 12px`，cursor: pointer
- 展开/折叠：点击 header 切换，动画与现有 turn 折叠一致（max-height + opacity + 0.25s 过渡）
- 执行中：header 显示 pulse 动画圆点（与现有 reasoning-pulse 圆点样式一致）
- 内容区：`padding: 0 12px 8px 24px`（左侧缩进），内部时间线的连接线向左偏移以对齐
- 子步骤：复用 ReasoningTraceBlock 的 turn/step 渲染 CSS 类
- 子思考内容：复用现有斜体 + 左侧虚线边框样式
- 失败状态：header 显示错误图标和错误信息，border-color 改为 error 色
- 支持 prefers-reduced-motion（与现有动画一致）

#### 4.4.3 ReasoningTraceBlock 模板改动

在现有 `mergedTimeline` 的 step 渲染处，新增条件分支（最小改动）：

```vue
<!-- 现有 step 渲染 -->
<div v-else-if="item.type === 'step'" class="tl-step">
  <!-- 现有 step 标签渲染（resolveStepLabel 等）-->
  
  <!-- 新增：如果是 subagent_started，渲染嵌套 SubAgentTraceBlock -->
  <SubAgentTraceBlock
    v-if="item.step.eventType === 'subagent_started'"
    :steps="item.step.subSteps || []"
    :thinking-segments="item.step.subThinkingSegments || []"
    :task="item.step.subTask || 'Sub-Agent'"
    :is-running="isSubAgentRunning(item.step)"
    :duration-ms="item.step.durationMs"
    :error-message="item.step.errorMessage"
  />
</div>
```

`isSubAgentRunning(step)` 判断逻辑：step 状态为 running 且 subSteps 中没有 final_answer/run_failed 步骤。

### 4.5 ChatMessageList.vue 不改

现有 v-if streaming → v-text / v-else → v-html 逻辑完全不动。AgentStepCard 全局卡片和消息内嵌 ReasoningTraceBlock 的渲染路径完全不动。

### 4.6 thinkingCache 兼容

- thinkingCache key 格式和写入时机不变
- 主 Agent 的 thinking 缓存逻辑不变
- Sub-Agent 的 thinking 内容在终态时随 agentSteps 树的 subThinkingSegments 一起持久化（通过 result_json 或 ChatThinking 虚拟 botMsgId），不独立写 thinkingCache
- applyFinishPayload 中保存 thinking 到缓存时，只保存主 Agent 的 thinkingContent

### 4.7 前端文件变更清单

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `types/agent.ts` | 修改 | 新增 subagentRunId/subSteps/subThinkingSegments/subTask/parentRunId/childrenRunIds 字段 |
| `utils/agentEventAdapter.ts` | 修改 | 解析 subagentRunId 字段 |
| `pages/chat/ChatLogicBase.vue` | 修改 | 事件路由（subagent_started 创建子 runEntry、R2 事件路由到子 runEntry）、buffer 管理（子 run 独立 buffer）、实时引用同步、终态合并、取消级联、快照扩展、历史加载 |
| `pages/chat/components/ReasoningTraceBlock.vue` | 修改 | 在 subagent_started step 处渲染 SubAgentTraceBlock（最小改动） |
| `pages/chat/components/SubAgentTraceBlock.vue` | 新增 | Sub-Agent 嵌套轨迹块组件（复用 ReasoningTraceBlock 样式和逻辑） |

---

## 5. 执行步骤拆分（细粒度，每步确认）

按"每次只改一点点，每步确认"原则，拆分为以下步骤：

### 阶段一：后端基础（不影响现有功能）

| # | 步骤 | 涉及文件 | 验证方式 |
|---|---|---|---|
| 1 | SQL: t_agent_run 加 parent_run_id，t_agent_step 加 subagent_run_id | fitmate_init.sql | 手动执行 ALTER TABLE，确认表结构更新 |
| 2 | Entity: AgentRun 加 parentRunId，AgentStep 加 subagentRunId | AgentRun.java, AgentStep.java | 编译通过，启动不报错 |
| 3 | 新增 SubAgentProperties 配置类并注册 | SubAgentProperties.java（新）, LlmConfigBeanConfig.java | 启动不报错，@Value 可注入 |
| 4 | application.yml + dev 配置新增 sub-agent 段 | application.yml, application-dev.yml | 启动不报错，配置值读取正确 |
| 5 | AgentExecuteContext 扩展 isSubAgent/parentRunId/subAgentTask/subAgentAllowedTools/subAgentResult/activeSubAgentRunId + forSubAgent 工厂方法 | AgentExecuteContext.java | 编译通过，现有创建 context 逻辑不受影响 |
| 6 | DTO: AgentStepEvent 加 subagentRunId 字段 | AgentStepEvent.java | 编译通过，新字段 null 时 JSON 序列化为忽略 |
| 7 | AgentTraceService 扩展：startEvent/finishEvent 支持 subagentRunId | AgentTraceService.java | 编译通过，现有调用不传 subagentRunId 时行为不变 |
| 8 | AgentRunService/Mapper 支持 parentRunId 创建 + 按 parent_run_id 查询子 run | AgentRunService.java, AgentRunServiceImpl.java, AgentRunMapper.java/XML | 编译通过，现有创建 run 不传 parentRunId 时为 null |
| 9 | AgentStepMapper 支持 subagent_run_id 查询 | AgentStepMapper.java/XML | 编译通过 |

### 阶段二：后端核心循环重构（最关键）

| # | 步骤 | 涉及文件 | 验证方式 |
|---|---|---|---|
| 10 | AgentLoopExecutor 纯重构：提取 runLoop(context, isSubAgent) 方法，isSubAgent 暂固定为 false | AgentLoopExecutor.java | 编译通过，现有 Agent 对话完全正常（简单问答+工具调用） |
| 11 | runLoop 中 isSubAgent 差异化：Sub-Agent 跳过压缩/预检索/历史加载，使用 SubAgentProperties 预算，构造 task 消息 | AgentLoopExecutor.java | 主 Agent 行为不变（isSubAgent=false 走原有逻辑） |
| 12 | Sub-Agent 起始事件：isSubAgent=true 时在 runLoop 入口发出 run_started（runId=subRunId） | AgentLoopExecutor.java | 主 Agent 行为不变 |
| 13 | 实现 spawn_subagent 分支骨架：解析 task/tools，校验 isSubAgent 和串行约束，确定工具集 | AgentLoopExecutor.java | LLM 输出 spawn_subagent 时能正确解析 |
| 14 | spawn 分支创建 subagent run + context：createRun(parentRunId)、forSubAgent、发出 subagent_started 事件 | AgentLoopExecutor.java | subagent_started 事件正确推送 |
| 15 | spawn 分支调用 runLoop(subContext, true) 并 catch 异常 | AgentLoopExecutor.java | Sub-Agent 能启动执行自己的循环 |
| 16 | Sub-Agent 的 finishWithAnswer 差异化：不推 FINISH、不创建 ChatMessage、不触发记忆提取、结果写 subAgentResult、thinking 用虚拟 botMsgId 持久化 | AgentLoopExecutor.java, ChatSessionService（如需要） | Sub-Agent 完成时结果正确回传 |
| 17 | spawn 分支 post-processing：发出 subagent_finished、结果包装为 observation、usage 汇总、activeSubAgentRunId 清理 | AgentLoopExecutor.java | 主 Agent 下一轮能看到 Sub-Agent 结果并继续决策 |
| 18 | 取消级联：主 Agent 取消时检查 activeSubAgentRunId 并取消 Sub-Agent | AgentCancellationRegistry.java, AgentExecuteContext.java, AgentAsyncServiceImpl.java | 点停止时 Sub-Agent 也被取消 |
| 19 | prompt 更新：agent-system.md 新增 spawn_subagent 格式说明 | agent-system.md | 人工审阅 prompt 内容清晰 |
| 20 | 历史查询接口扩展：递归加载子 run 和 steps，嵌套到 subagent_started step 下 | AgentRunServiceImpl.java, AgentRunMapper.java | API 返回包含子 run 数据 |

### 阶段三：前端基础

| # | 步骤 | 涉及文件 | 验证方式 |
|---|---|---|---|
| 21 | types/agent.ts 新增 subagentRunId/subSteps/subThinkingSegments/subTask/parentRunId/childrenRunIds 字段 | types/agent.ts | tsc 编译通过 |
| 22 | agentEventAdapter.ts 解析 subagentRunId 字段 | utils/agentEventAdapter.ts | tsc 编译通过，现有事件解析不受影响 |
| 23 | ChatLogicBase: runEntry/runBuffers 支持 parentRunId/childrenRunIds，createRunEntry 扩展 | ChatLogicBase.vue | tsc 编译通过，现有单 run 行为不变 |
| 24 | ChatLogicBase: AGENT_STEP 事件路由——subagent_started 创建子 runEntry + 父子引用关联（subSteps 指向子 runEntry.steps） | ChatLogicBase.vue | 子 runEntry 正确创建，父 step.subSteps 响应式关联 |
| 25 | ChatLogicBase: AGENT_STEP 事件路由——runId=R2 的事件路由到子 runEntry，更新子 runEntry.steps 时同步父 step 引用 | ChatLogicBase.vue | R2 事件正确更新到子 runEntry，UI 实时反映 |
| 26 | ChatLogicBase: THINKING/ADD chunk 按 runId 路由到子 runEntry buffer，rAF flush 到子 runEntry.thinkingSegments | ChatLogicBase.vue | 子 run 的流式内容正确缓冲和渲染 |
| 27 | ChatLogicBase: subagent_finished 事件处理 + 终态合并（子 run 数据快照到父 step，清理子 runEntry） | ChatLogicBase.vue | 终态时消息持有完整嵌套 steps 树 |
| 28 | ChatLogicBase: 取消级联前端实现（遍历 childrenRunIds 发送取消） | ChatLogicBase.vue | 点停止时子 run 也被取消 |
| 29 | ChatLogicBase: run 快照和历史加载兼容（递归 childrenRunIds，嵌套 steps 恢复） | ChatLogicBase.vue | 页面刷新/历史加载嵌套结构正确 |

### 阶段四：前端 UI（SubAgentTraceBlock）

| # | 步骤 | 涉及文件 | 验证方式 |
|---|---|---|---|
| 30 | 新建 SubAgentTraceBlock.vue 基础结构：props 定义、header（icon+task+meta）、折叠/展开状态 | SubAgentTraceBlock.vue（新） | 组件能正常渲染，header 可点击折叠 |
| 31 | SubAgentTraceBlock 实现 mergedTimeline 计算（参考 ReasoningTraceBlock 逻辑，复用步骤/思考段交错合并） | SubAgentTraceBlock.vue | 子步骤和思考内容正确交错显示 |
| 32 | SubAgentTraceBlock 子步骤/思考内容渲染（复用 ReasoningTraceBlock 的 CSS 类） | SubAgentTraceBlock.vue | 步骤标签、思考块样式正确 |
| 33 | SubAgentTraceBlock 运行中状态（pulse 圆点、折叠时显示"执行中..."摘要） | SubAgentTraceBlock.vue | 执行中状态正确显示 |
| 34 | SubAgentTraceBlock 完成/失败状态（durationMs、errorMessage 显示） | SubAgentTraceBlock.vue | 终态显示正确 |
| 35 | ReasoningTraceBlock 在 subagent_started step 处渲染 SubAgentTraceBlock | ReasoningTraceBlock.vue | 嵌套块正确显示，无 Sub-Agent 时不渲染 |
| 36 | 视觉细节打磨：边框、背景、缩进对齐、过渡动画、prefers-reduced-motion | SubAgentTraceBlock.vue, ReasoningTraceBlock.vue | 视觉风格与主时间线一致且有层次感 |

### 阶段五：端到端验证

| # | 步骤 | 验证方式 |
|---|---|---|
| 37 | 简单问题不触发 spawn，直接回答 | 对话正常，行为与改动前一致 |
| 38 | 复杂问题触发 spawn，Sub-Agent 执行并返回结果 | Sub-Agent 正确启动、流式显示、返回结果，主 Agent 整合回答 |
| 39 | 前端嵌套折叠块显示正常，流式渲染不卡顿，可展开/折叠 | UI 正确，无 O(n²) 性能问题 |
| 40 | 主 Agent 可以连续 spawn 多个 Sub-Agent（一个完成后再 spawn 下一个） | 串行 spawn 正常 |
| 41 | 取消级联：Sub-Agent 运行中点停止，Sub-Agent 也被取消 | 取消行为正确 |
| 42 | 历史消息加载：刷新页面后嵌套结构正确显示 | 历史数据正确恢复 |
| 43 | Sub-Agent 运行时正常调用工具、搜索、知识库等 | Sub-Agent 工具调用正常 |
| 44 | Sub-Agent 不能 spawn 子 Sub-Agent（被 allowedTools 阻止） | Sub-Agent 输出 spawn_subagent 时被 ToolRouter/主循环拒绝 |

---

## 6. 风险与缓解

| 风险 | 缓解措施 |
|---|---|
| AgentLoopExecutor 提取 runLoop 破坏现有功能 | 步骤 10 是纯提取重构，验证后再做后续改动；提取后主 Agent 走 isSubAgent=false 路径，逻辑不变 |
| LLM 不理解 spawn_subagent 格式 | prompt 给出明确 JSON 示例和规则；与 tool_call/final 格式一致；不支持 spawn 时自动降级为直接回答 |
| Sub-Agent 无限循环/超时 | Sub-Agent 有独立的 max-iterations/max-run-duration 预算；超时后强制结束并返回超时信息给主 Agent |
| 前端嵌套渲染性能问题 | 最多一层嵌套；rAF 批处理覆盖所有 run；子 run 独立 buffer 互不干扰；subSteps 通过引用关联避免数据复制 |
| Sub-Agent 污染主 Agent 上下文 | Sub-Agent 的 observations/thinking/usage 与主 Agent 完全隔离，只通过 subAgentResult 字符串通信 |
| SSE 事件顺序问题 | subagent_started 在 runLoop 调用前同步发出，保证先于 R2 事件到达前端；SSE 单连接顺序协议保证事件有序 |
| 子 runEntry.steps 不可变更新导致父 step.subSteps 引用断裂 | 更新子 runEntry 后显式同步父 step.subSteps 引用（步骤 25） |
| 历史加载时子 run 数据缺失 | API 层递归查询 parent_run_id 关联的子 run，嵌套组装到响应中 |
