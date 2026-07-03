# FitMate 长期记忆功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 FitMate Agent 增加长期记忆能力，支持会话后/Wiki 编译时/定时快照三个写入来源，画像常驻注入 Agent prompt，Dashboard 图像化展示用户画像，用户可查看与删除记忆。

**Architecture:** 统一 `t_user_memory` 表承载 4 类记忆（FACT/EPISODIC/SNAPSHOT/INSIGHT），`t_user_profile` 表缓存 LLM 生成的画像文本与标签。3 个异步写入入口（会话提取/Wiki 编译提取/定时快照聚合）→ 写入记忆 → 触发画像异步重生。读取时画像常驻注入 Agent 首轮 prompt，EPISODIC/INSIGHT 通过 `memory.search` 工具按需检索。

**Tech Stack:** Spring Boot 3 / MyBatis-Plus / Spring AI（ChatModel）/ Redis / MySQL / @Async / @Scheduled / Vue 3

**设计文档:** [docs/superpowers/specs/2026-07-03-long-term-memory-design.md](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/docs/superpowers/specs/2026-07-03-long-term-memory-design.md)

**项目约定:**
- 后端包结构按领域划分（DDD 风格）：每个领域包下有 controller/application/infrastructure/dto 子包
- 测试：JUnit 5 纯单元测试（不依赖 Spring Context），参考 `LlmConfigCipherTest.java`
- YAML：2 空格缩进，顶层 section 顺序遵循 project_memory.md
- 中文注释，commit message 用英文
- LLM 调用统一通过注入的 `ChatModel` Bean

---

## 文件结构总览

### 新建文件

**后端 - 记忆核心领域**（`FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/`）：
- `config/MemoryProperties.java` — 记忆配置属性绑定
- `config/MemoryAsyncConfig.java` — 异步线程池 + @EnableScheduling
- `infrastructure/entity/UserMemory.java` — 记忆实体
- `infrastructure/entity/UserProfile.java` — 画像缓存实体
- `infrastructure/mapper/UserMemoryMapper.java` — 记忆 Mapper
- `infrastructure/mapper/UserProfileMapper.java` — 画像 Mapper
- `application/MemoryWriter.java` — 写入收口服务
- `application/MemoryReader.java` — 读取服务（画像 + 事件检索）
- `application/ProfileBuilder.java` — 异步画像生成
- `application/MemoryInjector.java` — Agent prompt 注入器
- `application/extractor/SessionMemoryExtractor.java` — 会话后提取
- `application/extractor/MemoryExtractResult.java` — 提取结果 DTO
- `application/scheduler/SnapshotAggregator.java` — 定时快照聚合
- `controller/MemoryController.java` — 用户控制 API
- `controller/dto/MemoryListResponse.java` — 记忆列表响应
- `controller/dto/ProfileResponse.java` — 画像响应
- `tool/MemorySearchToolExecutor.java` — memory.search 工具

**后端 - Prompt 模板**（`FitMate-api/src/main/resources/prompts/`）：
- `memory-extract.md` — 会话提取 prompt
- `profile-build.md` — 画像生成 prompt

**后端 - 测试**（`FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/memory/longterm/`）：
- `infrastructure/MemoryWriterTest.java`
- `application/MemoryReaderTest.java`
- `application/extractor/SessionMemoryExtractorTest.java`
- `application/scheduler/SnapshotAggregatorTest.java`
- `application/ProfileBuilderTest.java`
- `application/MemoryInjectorTest.java`

**前端**（`FitMate-frontend/src/`）：
- `pages/dashboard/components/UserProfilePanel.vue` — Dashboard 画像展示组件
- `pages/settings/components/MemorySection.vue` — 记忆管理组件
- `services/memoryApi.ts` — 记忆相关 API 封装
- `types/memory.ts` — 记忆相关类型定义

### 修改文件

**后端**：
- `FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql` — 新增 2 张表 + 删除 PROFILE 枚举
- `FitMate-api/src/main/resources/application.yml` — 新增 `fitmate.memory.*` 配置 + enabled-tools 增加 memory.search
- `FitMate-api/src/main/resources/application-dev.yml` — 覆盖 memory 配置（如需要）
- `FitMate-api/src/main/resources/prompts/wiki-schema.md` — 删除 PROFILE + 新增 memory_extraction 字段
- `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/infrastructure/entity/WikiPage.java` — 注释删除 PROFILE
- `FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java` — finishWithAnswer 挂钩 + run 注入画像
- `FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/prompt/AgentPromptBuilder.java` — 新增 userProfileSection 参数
- `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/impl/WikiCompileServiceImpl.java` — 编译后调记忆提取
- `FitMate-api/src/main/java/com/itgeo/fitmate/api/prompt/PromptTemplateManager.java` — 新增 buildWikiCompilePrompt 附加段 + 新增 buildMemoryExtractPrompt / buildProfileBuildPrompt

**前端**：
- `pages/dashboard/DashboardPage.vue` — 替换右侧占位为 UserProfilePanel
- `pages/settings/SettingsPage.vue` — 新增 MemorySection 入口
- `pages/settings/components/SettingsSectionNav.vue` — 新增"记忆管理"导航项
- `services/doctorApi.ts` — 增加 memory 相关 API（或独立 memoryApi.ts）
- `types/settings.ts` — 新增 memory 相关类型（如需）

---

## 阶段划分

本计划分为 6 个阶段，每个阶段独立可测试、可提交：

1. **阶段 1：数据层与配置** — 建表、实体、Mapper、配置属性
2. **阶段 2：记忆写入核心** — MemoryWriter + SessionMemoryExtractor
3. **阶段 3：Wiki 编译提取 + 定时快照** — 两个额外写入源
4. **阶段 4：画像生成与 Agent 注入** — ProfileBuilder + MemoryInjector + prompt 改造
5. **阶段 5：用户控制 API + memory.search 工具** — Controller + Tool
6. **阶段 6：前端 Dashboard 画像 + 记忆管理** — Vue 组件

---

## 阶段 1：数据层与配置

### Task 1.1：Wiki PROFILE 枚举清理

**Files:**
- Modify: `FitMate-backend/FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql:319`
- Modify: `FitMate-backend/FitMate-api/src/main/resources/prompts/wiki-schema.md:13`
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/infrastructure/entity/WikiPage.java:15`

- [ ] **Step 1: 修改 fitmate_init.sql 注释**

将 `fitmate_init.sql` 第 319 行：
```sql
`page_type`        VARCHAR(30)  NOT NULL COMMENT 'INDEX/ENTITY/CONCEPT/SYNTHESIS/SOURCE_SUMMARY/LOG/PROFILE',
```
改为：
```sql
`page_type`        VARCHAR(30)  NOT NULL COMMENT 'INDEX/ENTITY/CONCEPT/SYNTHESIS/SOURCE_SUMMARY/LOG',
```

- [ ] **Step 2: 修改 wiki-schema.md**

删除 `wiki-schema.md` 第 13 行：
```
- `PROFILE`：用户画像页（Phase 2 用）
```

- [ ] **Step 3: 修改 WikiPage.java 注释**

将 `WikiPage.java` 第 15 行：
```java
private String pageType;        // INDEX/ENTITY/CONCEPT/SYNTHESIS/SOURCE_SUMMARY/LOG/PROFILE
```
改为：
```java
private String pageType;        // INDEX/ENTITY/CONCEPT/SYNTHESIS/SOURCE_SUMMARY/LOG
```

- [ ] **Step 4: 验证 grep 无残留**

运行：`grep -r "PROFILE" FitMate-backend/FitMate-api/src FitMate-backend/FitMate-mcpServer/src/main/resources`
预期：无匹配（target/classes 下的构建产物不算）

- [ ] **Step 5: 提供现有库 ALTER SQL（写入 SQL 脚本注释）**

在 `fitmate_init.sql` 文件末尾追加注释段：
```sql
-- ========== 升级脚本（已有库执行）==========
-- 长期记忆功能
-- ALTER TABLE t_wiki_page MODIFY COLUMN page_type VARCHAR(30) NOT NULL COMMENT 'INDEX/ENTITY/CONCEPT/SYNTHESIS/SOURCE_SUMMARY/LOG';
-- CREATE TABLE IF NOT EXISTS t_user_memory (...);
-- CREATE TABLE IF NOT EXISTS t_user_profile (...);
```

完整 SQL 在 Task 1.2/1.3 中给出。

- [ ] **Step 6: 提交**

```bash
git add FitMate-backend/FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql FitMate-backend/FitMate-api/src/main/resources/prompts/wiki-schema.md FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/infrastructure/entity/WikiPage.java
git commit -m "refactor(wiki): remove unused PROFILE page_type enum"
```

---

### Task 1.2：新增 t_user_memory 表

**Files:**
- Modify: `FitMate-backend/FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql`（在 t_user_preference 表后追加）

- [ ] **Step 1: 在 fitmate_init.sql 末尾（升级脚本注释段之前）追加建表语句**

```sql
-- ========== 长期记忆 ==========
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

- [ ] **Step 2: 更新升级脚本注释段**

将 Task 1.1 Step 5 中的 `-- CREATE TABLE IF NOT EXISTS t_user_memory (...);` 注释行删除（已正式建表）。

- [ ] **Step 3: 提交**

```bash
git add FitMate-backend/FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql
git commit -m "feat(memory): add t_user_memory table"
```

---

### Task 1.3：新增 t_user_profile 表

**Files:**
- Modify: `FitMate-backend/FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql`（紧跟 t_user_memory 之后）

- [ ] **Step 1: 追加建表语句**

```sql
CREATE TABLE IF NOT EXISTS `t_user_profile` (
    `id`                BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`           BIGINT   NOT NULL COMMENT '用户ID',
    `profile_text`      TEXT     NOT NULL COMMENT 'LLM 生成的自然语言画像',
    `profile_tags_json` JSON     NULL     COMMENT '关键标签数组，供可视化',
    `memory_version`    INT      NOT NULL DEFAULT 0 COMMENT '生成时基于的记忆版本号',
    `generated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像缓存';
```

- [ ] **Step 2: 更新升级脚本注释段**

删除 `-- CREATE TABLE IF NOT EXISTS t_user_profile (...);` 注释行。

- [ ] **Step 3: 提交**

```bash
git add FitMate-backend/FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql
git commit -m "feat(memory): add t_user_profile cache table"
```

---

### Task 1.4：UserMemory 实体

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/infrastructure/entity/UserMemory.java`

- [ ] **Step 1: 创建实体类**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_user_memory")
public class UserMemory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String memoryType;      // FACT|EPISODIC|SNAPSHOT|INSIGHT
    private String content;
    private String metadataJson;
    private String source;
    private String contentHash;
    private String status;          // active|archived|ignored
    private LocalDateTime expiredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/infrastructure/entity/UserMemory.java
git commit -m "feat(memory): add UserMemory entity"
```

---

### Task 1.5：UserProfile 实体

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/infrastructure/entity/UserProfile.java`

- [ ] **Step 1: 创建实体类**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_user_profile")
public class UserProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String profileText;
    private String profileTagsJson;
    private Integer memoryVersion;
    private LocalDateTime generatedAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/infrastructure/entity/UserProfile.java
git commit -m "feat(memory): add UserProfile entity"
```

---

### Task 1.6：Mapper 接口

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/infrastructure/mapper/UserMemoryMapper.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/infrastructure/mapper/UserProfileMapper.java`

- [ ] **Step 1: 创建 UserMemoryMapper**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMemoryMapper extends BaseMapper<UserMemory> {
}
```

- [ ] **Step 2: 创建 UserProfileMapper**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
```

- [ ] **Step 3: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/infrastructure/mapper/
git commit -m "feat(memory): add UserMemoryMapper and UserProfileMapper"
```

---

### Task 1.7：MemoryProperties 配置属性

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/config/MemoryProperties.java`

- [ ] **Step 1: 创建配置属性类**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fitmate.memory")
public class MemoryProperties {
    private boolean enabled = true;
    private int asyncPoolSize = 2;
    private Profile profile = new Profile();
    private Snapshot snapshot = new Snapshot();
    private Extract extract = new Extract();

    @Data
    public static class Profile {
        private int cacheTtlHours = 24;
    }

    @Data
    public static class Snapshot {
        private int windowDays = 14;
        private String cron = "0 0 2 * * *";
    }

    @Data
    public static class Extract {
        private int minConversationTurns = 3;
        private int minConversationChars = 100;
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/config/MemoryProperties.java
git commit -m "feat(memory): add MemoryProperties config binding"
```

---

### Task 1.8：application.yml 新增 memory 配置

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/resources/application.yml`

- [ ] **Step 1: 在 application.yml 的 fitmate 段下新增 memory 配置**

在 `fitmate.agent` 之后、`fitmate.wiki` 之前（保持顶层 section 顺序：server → website → spring → reasoning → rag → internet → fitmate → logging → mybatis-plus）插入：

```yaml
  memory:
    enabled: ${MEMORY_ENABLED:true}
    async-pool-size: ${MEMORY_ASYNC_POOL_SIZE:2}
    profile:
      cache-ttl-hours: ${MEMORY_PROFILE_CACHE_TTL_HOURS:24}
    snapshot:
      window-days: ${MEMORY_SNAPSHOT_WINDOW_DAYS:14}
      cron: ${MEMORY_SNAPSHOT_CRON:0 0 2 * * *}
    extract:
      min-conversation-turns: ${MEMORY_EXTRACT_MIN_TURNS:3}
      min-conversation-chars: ${MEMORY_EXTRACT_MIN_CHARS:100}
```

- [ ] **Step 2: 更新 enabled-tools 增加 memory.search**

将 application.yml 中：
```yaml
    enabled-tools: date.now, kb.search, rag.search, body_metrics.query, training_log.query, web.search, web.fetch
```
改为：
```yaml
    enabled-tools: date.now, kb.search, rag.search, body_metrics.query, training_log.query, web.search, web.fetch, memory.search
```

- [ ] **Step 3: 编译验证**

运行：`cd FitMate-backend && mvn -pl FitMate-api compile -q`
预期：BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/resources/application.yml
git commit -m "feat(memory): add fitmate.memory config and enable memory.search tool"
```

---

### Task 1.9：MemoryAsyncConfig 异步线程池与定时

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/config/MemoryAsyncConfig.java`

- [ ] **Step 1: 创建异步配置类**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class MemoryAsyncConfig {

    @Bean("memoryTaskExecutor")
    public ThreadPoolTaskExecutor memoryTaskExecutor(MemoryProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getAsyncPoolSize());
        executor.setMaxPoolSize(properties.getAsyncPoolSize() * 2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("memory-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 2: 编译验证**

运行：`cd FitMate-backend && mvn -pl FitMate-api compile -q`
预期：BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/config/MemoryAsyncConfig.java
git commit -m "feat(memory): add memoryTaskExecutor and EnableScheduling"
```

---

## 阶段 2：记忆写入核心

### Task 2.1：MemoryWriter 写入收口服务

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/application/MemoryWriter.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/memory/longterm/application/MemoryWriterTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MemoryWriterTest {

    private UserMemoryMapper mapper;
    private MemoryWriter writer;

    @BeforeEach
    void setUp() {
        mapper = mock(UserMemoryMapper.class);
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        writer = new MemoryWriter(mapper);
    }

    @Test
    void writeMemory_newContent_insertsActive() {
        MemoryWriteRequest req = MemoryWriteRequest.builder()
                .userId(1L)
                .memoryType("FACT")
                .content("用户目标是减脂到15%体脂")
                .metadataJson("{\"category\":\"goal\"}")
                .source("session:100")
                .build();

        writer.writeIfNotIgnored(req);

        ArgumentCaptor<UserMemory> captor = ArgumentCaptor.forClass(UserMemory.class);
        verify(mapper).insert(captor.capture());
        UserMemory saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals("FACT", saved.getMemoryType());
        assertEquals("用户目标是减脂到15%体脂", saved.getContent());
        assertEquals("active", saved.getStatus());
        assertNotNull(saved.getContentHash());
        assertEquals(64, saved.getContentHash().length()); // SHA-256 hex
    }

    @Test
    void writeMemory_contentHashIgnored_skips() {
        UserMemory existing = new UserMemory();
        existing.setContentHash("abc123");
        existing.setStatus("ignored");
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(existing));

        MemoryWriteRequest req = MemoryWriteRequest.builder()
                .userId(1L)
                .memoryType("FACT")
                .content("test")
                .source("session:1")
                .build();
        // 设置相同 hash
        req.setContentHash("abc123");

        writer.writeIfNotIgnored(req);

        verify(mapper, never()).insert(any());
    }

    @Test
    void writeMemory_contentHashActive_skips() {
        UserMemory existing = new UserMemory();
        existing.setContentHash("abc123");
        existing.setStatus("active");
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(existing));

        MemoryWriteRequest req = MemoryWriteRequest.builder()
                .userId(1L)
                .memoryType("FACT")
                .content("test")
                .source("session:1")
                .build();
        req.setContentHash("abc123");

        writer.writeIfNotIgnored(req);

        verify(mapper, never()).insert(any());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

运行：`cd FitMate-backend && mvn -pl FitMate-api test -Dtest=MemoryWriterTest -q`
预期：编译失败（MemoryWriter 和 MemoryWriteRequest 不存在）

- [ ] **Step 3: 创建 MemoryWriteRequest DTO**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.application;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemoryWriteRequest {
    private Long userId;
    private String memoryType;
    private String content;
    private String metadataJson;
    private String source;
    private String contentHash;

    public String computeHash() {
        if (contentHash == null && content != null) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) {
                    sb.append(String.format("%02x", b));
                }
                contentHash = sb.toString();
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        return contentHash;
    }
}
```

- [ ] **Step 4: 创建 MemoryWriter**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryWriter {

    private final UserMemoryMapper memoryMapper;

    /**
     * 写入记忆：若相同 content_hash 已存在（active 或 ignored）则跳过。
     * @return true 表示新写入，false 表示跳过
     */
    public boolean writeIfNotIgnored(MemoryWriteRequest req) {
        String hash = req.computeHash();

        // 查询是否已存在相同 hash
        List<UserMemory> existing = memoryMapper.selectList(new LambdaQueryWrapper<UserMemory>()
                .eq(UserMemory::getUserId, req.getUserId())
                .eq(UserMemory::getContentHash, hash));
        if (!existing.isEmpty()) {
            log.debug("记忆已存在（hash={}），跳过写入", hash);
            return false;
        }

        UserMemory entity = new UserMemory();
        entity.setUserId(req.getUserId());
        entity.setMemoryType(req.getMemoryType());
        entity.setContent(req.getContent());
        entity.setMetadataJson(req.getMetadataJson());
        entity.setSource(req.getSource());
        entity.setContentHash(hash);
        entity.setStatus("active");
        memoryMapper.insert(entity);
        log.info("写入记忆 userId={} type={} source={}", req.getUserId(), req.getMemoryType(), req.getSource());
        return true;
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

运行：`cd FitMate-backend && mvn -pl FitMate-api test -Dtest=MemoryWriterTest -q`
预期：3 个测试全部 PASS

- [ ] **Step 6: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/application/MemoryWriter.java FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/application/MemoryWriteRequest.java FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/memory/longterm/application/MemoryWriterTest.java
git commit -m "feat(memory): add MemoryWriter with dedup by content_hash"
```

---

### Task 2.2：memory-extract.md prompt 模板

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/resources/prompts/memory-extract.md`

- [ ] **Step 1: 创建提取 prompt 模板**

```markdown
# 用户记忆提取

你是用户画像分析助手。你的任务是从下面的对话中提取值得长期记住的用户信息。

## 提取类型

- **FACT**：用户明确表达的稳定事实。包括：
  - 训练目标（如"目标减脂到15%体脂"、"想增加卧推重量"）
  - 身体条件（如年龄/性别/身高/体重/伤病史："有腰椎间盘突出"、"膝盖术后3个月"）
  - 饮食偏好（如"低 FODMAP 饮食"、"素食主义"）
  - 训练历史与经验（如"训练5年"、"曾练过力量举"）
  - 个人条件与偏好（如"每周只能练3次"、"偏好早晨训练"）

- **EPISODIC**：带时间戳的关键决策点或事件。包括：
  - 训练计划调整（如"本周改为推拉腿分化"、"暂停深蹲因为腰伤"）
  - 重要里程碑（如"卧推突破100kg"）
  - 明确的决策转折点

- **INSIGHT**：你在对话中得出的分析结论。包括：
  - 训练风格判断（如"该用户适合高频率低容量训练"）
  - 恢复能力评估（如"该用户恢复能力偏弱，需要更多休息日"）
  - 个性化建议依据

## 不提取的内容

- 客套话、寒暄
- 临时性、一次性的需求（如"帮我看看今天的训练"）
- 通用健身知识（如"蛋白质摄入量建议1.6g/kg"）
- 已在记忆中重复的信息
- 模糊、不确定的信息

## 输出格式（严格 JSON，不要 markdown 代码块）

```json
{
  "memories": [
    {
      "type": "FACT",
      "content": "用户目标减脂到15%体脂",
      "metadata": {"category": "goal", "tags": ["减脂"]}
    },
    {
      "type": "EPISODIC",
      "content": "本周改为推拉腿分化训练",
      "metadata": {"occurred_at": "2026-07-03", "tags": ["训练计划调整"], "importance": "high"}
    }
  ]
}
```

无值得提取的内容时返回：`{"memories": []}`

## 对话内容

{conversation}
```

- [ ] **Step 2: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/resources/prompts/memory-extract.md
git commit -m "feat(memory): add memory-extract prompt template"
```

---

### Task 2.3：PromptTemplateManager 新增 buildMemoryExtractPrompt

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/prompt/PromptTemplateManager.java`

- [ ] **Step 1: 读取现有 PromptTemplateManager 了解模式**

先读取 `PromptTemplateManager.java` 全文，理解现有 buildXxxPrompt 方法的实现模式（特别是 buildWikiCompilePrompt 的实现）。

- [ ] **Step 2: 新增 buildMemoryExtractPrompt 方法**

在 PromptTemplateManager 中新增：

```java
private static final String MEMORY_EXTRACT_TEMPLATE = loadTemplate("prompts/memory-extract.md");

public String buildMemoryExtractPrompt(String conversation) {
    return MEMORY_EXTRACT_TEMPLATE.replace("{conversation}", conversation);
}
```

注：`loadTemplate` 是现有方法（如不存在，参考现有 buildWikiCompilePrompt 的加载方式调整）。

- [ ] **Step 3: 编译验证**

运行：`cd FitMate-backend && mvn -pl FitMate-api compile -q`
预期：BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/prompt/PromptTemplateManager.java
git commit -m "feat(memory): add buildMemoryExtractPrompt to PromptTemplateManager"
```

---

### Task 2.4：MemoryExtractResult DTO

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/application/extractor/MemoryExtractResult.java`

- [ ] **Step 1: 创建 DTO**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.application.extractor;

import java.util.List;
import lombok.Data;

@Data
public class MemoryExtractResult {
    private List<ExtractedMemory> memories;

    @Data
    public static class ExtractedMemory {
        private String type;         // FACT|EPISODIC|INSIGHT
        private String content;
        private Object metadata;     // 将被序列化为 JSON 字符串存储
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/application/extractor/MemoryExtractResult.java
git commit -m "feat(memory): add MemoryExtractResult DTO"
```

---

### Task 2.5：SessionMemoryExtractor 会话后提取

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/application/extractor/SessionMemoryExtractor.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/memory/longterm/application/extractor/SessionMemoryExtractorTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.application.extractor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriter;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriteRequest;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;

class SessionMemoryExtractorTest {

    private ChatModel chatModel;
    private PromptTemplateManager promptTemplateManager;
    private MemoryWriter memoryWriter;
    private MemoryProperties properties;
    private SessionMemoryExtractor extractor;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        promptTemplateManager = mock(PromptTemplateManager.class);
        memoryWriter = mock(MemoryWriter.class);
        properties = new MemoryProperties();
        when(promptTemplateManager.buildMemoryExtractPrompt(any())).thenReturn("prompt");
        extractor = new SessionMemoryExtractor(chatModel, promptTemplateManager, memoryWriter, properties);
    }

    @Test
    void extract_shortConversation_skips() {
        // 2 轮对话，应跳过
        List<Map<String, String>> conversation = List.of(
                Map.of("role", "user", "content", "你好"),
                Map.of("role", "assistant", "content", "你好！有什么可以帮你的？"));

        extractor.extract(1L, 100L, conversation);

        verify(chatModel, never()).call(any(Prompt.class));
        verify(memoryWriter, never()).writeIfNotIgnored(any());
    }

    @Test
    void extract_validConversation_noMemoriesReturned_writesNothing() {
        List<Map<String, String>> conversation = List.of(
                Map.of("role", "user", "content", "我想咨询一下训练计划，我是个新手，想增肌，身高180体重70kg，每周能练4次"),
                Map.of("role", "assistant", "content", "好的，根据你的情况，我建议..."),
                Map.of("role", "user", "content", "明白了，谢谢"),
                Map.of("role", "assistant", "content", "不客气，有问题随时找我"));

        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse("{\"memories\": []}"));

        extractor.extract(1L, 100L, conversation);

        verify(memoryWriter, never()).writeIfNotIgnored(any());
    }

    @Test
    void extract_validConversation_memoriesReturned_writesEach() {
        List<Map<String, String>> conversation = List.of(
                Map.of("role", "user", "content", "我身高180体重70kg，目标是增肌到80kg，每周能练4次，有轻微腰椎间盘突出"),
                Map.of("role", "assistant", "content", "了解你的情况。根据你的腰椎问题，我建议避免大重量深蹲..."),
                Map.of("role", "user", "content", "好的，那我应该怎么调整训练？"),
                Map.of("role", "assistant", "content", "建议改为推拉腿分化，腿部以罗马尼亚硬拉和腿弯举为主..."));

        String llmOutput = "{\"memories\":[{\"type\":\"FACT\",\"content\":\"用户身高180cm体重70kg，目标增肌到80kg\",\"metadata\":{\"category\":\"body_condition\",\"tags\":[\"增肌\",\"身高180\"]}},{\"type\":\"FACT\",\"content\":\"用户有轻微腰椎间盘突出\",\"metadata\":{\"category\":\"condition\",\"tags\":[\"腰椎间盘突出\"]}},{\"type\":\"INSIGHT\",\"content\":\"该用户适合推拉腿分化训练，腿部避免大重量深蹲\",\"metadata\":{\"category\":\"training_style\",\"tags\":[\"推拉腿分化\"],\"confidence\":0.85}}]}";
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse(llmOutput));
        when(memoryWriter.writeIfNotIgnored(any())).thenReturn(true);

        extractor.extract(1L, 100L, conversation);

        ArgumentCaptor<MemoryWriteRequest> captor = ArgumentCaptor.forClass(MemoryWriteRequest.class);
        verify(memoryWriter, times(3)).writeIfNotIgnored(captor.capture());
        List<MemoryWriteRequest> requests = captor.getAllValues();
        assertEquals("FACT", requests.get(0).getMemoryType());
        assertEquals("session:100", requests.get(0).getSource());
        assertEquals("INSIGHT", requests.get(2).getMemoryType());
    }

    @Test
    void extract_llmReturnsInvalidJson_writesNothing_noException() {
        List<Map<String, String>> conversation = List.of(
                Map.of("role", "user", "content", "我身高180体重70kg，目标是增肌到80kg，每周能练4次，有轻微腰椎间盘突出"),
                Map.of("role", "assistant", "content", "好的..."),
                Map.of("role", "user", "content", "怎么调整？"),
                Map.of("role", "assistant", "content", "建议..."));

        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse("invalid json"));

        // 不应抛异常
        assertDoesNotThrow(() -> extractor.extract(1L, 100L, conversation));
        verify(memoryWriter, never()).writeIfNotIgnored(any());
    }

    private ChatResponse mockChatResponse(String text) {
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage message = new AssistantMessage(text);
        when(generation.getOutput()).thenReturn(message);
        when(response.getResult()).thenReturn(generation);
        return response;
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

运行：`cd FitMate-backend && mvn -pl FitMate-api test -Dtest=SessionMemoryExtractorTest -q`
预期：编译失败（SessionMemoryExtractor 不存在）

- [ ] **Step 3: 创建 SessionMemoryExtractor**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.application.extractor;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriter;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriteRequest;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionMemoryExtractor {

    private final ChatModel chatModel;
    private final PromptTemplateManager promptTemplateManager;
    private final MemoryWriter memoryWriter;
    private final MemoryProperties properties;

    @Async("memoryTaskExecutor")
    public void extract(Long userId, Long sessionId, List<Map<String, String>> conversation) {
        if (!properties.isEnabled()) {
            return;
        }

        // 过短对话过滤
        int turns = conversation.size();
        int chars = conversation.stream().mapToInt(m -> m.getOrDefault("content", "").length()).sum();
        if (turns < properties.getExtract().getMinConversationTurns()
                || chars < properties.getExtract().getMinConversationChars()) {
            log.debug("会话过短（turns={}, chars={}），跳过记忆提取", turns, chars);
            return;
        }

        // 构建 conversation 文本
        String conversationText = conversation.stream()
                .map(m -> m.get("role") + ": " + m.get("content"))
                .collect(Collectors.joining("\n"));

        // LLM 提取
        String promptText = promptTemplateManager.buildMemoryExtractPrompt(conversationText);
        String llmOutput;
        try {
            llmOutput = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("会话记忆提取 LLM 调用失败 userId={} sessionId={}", userId, sessionId, e);
            return;
        }

        // 解析 JSON
        List<MemoryExtractResult.ExtractedMemory> memories;
        try {
            JSONObject json = JSONUtil.parseObj(llmOutput);
            memories = json.getBeanList("memories", MemoryExtractResult.ExtractedMemory.class);
        } catch (Exception e) {
            log.warn("会话记忆提取 JSON 解析失败 userId={} sessionId={} output={}", userId, sessionId, llmOutput, e);
            return;
        }

        // 写入
        String source = "session:" + sessionId;
        for (MemoryExtractResult.ExtractedMemory m : memories) {
            String metadataJson = m.getMetadata() != null ? JSONUtil.toJsonStr(m.getMetadata()) : null;
            MemoryWriteRequest req = MemoryWriteRequest.builder()
                    .userId(userId)
                    .memoryType(m.getType())
                    .content(m.getContent())
                    .metadataJson(metadataJson)
                    .source(source)
                    .build();
            memoryWriter.writeIfNotIgnored(req);
        }
        log.info("会话记忆提取完成 userId={} sessionId={} 提取 {} 条", userId, sessionId, memories.size());
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

运行：`cd FitMate-backend && mvn -pl FitMate-api test -Dtest=SessionMemoryExtractorTest -q`
预期：4 个测试全部 PASS

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/application/extractor/ FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/memory/longterm/application/extractor/
git commit -m "feat(memory): add SessionMemoryExtractor with LLM-based extraction"
```

---

## 阶段 3：Wiki 编译提取 + 定时快照

### Task 3.1：wiki-schema.md 新增 memory_extraction 字段

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/resources/prompts/wiki-schema.md`

- [ ] **Step 1: 在输出格式部分新增 memory_extraction 字段**

将 wiki-schema.md 的"输出格式（强制 JSON）"部分改为：

```json
{
  "actions": [
    {"action": "create", "page_type": "SOURCE_SUMMARY", "title": "...", "slug": "...", "content_md": "...", "links": ["slug1", "slug2"]},
    {"action": "update", "slug": "existing-slug", "content_md": "..."},
    {"action": "update_index", "content_md": "..."},
    {"action": "append_log", "entry": "## [YYYY-MM-DD] ingest | 文档标题"}
  ],
  "memory_extraction": [
    {"type": "FACT", "content": "用户目标减脂到15%体脂", "metadata": {"category": "goal", "tags": ["减脂"]}}
  ]
}
```

并在输出格式说明后追加：

```markdown
## 记忆提取（可选）

如果原始资料中明确包含用户本人的训练目标、身体条件、饮食偏好、伤病史、训练历史、
或用户明显关注的训练领域，请在 `memory_extraction` 字段中提取为 FACT 类型。

高阈值：仅在明确涉及用户个人化信息时提取，通用健身知识不要提取。
无个人化信息时返回空数组 `[]`。

每条记忆的 metadata 可包含 category（goal/body_condition/diet/injury/training_history/interest）和 tags。
```

- [ ] **Step 2: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/resources/prompts/wiki-schema.md
git commit -m "feat(wiki): add memory_extraction field to compile output schema"
```

---

### Task 3.2：WikiCompileServiceImpl 集成记忆提取

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/impl/WikiCompileServiceImpl.java`

- [ ] **Step 1: 读取 WikiCompileServiceImpl 现有 executeCompile 方法**

读取 `WikiCompileServiceImpl.java` 第 104-162 行，理解 LLM 输出解析和落库流程。

- [ ] **Step 2: 注入 MemoryWriter 和 MemoryProperties 依赖**

在 WikiCompileServiceImpl 类的依赖注入部分新增：

```java
private final com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriter memoryWriter;
private final com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties memoryProperties;
```

（根据现有依赖注入风格调整：若用 @RequiredArgsConstructor 则加 final 字段；若用 @Autowired 则加 setter）

- [ ] **Step 3: 在 executeCompile 方法中编译成功后添加记忆提取**

在 `executeCompile` 方法中，`job.setStatus("SUCCESS")` 之前，添加：

```java
// 记忆提取（仅 USER scope）
try {
    if ("USER".equals(space.getScopeType()) && memoryProperties.isEnabled()) {
        extractWikiMemories(llmOutput, space.getOwnerUserId(), sourceDocId);
    }
} catch (Exception e) {
    log.warn("Wiki 记忆提取失败 docId={} userId={}", sourceDocId, space.getOwnerUserId(), e);
}
```

并新增私有方法：

```java
private void extractWikiMemories(String llmOutput, Long userId, Long sourceDocId) {
    cn.hutool.json.JSONObject json;
    try {
        json = cn.hutool.json.JSONUtil.parseObj(llmOutput);
    } catch (Exception e) {
        return;
    }
    cn.hutool.json.JSONArray extractions = json.getJSONArray("memory_extraction");
    if (extractions == null || extractions.isEmpty()) {
        return;
    }
    String source = "wiki_compile:" + sourceDocId;
    for (Object item : extractions) {
        cn.hutool.json.JSONObject m = (cn.hutool.json.JSONObject) item;
        String content = m.getStr("content");
        if (content == null || content.isBlank()) continue;
        Object metadata = m.get("metadata");
        com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriteRequest req =
                com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriteRequest.builder()
                        .userId(userId)
                        .memoryType("FACT")
                        .content(content)
                        .metadataJson(metadata != null ? cn.hutool.json.JSONUtil.toJsonStr(metadata) : null)
                        .source(source)
                        .build();
        memoryWriter.writeIfNotIgnored(req);
    }
    log.info("Wiki 记忆提取完成 docId={} userId={} 提取 {} 条", sourceDocId, userId, extractions.size());
}
```

- [ ] **Step 4: 编译验证**

运行：`cd FitMate-backend && mvn -pl FitMate-api compile -q`
预期：BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/impl/WikiCompileServiceImpl.java
git commit -m "feat(wiki): extract memories during USER-scope wiki compilation"
```

---

### Task 3.3：SnapshotAggregator 定时快照聚合

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/application/scheduler/SnapshotAggregator.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/memory/longterm/application/scheduler/SnapshotAggregatorTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.application.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriter;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriteRequest;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity.BodyMetrics;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.mapper.BodyMetricsMapper;
import com.itgeo.fitmate.api.fitness.training.infrastructure.entity.TrainingLog;
import com.itgeo.fitmate.api.fitness.training.infrastructure.mapper.TrainingLogMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SnapshotAggregatorTest {

    private TrainingLogMapper trainingLogMapper;
    private BodyMetricsMapper bodyMetricsMapper;
    private UserMemoryMapper userMemoryMapper;
    private MemoryWriter memoryWriter;
    private MemoryProperties properties;
    private SnapshotAggregator aggregator;

    @BeforeEach
    void setUp() {
        trainingLogMapper = mock(TrainingLogMapper.class);
        bodyMetricsMapper = mock(BodyMetricsMapper.class);
        userMemoryMapper = mock(UserMemoryMapper.class);
        memoryWriter = mock(MemoryWriter.class);
        properties = new MemoryProperties();
        aggregator = new SnapshotAggregator(trainingLogMapper, bodyMetricsMapper, userMemoryMapper, memoryWriter, properties);
    }

    @Test
    void aggregateForUser_noData_writesNothing() {
        when(trainingLogMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(bodyMetricsMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userMemoryMapper.selectList(any())).thenReturn(Collections.emptyList());

        aggregator.aggregateForUser(1L);

        verify(memoryWriter, never()).writeIfNotIgnored(any());
    }

    @Test
    void aggregateForUser_hasTrainingData_writesSnapshot() {
        TrainingLog log1 = new TrainingLog();
        log1.setTrainingDate(LocalDate.now().minusDays(3));
        log1.setTotalVolume(8000);
        log1.setPrimaryMuscleGroup("胸");
        TrainingLog log2 = new TrainingLog();
        log2.setTrainingDate(LocalDate.now().minusDays(1));
        log2.setTotalVolume(10000);
        log2.setPrimaryMuscleGroup("背");
        when(trainingLogMapper.selectList(any())).thenReturn(List.of(log1, log2));
        when(bodyMetricsMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userMemoryMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(memoryWriter.writeIfNotIgnored(any())).thenReturn(true);

        aggregator.aggregateForUser(1L);

        ArgumentCaptor<MemoryWriteRequest> captor = ArgumentCaptor.forClass(MemoryWriteRequest.class);
        verify(memoryWriter).writeIfNotIgnored(captor.capture());
        MemoryWriteRequest req = captor.getValue();
        assertEquals("SNAPSHOT", req.getMemoryType());
        assertEquals("schedule", req.getSource());
        assertTrue(req.getContent().contains("训练2次"));
        assertTrue(req.getContent().contains("18000"));
    }

    @Test
    void aggregateForUser_existingSnapshot_archivesOld() {
        TrainingLog log1 = new TrainingLog();
        log1.setTrainingDate(LocalDate.now().minusDays(1));
        log1.setTotalVolume(5000);
        log1.setPrimaryMuscleGroup("腿");
        when(trainingLogMapper.selectList(any())).thenReturn(List.of(log1));
        when(bodyMetricsMapper.selectList(any())).thenReturn(Collections.emptyList());

        UserMemory oldSnapshot = new UserMemory();
        oldSnapshot.setId(99L);
        oldSnapshot.setMemoryType("SNAPSHOT");
        oldSnapshot.setStatus("active");
        when(userMemoryMapper.selectList(any())).thenReturn(List.of(oldSnapshot));
        when(memoryWriter.writeIfNotIgnored(any())).thenReturn(true);

        aggregator.aggregateForUser(1L);

        // 旧的应被归档
        ArgumentCaptor<UserMemory> updateCaptor = ArgumentCaptor.forClass(UserMemory.class);
        verify(userMemoryMapper).updateById(updateCaptor.capture());
        assertEquals("archived", updateCaptor.getValue().getStatus());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

运行：`cd FitMate-backend && mvn -pl FitMate-api test -Dtest=SnapshotAggregatorTest -q`
预期：编译失败

- [ ] **Step 3: 创建 SnapshotAggregator**

先确认 `TrainingLogMapper`、`BodyMetricsMapper`、`TrainingLog`、`BodyMetrics` 的包路径和字段名（通过读取现有实体）。

```java
package com.itgeo.fitmate.api.agent.memory.longterm.application.scheduler;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriter;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriteRequest;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity.BodyMetrics;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.mapper.BodyMetricsMapper;
import com.itgeo.fitmate.api.fitness.training.infrastructure.entity.TrainingLog;
import com.itgeo.fitmate.api.fitness.training.infrastructure.mapper.TrainingLogMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotAggregator {

    private final TrainingLogMapper trainingLogMapper;
    private final BodyMetricsMapper bodyMetricsMapper;
    private final UserMemoryMapper userMemoryMapper;
    private final MemoryWriter memoryWriter;
    private final MemoryProperties properties;

    @Scheduled(cron = "${fitmate.memory.snapshot.cron:0 0 2 * * *}")
    public void aggregateSnapshots() {
        if (!properties.isEnabled()) {
            return;
        }
        log.info("开始定时快照聚合");
        // 查询所有有训练/体测记录的用户
        // 简化实现：分别 distinct user_id from 两张表后合并
        // 注意：此处用 selectList + 手动 distinct，避免跨表 SQL
        List<Long> trainingUsers = trainingLogMapper.selectList(null).stream()
                .map(TrainingLog::getUserId).distinct().collect(Collectors.toList());
        List<Long> metricsUsers = bodyMetricsMapper.selectList(null).stream()
                .map(BodyMetrics::getUserId).distinct().collect(Collectors.toList());
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        userIds.addAll(trainingUsers);
        userIds.addAll(metricsUsers);

        for (Long userId : userIds) {
            try {
                aggregateForUser(userId);
            } catch (Exception e) {
                log.error("用户快照聚合失败 userId={}", userId, e);
            }
        }
        log.info("定时快照聚合完成，处理 {} 个用户", userIds.size());
    }

    public void aggregateForUser(Long userId) {
        int windowDays = properties.getSnapshot().getWindowDays();
        LocalDate from = LocalDate.now().minusDays(windowDays);
        LocalDateTime fromTime = from.atStartOfDay();

        // 查询最近 N 天训练记录
        List<TrainingLog> trainingLogs = trainingLogMapper.selectList(new LambdaQueryWrapper<TrainingLog>()
                .eq(TrainingLog::getUserId, userId)
                .ge(TrainingLog::getTrainingDate, from));

        // 查询最近 N 天体测记录
        List<BodyMetrics> metrics = bodyMetricsMapper.selectList(new LambdaQueryWrapper<BodyMetrics>()
                .eq(BodyMetrics::getUserId, userId)
                .ge(BodyMetrics::getRecordDate, from));

        if (trainingLogs.isEmpty() && metrics.isEmpty()) {
            return;
        }

        // 聚合
        int trainingDays = trainingLogs.size();
        int totalVolume = trainingLogs.stream().mapToInt(TrainingLog::getTotalVolume).sum();
        String muscleGroups = trainingLogs.stream()
                .map(TrainingLog::getPrimaryMuscleGroup)
                .filter(g -> g != null && !g.isBlank())
                .distinct().collect(Collectors.joining("/"));

        Double avgWeight = metrics.stream()
                .map(BodyMetrics::getWeight)
                .filter(w -> w != null && w > 0)
                .collect(Collectors.averagingDouble(Double::doubleValue));
        Double firstWeight = metrics.stream()
                .filter(m -> m.getWeight() != null && m.getWeight() > 0)
                .findFirst().map(BodyMetrics::getWeight).orElse(null);
        Double lastWeight = metrics.stream()
                .filter(m -> m.getWeight() != null && m.getWeight() > 0)
                .reduce((a, b) -> b).map(BodyMetrics::getWeight).orElse(null);
        double weightChange = (firstWeight != null && lastWeight != null) ? lastWeight - firstWeight : 0;

        String fatigueLevel = metrics.stream()
                .map(BodyMetrics::getFatigueLevel)
                .filter(f -> f != null && !f.isBlank())
                .collect(Collectors.groupingBy(f -> f, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("未知");

        // 生成自然语言摘要
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("近%d天训练%d次", windowDays, trainingDays));
        if (totalVolume > 0) {
            sb.append(String.format("，总训练量%dkg", totalVolume));
        }
        if (avgWeight > 0) {
            sb.append(String.format("，平均体重%.1fkg", avgWeight));
            if (Math.abs(weightChange) > 0.01) {
                sb.append(String.format("（%s%.1fkg）", weightChange > 0 ? "上升" : "下降", Math.abs(weightChange)));
            }
        }
        sb.append("，疲劳水平").append(fatigueLevel);
        if (!muscleGroups.isEmpty()) {
            sb.append("，主要训练").append(muscleGroups);
        }
        String content = sb.toString();

        // metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("period", from + "~" + LocalDate.now());
        Map<String, Object> metricsData = new HashMap<>();
        metricsData.put("training_days", trainingDays);
        metricsData.put("total_volume", totalVolume);
        if (avgWeight > 0) metricsData.put("avg_weight", Math.round(avgWeight * 10) / 10.0);
        if (weightChange != 0) metricsData.put("weight_change", Math.round(weightChange * 10) / 10.0);
        metadata.put("metrics", metricsData);

        // 归档旧 SNAPSHOT
        List<UserMemory> oldSnapshots = userMemoryMapper.selectList(new LambdaQueryWrapper<UserMemory>()
                .eq(UserMemory::getUserId, userId)
                .eq(UserMemory::getMemoryType, "SNAPSHOT")
                .eq(UserMemory::getStatus, "active"));
        for (UserMemory old : oldSnapshots) {
            old.setStatus("archived");
            old.setExpiredAt(LocalDateTime.now());
            userMemoryMapper.updateById(old);
        }

        // 写入新 SNAPSHOT
        MemoryWriteRequest req = MemoryWriteRequest.builder()
                .userId(userId)
                .memoryType("SNAPSHOT")
                .content(content)
                .metadataJson(JSONUtil.toJsonStr(metadata))
                .source("schedule")
                .build();
        memoryWriter.writeIfNotIgnored(req);
        log.info("用户快照聚合完成 userId={} content={}", userId, content);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

运行：`cd FitMate-backend && mvn -pl FitMate-api test -Dtest=SnapshotAggregatorTest -q`
预期：3 个测试全部 PASS

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/application/scheduler/ FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/memory/longterm/application/scheduler/
git commit -m "feat(memory): add SnapshotAggregator scheduled task for rolling metrics"
```

---

## 阶段 4：画像生成与 Agent 注入

### Task 4.1：profile-build.md prompt 模板

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/resources/prompts/profile-build.md`

- [ ] **Step 1: 创建画像生成 prompt 模板**

```markdown
# 用户画像生成

你是用户画像生成助手。基于用户的长期记忆，生成简洁的用户画像。

## 输入

以下是用户的长期记忆，按类型分组：

### 稳定事实（FACT）
{facts}

### 近期事件（EPISODIC，最近 5 条）
{episodics}

### 近期状态快照（SNAPSHOT，最新 1 条）
{snapshot}

### 洞察（INSIGHT）
{insights}

## 输出要求

生成：
1. `profile_text`：100-200 字的自然语言画像，涵盖用户的身份、目标、身体条件、偏好、近期状态
2. `tags`：5-8 个关键标签，每个包含：
   - `label`：标签文本（简洁，如"力量举训练者"、"减脂期"）
   - `weight`：0-1 之间的权重，反映确定性/重要性
   - `category`：类别，取值之一：identity（身份）/ goal（目标）/ condition（身体条件）/ preference（偏好）/ status（近期状态）

## 输出格式（严格 JSON，不要 markdown 代码块）

```json
{
  "profile_text": "28岁男性，力量举训练者，目标减脂到15%体脂。有腰椎间盘突出史，适合推拉腿分化训练。近14天训练5次，疲劳水平中等偏高...",
  "tags": [
    {"label": "力量举训练者", "weight": 0.95, "category": "identity"},
    {"label": "减脂期", "weight": 0.80, "category": "goal"},
    {"label": "腰椎间盘突出", "weight": 0.90, "category": "condition"}
  ]
}
```
```

- [ ] **Step 2: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/resources/prompts/profile-build.md
git commit -m "feat(memory): add profile-build prompt template"
```

---

### Task 4.2：PromptTemplateManager 新增 buildProfileBuildPrompt

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/prompt/PromptTemplateManager.java`

- [ ] **Step 1: 新增 buildProfileBuildPrompt 方法**

```java
private static final String PROFILE_BUILD_TEMPLATE = loadTemplate("prompts/profile-build.md");

public String buildProfileBuildPrompt(String facts, String episodics, String snapshot, String insights) {
    return PROFILE_BUILD_TEMPLATE
            .replace("{facts}", facts == null ? "无" : facts)
            .replace("{episodics}", episodics == null ? "无" : episodics)
            .replace("{snapshot}", snapshot == null ? "无" : snapshot)
            .replace("{insights}", insights == null ? "无" : insights);
}
```

- [ ] **Step 2: 编译验证**

运行：`cd FitMate-backend && mvn -pl FitMate-api compile -q`
预期：BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/prompt/PromptTemplateManager.java
git commit -m "feat(memory): add buildProfileBuildPrompt to PromptTemplateManager"
```

---

### Task 4.3：ProfileBuilder 画像生成器

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/application/ProfileBuilder.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/memory/longterm/application/ProfileBuilderTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.memory.longterm.application.ProfileBuilder;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserProfile;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserProfileMapper;
import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;

class ProfileBuilderTest {

    private UserMemoryMapper memoryMapper;
    private UserProfileMapper profileMapper;
    private ChatModel chatModel;
    private PromptTemplateManager promptTemplateManager;
    private MemoryProperties properties;
    private ProfileBuilder builder;

    @BeforeEach
    void setUp() {
        memoryMapper = mock(UserMemoryMapper.class);
        profileMapper = mock(UserProfileMapper.class);
        chatModel = mock(ChatModel.class);
        promptTemplateManager = mock(PromptTemplateManager.class);
        properties = new MemoryProperties();
        when(promptTemplateManager.buildProfileBuildPrompt(any(), any(), any(), any())).thenReturn("prompt");
        builder = new ProfileBuilder(memoryMapper, profileMapper, chatModel, promptTemplateManager, properties);
    }

    @Test
    void rebuild_noMemories_skips() {
        when(memoryMapper.selectList(any())).thenReturn(Collections.emptyList());

        builder.rebuild(1L);

        verify(chatModel, never()).call(any(Prompt.class));
        verify(profileMapper, never()).insert(any());
        verify(profileMapper, never()).updateById(any());
    }

    @Test
    void rebuild_hasMemories_generatesProfile() {
        UserMemory fact = new UserMemory();
        fact.setMemoryType("FACT");
        fact.setContent("用户身高180cm，目标增肌");
        fact.setStatus("active");
        when(memoryMapper.selectList(any())).thenReturn(List.of(fact));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse(
                "{\"profile_text\":\"28岁男性，目标增肌\",\"tags\":[{\"label\":\"增肌期\",\"weight\":0.9,\"category\":\"goal\"}]}"));
        when(profileMapper.selectOne(any())).thenReturn(null);

        builder.rebuild(1L);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileMapper).insert(captor.capture());
        UserProfile saved = captor.getValue();
        assertTrue(saved.getProfileText().contains("增肌"));
        assertNotNull(saved.getProfileTagsJson());
    }

    @Test
    void rebuild_existingProfile_updates() {
        UserMemory fact = new UserMemory();
        fact.setMemoryType("FACT");
        fact.setContent("用户身高180cm");
        fact.setStatus("active");
        when(memoryMapper.selectList(any())).thenReturn(List.of(fact));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse(
                "{\"profile_text\":\"更新后的画像\",\"tags\":[]}"));

        UserProfile existing = new UserProfile();
        existing.setId(5L);
        existing.setUserId(1L);
        when(profileMapper.selectOne(any())).thenReturn(existing);

        builder.rebuild(1L);

        verify(profileMapper).updateById(any(UserProfile.class));
        verify(profileMapper, never()).insert(any());
    }

    private ChatResponse mockChatResponse(String text) {
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage message = new AssistantMessage(text);
        when(generation.getOutput()).thenReturn(message);
        when(response.getResult()).thenReturn(generation);
        return response;
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

运行：`cd FitMate-backend && mvn -pl FitMate-api test -Dtest=ProfileBuilderTest -q`
预期：编译失败

- [ ] **Step 3: 创建 ProfileBuilder**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.application;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserProfile;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserProfileMapper;
import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileBuilder {

    private final UserMemoryMapper memoryMapper;
    private final UserProfileMapper profileMapper;
    private final ChatModel chatModel;
    private final PromptTemplateManager promptTemplateManager;
    private final MemoryProperties properties;

    @Async("memoryTaskExecutor")
    public void asyncRebuild(Long userId) {
        rebuild(userId);
    }

    public void rebuild(Long userId) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            doRebuild(userId);
        } catch (Exception e) {
            log.error("画像重建失败 userId={}", userId, e);
        }
    }

    private void doRebuild(Long userId) {
        // 查询全部 active 记忆
        List<UserMemory> allMemories = memoryMapper.selectList(new LambdaQueryWrapper<UserMemory>()
                .eq(UserMemory::getUserId, userId)
                .eq(UserMemory::getStatus, "active"));

        if (allMemories.isEmpty()) {
            log.debug("用户无记忆，跳过画像生成 userId={}", userId);
            return;
        }

        // 按类型分组
        String facts = allMemories.stream()
                .filter(m -> "FACT".equals(m.getMemoryType()))
                .map(UserMemory::getContent)
                .collect(Collectors.joining("\n"));
        String episodics = allMemories.stream()
                .filter(m -> "EPISODIC".equals(m.getMemoryType()))
                .sorted(Comparator.comparing(UserMemory::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(UserMemory::getContent)
                .collect(Collectors.joining("\n"));
        String snapshot = allMemories.stream()
                .filter(m -> "SNAPSHOT".equals(m.getMemoryType()))
                .sorted(Comparator.comparing(UserMemory::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(1)
                .map(UserMemory::getContent)
                .findFirst().orElse(null);
        String insights = allMemories.stream()
                .filter(m -> "INSIGHT".equals(m.getMemoryType()))
                .map(UserMemory::getContent)
                .collect(Collectors.joining("\n"));

        // LLM 生成
        String promptText = promptTemplateManager.buildProfileBuildPrompt(facts, episodics, snapshot, insights);
        String llmOutput = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();

        // 解析
        JSONObject json = JSONUtil.parseObj(llmOutput);
        String profileText = json.getStr("profile_text");
        String tagsJson = json.getJSONArray("tags") != null ? json.getJSONArray("tags").toString() : null;

        // 计算版本号
        int memoryVersion = allMemories.stream()
                .mapToInt(m -> m.getId() != null ? m.getId().intValue() : 0)
                .max().orElse(0);

        // upsert
        UserProfile existing = profileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
        if (existing == null) {
            UserProfile profile = new UserProfile();
            profile.setUserId(userId);
            profile.setProfileText(profileText);
            profile.setProfileTagsJson(tagsJson);
            profile.setMemoryVersion(memoryVersion);
            profileMapper.insert(profile);
        } else {
            existing.setProfileText(profileText);
            existing.setProfileTagsJson(tagsJson);
            existing.setMemoryVersion(memoryVersion);
            profileMapper.updateById(existing);
        }
        log.info("画像重建完成 userId={}", userId);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

运行：`cd FitMate-backend && mvn -pl FitMate-api test -Dtest=ProfileBuilderTest -q`
预期：3 个测试全部 PASS

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/application/ProfileBuilder.java FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/memory/longterm/application/ProfileBuilderTest.java
git commit -m "feat(memory): add ProfileBuilder for LLM-based profile generation"
```

---

### Task 4.4：在写入流程中触发画像重建

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/application/MemoryWriter.java`
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/application/extractor/SessionMemoryExtractor.java`
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/application/scheduler/SnapshotAggregator.java`
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/impl/WikiCompileServiceImpl.java`

- [ ] **Step 1: 在 MemoryWriter 中注入 ProfileBuilder 并在新写入后触发重建**

修改 MemoryWriter，增加 ProfileBuilder 依赖：

```java
private final ProfileBuilder profileBuilder;
```

修改 `writeIfNotIgnored` 方法，在 `return true` 之前添加：
```java
profileBuilder.asyncRebuild(req.getUserId());
```

注意：由于 ProfileBuilder 也有 @Async 方法，为避免循环依赖，MemoryWriter 注入 ProfileBuilder 时使用 @Lazy 或确保 Spring 能处理。

- [ ] **Step 2: 编译验证**

运行：`cd FitMate-backend && mvn -pl FitMate-api compile -q`
预期：BUILD SUCCESS

- [ ] **Step 3: 更新 MemoryWriterTest 的 mock**

在 MemoryWriterTest 的 setUp 中 mock ProfileBuilder：
```java
ProfileBuilder profileBuilder = mock(ProfileBuilder.class);
writer = new MemoryWriter(mapper, profileBuilder);
```
（doNothing 的 mock 对 asyncRebuild 是默认行为，无需额外配置）

- [ ] **Step 4: 运行测试验证**

运行：`cd FitMate-backend && mvn -pl FitMate-api test -Dtest=MemoryWriterTest -q`
预期：3 个测试全部 PASS

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/ FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/memory/longterm/
git commit -m "feat(memory): trigger profile rebuild after memory writes"
```

---

### Task 4.5：MemoryReader 读取服务

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/application/MemoryReader.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/memory/longterm/application/MemoryReaderTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserProfile;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserProfileMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryReaderTest {

    private UserProfileMapper profileMapper;
    private MemoryProperties properties;
    private MemoryReader reader;

    @BeforeEach
    void setUp() {
        profileMapper = mock(UserProfileMapper.class);
        properties = new MemoryProperties();
        reader = new MemoryReader(profileMapper, properties);
    }

    @Test
    void loadProfileSection_disabled_returnsEmpty() {
        properties.setEnabled(false);
        assertEquals("", reader.loadProfileSection(1L));
        verify(profileMapper, never()).selectOne(any());
    }

    @Test
    void loadProfileSection_noProfile_returnsEmpty() {
        when(profileMapper.selectOne(any())).thenReturn(null);
        assertEquals("", reader.loadProfileSection(1L));
    }

    @Test
    void loadProfileSection_expired_returnsEmpty() {
        UserProfile profile = new UserProfile();
        profile.setProfileText("旧画像");
        profile.setUpdatedAt(LocalDateTime.now().minusHours(25));
        when(profileMapper.selectOne(any())).thenReturn(profile);

        assertEquals("", reader.loadProfileSection(1L));
    }

    @Test
    void loadProfileSection_valid_returnsSection() {
        UserProfile profile = new UserProfile();
        profile.setProfileText("28岁男性，力量举训练者");
        profile.setUpdatedAt(LocalDateTime.now().minusHours(2));
        when(profileMapper.selectOne(any())).thenReturn(profile);

        String section = reader.loadProfileSection(1L);
        assertTrue(section.startsWith("## 用户画像"));
        assertTrue(section.contains("28岁男性"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

运行：`cd FitMate-backend && mvn -pl FitMate-api test -Dtest=MemoryReaderTest -q`
预期：编译失败

- [ ] **Step 3: 创建 MemoryReader**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserProfile;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserProfileMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryReader {

    private final UserProfileMapper profileMapper;
    private final MemoryProperties properties;

    /**
     * 加载用户画像区块文本，用于注入 Agent prompt。
     * 返回 "## 用户画像\n{profileText}" 或空串（禁用/无缓存/过期时）。
     */
    public String loadProfileSection(Long userId) {
        if (!properties.isEnabled()) {
            return "";
        }
        UserProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
        if (profile == null || profile.getProfileText() == null) {
            return "";
        }
        // 检查过期
        int ttlHours = properties.getProfile().getCacheTtlHours();
        if (profile.getUpdatedAt() != null
                && profile.getUpdatedAt().isBefore(LocalDateTime.now().minusHours(ttlHours))) {
            log.debug("画像缓存已过期 userId={}", userId);
            return "";
        }
        return "## 用户画像\n" + profile.getProfileText();
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

运行：`cd FitMate-backend && mvn -pl FitMate-api test -Dtest=MemoryReaderTest -q`
预期：4 个测试全部 PASS

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/application/MemoryReader.java FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/memory/longterm/application/MemoryReaderTest.java
git commit -m "feat(memory): add MemoryReader for profile loading with TTL"
```

---

### Task 4.6：AgentPromptBuilder 新增 userProfileSection 参数

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/prompt/AgentPromptBuilder.java`

- [ ] **Step 1: 读取现有 buildDecisionPrompt 方法**

读取 `AgentPromptBuilder.java` 第 36-55 行，理解现有参数和区块拼接逻辑。

- [ ] **Step 2: 新增带 userProfileSection 的重载方法**

在现有 6 参数 `buildDecisionPrompt` 旁边新增 7 参数版本：

```java
public String buildDecisionPrompt(AgentExecuteContext context,
                                   List<Map<String, String>> memory,
                                   List<Map<String, Object>> observations,
                                   List<ToolDescriptor> tools,
                                   String wikiContext,
                                   String summarySection,
                                   String userProfileSection) {
    // 复用现有构建逻辑，在 summarySection 之后插入 userProfileSection
    StringBuilder sb = new StringBuilder();
    // ... 现有逻辑（系统提示词、可用工具、summarySection）...
    // 新增：在 summarySection 之后
    if (userProfileSection != null && !userProfileSection.isBlank()) {
        sb.append(userProfileSection).append("\n\n");
    }
    // ... 现有逻辑（最近对话、wikiContext、observations、当前问题、收尾）...
    return sb.toString();
}
```

具体实现需根据现有 buildDecisionPrompt 的代码结构调整。建议：
- 将现有 6 参数方法改为调用 7 参数方法（传 userProfileSection=null）
- 这样保持向后兼容

- [ ] **Step 3: 编译验证**

运行：`cd FitMate-backend && mvn -pl FitMate-api compile -q`
预期：BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/prompt/AgentPromptBuilder.java
git commit -m "feat(agent): add userProfileSection param to buildDecisionPrompt"
```

---

### Task 4.7：AgentLoopExecutor 集成画像注入与会话提取

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java`

- [ ] **Step 1: 注入 MemoryReader 和 SessionMemoryExtractor**

在 AgentLoopExecutor 的依赖注入部分新增：

```java
private final com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryReader memoryReader;
private final com.itgeo.fitmate.api.agent.memory.longterm.application.extractor.SessionMemoryExtractor sessionMemoryExtractor;
```

- [ ] **Step 2: 在 run 方法中加载画像并传入 buildDecisionPrompt**

在 run 方法第 109 行附近（buildSummaryPromptSection 之后）添加：

```java
String userProfileSection = memoryReader.loadProfileSection(
        context.getAuthenticatedUser().getUserId());
```

并将 buildDecisionPrompt 调用改为 7 参数版本，传入 userProfileSection。

- [ ] **Step 3: 在 finishWithAnswer 方法末尾挂钩会话提取**

在 finishWithAnswer 方法第 325 行 markRunSuccess 之后（SSE FINISH 之后或之前均可）添加：

```java
// 触发会话记忆提取
try {
    List<Map<String, String>> conversation = ... // 从 chatSessionService 加载本次会话的消息
    sessionMemoryExtractor.extract(
            context.getAuthenticatedUser().getUserId(),
            context.getChatEntity().getSessionId(),
            conversation);
} catch (Exception e) {
    log.warn("触发会话记忆提取失败", e);
}
```

注意：conversation 的加载方式参考 AgentMemoryService.loadRecentMessages 的实现，或直接调用 chatSessionService 加载当前会话消息列表。

- [ ] **Step 4: 编译验证**

运行：`cd FitMate-backend && mvn -pl FitMate-api compile -q`
预期：BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java
git commit -m "feat(agent): inject user profile into prompt and trigger session memory extraction"
```

---

## 阶段 5：用户控制 API + memory.search 工具

### Task 5.1：MemoryController 用户控制 API

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/controller/MemoryController.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/controller/dto/MemoryListResponse.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/controller/dto/ProfileResponse.java`

- [ ] **Step 1: 创建 DTO**

MemoryListResponse.java：
```java
package com.itgeo.fitmate.api.agent.memory.longterm.controller.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class MemoryListResponse {
    private List<Item> items;
    private long total;
    private int page;
    private int size;

    @Data
    public static class Item {
        private Long id;
        private String memoryType;
        private String content;
        private String metadataJson;
        private String source;
        private String status;
        private LocalDateTime createdAt;
    }
}
```

ProfileResponse.java：
```java
package com.itgeo.fitmate.api.agent.memory.longterm.controller.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProfileResponse {
    private String profileText;
    private String profileTagsJson;
    private Integer memoryVersion;
    private LocalDateTime generatedAt;
}
```

- [ ] **Step 2: 创建 MemoryController**

先读取现有 Controller 的鉴权模式（如 UserController 如何获取当前用户），确保一致。

```java
package com.itgeo.fitmate.api.agent.memory.longterm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryReader;
import com.itgeo.fitmate.api.agent.memory.longterm.application.ProfileBuilder;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.agent.memory.longterm.controller.dto.MemoryListResponse;
import com.itgeo.fitmate.api.agent.memory.longterm.controller.dto.ProfileResponse;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserProfile;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserProfileMapper;
import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.common.response.LeeResult;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final UserMemoryMapper memoryMapper;
    private final UserProfileMapper profileMapper;
    private final ProfileBuilder profileBuilder;
    private final MemoryProperties properties;

    @GetMapping("/list")
    public LeeResult<MemoryListResponse> list(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContextHolder.getRequired().getUserId();
        Page<UserMemory> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<UserMemory> wrapper = new LambdaQueryWrapper<UserMemory>()
                .eq(UserMemory::getUserId, userId)
                .ne(UserMemory::getStatus, "ignored");
        if (type != null && !type.isBlank()) {
            wrapper.eq(UserMemory::getMemoryType, type);
        }
        wrapper.orderByDesc(UserMemory::getCreatedAt);
        Page<UserMemory> result = memoryMapper.selectPage(pageObj, wrapper);

        MemoryListResponse resp = new MemoryListResponse();
        resp.setItems(result.getRecords().stream().map(m -> {
            MemoryListResponse.Item item = new MemoryListResponse.Item();
            item.setId(m.getId());
            item.setMemoryType(m.getMemoryType());
            item.setContent(m.getContent());
            item.setMetadataJson(m.getMetadataJson());
            item.setSource(m.getSource());
            item.setStatus(m.getStatus());
            item.setCreatedAt(m.getCreatedAt());
            return item;
        }).collect(Collectors.toList()));
        resp.setTotal(result.getTotal());
        resp.setPage(page);
        resp.setSize(size);
        return LeeResult.success(resp);
    }

    @DeleteMapping("/{id}")
    public LeeResult<Void> delete(@PathVariable Long id) {
        Long userId = UserContextHolder.getRequired().getUserId();
        UserMemory memory = memoryMapper.selectById(id);
        if (memory == null || !memory.getUserId().equals(userId)) {
            return LeeResult.error(404, "记忆不存在");
        }
        memory.setStatus("ignored");
        memoryMapper.updateById(memory);
        profileBuilder.asyncRebuild(userId);
        return LeeResult.success(null);
    }

    @DeleteMapping("/all")
    public LeeResult<Void> deleteAll() {
        Long userId = UserContextHolder.getRequired().getUserId();
        List<UserMemory> memories = memoryMapper.selectList(new LambdaQueryWrapper<UserMemory>()
                .eq(UserMemory::getUserId, userId)
                .ne(UserMemory::getStatus, "ignored"));
        for (UserMemory m : memories) {
            m.setStatus("ignored");
            memoryMapper.updateById(m);
        }
        profileBuilder.asyncRebuild(userId);
        return LeeResult.success(null);
    }

    @GetMapping("/profile")
    public LeeResult<ProfileResponse> getProfile() {
        Long userId = UserContextHolder.getRequired().getUserId();
        UserProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
        ProfileResponse resp = new ProfileResponse();
        if (profile == null) {
            return LeeResult.success(resp); // 空画像
        }
        resp.setProfileText(profile.getProfileText());
        resp.setProfileTagsJson(profile.getProfileTagsJson());
        resp.setMemoryVersion(profile.getMemoryVersion());
        resp.setGeneratedAt(profile.getGeneratedAt());
        return LeeResult.success(resp);
    }

    @PostMapping("/profile/rebuild")
    public LeeResult<Void> rebuildProfile() {
        Long userId = UserContextHolder.getRequired().getUserId();
        profileBuilder.asyncRebuild(userId);
        return LeeResult.success(null);
    }
}
```

注意：根据现有项目的 LeeResult 使用方式调整返回值。若 `LeeResult.error` / `LeeResult.success` 签名不同，参考现有 Controller。

- [ ] **Step 3: 编译验证**

运行：`cd FitMate-backend && mvn -pl FitMate-api compile -q`
预期：BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/controller/
git commit -m "feat(memory): add MemoryController for user memory management"
```

---

### Task 5.2：MemorySearchToolExecutor memory.search 工具

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/tool/MemorySearchToolExecutor.java`

- [ ] **Step 1: 读取现有 ToolExecutor 接口和实现模式**

读取 `KbSearchToolExecutor.java` 或 `TrainingLogQueryToolExecutor.java`，理解 ToolExecutor 接口、ToolDescriptor、工具调用入参出参的规范。

- [ ] **Step 2: 创建 MemorySearchToolExecutor**

```java
package com.itgeo.fitmate.api.agent.memory.longterm.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 注意：根据现有 ToolExecutor 接口调整 implements 和方法签名
@Slf4j
@Component
@RequiredArgsConstructor
public class MemorySearchToolExecutor /* implements ToolExecutor */ {

    private final UserMemoryMapper memoryMapper;

    public Map<String, Object> execute(Map<String, Object> input, AuthenticatedUserContext user) {
        Long userId = user.getUserId();
        String query = (String) input.getOrDefault("query", "");
        int limit = input.containsKey("limit") ? ((Number) input.get("limit")).intValue() : 5;

        List<UserMemory> memories = memoryMapper.selectList(new LambdaQueryWrapper<UserMemory>()
                .eq(UserMemory::getUserId, userId)
                .eq(UserMemory::getStatus, "active")
                .in(UserMemory::getMemoryType, "EPISODIC", "INSIGHT")
                .and(w -> w.like(UserMemory::getContent, query)
                        .or().apply("JSON_EXTRACT(metadata_json, '$.tags') LIKE {0}", "%" + query + "%"))
                .orderByDesc(UserMemory::getCreatedAt)
                .last("LIMIT " + limit));

        List<Map<String, Object>> items = new ArrayList<>();
        for (UserMemory m : memories) {
            Map<String, Object> item = new HashMap<>();
            item.put("type", m.getMemoryType());
            item.put("content", m.getContent());
            item.put("createdAt", m.getCreatedAt());
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("memories", items);
        return result;
    }
}
```

**注意**：必须根据现有 ToolExecutor 接口的实际签名调整。若现有工具用的是 `ToolCall` / `ToolResult` 对象而非 Map，需要相应调整。先读现有工具代码确认。

- [ ] **Step 3: 编译验证**

运行：`cd FitMate-backend && mvn -pl FitMate-api compile -q`
预期：BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/memory/longterm/tool/MemorySearchToolExecutor.java
git commit -m "feat(memory): add memory.search Agent tool"
```

---

### Task 5.3：在 ToolRegistry 注册 memory.search 工具

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/ToolRegistry.java`（或对应工具注册位置）

- [ ] **Step 1: 读取 ToolRegistry 了解工具注册机制**

读取 `ToolRegistry.java`，理解工具如何被注册和通过白名单过滤。

- [ ] **Step 2: 注册 memory.search 工具**

根据现有工具注册模式（可能是 Spring Bean 自动注入 + name 映射），确保 MemorySearchToolExecutor 被映射到 `memory.search` 这个工具名。

可能需要：
- 在 MemorySearchToolExecutor 上添加工具名注解或实现 getName() 方法返回 "memory.search"
- 在 ToolRegistry 的白名单匹配逻辑中确认 "memory.search" 能匹配到该 Bean

- [ ] **Step 3: 编译验证**

运行：`cd FitMate-backend && mvn -pl FitMate-api compile -q`
预期：BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/
git commit -m "feat(memory): register memory.search tool in ToolRegistry"
```

---

## 阶段 6：前端 Dashboard 画像 + 记忆管理

### Task 6.1：前端类型定义与 API 封装

**Files:**
- Create: `FitMate-frontend/src/types/memory.ts`
- Create: `FitMate-frontend/src/services/memoryApi.ts`

- [ ] **Step 1: 创建类型定义**

```typescript
// types/memory.ts
export interface MemoryItem {
  id: number
  memoryType: 'FACT' | 'EPISODIC' | 'SNAPSHOT' | 'INSIGHT'
  content: string
  metadataJson: string | null
  source: string | null
  status: string
  createdAt: string
}

export interface MemoryListResponse {
  items: MemoryItem[]
  total: number
  page: number
  size: number
}

export interface ProfileTag {
  label: string
  weight: number
  category: 'identity' | 'goal' | 'condition' | 'preference' | 'status'
}

export interface ProfileResponse {
  profileText: string | null
  profileTagsJson: string | null
  memoryVersion: number | null
  generatedAt: string | null
}
```

- [ ] **Step 2: 创建 API 封装**

先读取 `doctorApi.ts` 了解 HTTP 调用和认证头注入模式。

```typescript
// services/memoryApi.ts
import { http } from './http'
import type { MemoryListResponse, ProfileResponse } from '../types/memory'

export const memoryApi = {
  list(type?: string, page = 1, size = 20): Promise<MemoryListResponse> {
    return http.get('/memory/list', { params: { type, page, size } })
  },
  delete(id: number): Promise<void> {
    return http.delete(`/memory/${id}`)
  },
  deleteAll(): Promise<void> {
    return http.delete('/memory/all')
  },
  getProfile(): Promise<ProfileResponse> {
    return http.get('/memory/profile')
  },
  rebuildProfile(): Promise<void> {
    return http.post('/memory/profile/rebuild')
  }
}
```

注意：根据 doctorApi.ts 中的实际 http 调用方式调整（可能是 `doctorApi` 对象或直接 axios）。

- [ ] **Step 3: 前端构建验证**

运行：`cd FitMate-frontend && npm run build`
预期：构建成功

- [ ] **Step 4: 提交**

```bash
git add FitMate-frontend/src/types/memory.ts FitMate-frontend/src/services/memoryApi.ts
git commit -m "feat(memory): add frontend types and API for memory"
```

---

### Task 6.2：UserProfilePanel 组件（Dashboard 画像展示）

**Files:**
- Create: `FitMate-frontend/src/pages/dashboard/components/UserProfilePanel.vue`
- Modify: `FitMate-frontend/src/pages/dashboard/DashboardPage.vue`

- [ ] **Step 1: 创建 UserProfilePanel 组件**

```vue
<template>
  <div class="profile-panel">
    <div v-if="loading" class="profile-loading">
      <span class="material-symbols-outlined spin">progress_activity</span>
      <span>正在生成用户画像...</span>
    </div>
    <div v-else-if="!profile || !profile.profileText" class="profile-empty">
      <span class="material-symbols-outlined">account_circle</span>
      <span>暂无画像</span>
      <span class="hint">开始对话或上传文档后自动生成</span>
    </div>
    <div v-else class="profile-content">
      <!-- 标签可视化区域 -->
      <div class="profile-tags" v-if="tags.length">
        <div
          v-for="tag in tags"
          :key="tag.label"
          class="profile-tag"
          :style="tagStyle(tag)"
          :title="tag.label"
        >
          {{ tag.label }}
        </div>
      </div>
      <!-- 画像文本 -->
      <div class="profile-text">
        {{ profile.profileText }}
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'
import { memoryApi } from '../../../services/memoryApi'
import type { ProfileResponse, ProfileTag } from '../../../types/memory'

export default defineComponent({
  name: 'UserProfilePanel',
  data() {
    return {
      profile: null as ProfileResponse | null,
      tags: [] as ProfileTag[],
      loading: true
    }
  },
  async mounted() {
    await this.loadProfile()
  },
  methods: {
    async loadProfile() {
      try {
        this.profile = await memoryApi.getProfile()
        if (this.profile?.profileTagsJson) {
          this.tags = JSON.parse(this.profile.profileTagsJson)
        }
      } catch (e) {
        console.error('加载画像失败', e)
      } finally {
        this.loading = false
      }
    },
    tagStyle(tag: ProfileTag) {
      const size = 12 + tag.weight * 16 // 12px - 28px
      const colors: Record<string, string> = {
        identity: '#5b8def',
        goal: '#f59e0b',
        condition: '#ef4444',
        preference: '#10b981',
        status: '#8b5cf6'
      }
      return {
        fontSize: `${size}px`,
        backgroundColor: colors[tag.category] || '#6b7280',
        opacity: 0.6 + tag.weight * 0.4
      }
    }
  }
})
</script>

<style scoped>
.profile-panel {
  min-height: 200px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.profile-loading, .profile-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--text-secondary, #888);
  padding: 40px 20px;
}
.profile-empty .hint {
  font-size: 12px;
  opacity: 0.7;
}
.profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 0;
}
.profile-tag {
  padding: 4px 10px;
  border-radius: 12px;
  color: white;
  font-weight: 500;
  cursor: default;
  transition: transform 0.2s;
}
.profile-tag:hover {
  transform: scale(1.1);
}
.profile-text {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-primary, #333);
}
.spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
```

注意：样式使用 var(--xxx) 与项目现有 tokens.css 保持一致。具体颜色变量需参考 `FitMate-frontend/src/styles/tokens.css`。

- [ ] **Step 2: 修改 DashboardPage.vue 替换占位区域**

将 DashboardPage.vue 第 52-63 行的占位 div 替换为：

```vue
<!-- User Profile -->
<div class="dash-col">
  <div class="dash-col-head">
    <h3 class="dash-col-title">User Profile</h3>
  </div>
  <UserProfilePanel />
</div>
```

并在 DashboardPage.vue 的 script 部分导入组件：

```typescript
import UserProfilePanel from './components/UserProfilePanel.vue'
```

并在 components 中注册：

```typescript
components: { UserProfilePanel }
```

- [ ] **Step 3: 前端构建验证**

运行：`cd FitMate-frontend && npm run build`
预期：构建成功

- [ ] **Step 4: 提交**

```bash
git add FitMate-frontend/src/pages/dashboard/components/UserProfilePanel.vue FitMate-frontend/src/pages/dashboard/DashboardPage.vue
git commit -m "feat(dashboard): replace profile placeholder with UserProfilePanel"
```

---

### Task 6.3：MemorySection 组件（Settings 记忆管理）

**Files:**
- Create: `FitMate-frontend/src/pages/settings/components/MemorySection.vue`
- Modify: `FitMate-frontend/src/pages/settings/components/SettingsSectionNav.vue`
- Modify: `FitMate-frontend/src/pages/settings/SettingsPage.vue`

- [ ] **Step 1: 创建 MemorySection 组件**

```vue
<template>
  <div class="memory-section">
    <h2>记忆管理</h2>
    <p class="section-desc">这些是 Agent 关于你的长期记忆，可查看和删除。</p>

    <div class="memory-filters">
      <button
        v-for="t in types"
        :key="t.value"
        :class="['filter-btn', { active: filterType === t.value }]"
        @click="setFilter(t.value)"
      >
        {{ t.label }}
      </button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="items.length === 0" class="empty">暂无记忆</div>
    <div v-else class="memory-list">
      <div v-for="item in items" :key="item.id" class="memory-item">
        <div class="memory-item-head">
          <span class="memory-type" :class="`type-${item.memoryType.toLowerCase()}`">{{ item.memoryType }}</span>
          <span class="memory-time">{{ formatTime(item.createdAt) }}</span>
          <button class="delete-btn" @click="deleteItem(item.id)">删除</button>
        </div>
        <div class="memory-content">{{ item.content }}</div>
      </div>
    </div>

    <div class="memory-actions" v-if="items.length > 0">
      <button class="danger-btn" @click="deleteAll">清空全部记忆</button>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'
import { memoryApi } from '../../../services/memoryApi'
import type { MemoryItem } from '../../../types/memory'

export default defineComponent({
  name: 'MemorySection',
  data() {
    return {
      items: [] as MemoryItem[],
      loading: true,
      filterType: '' as string,
      types: [
        { value: '', label: '全部' },
        { value: 'FACT', label: '事实' },
        { value: 'EPISODIC', label: '事件' },
        { value: 'SNAPSHOT', label: '快照' },
        { value: 'INSIGHT', label: '洞察' }
      ]
    }
  },
  async mounted() {
    await this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const resp = await memoryApi.list(this.filterType || undefined)
        this.items = resp.items
      } finally {
        this.loading = false
      }
    },
    setFilter(type: string) {
      this.filterType = type
      this.load()
    },
    async deleteItem(id: number) {
      if (!confirm('确定删除这条记忆？')) return
      await memoryApi.delete(id)
      await this.load()
    },
    async deleteAll() {
      if (!confirm('确定清空全部记忆？此操作不可恢复。')) return
      await memoryApi.deleteAll()
      await this.load()
    },
    formatTime(t: string): string {
      return new Date(t).toLocaleString('zh-CN')
    }
  }
})
</script>

<style scoped>
.memory-section h2 {
  margin-bottom: 8px;
}
.section-desc {
  color: var(--text-secondary, #888);
  font-size: 13px;
  margin-bottom: 16px;
}
.memory-filters {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.filter-btn {
  padding: 4px 12px;
  border: 1px solid var(--border, #ddd);
  background: transparent;
  border-radius: 12px;
  cursor: pointer;
  font-size: 13px;
}
.filter-btn.active {
  background: var(--accent, #5b8def);
  color: white;
  border-color: var(--accent, #5b8def);
}
.memory-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.memory-item {
  border: 1px solid var(--border, #ddd);
  border-radius: 8px;
  padding: 12px;
}
.memory-item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 12px;
}
.memory-type {
  padding: 2px 8px;
  border-radius: 4px;
  color: white;
  font-weight: 500;
}
.type-fact { background: #5b8def; }
.type-episodic { background: #f59e0b; }
.type-snapshot { background: #8b5cf6; }
.type-insight { background: #10b981; }
.memory-time {
  color: var(--text-secondary, #888);
}
.delete-btn {
  margin-left: auto;
  background: transparent;
  border: none;
  color: #ef4444;
  cursor: pointer;
  font-size: 12px;
}
.memory-content {
  font-size: 13px;
  line-height: 1.5;
}
.memory-actions {
  margin-top: 16px;
}
.danger-btn {
  padding: 6px 16px;
  background: #ef4444;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}
.loading, .empty {
  padding: 40px;
  text-align: center;
  color: var(--text-secondary, #888);
}
</style>
```

- [ ] **Step 2: 在 SettingsSectionNav 中新增导航项**

读取 `SettingsSectionNav.vue`，按现有导航项模式新增"记忆管理"项。

- [ ] **Step 3: 在 SettingsPage 中注册 MemorySection**

读取 `SettingsPage.vue`，按现有 section 注册模式新增 MemorySection。

- [ ] **Step 4: 前端构建验证**

运行：`cd FitMate-frontend && npm run build`
预期：构建成功

- [ ] **Step 5: 提交**

```bash
git add FitMate-frontend/src/pages/settings/components/MemorySection.vue FitMate-frontend/src/pages/settings/components/SettingsSectionNav.vue FitMate-frontend/src/pages/settings/SettingsPage.vue
git commit -m "feat(settings): add memory management section"
```

---

## 验收清单

实现完成后，按以下清单逐项验证：

- [ ] **后端编译**：`cd FitMate-backend && mvn clean compile -q` BUILD SUCCESS
- [ ] **后端测试**：`cd FitMate-backend && mvn test -q` 全部 PASS
- [ ] **前端构建**：`cd FitMate-frontend && npm run build` 成功
- [ ] **数据库**：在现有库执行升级 SQL（建表 + ALTER）
- [ ] **启动**：后端启动无报错，`fitmate.memory` 配置加载成功
- [ ] **会话提取**：完成一段含个人信息的对话后，查询 `t_user_memory` 出现新记录
- [ ] **Wiki 编译提取**：上传含个人信息的 USER scope 文档，编译完成后 `t_user_memory` 出现 FACT 记录
- [ ] **快照聚合**：手动触发或等待定时，有训练记录的用户出现 SNAPSHOT
- [ ] **画像注入**：有记忆的用户开启新会话，Agent prompt 日志包含 `## 用户画像` 区块
- [ ] **memory.search 工具**：Agent 在对话中能调用 memory.search 工具并返回结果
- [ ] **Dashboard 画像**：Dashboard 右侧显示标签 + 画像文本
- [ ] **记忆管理**：Settings 页面可查看记忆列表、按类型过滤、删除单条
- [ ] **降级**：设置 `fitmate.memory.enabled=false`，Agent 正常运行无画像注入
- [ ] **PROFILE 清理**：`grep -r PROFILE FitMate-backend/FitMate-api/src FitMate-backend/FitMate-mcpServer/src/main/resources` 无匹配

---

## 自审记录

完成计划后对照设计文档检查：

**1. Spec 覆盖：**
- ✅ 第 1 节背景目标 → 计划整体覆盖
- ✅ 第 2 节需求基线 → 6 个维度均有对应任务
- ✅ 第 3 节架构总览 → 阶段 1-6 完整实现所有组件
- ✅ 第 4 节数据模型 → Task 1.2-1.6
- ✅ 第 5.1 节会话提取 → Task 2.1-2.5
- ✅ 第 5.2 节 Wiki 编译提取 → Task 3.1-3.2
- ✅ 第 5.3 节定时快照 → Task 3.3
- ✅ 第 5.4 节异步线程池 → Task 1.9
- ✅ 第 6.1 节画像常驻注入 → Task 4.5-4.7
- ✅ 第 6.2 节事件检索 → Task 5.2-5.3
- ✅ 第 6.3 节画像生成 → Task 4.1-4.4
- ✅ 第 7 节 Dashboard 展示 → Task 6.2
- ✅ 第 8 节用户控制 → Task 5.1, 6.3
- ✅ 第 9 节配置项 → Task 1.7-1.8
- ✅ 第 10 节 PROFILE 清理 → Task 1.1
- ✅ 第 11 节错误处理 → 各 Task 中的 try-catch 和降级逻辑
- ✅ 第 12 节验收标准 → 验收清单
- ✅ 第 13 节演进路径 → 不需实现任务

**2. Placeholder 扫描：**
- 无 TBD/TODO
- 部分 Step 含"根据现有代码调整"说明——这是因为需要先读取现有代码才能给出精确实现（如 ToolExecutor 接口签名、LeeResult 用法等），已在 Step 中明确读取要求

**3. Type 一致性：**
- MemoryWriteRequest 在 Task 2.1 定义，在 Task 2.5/3.2/3.3 引用，字段一致
- MemoryExtractResult 在 Task 2.4 定义，在 Task 2.5 引用，字段一致
- ProfileBuilder.asyncRebuild 在 Task 4.3 定义，在 Task 4.4/5.1 引用，签名一致
- MemoryReader.loadProfileSection 在 Task 4.5 定义，在 Task 4.7 引用，签名一致

**4. 跨任务一致性说明：**
- Task 4.4 修改了 MemoryWriter 的构造函数（新增 ProfileBuilder 依赖），Task 2.5 和 3.3 的 SessionMemoryExtractor/SnapshotAggregator 注入的是 MemoryWriter，不受影响
- Task 4.6 的 buildDecisionPrompt 新增重载，Task 4.7 调用新重载，向后兼容
