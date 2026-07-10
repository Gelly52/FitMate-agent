# 后端模块清理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除 mcpServer 中遗留的死代码，移除空壳的 `FitMate-domain` 模块，把 `ListSortEnum` 收敛到 `FitMate-common`，并把 Agent 并发锁的硬编码 key 提取为常量。

**Architecture:** 不再为"消除跨模块重复"而下沉实体到 domain——而是**先清理死代码**，让每个应用模块自持自需的实体。mcpServer 中真正在用的 `RagDocumentMeta` / `RagBenchmarkRun` 留在 mcpServer；`ListSortEnum` 这种轻量公共枚举上提到 common；删除整个 `FitMate-domain` 模块以消除空壳。

**Tech Stack:** Java 21 + Spring Boot 3.5 + Maven 多模块 + MyBatis-Plus。无后端单元测试，验证靠 `mvn clean compile` + 应用启动 + 关键端点冒烟。

---

## 文件结构

| 文件 | 责任 | 改动类型 |
|---|---|---|
| `FitMate-backend/FitMate-mcpServer/src/main/java/com/itgeo/fitmate/mcp/fitness/` | 死代码子包（无 Tool 引用） | 整包删除 |
| `FitMate-backend/FitMate-mcpServer/src/main/java/com/itgeo/fitmate/mcp/sample/` | 模板残留 | 整包删除 |
| `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/ServiceLogAspect/` | 包名错误（`api.ServiceLogAspect.java`），且切点 `api.impl..*` 不匹配任何现存包，是死代码 | 整包删除 |
| `FitMate-backend/FitMate-domain/` | 仅含 `ListSortEnum.java`，空壳模块 | 整模块删除 |
| `FitMate-backend/FitMate-common/src/main/java/com/itgeo/fitmate/common/enums/ListSortEnum.java` | 从 domain 移入的列表排序枚举 | 新建（内容迁移） |
| `FitMate-backend/FitMate-common/src/main/java/com/itgeo/fitmate/common/constant/RedisKeyConstants.java` | Agent 并发锁 Redis key 前缀常量 | 新建 |
| `FitMate-backend/pom.xml` | 根 POM，聚合模块列表 | 移除 `FitMate-domain` |
| `FitMate-backend/FitMate-api/pom.xml` | api 模块依赖 | 移除 `fitmate-domain` 依赖 |
| `FitMate-backend/FitMate-mcpServer/pom.xml` | mcpServer 模块依赖 | 移除 `fitmate-domain` 依赖 |
| `FitMate-backend/FitMate-mcpServer/src/main/java/com/itgeo/fitmate/mcp/rag/RagManageTool.java` | 引用了 `ListSortEnum` | 改 import |
| `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/application/impl/AgentExecuteServiceImpl.java` | 硬编码 `AGENT_LOCK_KEY_PREFIX` | 改用 `RedisKeyConstants` |

---

## Task 1: 删除 mcpServer 中的 fitness 死代码子包

**Files:**
- Delete: `FitMate-backend/FitMate-mcpServer/src/main/java/com/itgeo/fitmate/mcp/fitness/`（整包）

**目标：** 删除 mcpServer 中没有任何 Tool 引用的 fitness 子包。这些实体（`BodyMetrics` / `TrainingLog` / `TrainingExercise`）及对应 Mapper 是阶段一迁移遗留，未被 `@Tool` 类引用，纯属死代码。`FitMateMcpServerApplication.registMCPTools` 中仅注册了 `EmailTool` 和 `RagManageTool`。

**依据：** [FitMateMcpServerApplication.java#L27](file:///d:/Applications/Java/A-Learning/FitMate-AI-0/FitMate-backend/FitMate-mcpServer/src/main/java/com/itgeo/fitmate/mcp/FitMateMcpServerApplication.java#L27) 注释明确写明"阶段二清理：DateTool/TrainingLogTool/BodyMetricsTool 已迁本地 ToolExecutor，此处仅保留独有工具"。

- [ ] **Step 1: 确认 fitness 子包下无任何 Tool 引用**

运行：
```bash
grep -rn "mcp.fitness" FitMate-backend/FitMate-mcpServer/src/main/java --include="*.java"
```
预期：仅命中 `mcp/fitness/` 内部自身的相互引用（entity ↔ mapper），无 `@Tool` 类引用。

- [ ] **Step 2: 删除 fitness 整包**

删除目录：
- `FitMate-backend/FitMate-mcpServer/src/main/java/com/itgeo/fitmate/mcp/fitness/`

具体文件清单（删除前确认存在）：
- `mcp/fitness/metrics/infrastructure/entity/BodyMetrics.java`
- `mcp/fitness/metrics/infrastructure/mapper/BodyMetricsMapper.java`
- `mcp/fitness/training/infrastructure/entity/TrainingLog.java`
- `mcp/fitness/training/infrastructure/entity/TrainingExercise.java`
- `mcp/fitness/training/infrastructure/mapper/TrainingLogMapper.java`
- `mcp/fitness/training/infrastructure/mapper/TrainingExerciseMapper.java`

- [ ] **Step 3: 编译 mcpServer 验证删除无影响**

Run: `mvn -pl FitMate-mcpServer -am clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add -A FitMate-backend/FitMate-mcpServer/src/main/java/com/itgeo/fitmate/mcp/fitness
git commit -m "refactor(mcp): remove dead fitness subpackage

阶段二遗留死代码：BodyMetrics/TrainingLog/TrainingExercise 实体与 Mapper 在 mcpServer 内无 @Tool 引用，对应工具已迁入 api 模块本地 ToolExecutor，清理之。"
```

---

## Task 2: 删除 mcpServer 中的 sample 模板残留

**Files:**
- Delete: `FitMate-backend/FitMate-mcpServer/src/main/java/com/itgeo/fitmate/mcp/sample/`（整包）

**目标：** 删除 `sample/infrastructure/entity/Product.java` 这一模板示例代码，无任何引用。

- [ ] **Step 1: 确认 sample 子包无引用**

运行：
```bash
grep -rn "mcp.sample" FitMate-backend/FitMate-mcpServer/src/main/java --include="*.java"
```
预期：无命中（除 sample 包自身）。

- [ ] **Step 2: 删除 sample 整包**

删除目录：`FitMate-backend/FitMate-mcpServer/src/main/java/com/itgeo/fitmate/mcp/sample/`

- [ ] **Step 3: 编译验证**

Run: `mvn -pl FitMate-mcpServer -am clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add -A FitMate-backend/FitMate-mcpServer/src/main/java/com/itgeo/fitmate/mcp/sample
git commit -m "chore(mcp): remove sample template subpackage"
```

---

## Task 3: 在 common 新建 enums 包并迁入 ListSortEnum

**Files:**
- Create: `FitMate-backend/FitMate-common/src/main/java/com/itgeo/fitmate/common/enums/ListSortEnum.java`

**目标：** 在 common 模块新建 `enums` 包，把 `ListSortEnum` 从 domain 迁入。`ListSortEnum` 是纯枚举，不依赖任何外部 jar，迁入 common 后可被 api 与 mcpServer 共用。

- [ ] **Step 1: 新建 `FitMate-common/src/main/java/com/itgeo/fitmate/common/enums/ListSortEnum.java`**

文件内容（注意包名改为 `com.itgeo.fitmate.common.enums`，其余与 domain 版本完全一致）：

```java
package com.itgeo.fitmate.common.enums;

/**
 * @author gzx
 * @description: 列表排序方式
 * @date 2024-05-20 10:00:00
 */
public enum ListSortEnum {

    ASC("asc", "升序排序"),
    DESC("desc", "降序排序");

    public final String code;
    public final String label;

    ListSortEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

}
```

- [ ] **Step 2: 编译 common 模块验证**

Run: `mvn -pl FitMate-common -am clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/FitMate-common/src/main/java/com/itgeo/fitmate/common/enums/ListSortEnum.java
git commit -m "refactor(common): relocate ListSortEnum to common.enums"
```

---

## Task 4: 在 common 新建 RedisKeyConstants

**Files:**
- Create: `FitMate-backend/FitMate-common/src/main/java/com/itgeo/fitmate/common/constant/RedisKeyConstants.java`

**目标：** 把 api 模块中硬编码的 Agent 并发锁 key 前缀（`fitmate:dev:agent:lock:session:`）和 slot 数量（3）提取为 common 常量。这两个值在 [AgentExecuteServiceImpl.java](file:///d:/Applications/Java/A-Learning/FitMate-AI-0/FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/application/impl/AgentExecuteServiceImpl.java#L48) 中以 `private static final` 形式存在，但 `RedisKeyConstants` 让任何需要观察 / 监控这些 key 的代码都能引用同一份定义。

- [ ] **Step 1: 新建 `FitMate-common/src/main/java/com/itgeo/fitmate/common/constant/RedisKeyConstants.java`**

文件内容：

```java
package com.itgeo.fitmate.common.constant;

/**
 * Redis key 常量集合。
 * 集中维护跨模块共享的 Redis key 前缀，避免硬编码散落各处。
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {}

    /**
     * Agent 任务并发锁 key 前缀。
     * 完整 key 形如：fitmate:dev:agent:lock:session:{sessionId}:slot:{1..N}
     */
    public static final String AGENT_LOCK_KEY_PREFIX = "fitmate:dev:agent:lock:session:";

    /**
     * 单个登录 sessionId 允许同时运行的 Agent 任务上限。
     * 对应 N 个 slot 锁。
     */
    public static final int AGENT_LOCK_SLOT_COUNT = 3;
}
```

- [ ] **Step 2: 编译 common 模块验证**

Run: `mvn -pl FitMate-common -am clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/FitMate-common/src/main/java/com/itgeo/fitmate/common/constant/RedisKeyConstants.java
git commit -m "feat(common): add RedisKeyConstants for agent lock"
```

---

## Task 5: 在 mcpServer 中替换 ListSortEnum 的 import

**Files:**
- Modify: `FitMate-backend/FitMate-mcpServer/src/main/java/com/itgeo/fitmate/mcp/rag/RagManageTool.java:4`

**目标：** 把 `RagManageTool` 中对旧 domain 包 `ListSortEnum` 的引用改为 common 包。这是删除 domain 模块的前置条件——必须先消除所有对 domain 的引用。

- [ ] **Step 1: 替换 import 行**

在 `RagManageTool.java` 第 4 行：

替换前：
```java
import com.itgeo.fitmate.domain.common.ListSortEnum;
```

替换后：
```java
import com.itgeo.fitmate.common.enums.ListSortEnum;
```

文件中其余代码（`ListSortEnum.ASC` / `ListSortEnum.DESC` 等使用）无需改动，仅 import 行变化。

- [ ] **Step 2: 编译 mcpServer 验证**

Run: `mvn -pl FitMate-mcpServer -am clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/FitMate-mcpServer/src/main/java/com/itgeo/fitmate/mcp/rag/RagManageTool.java
git commit -m "refactor(mcp): point RagManageTool ListSortEnum import to common"
```

---

## Task 6: 在 api 中改用 RedisKeyConstants 替换硬编码

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/application/impl/AgentExecuteServiceImpl.java:48,53`

**目标：** 把 `AgentExecuteServiceImpl` 中的 `AGENT_LOCK_KEY_PREFIX` 和 `MAX_CONCURRENT_AGENTS_PER_SESSION` 改为引用 `RedisKeyConstants`，消除硬编码重复。

- [ ] **Step 1: 新增 import**

在 `AgentExecuteServiceImpl.java` 现有 import 区（`com.itgeo.fitmate.api.agent.application.AgentAsyncService` 之上按字母序插入）新增：

```java
import com.itgeo.fitmate.common.constant.RedisKeyConstants;
```

- [ ] **Step 2: 替换两个常量定义为引用**

替换前（L47-53）：
```java
    private static final long AGENT_LOCK_TTL_SECONDS = 120L;
    private static final String AGENT_LOCK_KEY_PREFIX = "fitmate:dev:agent:lock:session:";
    /**
     * 同一登录 sessionId 允许同时运行的 Agent 任务上限。
     * 通过为每个登录会话维护 N 个独立 slot 锁实现：申请时按序尝试，命中首个空槽即占用。
     */
    private static final int MAX_CONCURRENT_AGENTS_PER_SESSION = 3;
```

替换后：
```java
    private static final long AGENT_LOCK_TTL_SECONDS = 120L;
    private static final String AGENT_LOCK_KEY_PREFIX = RedisKeyConstants.AGENT_LOCK_KEY_PREFIX;
    /**
     * 同一登录 sessionId 允许同时运行的 Agent 任务上限。
     * 通过为每个登录会话维护 N 个独立 slot 锁实现：申请时按序尝试，命中首个空槽即占用。
     */
    private static final int MAX_CONCURRENT_AGENTS_PER_SESSION = RedisKeyConstants.AGENT_LOCK_SLOT_COUNT;
```

说明：保留本类的 `AGENT_LOCK_KEY_PREFIX` 与 `MAX_CONCURRENT_AGENTS_PER_SESSION` 私有常量作为本地别名，避免修改类内其他对这两个名字的引用（L162、L258、L278）。

- [ ] **Step 3: 编译 api 模块验证**

Run: `mvn -pl FitMate-api -am clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/application/impl/AgentExecuteServiceImpl.java
git commit -m "refactor(agent): use RedisKeyConstants for lock key prefix"
```

---

## Task 7: 删除 api 中错误的 ServiceLogAspect 死代码

**Files:**
- Delete: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/ServiceLogAspect/`（整包）

**目标：** 删除包名错误且切点失效的 `ServiceLogAspect`。

**依据：**
1. 包名 `com.itgeo.fitmate.api.ServiceLogAspect.java`（带 `.java` 后缀）是明显的笔误，目录被建成了 `ServiceLogAspect/java/`。
2. 切点 `@Around("execution(* com.itgeo.fitmate.api.impl..*.*(..))")` 指向 `api.impl` 包，但当前 api 模块下所有 Service 实现位于 `api.{module}.application.impl`（如 `api.agent.application.impl.AgentExecuteServiceImpl`），**不存在 `api.impl` 包**，因此该切面实际匹配不到任何方法，是死代码。

> 若未来需要服务耗时日志，应在 `api/aspect/` 下新建并用正确切点（如 `execution(* com.itgeo.fitmate.api..application.impl..*.*(..))`）重新实现，不在本次清理范围。

- [ ] **Step 1: 确认 api.impl 包不存在**

运行：
```bash
grep -rn "com.itgeo.fitmate.api.impl" FitMate-backend/FitMate-api/src --include="*.java"
```
预期：仅命中 `ServiceLogAspect.java` 自身的切点字符串，无任何 Service 实际位于 `api.impl` 包。

- [ ] **Step 2: 确认无其他文件引用 ServiceLogAspect**

运行：
```bash
grep -rn "ServiceLogAspect" FitMate-backend/FitMate-api/src --include="*.java"
```
预期：仅命中 `ServiceLogAspect.java` 自身，无外部引用（它通过 `@Component` 被 Spring 自动扫描，不需要显式 import）。

- [ ] **Step 3: 删除 ServiceLogAspect 整包**

删除目录：`FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/ServiceLogAspect/`

- [ ] **Step 4: 编译 api 模块验证**

Run: `mvn -pl FitMate-api -am clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add -A FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/ServiceLogAspect
git commit -m "refactor(api): remove dead ServiceLogAspect

包名错误（api.ServiceLogAspect.java 带 .java 后缀），且切点 api.impl..* 不匹配任何现存包（实际 Service 在 api.{module}.application.impl），属死代码。"
```

---

## Task 8: 从根 pom.xml 移除 FitMate-domain 模块

**Files:**
- Modify: `FitMate-backend/pom.xml:19-24`

**目标：** 从父 POM 的 `<modules>` 中移除 `FitMate-domain`，使其不再参与聚合构建。前置条件：Task 5 已完成（mcpServer 不再引用 domain 中任何类）。

- [ ] **Step 1: 编辑父 pom.xml 的 `<modules>` 段**

替换前（L19-24）：
```xml
    <modules>
        <module>FitMate-common</module>
        <module>FitMate-domain</module>
        <module>FitMate-api</module>
        <module>FitMate-mcpServer</module>
    </modules>
```

替换后：
```xml
    <modules>
        <module>FitMate-common</module>
        <module>FitMate-api</module>
        <module>FitMate-mcpServer</module>
    </modules>
```

- [ ] **Step 2: 编译全工程验证（此时 domain 模块还在文件系统但已不被聚合）**

Run: `mvn -pl FitMate-api,FitMate-mcpServer -am clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/pom.xml
git commit -m "build: drop FitMate-domain from reactor modules"
```

---

## Task 9: 从 api 与 mcpServer 的 pom.xml 移除 fitmate-domain 依赖

**Files:**
- Modify: `FitMate-backend/FitMate-api/pom.xml:22-26`
- Modify: `FitMate-backend/FitMate-mcpServer/pom.xml:22-26`

**目标：** 移除两个应用模块对 `fitmate-domain` 的 Maven 依赖声明。

- [ ] **Step 1: 编辑 api 模块 pom.xml**

在 `FitMate-backend/FitMate-api/pom.xml` 中删除以下 5 行（L22-26）：

```xml
        <dependency>
            <groupId>com.itgeo</groupId>
            <artifactId>fitmate-domain</artifactId>
            <version>${project.version}</version>
        </dependency>
```

保留紧邻其上的 `fitmate-common` 依赖。

- [ ] **Step 2: 编辑 mcpServer 模块 pom.xml**

在 `FitMate-backend/FitMate-mcpServer/pom.xml` 中删除以下 5 行（L22-26）：

```xml
        <dependency>
            <groupId>com.itgeo</groupId>
            <artifactId>fitmate-domain</artifactId>
            <version>${project.version}</version>
        </dependency>
```

保留紧邻其上的 `fitmate-common` 依赖。

- [ ] **Step 3: 编译全工程验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS（此时 domain 模块仍在文件系统但已不被任何模块依赖、不在聚合中）

- [ ] **Step 4: Commit**

```bash
git add FitMate-backend/FitMate-api/pom.xml FitMate-backend/FitMate-mcpServer/pom.xml
git commit -m "build: remove fitmate-domain dependency from api and mcpServer"
```

---

## Task 10: 删除 FitMate-domain 模块目录

**Files:**
- Delete: `FitMate-backend/FitMate-domain/`（整个目录）

**目标：** 物理删除 `FitMate-domain` 模块。前置条件：Task 8（聚合移除）+ Task 9（依赖移除）均已完成，此时无任何代码引用 domain 包，也无任何 pom 引用其构件。

- [ ] **Step 1: 全工程确认无任何对 domain 包的引用残留**

运行：
```bash
grep -rn "com.itgeo.fitmate.domain" FitMate-backend --include="*.java"
grep -rn "fitmate-domain" FitMate-backend --include="pom.xml"
```
预期：两条命令均无命中。

- [ ] **Step 2: 删除整个 domain 目录**

删除目录：`FitMate-backend/FitMate-domain/`

包含文件：
- `FitMate-domain/pom.xml`
- `FitMate-domain/src/main/java/com/itgeo/fitmate/domain/common/ListSortEnum.java`

- [ ] **Step 3: 全量编译验证**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add -A FitMate-backend/FitMate-domain
git commit -m "refactor: remove empty FitMate-domain module

domain 模块仅含 ListSortEnum 一个枚举类，无其他领域职责。ListSortEnum 已迁入 FitMate-common.enums；domain 模块本身删除。"
```

---

## Task 11: 启动验证 + 最终冒烟

**目标：** 通过实际启动两个应用 + 触发关键端点，确认重构未破坏运行时行为。

- [ ] **Step 1: 启动 mcpServer**

启动 `FitMateMcpServerApplication`，确认日志中出现：
- `MCP server sse endpoint: /sse`
- 无 `BeanCreationException` 或 `UnsatisfiedDependencyException`

若启动失败，定位问题并修复后重新执行本步。

- [ ] **Step 2: 启动 api 模块**

启动 `FitMateApiApplication`，确认日志中出现：
- `Started FitMateApiApplication in X seconds`
- MyBatis-Plus Mapper 扫描日志（确认 11 个 mapper 包均被注册）
- 无 `BeanCreationException`

- [ ] **Step 3: 触发 Agent 并发锁路径冒烟**

通过前端或 curl 触发一次 `/agent/execute` 请求，确认：
- 接口正常返回 ack
- Redis 中出现 key `fitmate:dev:agent:lock:session:{sessionId}:slot:1`
- 任务完成后该 key 被释放

可用 redis-cli 验证：
```bash
redis-cli -p 9379 -a your_redis_password KEYS "fitmate:dev:agent:lock:*"
```

- [ ] **Step 4: 触发 RAG 查询冒烟（通过 MCP）**

通过 api 端的 MCP client 配置接入 mcpServer（端口 9070，SSE），触发一次 Agent 任务调用 `queryRagDocuments` 工具，确认：
- mcpServer 日志出现 `调用MCP工具：queryRagDocuments`
- 工具返回 RAG 文档列表（无 `ClassNotFoundException: ListSortEnum`）

- [ ] **Step 5: 最终提交（如有微调）**

若 Step 1-4 出现需要修复的问题，修复后提交：
```bash
git add -A
git commit -m "fix: post-cleanup smoke test adjustments"
```

若全部通过，无需提交，整个清理工作完成。

---

## 完成判据

- [ ] `mvn clean compile` 全工程通过
- [ ] api 与 mcpServer 均可独立启动
- [ ] grep 确认 `com.itgeo.fitmate.domain` 在 `FitMate-backend` 下零命中
- [ ] grep 确认 `fitmate-domain` 在所有 `pom.xml` 中零命中
- [ ] grep 确认 `mcp.fitness` 和 `mcp.sample` 在 mcpServer 中零命中
- [ ] grep 确认 `fitmate:dev:agent:lock:session:` 在 Java 源码中仅出现在 `RedisKeyConstants.java` 一处
- [ ] Agent 并发锁路径正常工作（slot 1/2/3）
- [ ] MCP `queryRagDocuments` 工具正常工作

---

## 不在本次范围

- **不下沉实体到 domain**：api 中的 `BodyMetrics` / `TrainingLog` / `RagDocument` / `RagBenchmarkRun` / `User` 等保持原位。mcpServer 中真正在用的 `RagDocumentMeta` / `RagBenchmarkRun` 也保持原位。
- **不合并 `RagDocument` 与 `RagDocumentMeta` 字段**：两模块的字段差异是历史遗留，但当前业务各自工作正常，保持现状。
- **不补齐 `ServiceLogAspect` 替代实现**：本次只删不补。如未来确需服务耗时日志，应在 `api/aspect/` 下用正确包名和正确切点新建。
- **不动 `@MapperScan` 路径**：api 的 `@MapperScan` 已是显式列举 11 个 mapper 包，无 domain 包路径，无需修改。mcpServer 的 `@MapperScan("com.itgeo.fitmate.mcp")` 删除 fitness 后仍正常覆盖 rag/email 子包，无需修改。
