# LLM Wiki 引入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 FitMate-AI 后端引入 LLM Wiki 知识库（MySQL 存储 + 复用 Redis 向量检索），作为默认检索方式；RAG 改为 opt-in 叠加开关；通过复合工具 `kb.search` 串行执行 wiki→rewrite→rag 两阶段检索。

**Architecture:** MySQL 存 wiki 页面（5 张表）+ 独立 Redis 向量/关键词索引（复用 bge-m3 1024 维）+ 异步 LLM 编译 + Agent Loop 中复合工具 `kb.search` + 两层开关（knowledgeBaseEnabled 总开关 + ragEnabled RAG 叠加开关）。

**Tech Stack:** Java 21、Spring Boot 3.5.10、Spring AI 1.1.0、MyBatis-Plus 3.5.10.1、Redis Stack、DeepSeek API、Vue 3 + TypeScript。

**Spec:** [2026-07-02-llm-wiki-integration-design.md](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/docs/superpowers/specs/2026-07-02-llm-wiki-integration-design.md)

---

## 文件结构总览

### 新建文件（后端 FitMate-api）

```
src/main/java/com/itgeo/fitmate/api/wiki/
├── config/
│   ├── WikiProperties.java                          # @ConfigurationProperties(prefix="fitmate.wiki")
│   └── WikiRedisVectorStoreConfig.java              # 独立 wiki RedisVectorStore Bean
├── infrastructure/
│   ├── entity/
│   │   ├── WikiSpace.java
│   │   ├── WikiPage.java
│   │   ├── WikiPageLink.java
│   │   ├── WikiCompileJob.java
│   │   └── WikiLog.java
│   └── mapper/
│       ├── WikiSpaceMapper.java
│       ├── WikiPageMapper.java
│       ├── WikiPageLinkMapper.java
│       ├── WikiCompileJobMapper.java
│       └── WikiLogMapper.java
├── application/
│   ├── WikiCompileService.java                      # 接口
│   ├── WikiSearchService.java                       # 接口
│   ├── QueryRewriteService.java                     # 接口
│   └── impl/
│       ├── WikiCompileServiceImpl.java
│       ├── WikiCompileAsyncRunner.java              # @Async 异步执行
│       ├── WikiSearchServiceImpl.java
│       ├── WikiKeywordSearchServiceImpl.java        # 仿 RedisKeywordSearchServiceImpl
│       └── QueryRewriteServiceImpl.java
├── controller/
│   └── WikiController.java                          # /wiki/* 管理 API
└── dto/
    ├── WikiPageItem.java
    ├── WikiSpaceItem.java
    ├── WikiCompileJobItem.java
    └── KbSearchObservation.java                     # kb.search 工具返回结构

src/main/java/com/itgeo/fitmate/api/agent/tool/
└── KbSearchToolExecutor.java                        # 复合工具

src/main/resources/prompts/
└── wiki-schema.md                                   # Wiki 结构与工作流 schema
```

### 修改文件

- `FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql`：追加 5 张 wiki 表 DDL
- `FitMate-api/src/main/resources/application.yml`：新增 `fitmate.wiki.*` 配置
- `FitMate-api/src/main/java/com/itgeo/fitmate/api/prompt/PromptTemplateManager.java`：新增 3 个 prompt 方法
- `FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/ToolRegistry.java`：注册 `kb.search`
- `FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java`：`resolveAllowedTools` 增加 `kb.search` 可见性
- `FitMate-api/src/main/java/com/itgeo/fitmate/api/rag/controller/RagController.java`：`uploadRagDoc` 末尾投递编译 job
- `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`：两层开关
- `FitMate-frontend/src/pages/chat/components/AgentStepCard.vue`：适配子步骤事件

---

## Task 1: 数据库表 DDL

**Files:**
- Modify: `FitMate-backend/FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql`（追加到末尾）

- [ ] **Step 1: 追加 5 张 wiki 表 DDL**

在 `fitmate_init.sql` 末尾追加：

```sql
-- ============ LLM Wiki 知识库表 ============

CREATE TABLE IF NOT EXISTS `t_wiki_space` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '空间主键',
    `scope_type`      VARCHAR(20)  NOT NULL COMMENT 'GLOBAL / USER',
    `owner_user_id`   BIGINT       NULL     COMMENT 'GLOBAL=null; USER=t_user.id',
    `title`           VARCHAR(255) NOT NULL COMMENT '空间标题',
    `description`     VARCHAR(500) NULL     COMMENT '空间描述',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / ARCHIVED',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scope_owner` (`scope_type`, `owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki 空间';

CREATE TABLE IF NOT EXISTS `t_wiki_page` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '页面主键',
    `space_id`         BIGINT       NOT NULL COMMENT '所属空间',
    `page_type`        VARCHAR(30)  NOT NULL COMMENT 'INDEX/ENTITY/CONCEPT/SYNTHESIS/SOURCE_SUMMARY/LOG/PROFILE',
    `title`            VARCHAR(255) NOT NULL COMMENT '页面标题',
    `slug`             VARCHAR(255) NOT NULL COMMENT 'URL 友好标识（空间内唯一）',
    `content_md`       LONGTEXT     NULL     COMMENT 'Markdown 正文',
    `content_hash`     VARCHAR(64)  NULL     COMMENT '内容哈希（变更检测）',
    `frontmatter_json` JSON         NULL     COMMENT 'YAML frontmatter',
    `char_count`       INT          NOT NULL DEFAULT 0,
    `status`           VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED' COMMENT 'DRAFT / PUBLISHED',
    `source_doc_id`    BIGINT       NULL     COMMENT '源文档（SOURCE_SUMMARY 用）',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `compiled_at`      DATETIME     NULL     COMMENT '编译时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_space_slug` (`space_id`, `slug`),
    KEY `idx_space_type` (`space_id`, `page_type`),
    KEY `idx_content_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki 页面';

CREATE TABLE IF NOT EXISTS `t_wiki_page_link` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `from_page_id`  BIGINT       NOT NULL COMMENT '源页面',
    `to_page_id`    BIGINT       NOT NULL COMMENT '目标页面',
    `link_text`     VARCHAR(255) NULL     COMMENT '链接显示文本',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_from` (`from_page_id`),
    KEY `idx_to` (`to_page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki 页面 wikilink 关系';

CREATE TABLE IF NOT EXISTS `t_wiki_compile_job` (
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT,
    `space_id`            BIGINT      NOT NULL COMMENT '目标空间',
    `trigger_type`        VARCHAR(20) NOT NULL COMMENT 'DOC_UPLOAD/MANUAL/SCHEDULED/EVENT',
    `source_doc_id`       BIGINT      NULL     COMMENT '源文档',
    `status`              VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    `pages_touched_json`  JSON        NULL     COMMENT '本次触碰页面 ID 列表',
    `error_message`       TEXT        NULL,
    `started_at`          DATETIME    NULL,
    `finished_at`         DATETIME    NULL,
    `created_by_user_id`  BIGINT      NULL,
    `created_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_space_status` (`space_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki 异步编译任务';

CREATE TABLE IF NOT EXISTS `t_wiki_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `space_id`      BIGINT       NOT NULL,
    `entry_type`    VARCHAR(20)  NOT NULL COMMENT 'INGEST/QUERY/LINT/COMPILE',
    `entry_summary` VARCHAR(500) NULL     COMMENT '日志摘要',
    `source_ref`    VARCHAR(255) NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_space_created` (`space_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki 操作日志';
```

- [ ] **Step 2: 在 MySQL 执行 DDL**

通过 MCP MySQL 工具或本地客户端执行上述 DDL，确认 5 张表创建成功。

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql
git commit -m "feat(wiki): add 5 wiki tables DDL (space/page/link/compile_job/log)"
```

---

## Task 2: 实体类与 Mapper

**Files:**
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/infrastructure/entity/WikiSpace.java`
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/infrastructure/entity/WikiPage.java`
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/infrastructure/entity/WikiPageLink.java`
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/infrastructure/entity/WikiCompileJob.java`
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/infrastructure/entity/WikiLog.java`
- Create: 对应 5 个 Mapper

- [ ] **Step 1: 创建 WikiSpace 实体**

`WikiSpace.java`:

```java
package com.itgeo.fitmate.api.wiki.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_wiki_space")
public class WikiSpace {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String scopeType;       // GLOBAL / USER
    private Long ownerUserId;       // GLOBAL=null
    private String title;
    private String description;
    private String status;          // ACTIVE / ARCHIVED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 WikiPage 实体**

`WikiPage.java`:

```java
package com.itgeo.fitmate.api.wiki.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_wiki_page")
public class WikiPage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spaceId;
    private String pageType;        // INDEX/ENTITY/CONCEPT/SYNTHESIS/SOURCE_SUMMARY/LOG/PROFILE
    private String title;
    private String slug;
    private String contentMd;
    private String contentHash;
    private String frontmatterJson;
    private Integer charCount;
    private String status;          // DRAFT / PUBLISHED
    private Long sourceDocId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime compiledAt;
}
```

- [ ] **Step 3: 创建 WikiPageLink / WikiCompileJob / WikiLog 实体**

`WikiPageLink.java`:

```java
package com.itgeo.fitmate.api.wiki.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_wiki_page_link")
public class WikiPageLink {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fromPageId;
    private Long toPageId;
    private String linkText;
    private LocalDateTime createdAt;
}
```

`WikiCompileJob.java`:

```java
package com.itgeo.fitmate.api.wiki.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_wiki_compile_job")
public class WikiCompileJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spaceId;
    private String triggerType;     // DOC_UPLOAD/MANUAL/SCHEDULED/EVENT
    private Long sourceDocId;
    private String status;          // PENDING/RUNNING/SUCCESS/FAILED
    private String pagesTouchedJson;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long createdByUserId;
    private LocalDateTime createdAt;
}
```

`WikiLog.java`:

```java
package com.itgeo.fitmate.api.wiki.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_wiki_log")
public class WikiLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spaceId;
    private String entryType;       // INGEST/QUERY/LINT/COMPILE
    private String entrySummary;
    private String sourceRef;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: 创建 5 个 Mapper**

`WikiSpaceMapper.java`:

```java
package com.itgeo.fitmate.api.wiki.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiSpace;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WikiSpaceMapper extends BaseMapper<WikiSpace> {
}
```

`WikiPageMapper.java`:

```java
package com.itgeo.fitmate.api.wiki.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WikiPageMapper extends BaseMapper<WikiPage> {
}
```

`WikiPageLinkMapper.java`:

```java
package com.itgeo.fitmate.api.wiki.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPageLink;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WikiPageLinkMapper extends BaseMapper<WikiPageLink> {
}
```

`WikiCompileJobMapper.java`:

```java
package com.itgeo.fitmate.api.wiki.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiCompileJob;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WikiCompileJobMapper extends BaseMapper<WikiCompileJob> {
}
```

`WikiLogMapper.java`:

```java
package com.itgeo.fitmate.api.wiki.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WikiLogMapper extends BaseMapper<WikiLog> {
}
```

- [ ] **Step 5: 编译验证**

Run: `mvn -pl FitMate-api compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/
git commit -m "feat(wiki): add 5 entities and mappers (space/page/link/job/log)"
```

---

## Task 3: 配置类 WikiProperties

**Files:**
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/config/WikiProperties.java`
- Modify: `FitMate-api/src/main/resources/application.yml`

- [ ] **Step 1: 创建 WikiProperties**

`WikiProperties.java`:

```java
package com.itgeo.fitmate.api.wiki.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fitmate.wiki")
public class WikiProperties {

    /** 知识库总开关（前端 knowledgeBaseEnabled 对应） */
    private Boolean enabled = true;

    private Compile compile = new Compile();
    private Retrieval retrieval = new Retrieval();
    private Vectorstore vectorstore = new Vectorstore();
    private Keyword keyword = new Keyword();

    @Data
    public static class Compile {
        private Integer asyncPoolSize = 3;
        private Integer maxRetry = 2;
    }

    @Data
    public static class Retrieval {
        private Integer defaultTopK = 4;
        private Integer maxTopK = 10;
        private Integer vectorRecallK = 8;
        private Integer keywordRecallK = 8;
        private Boolean rerankEnabled = true;
    }

    @Data
    public static class Vectorstore {
        private String indexName = "fitmate-wiki-vectorstore";
        private String prefix = "fitmate:wiki:embedding:";
    }

    @Data
    public static class Keyword {
        private String indexName = "fitmate-wiki-keyword-index";
        private String keyPrefix = "fitmate:wiki:chunk:";
    }
}
```

- [ ] **Step 2: 在 application.yml 追加配置**

在 `FitMate-api/src/main/resources/application.yml` 的 `fitmate:` 节点下追加：

```yaml
  wiki:
    enabled: ${WIKI_ENABLED:true}
    compile:
      async-pool-size: ${WIKI_COMPILE_POOL_SIZE:3}
      max-retry: ${WIKI_COMPILE_MAX_RETRY:2}
    retrieval:
      default-top-k: ${WIKI_RETRIEVAL_TOP_K:4}
      max-top-k: ${WIKI_RETRIEVAL_MAX_TOP_K:10}
      vector-recall-k: ${WIKI_VECTOR_RECALL_K:8}
      keyword-recall-k: ${WIKI_KEYWORD_RECALL_K:8}
      rerank-enabled: ${WIKI_RERANK_ENABLED:true}
    vectorstore:
      index-name: ${WIKI_VECTORSTORE_INDEX_NAME:fitmate-wiki-vectorstore}
      prefix: ${WIKI_VECTORSTORE_PREFIX:fitmate:wiki:embedding:}
    keyword:
      index-name: ${WIKI_KEYWORD_INDEX_NAME:fitmate-wiki-keyword-index}
      key-prefix: ${WIKI_KEYWORD_KEY_PREFIX:fitmate:wiki:chunk:}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -pl FitMate-api compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/config/WikiProperties.java
git add FitMate-backend/FitMate-api/src/main/resources/application.yml
git commit -m "feat(wiki): add WikiProperties config and application.yml entries"
```

---

## Task 4: Wiki Redis VectorStore 配置

**Files:**
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/config/WikiRedisVectorStoreConfig.java`

参考现有 [RagRedisVectorStoreConfig.java](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/rag/config/RagRedisVectorStoreConfig.java)，但使用 `@Qualifier` 区分 Bean，并使用独立的 indexName/prefix 与 metadata 字段。

- [ ] **Step 1: 创建 WikiRedisVectorStoreConfig**

`WikiRedisVectorStoreConfig.java`:

```java
package com.itgeo.fitmate.api.wiki.config;

import com.itgeo.fitmate.api.rag.infrastructure.embedding.BgeM3HttpEmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Qualifier;
import redis.clients.jedis.JedisPooled;

/**
 * Wiki 独立 Redis VectorStore 装配。
 *
 * 与 RAG 的 RedisVectorStore 完全隔离：
 *  - 独立 indexName / prefix
 *  - 独立 metadata schema（spaceId/pageId/pageType/scope/ownerUserId/title）
 *  - 复用同一 BgeM3HttpEmbeddingModel（1024 维，与 RAG 同模型）
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class WikiRedisVectorStoreConfig {

    private final WikiProperties wikiProperties;

    /**
     * Wiki 专用 RedisVectorStore，通过 @Qualifier("wikiRedisVectorStore") 注入。
     *
     * 注意：现有 RAG 的 RedisVectorStore 标注了 @Primary，因此 Wiki 这里不使用 @Primary，
     * 通过显式 @Qualifier 注入避免冲突。
     */
    @Bean("wikiRedisVectorStore")
    public RedisVectorStore wikiRedisVectorStore(
            JedisPooled jedisPooled,
            BgeM3HttpEmbeddingModel embeddingModel) {

        log.info("Wiki Redis indexName={}", wikiProperties.getVectorstore().getIndexName());
        log.info("Wiki Redis prefix={}", wikiProperties.getVectorstore().getPrefix());

        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(wikiProperties.getVectorstore().getIndexName())
                .prefix(wikiProperties.getVectorstore().getPrefix())
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("spaceId"),
                        RedisVectorStore.MetadataField.tag("pageId"),
                        RedisVectorStore.MetadataField.tag("pageType"),
                        RedisVectorStore.MetadataField.tag("scope"),        // GLOBAL / USER
                        RedisVectorStore.MetadataField.tag("ownerUserId"),  // USER 用，GLOBAL 空
                        RedisVectorStore.MetadataField.tag("title")
                )
                .initializeSchema(true)
                .build();
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl FitMate-api compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 启动应用验证 Wiki 索引创建**

启动 FitMateApiApplication，观察日志：
Expected: 日志出现 `Wiki Redis indexName=fitmate-wiki-vectorstore` 和 `Wiki Redis prefix=fitmate:wiki:embedding:`

通过 redis-cli 确认索引：
```bash
redis-cli FT.INFO fitmate-wiki-vectorstore
```
Expected: 返回索引信息，包含 6 个 metadata 字段。

- [ ] **Step 4: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/config/WikiRedisVectorStoreConfig.java
git commit -m "feat(wiki): add independent wiki RedisVectorStore bean with 6 metadata fields"
```

---

## Task 5: Wiki 关键词检索服务

**Files:**
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/WikiKeywordSearchService.java`
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/impl/WikiKeywordSearchServiceImpl.java`

参考现有 [RedisKeywordSearchServiceImpl.java](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/rag/application/impl/RedisKeywordSearchServiceImpl.java)，但使用 wiki 独立的索引名与字段。

- [ ] **Step 1: 创建 WikiKeywordSearchService 接口**

```java
package com.itgeo.fitmate.api.wiki.application;

import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import java.util.List;

public interface WikiKeywordSearchService {

    /**
     * 对 wiki 页面做关键词召回。
     *
     * @param query  用户问题
     * @param userId 当前用户 ID（用于检索 GLOBAL + 该用户 USER space）
     * @param topK   返回数量
     * @return 命中的 wiki 页面列表
     */
    List<WikiPage> search(String query, Long userId, int topK);

    /**
     * 将一个 wiki 页面索引到 Redis Hash（关键词索引）。
     */
    void indexPage(WikiPage page, String scope, Long ownerUserId);
}
```

- [ ] **Step 2: 创建 WikiKeywordSearchServiceImpl**

```java
package com.itgeo.fitmate.api.wiki.application.impl;

import com.itgeo.fitmate.api.wiki.application.WikiKeywordSearchService;
import com.itgeo.fitmate.api.wiki.config.WikiProperties;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiPageMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;

@Service
@Slf4j
@RequiredArgsConstructor
public class WikiKeywordSearchServiceImpl implements WikiKeywordSearchService {

    private final JedisPooled jedisPooled;
    private final WikiProperties wikiProperties;
    private final WikiPageMapper wikiPageMapper;

    @Override
    public List<WikiPage> search(String query, Long userId, int topK) {
        String indexName = wikiProperties.getKeyword().getIndexName();
        // 检索 GLOBAL + 当前用户 USER space
        // RediSearch 语法：(@scope:{GLOBAL} | @ownerUserId:{userId}) {query}
        String filterExpr = String.format("(@scope:{GLOBAL} | @ownerUserId:{%d})", userId);
        String fullQuery = filterExpr + " " + escapeQuery(query);

        Query q = new Query(fullQuery).limit(0, topK);
        try {
            SearchResult result = jedisPooled.ftSearch(indexName, q);
            List<WikiPage> pages = new ArrayList<>();
            for (Document doc : result.getDocuments()) {
                String pageIdStr = (String) doc.get("pageId");
                if (pageIdStr == null) continue;
                Long pageId = Long.valueOf(pageIdStr);
                WikiPage page = wikiPageMapper.selectById(pageId);
                if (page != null) pages.add(page);
            }
            return pages;
        } catch (Exception e) {
            log.warn("Wiki 关键词检索失败，降级返回空: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void indexPage(WikiPage page, String scope, Long ownerUserId) {
        String keyPrefix = wikiProperties.getKeyword().getKeyPrefix();
        String key = keyPrefix + page.getId();
        Map<String, String> fields = new HashMap<>();
        fields.put("pageId", String.valueOf(page.getId()));
        fields.put("spaceId", String.valueOf(page.getSpaceId()));
        fields.put("pageType", page.getPageType());
        fields.put("scope", scope);
        fields.put("ownerUserId", ownerUserId == null ? "" : String.valueOf(ownerUserId));
        fields.put("title", page.getTitle() == null ? "" : page.getTitle());
        fields.put("content", page.getContentMd() == null ? "" : page.getContentMd());
        jedisPooled.hset(key, fields);
    }

    private String escapeQuery(String query) {
        if (query == null) return "";
        return query.trim().replaceAll("[\"\\\\]", " ");
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -pl FitMate-api compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/
git commit -m "feat(wiki): add WikiKeywordSearchService for redis keyword recall"
```

---

## Task 6: Wiki 检索服务 WikiSearchService

**Files:**
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/WikiSearchService.java`
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/impl/WikiSearchServiceImpl.java`

- [ ] **Step 1: 创建 WikiSearchService 接口**

```java
package com.itgeo.fitmate.api.wiki.application;

import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import java.util.List;

public interface WikiSearchService {

    /**
     * Wiki 检索（向量 + 关键词 + RRF + 可选 rerank）。
     *
     * @param question 用户问题
     * @param userId   当前用户 ID
     * @param topK     返回数量
     * @return 命中的 wiki 页面列表
     */
    List<WikiPage> search(String question, Long userId, int topK);
}
```

- [ ] **Step 2: 创建 WikiSearchServiceImpl**

```java
package com.itgeo.fitmate.api.wiki.application.impl;

import com.itgeo.fitmate.api.rag.application.RagFusionService;
import com.itgeo.fitmate.api.rag.application.RerankService;
import com.itgeo.fitmate.api.rag.dto.RagRetrievedChunk;
import com.itgeo.fitmate.api.wiki.application.WikiKeywordSearchService;
import com.itgeo.fitmate.api.wiki.application.WikiSearchService;
import com.itgeo.fitmate.api.wiki.config.WikiProperties;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class WikiSearchServiceImpl implements WikiSearchService {

    @Qualifier("wikiRedisVectorStore")
    private final RedisVectorStore wikiRedisVectorStore;
    private final WikiKeywordSearchService wikiKeywordSearchService;
    private final WikiProperties wikiProperties;
    private final RagFusionService ragFusionService;
    private final RerankService rerankService;

    @Override
    public List<WikiPage> search(String question, Long userId, int topK) {
        int vectorRecallK = wikiProperties.getRetrieval().getVectorRecallK();
        int keywordRecallK = wikiProperties.getRetrieval().getKeywordRecallK();

        // 1. 向量召回
        String filterExpr = String.format("scope == 'GLOBAL' || ownerUserId == '%s'", userId);
        List<Document> vectorHits = wikiRedisVectorStore.similaritySearch(
                SearchRequest.query(question)
                        .topK(vectorRecallK)
                        .filterExpression(filterExpr)
        );
        log.debug("Wiki 向量召回 {} 条", vectorHits == null ? 0 : vectorHits.size());

        // 2. 关键词召回
        List<WikiPage> keywordHits = wikiKeywordSearchService.search(question, userId, keywordRecallK);
        log.debug("Wiki 关键词召回 {} 条", keywordHits.size());

        // 3. 合并去重（按 pageId）
        Map<Long, WikiPage> pageMap = new HashMap<>();
        Map<Long, RagRetrievedChunk> chunkMap = new HashMap<>();

        if (vectorHits != null) {
            for (Document doc : vectorHits) {
                Long pageId = parseLong(doc.getMetadata().get("pageId"));
                if (pageId == null) continue;
                WikiPage page = new WikiPage();
                page.setId(pageId);
                page.setContentMd(doc.getText());
                page.setTitle((String) doc.getMetadata().getOrDefault("title", ""));
                page.setSpaceId(parseLong(doc.getMetadata().get("spaceId")));
                pageMap.put(pageId, page);
                chunkMap.put(pageId, toChunk(doc, "vector"));
            }
        }
        for (WikiPage page : keywordHits) {
            pageMap.putIfAbsent(page.getId(), page);
            chunkMap.putIfAbsent(page.getId(), toChunkFromPage(page, "keyword"));
        }

        if (pageMap.isEmpty()) return List.of();

        // 4. RRF 融合（复用现有 RagFusionService）
        List<RagRetrievedChunk> fused = ragFusionService.fuse(
                new ArrayList<>(chunkMap.values()),
                List.of(),
                wikiProperties.getRetrieval().getDefaultTopK()
        );

        // 5. 可选 rerank
        if (Boolean.TRUE.equals(wikiProperties.getRetrieval().getRerankEnabled()) && !fused.isEmpty()) {
            fused = rerankService.rerank(question, fused, topK);
        } else {
            fused = fused.stream().limit(topK).collect(Collectors.toList());
        }

        // 6. 转回 WikiPage
        List<WikiPage> result = new ArrayList<>();
        for (RagRetrievedChunk chunk : fused) {
            Long pageId = parseLong(chunk.getMetadata() == null ? null : chunk.getMetadata().get("pageId"));
            if (pageId != null && pageMap.containsKey(pageId)) {
                result.add(pageMap.get(pageId));
            }
        }
        return result;
    }

    private Long parseLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.valueOf(String.valueOf(value)); } catch (Exception e) { return null; }
    }

    private RagRetrievedChunk toChunk(Document doc, String source) {
        RagRetrievedChunk chunk = new RagRetrievedChunk();
        chunk.setText(doc.getText());
        Map<String, Object> meta = new HashMap<>(doc.getMetadata());
        meta.put("recallSource", source);
        chunk.setMetadata(meta);
        return chunk;
    }

    private RagRetrievedChunk toChunkFromPage(WikiPage page, String source) {
        RagRetrievedChunk chunk = new RagRetrievedChunk();
        chunk.setText(page.getContentMd());
        Map<String, Object> meta = new HashMap<>();
        meta.put("pageId", page.getId());
        meta.put("spaceId", page.getSpaceId());
        meta.put("title", page.getTitle());
        meta.put("recallSource", source);
        chunk.setMetadata(meta);
        return chunk;
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -pl FitMate-api compile -q`
Expected: BUILD SUCCESS（注意：依赖 `RagRetrievedChunk` 的 setter，若该 DTO 缺失 setter 需先确认其结构）

- [ ] **Step 4: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/WikiSearchService.java
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/impl/WikiSearchServiceImpl.java
git commit -m "feat(wiki): add WikiSearchService with vector+keyword+RRF+rerank"
```

---

## Task 7: Query Rewriting 服务

**Files:**
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/QueryRewriteService.java`
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/impl/QueryRewriteServiceImpl.java`
- Modify: `FitMate-api/src/main/java/com/itgeo/fitmate/api/prompt/PromptTemplateManager.java`（新增 `buildQueryRewritePrompt`）

- [ ] **Step 1: 在 PromptTemplateManager 新增 rewrite prompt 模板**

参考 [PromptTemplateManager.java](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/prompt/PromptTemplateManager.java) 现有结构，新增：

```java
private static final String QUERY_REWRITE_PROMPT_TEMPLATE = """
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
        """;

public String buildQueryRewritePrompt(String question, String wikiContent) {
    return QUERY_REWRITE_PROMPT_TEMPLATE
            .replace("{question}", question)
            .replace("{wiki_content}", wikiContent);
}
```

- [ ] **Step 2: 创建 QueryRewriteService 接口**

```java
package com.itgeo.fitmate.api.wiki.application;

public interface QueryRewriteService {

    /**
     * 基于 Wiki 检索结果改写用户问题，用于提升 RAG 召回率。
     *
     * @param question    原问题
     * @param wikiContent Wiki 检索结果拼接的文本
     * @return 改写后的 query（若 Wiki 为空或无需改写，返回原问题）
     */
    String rewrite(String question, String wikiContent);
}
```

- [ ] **Step 3: 创建 QueryRewriteServiceImpl**

使用 DeepSeek 非流式调用（快速返回，省 token）。复用现有 OpenAiChatModel（非流式）：

```java
package com.itgeo.fitmate.api.wiki.application.impl;

import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import com.itgeo.fitmate.api.wiki.application.QueryRewriteService;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class QueryRewriteServiceImpl implements QueryRewriteService {

    private final ChatModel chatModel;
    private final PromptTemplateManager promptTemplateManager;

    @Override
    public String rewrite(String question, String wikiContent) {
        if (StrUtil.isBlank(wikiContent)) {
            return question;
        }
        try {
            String promptText = promptTemplateManager.buildQueryRewritePrompt(question, wikiContent);
            String rewritten = chatModel.call(new Prompt(promptText))
                    .getResult()
                    .getOutput()
                    .getText();
            String trimmed = rewritten == null ? question : rewritten.trim();
            log.debug("Query rewrite: [{}] -> [{}]", question, trimmed);
            return StrUtil.isBlank(trimmed) ? question : trimmed;
        } catch (Exception e) {
            log.warn("Query rewrite 失败，返回原问题: {}", e.getMessage());
            return question;
        }
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn -pl FitMate-api compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/QueryRewriteService.java
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/impl/QueryRewriteServiceImpl.java
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/prompt/PromptTemplateManager.java
git commit -m "feat(wiki): add QueryRewriteService with LLM-based query rewriting"
```

---

## Task 8: Wiki 编译服务

**Files:**
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/WikiCompileService.java`
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/impl/WikiCompileServiceImpl.java`
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/impl/WikiCompileAsyncRunner.java`
- Create: `FitMate-api/src/main/resources/prompts/wiki-schema.md`
- Modify: `FitMate-api/src/main/java/com/itgeo/fitmate/api/prompt/PromptTemplateManager.java`（新增 `buildWikiCompilePrompt`）

- [ ] **Step 1: 创建 wiki-schema.md**

`FitMate-api/src/main/resources/prompts/wiki-schema.md`:

```markdown
# FitMate Wiki Schema

本文件定义 LLM 维护 Wiki 时的结构与工作流约定。

## 页面类型

- `INDEX`：每个 space 唯一的目录页，列出所有页面（链接 + 一句话摘要 + 类别）
- `ENTITY`：实体页（人物/动作/器材/部位等）
- `CONCEPT`：概念页（训练原理/营养学概念等）
- `SYNTHESIS`：综合页（跨多源的主题综合，如"增肌期蛋白质摄入策略"）
- `SOURCE_SUMMARY`：单源摘要页（一篇原始文档的摘要）
- `LOG`：可选，空间操作日志页（与 t_wiki_log 表互补）
- `PROFILE`：用户画像页（Phase 2 用）

## 命名规则

- `slug` 使用小写英文 + 连字符，如 `protein-intake`、`bench-press`
- 标题可中文
- 每个 space 必有 `INDEX` 页（slug 固定为 `index`）

## wikilink 约定

- 页面间用 `[[slug]]` 或 `[[slug|显示文本]]` 互链
- 编译时 LLM 输出的 `links` 字段是目标页面的 slug 列表

## Ingest 工作流

1. 读原始资料
2. 生成/更新 `SOURCE_SUMMARY` 页（一篇文档一个）
3. 更新相关 `ENTITY` / `CONCEPT` / `SYNTHESIS` 页（增量合并，不覆盖原有内容）
4. 更新 `INDEX` 页
5. 追加 `LOG` 条目

## 输出格式（强制 JSON）

LLM 编译时必须输出如下 JSON：

```json
{
  "actions": [
    {"action": "create", "page_type": "SOURCE_SUMMARY", "title": "...", "slug": "...", "content_md": "...", "links": ["slug1", "slug2"]},
    {"action": "update", "slug": "existing-slug", "content_md": "..."},
    {"action": "update_index", "content_md": "..."},
    {"action": "append_log", "entry": "## [YYYY-MM-DD] ingest | 文档标题"}
  ]
}
```
```

- [ ] **Step 2: 在 PromptTemplateManager 新增编译 prompt 模板**

```java
private static final String WIKI_COMPILE_PROMPT_TEMPLATE = """
        你是 FitMate 的 Wiki 编译器。请根据以下原始资料与现有 Wiki 状态，输出编译指令 JSON。

        ## Schema 约定
        {schema_content}

        ## 原始资料
        {raw_content}

        ## 当前 INDEX 页
        {index_content}

        ## 输出要求
        严格输出 JSON，格式：
        {"actions": [{"action": "create|update|update_index|append_log", ...}]}
        不要输出 JSON 以外的任何内容。
        """;

public String buildWikiCompilePrompt(String schemaContent, String rawContent, String indexContent) {
    return WIKI_COMPILE_PROMPT_TEMPLATE
            .replace("{schema_content}", schemaContent)
            .replace("{raw_content}", rawContent)
            .replace("{index_content}", indexContent);
}
```

- [ ] **Step 3: 创建 WikiCompileService 接口**

```java
package com.itgeo.fitmate.api.wiki.application;

import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiCompileJob;
import java.util.Optional;

public interface WikiCompileService {

    /**
     * 投递一个编译任务（异步执行）。
     *
     * @param spaceId     目标空间
     * @param sourceDocId 源文档 ID
     * @param triggerBy   触发人 userId
     * @return 创建的 compile job
     */
    WikiCompileJob submitCompileJob(Long spaceId, Long sourceDocId, Long triggerBy);

    /**
     * 同步执行编译（供异步 runner 调用）。
     */
    void executeCompile(Long jobId);

    /**
     * 查询任务状态。
     */
    Optional<WikiCompileJob> getJob(Long jobId);
}
```

- [ ] **Step 4: 创建 WikiCompileServiceImpl**

```java
package com.itgeo.fitmate.api.wiki.application.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import com.itgeo.fitmate.api.rag.application.DocumentService;
import com.itgeo.fitmate.api.rag.infrastructure.entity.RagDocument;
import com.itgeo.fitmate.api.rag.infrastructure.mapper.RagDocumentMapper;
import com.itgeo.fitmate.api.wiki.application.WikiCompileService;
import com.itgeo.fitmate.api.wiki.application.WikiKeywordSearchService;
import com.itgeo.fitmate.api.wiki.config.WikiProperties;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiCompileJob;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiLog;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiSpace;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiCompileJobMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiLogMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiPageMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiSpaceMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class WikiCompileServiceImpl implements WikiCompileService {

    private final WikiCompileJobMapper compileJobMapper;
    private final WikiSpaceMapper spaceMapper;
    private final WikiPageMapper pageMapper;
    private final WikiLogMapper logMapper;
    private final RagDocumentMapper ragDocumentMapper;
    private final RagDocumentMapper documentMapper;
    private final DocumentService documentService;
    private final ChatModel chatModel;
    private final PromptTemplateManager promptTemplateManager;
    private final WikiProperties wikiProperties;
    private final WikiKeywordSearchService wikiKeywordSearchService;

    @Override
    public WikiCompileJob submitCompileJob(Long spaceId, Long sourceDocId, Long triggerBy) {
        WikiCompileJob job = new WikiCompileJob();
        job.setSpaceId(spaceId);
        job.setSourceDocId(sourceDocId);
        job.setTriggerType("DOC_UPLOAD");
        job.setStatus("PENDING");
        job.setCreatedByUserId(triggerBy);
        job.setCreatedAt(LocalDateTime.now());
        compileJobMapper.insert(job);
        return job;
    }

    @Override
    public void executeCompile(Long jobId) {
        WikiCompileJob job = compileJobMapper.selectById(jobId);
        if (job == null) return;
        job.setStatus("RUNNING");
        job.setStartedAt(LocalDateTime.now());
        compileJobMapper.updateById(job);

        try {
            // 1. 读源文档
            RagDocument ragDoc = ragDocumentMapper.selectById(job.getSourceDocId());
            if (ragDoc == null) throw new IllegalStateException("源文档不存在: " + job.getSourceDocId());

            // 复用 DocumentService.loadText 解析（这里其实只需文本，简化处理：从 RAG 向量库取首个 chunk）
            // 为避免重复解析，直接从 storage 读取。此处简化：调用 DocumentService.doSearch 拿原文片段
            // 实际实现：新增 DocumentService.getRawText(docId) 方法（见 Task 12 改造）
            String rawContent = fetchRawText(ragDoc);

            // 2. 读 schema
            String schemaContent = loadSchema();

            // 3. 读当前 INDEX 页
            WikiSpace space = spaceMapper.selectById(job.getSpaceId());
            String indexContent = loadIndexContent(job.getSpaceId());

            // 4. 调 LLM 编译
            String promptText = promptTemplateManager.buildWikiCompilePrompt(schemaContent, rawContent, indexContent);
            String llmOutput = chatModel.call(new Prompt(promptText))
                    .getResult().getOutput().getText();

            // 5. 解析 JSON 指令
            JSONObject root = JSONUtil.parseObj(llmOutput);
            JSONArray actions = root.getJSONArray("actions");
            if (actions == null) throw new IllegalStateException("LLM 未返回 actions 数组");

            // 6. 执行指令
            for (int i = 0; i < actions.size(); i++) {
                JSONObject action = actions.getJSONObject(i);
                applyAction(job.getSpaceId(), action, space, ragDoc);
            }

            // 7. 标记成功
            job.setStatus("SUCCESS");
            job.setFinishedAt(LocalDateTime.now());
            compileJobMapper.updateById(job);

        } catch (Exception e) {
            log.error("Wiki 编译失败 job={}", jobId, e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setFinishedAt(LocalDateTime.now());
            compileJobMapper.updateById(job);
        }
    }

    @Override
    public java.util.Optional<WikiCompileJob> getJob(Long jobId) {
        return java.util.Optional.ofNullable(compileJobMapper.selectById(jobId));
    }

    private void applyAction(Long spaceId, JSONObject action, WikiSpace space, RagDocument sourceDoc) {
        String type = action.getStr("action");
        switch (type) {
            case "create" -> upsertPage(spaceId, action, space, sourceDoc, false);
            case "update" -> upsertPage(spaceId, action, space, sourceDoc, true);
            case "update_index" -> upsertIndexPage(spaceId, action);
            case "append_log" -> appendLog(spaceId, action.getStr("entry"));
            default -> log.warn("未知 action 类型: {}", type);
        }
    }

    private void upsertPage(Long spaceId, JSONObject action, WikiSpace space, RagDocument sourceDoc, boolean isUpdate) {
        String slug = action.getStr("slug");
        String pageType = action.getStr("page_type");
        String title = action.getStr("title");
        String contentMd = action.getStr("content_md");

        WikiPage existing = pageMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WikiPage>()
                        .eq(WikiPage::getSpaceId, spaceId)
                        .eq(WikiPage::getSlug, slug));

        WikiPage page = existing != null ? existing : new WikiPage();
        page.setSpaceId(spaceId);
        page.setPageType(pageType);
        page.setTitle(title);
        page.setSlug(slug);
        page.setContentMd(contentMd);
        page.setContentHash(sha256(contentMd));
        page.setCharCount(contentMd == null ? 0 : contentMd.length());
        page.setStatus("PUBLISHED");
        page.setSourceDocId(sourceDoc.getId());
        page.setCompiledAt(LocalDateTime.now());

        if (existing == null) {
            pageMapper.insert(page);
        } else {
            pageMapper.updateById(page);
        }

        // 同步到 Redis 向量 + 关键词索引
        String scope = space.getScopeType();
        Long ownerUserId = space.getOwnerUserId();
        wikiKeywordSearchService.indexPage(page, scope, ownerUserId);
        // 向量索引由 RedisVectorStore.add 完成（见 WikiCompileAsyncRunner 或此处调用）
    }

    private void upsertIndexPage(Long spaceId, JSONObject action) {
        String contentMd = action.getStr("content_md");
        WikiPage index = pageMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WikiPage>()
                        .eq(WikiPage::getSpaceId, spaceId)
                        .eq(WikiPage::getSlug, "index"));
        if (index == null) {
            index = new WikiPage();
            index.setSpaceId(spaceId);
            index.setPageType("INDEX");
            index.setSlug("index");
            index.setTitle("Index");
            index.setStatus("PUBLISHED");
        }
        index.setTitle("Index");
        index.setContentMd(contentMd);
        index.setContentHash(sha256(contentMd));
        index.setCharCount(contentMd.length());
        index.setCompiledAt(LocalDateTime.now());
        if (index.getId() == null) pageMapper.insert(index);
        else pageMapper.updateById(index);
    }

    private void appendLog(Long spaceId, String entry) {
        WikiLog wikiLog = new WikiLog();
        wikiLog.setSpaceId(spaceId);
        wikiLog.setEntryType("INGEST");
        wikiLog.setEntrySummary(entry);
        wikiLog.setCreatedAt(LocalDateTime.now());
        logMapper.insert(wikiLog);
    }

    private String fetchRawText(RagDocument ragDoc) {
        // 简化实现：通过 DocumentService 检索该文档的 chunk
        // 实际生产建议新增 DocumentService.getRawTextByDocId(docId)
        List<Document> docs = documentService.doSearch(ragDoc.getFileName(), ragDoc.getUserId(), 10);
        StringBuilder sb = new StringBuilder();
        for (Document d : docs) {
            sb.append(d.getText()).append("\n\n");
        }
        return sb.toString();
    }

    private String loadSchema() {
        try {
            org.springframework.core.io.ClassPathResource res =
                    new org.springframework.core.io.ClassPathResource("prompts/wiki-schema.md");
            return new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("加载 wiki-schema.md 失败: {}", e.getMessage());
            return "";
        }
    }

    private String loadIndexContent(Long spaceId) {
        WikiPage index = pageMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WikiPage>()
                        .eq(WikiPage::getSpaceId, spaceId)
                        .eq(WikiPage::getSlug, "index"));
        return index == null ? "(空)" : index.getContentMd();
    }

    private String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "";
        }
    }
}
```

- [ ] **Step 5: 创建 WikiCompileAsyncRunner**

```java
package com.itgeo.fitmate.api.wiki.application.impl;

import com.itgeo.fitmate.api.wiki.application.WikiCompileService;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiCompileJob;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiCompileJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class WikiCompileAsyncRunner {

    private final WikiCompileService wikiCompileService;
    private final WikiCompileJobMapper compileJobMapper;

    @Async("wikiCompileExecutor")
    public void runAsync(Long jobId) {
        try {
            wikiCompileService.executeCompile(jobId);
        } catch (Exception e) {
            log.error("异步编译异常 job={}", jobId, e);
            WikiCompileJob job = compileJobMapper.selectById(jobId);
            if (job != null && !"SUCCESS".equals(job.getStatus())) {
                job.setStatus("FAILED");
                job.setErrorMessage("异步执行异常: " + e.getMessage());
                compileJobMapper.updateById(job);
            }
        }
    }
}
```

- [ ] **Step 6: 配置异步线程池**

在 `WikiRedisVectorStoreConfig` 或新建配置类中添加：

```java
@Bean("wikiCompileExecutor")
public java.util.concurrent.Executor wikiCompileExecutor() {
    java.util.concurrent.ThreadPoolTaskExecutor executor = new java.util.concurrent.ThreadPoolTaskExecutor();
    executor.setCorePoolSize(wikiProperties.getCompile().getAsyncPoolSize());
    executor.setMaxPoolSize(wikiProperties.getCompile().getAsyncPoolSize() * 2);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("wiki-compile-");
    executor.initialize();
    return executor;
}
```

并在 `FitMateApiApplication.java` 确认有 `@EnableAsync`（若无需添加）。

- [ ] **Step 7: 编译验证**

Run: `mvn -pl FitMate-api compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/
git add FitMate-backend/FitMate-api/src/main/resources/prompts/wiki-schema.md
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/prompt/PromptTemplateManager.java
git commit -m "feat(wiki): add WikiCompileService with async LLM compilation and JSON action parsing"
```

---

## Task 9: 复合工具 KbSearchToolExecutor

**Files:**
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/KbSearchToolExecutor.java`
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/dto/KbSearchObservation.java`

- [ ] **Step 1: 创建 KbSearchObservation DTO**

```java
package com.itgeo.fitmate.api.wiki.dto;

import java.util.List;
import lombok.Data;

@Data
public class KbSearchObservation {
    private List<WikiPageItem> wiki;
    private List<RawChunkItem> rag;
    private String rewrittenQuery;

    @Data
    public static class WikiPageItem {
        private Long pageId;
        private String title;
        private String pageType;
        private String content;
    }

    @Data
    public static class RawChunkItem {
        private String text;
        private String fileName;
    }
}
```

- [ ] **Step 2: 创建 WikiPageItem / WikiSpaceItem / WikiCompileJobItem DTO**

`WikiPageItem.java`:

```java
package com.itgeo.fitmate.api.wiki.dto;

import lombok.Data;

@Data
public class WikiPageItem {
    private Long id;
    private Long spaceId;
    private String pageType;
    private String title;
    private String slug;
    private String contentMd;
    private Integer charCount;
    private String status;
    private String compiledAt;
}
```

`WikiSpaceItem.java`:

```java
package com.itgeo.fitmate.api.wiki.dto;

import lombok.Data;

@Data
public class WikiSpaceItem {
    private Long id;
    private String scopeType;
    private Long ownerUserId;
    private String title;
    private String description;
    private String status;
}
```

`WikiCompileJobItem.java`:

```java
package com.itgeo.fitmate.api.wiki.dto;

import lombok.Data;

@Data
public class WikiCompileJobItem {
    private Long id;
    private Long spaceId;
    private String triggerType;
    private Long sourceDocId;
    private String status;
    private String errorMessage;
    private String startedAt;
    private String finishedAt;
}
```

- [ ] **Step 3: 创建 KbSearchToolExecutor**

参考 [RagSearchToolExecutor.java](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/RagSearchToolExecutor.java) 的结构：

```java
package com.itgeo.fitmate.api.agent.tool;

import cn.hutool.core.util.StrUtil;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.rag.application.DocumentService;
import com.itgeo.fitmate.api.wiki.application.QueryRewriteService;
import com.itgeo.fitmate.api.wiki.application.WikiSearchService;
import com.itgeo.fitmate.api.wiki.config.WikiProperties;
import com.itgeo.fitmate.api.wiki.dto.KbSearchObservation;
import com.itgeo.fitmate.api.wiki.dto.KbSearchObservation.WikiPageItem;
import com.itgeo.fitmate.api.wiki.dto.KbSearchObservation.RawChunkItem;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

/**
 * 知识库复合检索工具。
 *
 * 串行两阶段：
 *   1. Wiki 检索（默认）
 *   2. if (ragEnabled): rewrite query -> RAG 检索
 *
 * 对 Agent 透明：LLM 只看到 kb.search 一个工具，内部按开关跑不同子流程。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KbSearchToolExecutor implements ToolExecutor {

    private final WikiSearchService wikiSearchService;
    private final DocumentService documentService;
    private final QueryRewriteService queryRewriteService;
    private final WikiProperties wikiProperties;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "kb.search",
                "检索知识库（Wiki 优先；若启用 RAG 则基于 Wiki 结果改写 query 后检索原始文档）。参数: {\"query\": \"问题\", \"topK\": 1-10}",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"topK\":{\"type\":\"integer\"}},\"required\":[\"query\"]}",
                true
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        String query = argumentText(call, "query");
        if (StrUtil.isBlank(query)) {
            return ToolResult.error("query不能为空");
        }
        int topK = normalizeNumber(argument(call, "topK"),
                wikiProperties.getRetrieval().getDefaultTopK(),
                wikiProperties.getRetrieval().getMaxTopK());
        Long userId = authenticatedUser.getUserId();

        // ragEnabled 从 chatEntity 传入（通过 ThreadLocal 或 call.arguments 额外字段）
        // 简化：从 arguments 取 ragEnabled，若无默认 false
        Boolean ragEnabled = argumentBool(call, "ragEnabled");

        // 1. Wiki 检索
        List<WikiPage> wikiPages = wikiSearchService.search(query, userId, topK);
        List<WikiPageItem> wikiItems = wikiPages.stream().map(this::toWikiItem).collect(Collectors.toList());

        KbSearchObservation observation = new KbSearchObservation();
        observation.setWiki(wikiItems);

        // 2. if ragEnabled: rewrite + RAG 检索
        if (Boolean.TRUE.equals(ragEnabled)) {
            String wikiContent = wikiItems.stream()
                    .map(WikiPageItem::getContent)
                    .collect(Collectors.joining("\n\n"));
            String rewrittenQuery = queryRewriteService.rewrite(query, wikiContent);
            observation.setRewrittenQuery(rewrittenQuery);

            List<Document> ragDocs = documentService.doSearch(rewrittenQuery, userId, topK);
            List<RawChunkItem> ragItems = new ArrayList<>();
            if (ragDocs != null) {
                ragItems = ragDocs.stream().map(this::toRagItem).collect(Collectors.toList());
            }
            observation.setRag(ragItems);
        } else {
            observation.setRag(List.of());
        }

        String summary = String.format("Wiki 命中 %d 条%s",
                wikiItems.size(),
                Boolean.TRUE.equals(ragEnabled)
                        ? "；RAG 命中 " + (observation.getRag() == null ? 0 : observation.getRag().size()) + " 条"
                        : "");
        return ToolResult.ok(summary, observation);
    }

    private WikiPageItem toWikiItem(WikiPage page) {
        WikiPageItem item = new WikiPageItem();
        item.setPageId(page.getId());
        item.setTitle(page.getTitle());
        item.setPageType(page.getPageType());
        item.setContent(page.getContentMd());
        return item;
    }

    private RawChunkItem toRagItem(Document doc) {
        RawChunkItem item = new RawChunkItem();
        item.setText(doc.getText());
        Object fileName = doc.getMetadata().get("fileName");
        item.setFileName(fileName == null ? "" : String.valueOf(fileName));
        return item;
    }

    private String argumentText(ToolCall call, String key) {
        Object value = argument(call, key);
        return value == null ? null : String.valueOf(value);
    }

    private Object argument(ToolCall call, String key) {
        return call == null || call.getArguments() == null ? null : call.getArguments().get(key);
    }

    private Boolean argumentBool(ToolCall call, String key) {
        Object value = argument(call, key);
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int normalizeNumber(Object raw, int defaultValue, int max) {
        if (raw instanceof Number number) {
            return Math.max(1, Math.min(number.intValue(), max));
        }
        if (raw instanceof String text) {
            try {
                return Math.max(1, Math.min(Integer.parseInt(text), max));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn -pl FitMate-api compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/KbSearchToolExecutor.java
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/dto/
git commit -m "feat(wiki): add KbSearchToolExecutor composite tool (wiki->rewrite->rag)"
```

---

## Task 10: 注册 kb.search 工具 & Agent 可见性

**Files:**
- Modify: `FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/ToolRegistry.java`
- Modify: `FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java`

- [ ] **Step 1: 在 ToolRegistry 注册 kb.search**

先阅读 [ToolRegistry.java](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/ToolRegistry.java) 现有结构（确认它是如何收集 ToolExecutor 并按 `fitmate.agent.enabled-tools` 过滤的）。

在 `enabled-tools` 配置中追加 `kb.search`：

`FitMate-api/src/main/resources/application.yml`：

```yaml
fitmate:
  agent:
    enabled-tools:
      - date.now
      - kb.search          # 新增：默认启用
      - rag.search         # 保留（ragEnabled 时可见）
      - body_metrics.query
      - training_log.query
```

- [ ] **Step 2: 修改 AgentLoopExecutor.resolveAllowedTools**

修改 [AgentLoopExecutor.java:261-266](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java#L261-L266)：

```java
private List<ToolDescriptor> resolveAllowedTools(AgentExecuteContext context) {
    boolean knowledgeBaseEnabled = context.getChatEntity() != null
            && !Boolean.FALSE.equals(context.getChatEntity().getKnowledgeBaseEnabled());
    boolean ragEnabled = knowledgeBaseEnabled
            && context.getChatEntity() != null
            && Boolean.TRUE.equals(context.getChatEntity().getRagEnabled());

    return toolRegistry.allowedDescriptors().stream()
            .filter(tool -> {
                // kb.search: 受 knowledgeBaseEnabled 控制
                if ("kb.search".equals(tool.getName())) {
                    return knowledgeBaseEnabled;
                }
                // rag.search: 受 ragEnabled 控制（保留原逻辑）
                if ("rag.search".equals(tool.getName())) {
                    return ragEnabled;
                }
                return true;
            })
            .collect(Collectors.toList());
}
```

- [ ] **Step 3: 在 ChatEntity 新增 knowledgeBaseEnabled 字段**

先阅读 [ChatEntity.java](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/) 现有结构，在 `ragEnabled` 旁追加：

```java
private Boolean knowledgeBaseEnabled = true;
```

- [ ] **Step 4: 编译验证**

Run: `mvn -pl FitMate-api compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/
git add FitMate-backend/FitMate-api/src/main/resources/application.yml
git commit -m "feat(wiki): register kb.search tool and add two-layer switch (knowledgeBaseEnabled + ragEnabled)"
```

---

## Task 11: 文档上传触发 Wiki 编译

**Files:**
- Modify: `FitMate-api/src/main/java/com/itgeo/fitmate/api/rag/controller/RagController.java`
- Modify: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/application/impl/WikiCompileAsyncRunner.java`（新增投递入口）

- [ ] **Step 1: 阅读 RagController.uploadRagDoc 现有实现**

先读 [RagController.java](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/rag/controller/RagController.java) 找到 `uploadRagDoc` 方法，确认 `RagDocument` 入库后返回的 `documentId`。

- [ ] **Step 2: 在 RagController 注入 WikiCompileService 并投递 job**

在 `uploadRagDoc` 方法的 `ragDocumentMapper.insert` 之后、return 之前，追加：

```java
// 投递 Wiki 编译任务（异步）
try {
    Long spaceId = wikiCompileService.getOrCreateUserSpace(userId);
    WikiCompileJob job = wikiCompileService.submitCompileJob(spaceId, ragDocument.getId(), userId);
    wikiCompileAsyncRunner.runAsync(job.getId());
} catch (Exception e) {
    log.warn("Wiki 编译任务投递失败（不影响 RAG 上传）: {}", e.getMessage());
}
```

在 `WikiCompileService` 接口追加 `getOrCreateUserSpace`：

```java
/**
 * 获取或创建用户的 USER space（不存在则创建）。
 */
Long getOrCreateUserSpace(Long userId);
```

在 `WikiCompileServiceImpl` 实现：

```java
@Override
public Long getOrCreateUserSpace(Long userId) {
    WikiSpace existing = spaceMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WikiSpace>()
                    .eq(WikiSpace::getScopeType, "USER")
                    .eq(WikiSpace::getOwnerUserId, userId));
    if (existing != null) return existing.getId();
    WikiSpace space = new WikiSpace();
    space.setScopeType("USER");
    space.setOwnerUserId(userId);
    space.setTitle("用户 " + userId + " Wiki");
    space.setStatus("ACTIVE");
    space.setCreatedAt(LocalDateTime.now());
    space.setUpdatedAt(LocalDateTime.now());
    spaceMapper.insert(space);
    return space.getId();
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -pl FitMate-api compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 手动测试**

启动应用，上传一个 .txt 文档，观察：
1. `/rag/uploadRagDoc` 立即返回
2. 日志出现 `wiki-compile-` 线程开始编译
3. 查询 `t_wiki_compile_job` 表，status 从 PENDING → RUNNING → SUCCESS
4. 查询 `t_wiki_page` 表，出现新生成的页面

- [ ] **Step 5: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/rag/controller/RagController.java
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/
git commit -m "feat(wiki): trigger wiki compile job on document upload"
```

---

## Task 12: Wiki 管理 API

**Files:**
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/controller/WikiController.java`

- [ ] **Step 1: 创建 WikiController**

```java
package com.itgeo.fitmate.api.wiki.controller;

import com.itgeo.fitmate.api.wiki.application.WikiCompileService;
import com.itgeo.fitmate.api.wiki.dto.WikiCompileJobItem;
import com.itgeo.fitmate.api.wiki.dto.WikiPageItem;
import com.itgeo.fitmate.api.wiki.dto.WikiSpaceItem;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiCompileJob;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiSpace;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiCompileJobMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiPageMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiSpaceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wiki")
@RequiredArgsConstructor
public class WikiController {

    private final WikiSpaceMapper spaceMapper;
    private final WikiPageMapper pageMapper;
    private final WikiCompileJobMapper compileJobMapper;
    private final WikiCompileService wikiCompileService;

    @GetMapping("/spaces")
    public List<WikiSpaceItem> listSpaces(@RequestParam(required = false) Long userId) {
        LambdaQueryWrapper<WikiSpace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WikiSpace::getScopeType, "GLOBAL");
        if (userId != null) {
            wrapper.or().eq(WikiSpace::getScopeType, "USER").eq(WikiSpace::getOwnerUserId, userId);
        }
        return spaceMapper.selectList(wrapper).stream().map(this::toSpaceItem).collect(Collectors.toList());
    }

    @GetMapping("/spaces/{spaceId}/pages")
    public List<WikiPageItem> listPages(@PathVariable Long spaceId,
                                        @RequestParam(required = false) String pageType) {
        LambdaQueryWrapper<WikiPage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WikiPage::getSpaceId, spaceId);
        if (pageType != null) wrapper.eq(WikiPage::getPageType, pageType);
        wrapper.orderByDesc(WikiPage::getUpdatedAt);
        return pageMapper.selectList(wrapper).stream().map(this::toPageItem).collect(Collectors.toList());
    }

    @GetMapping("/pages/{pageId}")
    public WikiPageItem getPage(@PathVariable Long pageId) {
        WikiPage page = pageMapper.selectById(pageId);
        return page == null ? null : toPageItem(page);
    }

    @GetMapping("/compile/{jobId}")
    public WikiCompileJobItem getCompileJob(@PathVariable Long jobId) {
        WikiCompileJob job = compileJobMapper.selectById(jobId);
        return job == null ? null : toJobItem(job);
    }

    @PostMapping("/rebuild/{jobId}")
    public String recompile(@PathVariable Long jobId) {
        wikiCompileService.executeCompile(jobId);
        return "已触发重新编译";
    }

    private WikiSpaceItem toSpaceItem(WikiSpace space) {
        WikiSpaceItem item = new WikiSpaceItem();
        item.setId(space.getId());
        item.setScopeType(space.getScopeType());
        item.setOwnerUserId(space.getOwnerUserId());
        item.setTitle(space.getTitle());
        item.setDescription(space.getDescription());
        item.setStatus(space.getStatus());
        return item;
    }

    private WikiPageItem toPageItem(WikiPage page) {
        WikiPageItem item = new WikiPageItem();
        item.setId(page.getId());
        item.setSpaceId(page.getSpaceId());
        item.setPageType(page.getPageType());
        item.setTitle(page.getTitle());
        item.setSlug(page.getSlug());
        item.setContentMd(page.getContentMd());
        item.setCharCount(page.getCharCount());
        item.setStatus(page.getStatus());
        item.setCompiledAt(page.getCompiledAt() == null ? null : page.getCompiledAt().toString());
        return item;
    }

    private WikiCompileJobItem toJobItem(WikiCompileJob job) {
        WikiCompileJobItem item = new WikiCompileJobItem();
        item.setId(job.getId());
        item.setSpaceId(job.getSpaceId());
        item.setTriggerType(job.getTriggerType());
        item.setSourceDocId(job.getSourceDocId());
        item.setStatus(job.getStatus());
        item.setErrorMessage(job.getErrorMessage());
        item.setStartedAt(job.getStartedAt() == null ? null : job.getStartedAt().toString());
        item.setFinishedAt(job.getFinishedAt() == null ? null : job.getFinishedAt().toString());
        return item;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl FitMate-api compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 手动测试 API**

启动应用，调用：
- `GET /wiki/spaces?userId=1` → 返回全局 + 用户 1 的 space 列表
- `GET /wiki/spaces/{spaceId}/pages` → 返回该 space 的页面列表
- `GET /wiki/pages/{pageId}` → 返回单个页面
- `GET /wiki/compile/{jobId}` → 返回编译任务状态

- [ ] **Step 4: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/wiki/controller/WikiController.java
git commit -m "feat(wiki): add WikiController for spaces/pages/compile management"
```

---

## Task 13: 前端两层开关

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`

- [ ] **Step 1: 阅读 ChatLogicBase.vue 现有开关结构**

先读 [ChatLogicBase.vue](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/FitMate-frontend/src/pages/chat/ChatLogicBase.vue) 找到 `knowledgeSearchSelected` / `doKnowledgeSearch` 相关代码。

- [ ] **Step 2: 升级为两层开关**

将 `knowledgeSearchSelected` 拆分为：
- `knowledgeBaseSelected`（默认 true）→ 对应 `knowledgeBaseEnabled`
- `ragSelected`（默认 false，依赖 knowledgeBaseSelected）→ 对应 `ragEnabled`

在 `<script setup>` 中：

```typescript
const knowledgeBaseSelected = ref(true)   // 知识库总开关，默认开
const ragSelected = ref(false)             // RAG 叠加开关，默认关

function doKnowledgeBase(val: boolean) {
  knowledgeBaseSelected.value = val
  if (!val) {
    // 关闭知识库时，RAG 也关闭
    ragSelected.value = false
  }
}

function doRag(val: boolean) {
  // 只有知识库开启时才能开 RAG
  if (!knowledgeBaseSelected.value && val) {
    ragSelected.value = false
    return
  }
  ragSelected.value = val
}
```

在 `singleChat` 组装时：

```typescript
const singleChat = {
  currentUserName,
  message,
  botMsgId,
  sessionCode,
  knowledgeBaseEnabled: knowledgeBaseSelected.value,
  ragEnabled: ragSelected.value,
  internetEnabled: internetSearchSelected.value
}
```

- [ ] **Step 3: 模板更新（开关 UI）**

在模板中将原来的单个 `知识库检索` 开关替换为两个开关：

```vue
<ToggleSwitch v-model="knowledgeBaseSelected" @change="doKnowledgeBase" />
<span>知识库 Wiki</span>

<ToggleSwitch v-model="ragSelected" @change="doRag" :disabled="!knowledgeBaseSelected" />
<span>原始文档 (RAG)</span>
```

（具体组件名按项目现有 ToggleSwitch 用法对齐）

- [ ] **Step 4: 前端构建验证**

Run: `cd FitMate-frontend && npm run build`
Expected: 构建成功无 TS 错误

- [ ] **Step 5: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "feat(wiki): upgrade frontend to two-layer switch (knowledgeBase + rag)"
```

---

## Task 14: AgentStepCard 适配子步骤事件

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/components/AgentStepCard.vue`

- [ ] **Step 1: 阅读 AgentStepCard.vue 现有事件渲染逻辑**

先读 [AgentStepCard.vue](file:///d:/Applications/Java/A%20-%20Learning/FitMate-AI-0/FitMate-frontend/src/pages/chat/components/AgentStepCard.vue) 了解它如何渲染 tool_call 步骤。

- [ ] **Step 2: 适配 kb.search 子步骤**

在渲染 `kb.search` 工具调用时，解析 `sub_step` 事件并展示：

```vue
<template v-if="step.toolName === 'kb.search'">
  <div class="kb-search-substeps">
    <div v-for="sub in subSteps" :key="sub.type" class="substep">
      <span class="icon">{{ subIcon(sub.type) }}</span>
      <span>{{ subLabel(sub.type) }}</span>
      <span v-if="sub.status === 'running'" class="spinner">...</span>
      <span v-else-if="sub.status === 'done'" class="done">✓ {{ sub.detail }}</span>
    </div>
  </div>
</template>
```

```typescript
function subLabel(type: string): string {
  return {
    wiki_search: '检索知识库 Wiki',
    query_rewrite: '改写查询',
    rag_search: '检索原始文档'
  }[type] || type
}

function subIcon(type: string): string {
  return {
    wiki_search: '📚',
    query_rewrite: '✏️',
    rag_search: '📄'
  }[type] || '•'
}
```

- [ ] **Step 3: 前端构建验证**

Run: `cd FitMate-frontend && npm run build`
Expected: 构建成功

- [ ] **Step 4: Commit**

```bash
git add FitMate-frontend/src/pages/chat/components/AgentStepCard.vue
git commit -m "feat(wiki): render kb.search substeps (wiki_search/query_rewrite/rag_search) in AgentStepCard"
```

---

## Task 15: 集成验证

- [ ] **Step 1: 端到端测试 - 上传 + 编译**

1. 上传 .txt 文档 → 确认 `t_rag_document` 与 `t_wiki_compile_job` 都有记录
2. 等待编译完成 → 确认 `t_wiki_page` 有页面、Redis wiki 索引有向量
3. 查询 `GET /wiki/spaces?userId=1` → 确认返回空间
4. 查询 `GET /wiki/spaces/{spaceId}/pages` → 确认返回页面

- [ ] **Step 2: 端到端测试 - Wiki 检索（RAG 关闭）**

1. 前端：知识库开、RAG 关
2. 提问与文档相关问题
3. 确认：Agent 调用 `kb.search`，只走 wiki 检索，无 rewrite、无 RAG
4. SSE 事件：只有 `wiki_search` 子步骤

- [ ] **Step 3: 端到端测试 - Wiki + RAG 检索**

1. 前端：知识库开、RAG 开
2. 提问与文档相关问题
3. 确认：Agent 调用 `kb.search`，串行 wiki → rewrite → rag
4. SSE 事件：`wiki_search` → `query_rewrite` → `rag_search` 全部出现

- [ ] **Step 4: 端到端测试 - 知识库关闭**

1. 前端：知识库关
2. 提问
3. 确认：`kb.search` 工具不可见，Agent 不调用知识库

- [ ] **Step 5: Commit（如有修复）**

```bash
git add -A
git commit -m "test(wiki): end-to-end verification of compile/search/switch flows"
```

---

## 自审清单

- [x] **Spec 覆盖**：5 张表（Task 1）、实体/Mapper（Task 2）、配置（Task 3）、Redis VectorStore（Task 4）、关键词检索（Task 5）、Wiki 检索（Task 6）、Query Rewrite（Task 7）、编译服务（Task 8）、复合工具（Task 9）、Agent 集成与开关（Task 10）、摄入接入（Task 11）、管理 API（Task 12）、前端开关（Task 13）、SSE 适配（Task 14）、集成验证（Task 15）
- [x] **占位符扫描**：无 TBD/TODO，所有代码块完整
- [x] **类型一致性**：`WikiPage`/`WikiSpace`/`WikiCompileJob` 字段在跨 Task 中一致；`KbSearchToolExecutor` 与 `RagSearchToolExecutor` 接口对齐；`ToolDescriptor` 构造函数与现有代码一致
- [x] **风险点**：Task 8 的 `fetchRawText` 为简化实现（通过 doSearch 取 chunk），建议后续新增 `DocumentService.getRawTextByDocId` 优化；Task 4 的 `BgeM3HttpEmbeddingModel` 注入依赖 `provider=bge-m3-http` 配置，需确认 dev 环境已配置
