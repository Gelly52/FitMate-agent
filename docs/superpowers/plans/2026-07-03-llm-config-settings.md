# LLM 配置与聊天页输入框改造 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在设置页新增「配置」区块让用户自行配置 DeepSeek API URL/Key/模型/上下文/思考模式/推理强度，后端按用户动态解析（DB 覆盖 env，API Key AES 加密落库），聊天页输入框左下角改为模型选择下拉，删除 disclaimer，placeholder 改淡。

**Architecture:** 扩展 `t_user_preference` 加 `llm_config_json` 列（per-user，AES 加密 apiKey）；新增 `LlmConfigResolver` 做 DB 覆盖 env 解析；`ReasoningChatClient` 移除 `@Value` 改为动态解析；新增 4 个 `/user/llm*` 接口（GET/PUT 配置 + list-models + test 代理）；前端新增 `llmConfig.ts` 状态服务 + `LlmConfigSection.vue` + `ModelSelector.vue`。

**Tech Stack:** 后端 Spring Boot 3 + MyBatis-Plus + Hutool + Jackson；前端 Vue 3 Options API + TS + Tailwind + axios；DeepSeek OpenAI-compatible API。

**关联 spec:** `docs/superpowers/specs/2026-07-03-llm-config-settings-design.md`

**项目约定（来自 project_memory）：**
- YAML 2 空格缩进，顶层 section 顺序 `server → website → spring → reasoning → rag → internet → fitmate → logging → mybatis-plus`，section 间 1 空行、section 内无空行
- 配置文件仅 `application.yml` 与 `application-{profile}.yml` 生效，不修改配置值只规范化格式
- 前端 Vue Options API，`<Feature>Page.vue` 命名，扁平 `components/`，`doctorApi.ts` 集中 API

**测试基建：** 后端有 JUnit（`src/test/java`）；前端无测试框架，按现有约定不强制加前端测试。

---

## 文件结构总览

### 后端新增/修改
```
FitMate-api/src/main/java/com/itgeo/fitmate/api/
  auth/
    dto/LlmConfigItem.java                    # 新增 · GET 返回用（apiKey 脱敏）
    dto/LlmConfigSaveRequest.java             # 新增 · PUT 请求用（apiKey 明文可空）
    controller/UserController.java            # 修改 · 追加 4 个 LLM 接口
  chat/
    application/LlmConfigResolver.java        # 新增 · 配置解析（DB 覆盖 env）
    application/ResolvedLlmConfig.java        # 新增 · 内部 DTO（apiKey 明文）
    infrastructure/LlmConfigCipher.java       # 新增 · AES/GCM 加解密 + 脱敏
    infrastructure/LlmProxyClient.java        # 新增 · 代理调 DeepSeek list-models/test
    infrastructure/ReasoningChatClient.java   # 修改 · 移除 @Value，注入 LlmConfigResolver
    dto/LlmModelItem.java                     # 新增
    dto/LlmTestResult.java                    # 新增
    dto/LlmProxyRequest.java                  # 新增 · list-models/test 请求体
  config/LlmConfigProperties.java             # 新增 · @ConfigurationProperties("fitmate.llm")
FitMate-api/src/main/resources/
  application.yml                             # 修改 · 新增 fitmate.llm 配置块
  application-dev.yml                         # 修改 · 新增 fitmate.llm.encryption-key 默认值
FitMate-mcpServer/src/main/resources/sql/
  fitmate_init.sql                            # 修改 · t_user_preference 加 llm_config_json 列
FitMate-api/src/test/java/com/itgeo/fitmate/api/chat/infrastructure/
  LlmConfigCipherTest.java                    # 新增 · 加解密单元测试
```

### 前端新增/修改
```
FitMate-frontend/src/
  types/settings.ts                           # 修改 · 追加 LlmConfig 等类型
  services/llmConfig.ts                       # 新增 · LLM 配置状态管理
  services/doctorApi.ts                       # 修改 · 追加 4 个 LLM API 方法
  layouts/AppLayout.vue                       # 修改 · mounted 调 llmConfig.load()
  pages/settings/
    SettingsPage.vue                          # 修改 · 新增 llm 区块分支
    components/SettingsSectionNav.vue         # 修改 · 新增 llm 导航项
    components/LlmConfigSection.vue           # 新增 · LLM 配置表单
  pages/chat/
    ChatLogicBase.vue                         # 修改 · 订阅 llmConfig、新增 currentModel/availableModels
    ChatPage.vue                              # 修改 · 传入新 props
    components/ChatInput.vue                  # 修改 · 删 disclaimer、加 ModelSelector、placeholder 改淡
    components/ModelSelector.vue              # 新增 · 模型选择下拉
```

---

## Task 1: DDL — t_user_preference 加 llm_config_json 列

**Files:**
- Modify: `FitMate-backend/FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql`（`t_user_preference` 建表语句约在 362 行）

- [ ] **Step 1: 修改 fitmate_init.sql 建表语句**

在 `preferences_json` 行之后、`created_at` 之前插入 `llm_config_json` 列：

```sql
CREATE TABLE IF NOT EXISTS `t_user_preference` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '偏好主键',
    `user_id` BIGINT NOT NULL COMMENT '所属用户主键',
    `preferences_json` JSON NOT NULL COMMENT '偏好设置 JSON，如 {"themeMode":"dark","accentColor":"blue"}',
    `llm_config_json` JSON NULL COMMENT 'LLM 配置 JSON，apiKey 字段为 AES 加密密文',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_preference_user` (`user_id`),
    CONSTRAINT `fk_user_preference_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户偏好设置表';
```

- [ ] **Step 2: 对已有数据库执行 ALTER（如库已存在）**

在 MySQL 执行（通过 MCP execute_sql 或手动）：
```sql
ALTER TABLE `t_user_preference`
  ADD COLUMN `llm_config_json` JSON NULL COMMENT 'LLM 配置 JSON，apiKey 字段为 AES 加密密文' AFTER `preferences_json`;
```

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql
git commit -m "feat(db): add llm_config_json column to t_user_preference"
```

---

## Task 2: UserPreference 实体加 llmConfigJson 字段

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/infrastructure/entity/UserPreference.java`

- [ ] **Step 1: 在 preferencesJson 字段后追加 llmConfigJson**

在 `private String preferencesJson;` 之后（约 27 行后）插入：

```java
    /** LLM 配置 JSON，原始字符串读写，由 service 层序列化/反序列化，apiKey 字段为 AES 加密密文。 */
    private String llmConfigJson;
```

（MyBatis-Plus 默认驼峰转下划线，`llmConfigJson` → `llm_config_json`，无需 `@TableField`）

- [ ] **Step 2: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/infrastructure/entity/UserPreference.java
git commit -m "feat(entity): add llmConfigJson field to UserPreference"
```

---

## Task 3: 后端 DTO — LlmConfigItem / LlmConfigSaveRequest / ResolvedLlmConfig / LlmModelItem / LlmTestResult / LlmProxyRequest

**Files:**
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/dto/LlmConfigItem.java`
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/dto/LlmConfigSaveRequest.java`
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/ResolvedLlmConfig.java`
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/dto/LlmModelItem.java`
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/dto/LlmTestResult.java`
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/dto/LlmProxyRequest.java`

- [ ] **Step 1: 创建 LlmConfigItem.java**

```java
package com.itgeo.fitmate.api.auth.dto;

import lombok.Data;

/**
 * LLM 配置项（GET 返回用，apiKey 脱敏）。
 */
@Data
public class LlmConfigItem {
    private String baseUrl;
    /** 脱敏值，如 sk-****e05f */
    private String apiKey;
    private String model;
    private Integer maxInputContextTokens;
    private Integer maxOutputContextTokens;
    private Boolean thinkingEnabled;
    /** high / max */
    private String reasoningEffort;
}
```

- [ ] **Step 2: 创建 LlmConfigSaveRequest.java**

```java
package com.itgeo.fitmate.api.auth.dto;

import lombok.Data;

/**
 * LLM 配置保存请求（PUT 用，apiKey 明文，空表示不修改原值）。
 */
@Data
public class LlmConfigSaveRequest {
    private String baseUrl;
    /** 明文，可为空（空表示不修改原 key） */
    private String apiKey;
    private String model;
    private Integer maxInputContextTokens;
    private Integer maxOutputContextTokens;
    private Boolean thinkingEnabled;
    private String reasoningEffort;
}
```

- [ ] **Step 3: 创建 ResolvedLlmConfig.java**

```java
package com.itgeo.fitmate.api.chat.application;

import lombok.Data;

/**
 * 已解析的 LLM 配置（内部使用，apiKey 为明文）。
 */
@Data
public class ResolvedLlmConfig {
    private String baseUrl;
    private String apiKey;
    private String model;
    private Integer maxInputContextTokens;
    private Integer maxOutputContextTokens;
    private Boolean thinkingEnabled;
    private String reasoningEffort;
}
```

- [ ] **Step 4: 创建 LlmModelItem.java**

```java
package com.itgeo.fitmate.api.chat.dto;

import lombok.Data;

/**
 * DeepSeek 模型列表项。
 */
@Data
public class LlmModelItem {
    private String id;
    private String ownedBy;
}
```

- [ ] **Step 5: 创建 LlmTestResult.java**

```java
package com.itgeo.fitmate.api.chat.dto;

import lombok.Data;

/**
 * LLM 测活结果。
 */
@Data
public class LlmTestResult {
    private Boolean ok;
    private String model;
    private Long latencyMs;
    private String error;
}
```

- [ ] **Step 6: 创建 LlmProxyRequest.java**

```java
package com.itgeo.fitmate.api.chat.dto;

import lombok.Data;

/**
 * list-models / test 代理请求体。字段为空时后端用当前用户已存配置。
 */
@Data
public class LlmProxyRequest {
    private String baseUrl;
    private String apiKey;
    private String model;
}
```

- [ ] **Step 7: Commit**

```bash
git add FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/dto/LlmConfigItem.java FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/dto/LlmConfigSaveRequest.java FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/ResolvedLlmConfig.java FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/dto/LlmModelItem.java FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/dto/LlmTestResult.java FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/dto/LlmProxyRequest.java
git commit -m "feat(dto): add LLM config and proxy DTOs"
```

---

## Task 4: LlmConfigProperties — @ConfigurationProperties

**Files:**
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/config/LlmConfigProperties.java`

- [ ] **Step 1: 创建 LlmConfigProperties.java**

```java
package com.itgeo.fitmate.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 配置属性（fitmate.llm.*）。
 * encryption-key 用于 AES 加密 apiKey；default.* 为 DB 无用户配置时的回退值。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fitmate.llm")
public class LlmConfigProperties {
    /** AES-256 密钥（32 字节 Base64），env 注入，启动时 fail-fast 校验 */
    private String encryptionKey;
    private DefaultConfig defaultConfig = new DefaultConfig();

    @Data
    public static class DefaultConfig {
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        private String model = "deepseek-v4-flash";
        private Integer maxInputContextTokens = 204800;
        private Integer maxOutputContextTokens = 65536;
        private Boolean thinkingEnabled = true;
        private String reasoningEffort = "high";
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-api/src/main/java/com/itgeo/fitmate/api/config/LlmConfigProperties.java
git commit -m "feat(config): add LlmConfigProperties for fitmate.llm"
```

---

## Task 5: LlmConfigCipher — AES/GCM 加解密 + 脱敏

**Files:**
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/infrastructure/LlmConfigCipher.java`
- Create: `FitMate-api/src/test/java/com/itgeo/fitmate/api/chat/infrastructure/LlmConfigCipherTest.java`

- [ ] **Step 1: 先写失败测试 LlmConfigCipherTest.java**

```java
package com.itgeo.fitmate.api.chat.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class LlmConfigCipherTest {

    private LlmConfigCipher cipher;

    @BeforeEach
    void setUp() {
        // 32 字节密钥 Base64
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) key[i] = (byte) i;
        String encryptionKey = Base64.getEncoder().encodeToString(key);
        cipher = new LlmConfigCipher(encryptionKey);
    }

    @Test
    void encrypt_then_decrypt_roundTrip() {
        String plain = "your_openai_api_key";
        String encrypted = cipher.encrypt(plain);
        assertNotEquals(plain, encrypted);
        assertEquals(plain, cipher.decrypt(encrypted));
    }

    @Test
    void decrypt_empty_returns_empty() {
        assertEquals("", cipher.decrypt(""));
        assertEquals("", cipher.decrypt(null));
    }

    @Test
    void mask_keeps_prefix3_and_suffix4() {
        String plain = "your_openai_api_key";
        String masked = cipher.mask(plain);
        assertTrue(masked.startsWith("sk-"));
        assertTrue(masked.endsWith("e05f"));
        assertTrue(masked.contains("****"));
    }

    @Test
    void mask_shortInput_returns_asterisks() {
        assertEquals("****", cipher.mask("ab"));
        assertEquals("****", cipher.mask(""));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl FitMate-api test -Dtest=LlmConfigCipherTest -q`（在 `FitMate-backend` 目录下）
Expected: FAIL（LlmConfigCipher 类不存在）

- [ ] **Step 3: 实现 LlmConfigCipher.java**

```java
package com.itgeo.fitmate.api.chat.infrastructure;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLM API Key 加解密与脱敏工具（AES-256/GCM）。
 * 密钥来自 fitmate.llm.encryption-key（32 字节 Base64）。
 */
@Slf4j
@Component
public class LlmConfigCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final String encryptionKeyBase64;
    private SecretKeySpec secretKey;

    public LlmConfigCipher(String encryptionKeyBase64) {
        this.encryptionKeyBase64 = encryptionKeyBase64;
    }

    @PostConstruct
    public void init() {
        if (StrUtil.isBlank(encryptionKeyBase64)) {
            throw new IllegalStateException("fitmate.llm.encryption-key 未配置，请通过 env LLM_ENCRYPTION_KEY 注入 32 字节 Base64 密钥");
        }
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("fitmate.llm.encryption-key 解码后必须为 32 字节，当前=" + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("LlmConfigCipher 初始化成功");
    }

    /** 加密明文 → Base64(IV + ciphertext) */
    public String encrypt(String plain) {
        if (StrUtil.isBlank(plain)) {
            return "";
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 加密失败", e);
        }
    }

    /** 解密 Base64(IV + ciphertext) → 明文；空值返回空 */
    public String decrypt(String encrypted) {
        if (StrUtil.isBlank(encrypted)) {
            return "";
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("API Key 解密失败", e);
        }
    }

    /** 脱敏：保留前 3 + 后 4，中间 ****；过短直接 **** */
    public String mask(String plain) {
        if (StrUtil.isBlank(plain) || plain.length() < 8) {
            return "****";
        }
        return plain.substring(0, 3) + "****" + plain.substring(plain.length() - 4);
    }
}
```

注意：`LlmConfigCipher` 不用 `@Component` 注解的自动注入（因构造参数来自 `@ConfigurationProperties`），改为通过 `@Configuration` 显式 Bean 注册。

- [ ] **Step 4: 调整为 @Configuration 显式注册**

将 `LlmConfigCipher` 类上的 `@Component` 去掉（保留 `@Slf4j`），新增配置类 `FitMate-api/src/main/java/com/itgeo/fitmate/api/config/LlmConfigBeanConfig.java`：

```java
package com.itgeo.fitmate.api.config;

import com.itgeo.fitmate.api.chat.infrastructure.LlmConfigCipher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfigBeanConfig {

    @Bean
    public LlmConfigCipher llmConfigCipher(LlmConfigProperties properties) {
        return new LlmConfigCipher(properties.getEncryptionKey());
    }
}
```

同时修改测试 `setUp()`，直接 `new LlmConfigCipher(encryptionKey)`（已符合，因构造参数即 key）。

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -pl FitMate-api test -Dtest=LlmConfigCipherTest -q`（在 `FitMate-backend` 目录下）
Expected: PASS（4 个测试全通过）

- [ ] **Step 6: Commit**

```bash
git add FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/infrastructure/LlmConfigCipher.java FitMate-api/src/main/java/com/itgeo/fitmate/api/config/LlmConfigBeanConfig.java FitMate-api/src/test/java/com/itgeo/fitmate/api/chat/infrastructure/LlmConfigCipherTest.java
git commit -m "feat(infra): add LlmConfigCipher with AES/GCM encryption and tests"
```

---

## Task 6: LlmConfigResolver — DB 覆盖 env 配置解析

**Files:**
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/LlmConfigResolver.java`

- [ ] **Step 1: 创建 LlmConfigResolver.java**

```java
package com.itgeo.fitmate.api.chat.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.auth.dto.LlmConfigItem;
import com.itgeo.fitmate.api.auth.dto.LlmConfigSaveRequest;
import com.itgeo.fitmate.api.auth.infrastructure.entity.UserPreference;
import com.itgeo.fitmate.api.auth.infrastructure.mapper.UserPreferenceMapper;
import com.itgeo.fitmate.api.chat.infrastructure.LlmConfigCipher;
import com.itgeo.fitmate.api.config.LlmConfigProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLM 配置解析器：DB 覆盖 env。
 * DB 有 llm_config_json 时解密并使用 DB 值；DB 无值时回退 env 默认值。
 */
@Slf4j
@Component
public class LlmConfigResolver {

    @Resource
    private UserPreferenceMapper userPreferenceMapper;

    @Resource
    private LlmConfigCipher cipher;

    @Resource
    private LlmConfigProperties llmConfigProperties;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 解析当前登录用户的 LLM 配置。
     * 无登录用户时回退 env 默认值（兼容系统级调用）。
     */
    public ResolvedLlmConfig resolveForCurrentUser() {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return resolveByUserId(userId);
        } catch (Exception e) {
            log.warn("无登录用户上下文，回退 env 默认 LLM 配置: {}", e.getMessage());
            return resolveFromEnv();
        }
    }

    /** 解析指定用户的 LLM 配置 */
    public ResolvedLlmConfig resolveByUserId(Long userId) {
        UserPreference pref = loadPreference(userId);
        if (pref == null || StrUtil.isBlank(pref.getLlmConfigJson())) {
            return resolveFromEnv();
        }
        try {
            JSONObject json = JSONUtil.parseObj(pref.getLlmConfigJson());
            ResolvedLlmConfig config = new ResolvedLlmConfig();
            config.setBaseUrl(json.getStr("baseUrl", envDefault().getBaseUrl()));
            String encryptedKey = json.getStr("apiKey", "");
            config.setApiKey(StrUtil.isBlank(encryptedKey) ? envDefault().getApiKey() : cipher.decrypt(encryptedKey));
            config.setModel(json.getStr("model", envDefault().getModel()));
            config.setMaxInputContextTokens(json.getInt("maxInputContextTokens", envDefault().getMaxInputContextTokens()));
            config.setMaxOutputContextTokens(json.getInt("maxOutputContextTokens", envDefault().getMaxOutputContextTokens()));
            config.setThinkingEnabled(json.getBool("thinkingEnabled", envDefault().getThinkingEnabled()));
            config.setReasoningEffort(json.getStr("reasoningEffort", envDefault().getReasoningEffort()));
            return config;
        } catch (Exception e) {
            log.warn("解析用户 LLM 配置 JSON 失败，回退 env: userId={}", userId, e);
            return resolveFromEnv();
        }
    }

    /** 获取脱敏配置（供 GET 接口返回） */
    public LlmConfigItem getByUserId(Long userId) {
        ResolvedLlmConfig resolved = resolveByUserId(userId);
        LlmConfigItem item = new LlmConfigItem();
        item.setBaseUrl(resolved.getBaseUrl());
        item.setApiKey(cipher.mask(resolved.getApiKey()));
        item.setModel(resolved.getModel());
        item.setMaxInputContextTokens(resolved.getMaxInputContextTokens());
        item.setMaxOutputContextTokens(resolved.getMaxOutputContextTokens());
        item.setThinkingEnabled(resolved.getThinkingEnabled());
        item.setReasoningEffort(resolved.getReasoningEffort());
        return item;
    }

    /** 保存用户 LLM 配置（apiKey 加密落库；apiKey 为空保留原密文） */
    public void saveByUserId(Long userId, LlmConfigSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("LLM 配置不能为空");
        }
        if (StrUtil.isBlank(request.getBaseUrl())) {
            throw new IllegalArgumentException("API 地址不能为空");
        }
        UserPreference existing = loadPreference(userId);
        String existingEncryptedKey = "";
        if (existing != null && StrUtil.isNotBlank(existing.getLlmConfigJson())) {
            try {
                JSONObject oldJson = JSONUtil.parseObj(existing.getLlmConfigJson());
                existingEncryptedKey = oldJson.getStr("apiKey", "");
            } catch (Exception ignored) {
            }
        }
        // apiKey：请求非空则加密新值；为空则保留原密文（若有）
        String encryptedKey;
        if (StrUtil.isNotBlank(request.getApiKey())) {
            encryptedKey = cipher.encrypt(request.getApiKey());
        } else {
            encryptedKey = existingEncryptedKey;
        }
        if (StrUtil.isBlank(encryptedKey)) {
            throw new IllegalArgumentException("API Key 不能为空");
        }

        JSONObject json = new JSONObject();
        json.set("baseUrl", request.getBaseUrl());
        json.set("apiKey", encryptedKey);
        json.set("model", StrUtil.blankToDefault(request.getModel(), envDefault().getModel()));
        json.set("maxInputContextTokens", request.getMaxInputContextTokens() != null ? request.getMaxInputContextTokens() : envDefault().getMaxInputContextTokens());
        json.set("maxOutputContextTokens", request.getMaxOutputContextTokens() != null ? request.getMaxOutputContextTokens() : envDefault().getMaxOutputContextTokens());
        json.set("thinkingEnabled", request.getThinkingEnabled() != null ? request.getThinkingEnabled() : envDefault().getThinkingEnabled());
        json.set("reasoningEffort", StrUtil.blankToDefault(request.getReasoningEffort(), envDefault().getReasoningEffort()));
        String jsonStr = json.toString();

        if (existing == null) {
            UserPreference pref = new UserPreference();
            pref.setUserId(userId);
            pref.setPreferencesJson(objectMapper.writeValueAsString(new com.itgeo.fitmate.api.auth.dto.UserPreferenceItem()));
            pref.setLlmConfigJson(jsonStr);
            userPreferenceMapper.insert(pref);
        } else {
            existing.setLlmConfigJson(jsonStr);
            userPreferenceMapper.updateById(existing);
        }
    }

    private ResolvedLlmConfig resolveFromEnv() {
        LlmConfigProperties.DefaultConfig d = envDefault();
        ResolvedLlmConfig config = new ResolvedLlmConfig();
        config.setBaseUrl(d.getBaseUrl());
        config.setApiKey(d.getApiKey());
        config.setModel(d.getModel());
        config.setMaxInputContextTokens(d.getMaxInputContextTokens());
        config.setMaxOutputContextTokens(d.getMaxOutputContextTokens());
        config.setThinkingEnabled(d.getThinkingEnabled());
        config.setReasoningEffort(d.getReasoningEffort());
        return config;
    }

    private LlmConfigProperties.DefaultConfig envDefault() {
        return llmConfigProperties.getDefaultConfig();
    }

    private UserPreference loadPreference(Long userId) {
        LambdaQueryWrapper<UserPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPreference::getUserId, userId);
        return userPreferenceMapper.selectOne(wrapper);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/application/LlmConfigResolver.java
git commit -m "feat(app): add LlmConfigResolver for DB-over-env config resolution"
```

---

## Task 7: application.yml 新增 fitmate.llm 配置块

**Files:**
- Modify: `FitMate-api/src/main/resources/application.yml`
- Modify: `FitMate-api/src/main/resources/application-dev.yml`

- [ ] **Step 1: application.yml 在 fitmate 段追加 llm 子段**

在 `application.yml` 的 `fitmate:` 段（约 80 行）下，`agent:` 之前插入 `llm:` 子段（注意 YAML 约定：2 空格缩进，section 内无空行）：

```yaml
# FitMate
fitmate:
  llm:
    encryption-key: ${LLM_ENCRYPTION_KEY:}
    default:
      base-url: ${OPENAI_BASE_URL:https://api.deepseek.com}
      api-key: ${OPENAI_API_KEY:}
      model: ${OPENAI_MODEL:deepseek-v4-flash}
      max-input-context-tokens: 204800
      max-output-context-tokens: 65536
      thinking-enabled: true
      reasoning-effort: ${REASONING_EFFORT:high}
  agent:
    max-iterations: 20
    max-tool-calls: 100
    max-run-duration-seconds: 1800
    llm-timeout-seconds: 120
    tool-timeout-seconds: 30
    memory-window-size: 20
    enabled-tools:
      - date.now
      - kb.search
      - rag.search
      - body_metrics.query
      - training_log.query
      - web.search
      - web.fetch
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

- [ ] **Step 2: application-dev.yml 在 fitmate 段追加 encryption-key 默认值**

在 `application-dev.yml` 的 `fitmate:` 段下，`agent:` 之前插入：

```yaml
# FitMate
fitmate:
  llm:
    encryption-key: ${LLM_ENCRYPTION_KEY:ZGV2ZW5jcnlwdGlvbmtleTk5OTk5OTk5OTk5OTk5OTk5}
  agent:
    max-iterations: ${AGENT_MAX_ITERATIONS:20}
    max-tool-calls: ${AGENT_MAX_TOOL_CALLS:100}
    max-run-duration-seconds: ${AGENT_MAX_RUN_DURATION_SECONDS:1800}
    llm-timeout-seconds: ${AGENT_LLM_TIMEOUT_SECONDS:120}
    tool-timeout-seconds: ${AGENT_TOOL_TIMEOUT_SECONDS:30}
    memory-window-size: ${AGENT_MEMORY_WINDOW_SIZE:20}
```

（`ZGV2ZW5jcnlwdGlvbmtleTk5OTk5OTk5OTk5OTk5OTk5` 是 `devencriptionkey99999999999999` 的 Base64，仅 dev 用，生产用 env 覆盖）

- [ ] **Step 3: Commit**

```bash
git add FitMate-api/src/main/resources/application.yml FitMate-api/src/main/resources/application-dev.yml
git commit -m "feat(config): add fitmate.llm config block to yml files"
```

---

## Task 8: ReasoningChatClient 改造 — 移除 @Value，动态解析配置

**Files:**
- Modify: `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/infrastructure/ReasoningChatClient.java`

- [ ] **Step 1: 移除 @Value 字段，注入 LlmConfigResolver**

将 `ReasoningChatClient` 类头改造（替换 35-45 行的 `@Value` 字段）：

```java
@Slf4j
@Component
public class ReasoningChatClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final LlmConfigResolver llmConfigResolver;

    public ReasoningChatClient(LlmConfigResolver llmConfigResolver) {
        this.llmConfigResolver = llmConfigResolver;
    }
```

并新增 import：
```java
import com.itgeo.fitmate.api.chat.application.LlmConfigResolver;
import com.itgeo.fitmate.api.chat.application.ResolvedLlmConfig;
```

删除原 import：
```java
import org.springframework.beans.factory.annotation.Value;
```

- [ ] **Step 2: stream 方法改为动态解析**

将 `stream(String prompt)` 方法改造（替换 66-97 行），在方法开头解析配置：

```java
    public Flux<ReasoningStreamChunk> stream(String prompt) {
        return Flux.create(sink -> {
            try {
                ResolvedLlmConfig config = llmConfigResolver.resolveForCurrentUser();
                String requestBody = buildRequestBody(prompt, config);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(buildChatCompletionsUrl(config.getBaseUrl())))
                        .timeout(Duration.ofMinutes(10))
                        .header("Authorization", "Bearer " + config.getApiKey())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<Stream<String>> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofLines()
                );
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    sink.error(new IllegalStateException("Reasoning chat request failed, status=" + response.statusCode()));
                    return;
                }

                try (Stream<String> lines = response.body()) {
                    lines.forEach(line -> handleSseLine(line, sink, config));
                }
                sink.complete();
            } catch (Exception error) {
                log.error("Reasoning chat stream failed", error);
                sink.error(error);
            }
        });
    }
```

- [ ] **Step 3: buildRequestBody 改为接收 ResolvedLlmConfig**

替换 99-108 行的 `buildRequestBody` 与 `buildChatCompletionsUrl`：

```java
    private String buildRequestBody(String prompt, ResolvedLlmConfig config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));
        body.put("reasoning_effort", StrUtil.blankToDefault(config.getReasoningEffort(), "high"));
        body.put("thinking", Map.of("type", Boolean.TRUE.equals(config.getThinkingEnabled()) ? "enabled" : "disabled"));
        if (config.getMaxOutputContextTokens() != null && config.getMaxOutputContextTokens() > 0) {
            body.put("max_tokens", config.getMaxOutputContextTokens());
        }
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        return JSONUtil.toJsonStr(body);
    }

    private String buildChatCompletionsUrl(String baseUrl) {
        String normalized = StrUtil.blankToDefault(baseUrl, "https://api.deepseek.com");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/chat/completions";
    }
```

- [ ] **Step 4: handleSseLine 与 parseUsage 接收 config 以用 maxInputContextTokens 作为 windowSize**

将 `handleSseLine` 签名改为（替换 118 行）：

```java
    private void handleSseLine(String line, reactor.core.publisher.FluxSink<ReasoningStreamChunk> sink, ResolvedLlmConfig config) {
```

并在 `handleSseLine` 内调用 `parseUsage` 处（131 行与 151 行）传 config：

```java
            TokenUsage usage = parseUsage(root.getJSONObject("usage"), config);
```
```java
        TokenUsage usage = parseUsage(root.getJSONObject("usage"), config);
```

将 `parseUsage` 签名与 `resolveContextWindow` 改造（替换 162-207 行）：

```java
    private TokenUsage parseUsage(JSONObject usage, ResolvedLlmConfig config) {
        if (usage == null) {
            return null;
        }
        Integer promptTokens = usage.getInt("prompt_tokens");
        Integer completionTokens = usage.getInt("completion_tokens");
        Integer totalTokens = usage.getInt("total_tokens");
        Integer reasoningTokens = null;
        JSONObject completionDetails = usage.getJSONObject("completion_tokens_details");
        if (completionDetails != null) {
            reasoningTokens = completionDetails.getInt("reasoning_tokens");
        }
        Integer cacheHitTokens = usage.getInt("prompt_cache_hit_tokens");
        Integer cacheMissTokens = usage.getInt("prompt_cache_miss_tokens");
        Integer windowSize = config.getMaxInputContextTokens() != null ? config.getMaxInputContextTokens() : 204800;
        return new TokenUsage(
                promptTokens,
                completionTokens,
                totalTokens,
                reasoningTokens,
                totalTokens,
                windowSize,
                cacheHitTokens,
                cacheMissTokens
        );
    }
```

删除原 `resolveContextWindow(String modelName)` 方法（已被 config.maxInputContextTokens 取代）。

- [ ] **Step 5: 编译验证**

Run: `mvn -pl FitMate-api compile -q`（在 `FitMate-backend` 目录下）
Expected: BUILD SUCCESS，无编译错误

- [ ] **Step 6: Commit**

```bash
git add FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/infrastructure/ReasoningChatClient.java
git commit -m "refactor(chat): ReasoningChatClient uses LlmConfigResolver instead of @Value"
```

---

## Task 9: LlmProxyClient — 代理调 DeepSeek list-models/test

**Files:**
- Create: `FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/infrastructure/LlmProxyClient.java`

- [ ] **Step 1: 创建 LlmProxyClient.java**

```java
package com.itgeo.fitmate.api.chat.infrastructure;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.chat.dto.LlmModelItem;
import com.itgeo.fitmate.api.chat.dto.LlmTestResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 代理调用 DeepSeek list-models 与 test 接口。
 * 超时 10 秒，避免拖死设置页。
 */
@Slf4j
@Component
public class LlmProxyClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** GET /models 代理，返回模型列表 */
    public List<LlmModelItem> listModels(String baseUrl, String apiKey) {
        String url = normalizeBaseUrl(baseUrl) + "/models";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("list-models 失败 status=" + response.statusCode());
            }
            JSONObject body = JSONUtil.parseObj(response.body());
            JSONArray data = body.getJSONArray("data");
            List<LlmModelItem> models = new ArrayList<>();
            if (data != null) {
                for (int i = 0; i < data.size(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    LlmModelItem model = new LlmModelItem();
                    model.setId(item.getStr("id"));
                    model.setOwnedBy(item.getStr("owned_by"));
                    models.add(model);
                }
            }
            return models;
        } catch (Exception e) {
            log.warn("list-models 代理失败 url={}", url, e);
            throw new IllegalStateException("拉取模型列表失败: " + e.getMessage(), e);
        }
    }

    /** 测活：极简 chat completion（max_tokens=1, thinking=disabled） */
    public LlmTestResult testConnection(String baseUrl, String apiKey, String model) {
        LlmTestResult result = new LlmTestResult();
        result.setModel(model);
        result.setOk(false);
        String url = normalizeBaseUrl(baseUrl) + "/chat/completions";
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
            body.put("max_tokens", 1);
            body.put("stream", false);
            body.put("thinking", Map.of("type", "disabled"));
            String requestBody = JSONUtil.toJsonStr(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            result.setLatencyMs(System.currentTimeMillis() - start);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                result.setOk(true);
            } else {
                result.setError("HTTP " + response.statusCode() + ": " + truncate(response.body(), 200));
            }
        } catch (Exception e) {
            result.setLatencyMs(System.currentTimeMillis() - start);
            result.setError(truncate(e.getMessage(), 200));
        }
        return result;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = StrUtil.blankToDefault(baseUrl, "https://api.deepseek.com");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/infrastructure/LlmProxyClient.java
git commit -m "feat(infra): add LlmProxyClient for list-models and test proxy"
```

---

## Task 10: UserController 追加 4 个 LLM 接口

**Files:**
- Modify: `FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/controller/UserController.java`

- [ ] **Step 1: 在 UserController 追加 4 个接口与依赖注入**

在类字段区（约 30-34 行）追加注入：

```java
    @Resource
    private com.itgeo.fitmate.api.chat.application.LlmConfigResolver llmConfigResolver;

    @Resource
    private com.itgeo.fitmate.api.chat.infrastructure.LlmProxyClient llmProxyClient;
```

在 `savePreferences` 方法后（约 188 行后、`resolveClientIp` 前）追加 4 个接口：

```java
    /**
     * 获取当前登录用户的 LLM 配置（apiKey 脱敏）。
     *
     * @return 通用响应结果
     */
    @GetMapping("/llm-config")
    public LeeResult getLlmConfig() {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(llmConfigResolver.getByUserId(userId));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("获取 LLM 配置失败", e);
            return LeeResult.errorException("获取 LLM 配置失败");
        }
    }

    /**
     * 保存当前登录用户的 LLM 配置（apiKey 为空表示不修改原值）。
     *
     * @param request 保存请求体
     * @return 通用响应结果
     */
    @PutMapping("/llm-config")
    public LeeResult saveLlmConfig(@RequestBody com.itgeo.fitmate.api.auth.dto.LlmConfigSaveRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            llmConfigResolver.saveByUserId(userId, request);
            return LeeResult.ok(llmConfigResolver.getByUserId(userId));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("保存 LLM 配置失败", e);
            return LeeResult.errorException("保存 LLM 配置失败");
        }
    }

    /**
     * 代理调用 DeepSeek GET /models 拉取模型列表。
     * 请求体字段为空时用当前用户已存配置。
     *
     * @param request 代理请求体（可为空）
     * @return 通用响应结果
     */
    @PostMapping("/llm/models")
    public LeeResult listLlmModels(@RequestBody(required = false) com.itgeo.fitmate.api.chat.dto.LlmProxyRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            com.itgeo.fitmate.api.chat.application.ResolvedLlmConfig resolved = llmConfigResolver.resolveByUserId(userId);
            String baseUrl = request != null && request.getBaseUrl() != null ? request.getBaseUrl() : resolved.getBaseUrl();
            String apiKey = request != null && request.getApiKey() != null ? request.getApiKey() : resolved.getApiKey();
            return LeeResult.ok(llmProxyClient.listModels(baseUrl, apiKey));
        } catch (Exception e) {
            log.error("拉取模型列表失败", e);
            return LeeResult.errorMsg(e.getMessage());
        }
    }

    /**
     * 测活：极简 chat completion（max_tokens=1, thinking=disabled）。
     * 请求体字段为空时用当前用户已存配置。
     *
     * @param request 代理请求体（可为空）
     * @return 通用响应结果
     */
    @PostMapping("/llm/test")
    public LeeResult testLlmConnection(@RequestBody(required = false) com.itgeo.fitmate.api.chat.dto.LlmProxyRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            com.itgeo.fitmate.api.chat.application.ResolvedLlmConfig resolved = llmConfigResolver.resolveByUserId(userId);
            String baseUrl = request != null && request.getBaseUrl() != null ? request.getBaseUrl() : resolved.getBaseUrl();
            String apiKey = request != null && request.getApiKey() != null ? request.getApiKey() : resolved.getApiKey();
            String model = request != null && request.getModel() != null ? request.getModel() : resolved.getModel();
            return LeeResult.ok(llmProxyClient.testConnection(baseUrl, apiKey, model));
        } catch (Exception e) {
            log.error("LLM 测活失败", e);
            return LeeResult.errorMsg(e.getMessage());
        }
    }
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl FitMate-api compile -q`（在 `FitMate-backend` 目录下）
Expected: BUILD SUCCESS

- [ ] **Step 3: 启动后端验证接口可达**

Run: `mvn -pl FitMate-api spring-boot:run`（在 `FitMate-backend` 目录下，需先设置 `LLM_ENCRYPTION_KEY` 环境变量或在 dev yml 用默认值）
Expected: 启动成功，日志显示 "LlmConfigCipher 初始化成功"
（验证后 Ctrl+C 停止）

- [ ] **Step 4: Commit**

```bash
git add FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/controller/UserController.java
git commit -m "feat(api): add 4 LLM config/proxy endpoints to UserController"
```

---

## Task 11: 前端类型 — settings.ts 追加 LlmConfig 等

**Files:**
- Modify: `FitMate-frontend/src/types/settings.ts`

- [ ] **Step 1: 在 settings.ts 末尾追加 LLM 相关类型**

读取现有 `settings.ts` 末尾，追加：

```typescript
/** LLM 配置（前后端共享结构，GET 接口返回脱敏 apiKey） */
export interface LlmConfig {
  /** API 地址，必填，默认 https://api.deepseek.com */
  baseUrl: string;
  /** API Key，必填。GET 返回脱敏值（如 sk-****e05f），PUT 接收明文 */
  apiKey: string;
  /** 模型 ID，默认 deepseek-v4-flash */
  model: string;
  /** 输入上下文最大值（token），默认 204800（200K）。用作截断阈值与窗口展示 */
  maxInputContextTokens: number;
  /** 输出上下文最大值（token，对应 API max_tokens），默认 65536（64K） */
  maxOutputContextTokens: number;
  /** 是否启用思考模式，默认 true */
  thinkingEnabled: boolean;
  /** 推理强度，默认 high */
  reasoningEffort: "high" | "max";
}

/** 默认 LLM 配置（DB 无值时回退） */
export const DEFAULT_LLM_CONFIG: LlmConfig = {
  baseUrl: "https://api.deepseek.com",
  apiKey: "",
  model: "deepseek-v4-flash",
  maxInputContextTokens: 204800,
  maxOutputContextTokens: 65536,
  thinkingEnabled: true,
  reasoningEffort: "high",
};

/** DeepSeek 模型列表项（GET /models 返回） */
export interface LlmModelOption {
  id: string;
  ownedBy: string;
}

/** 测活结果 */
export interface LlmTestResult {
  ok: boolean;
  model?: string;
  latencyMs?: number;
  error?: string;
}
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/types/settings.ts
git commit -m "feat(types): add LlmConfig/LlmModelOption/LlmTestResult types"
```

---

## Task 12: doctorApi.ts 追加 4 个 LLM API 方法

**Files:**
- Modify: `FitMate-frontend/src/services/doctorApi.ts`

- [ ] **Step 1: 在 getUserPreferences/saveUserPreferences 之后追加 4 个方法**

在 `saveUserPreferences` 函数后（约 178 行后）追加：

```typescript
export function getLlmConfig() {
  return instance({
    url: "/user/llm-config",
    method: "get",
  });
}

export function saveLlmConfig(bo) {
  return instance({
    url: "/user/llm-config",
    method: "put",
    data: bo,
  });
}

export function listLlmModels(bo) {
  return instance({
    url: "/user/llm/models",
    method: "post",
    data: bo || {},
  });
}

export function testLlmConnection(bo) {
  return instance({
    url: "/user/llm/test",
    method: "post",
    data: bo || {},
  });
}
```

- [ ] **Step 2: 在 doctorApi 默认导出对象追加 4 个方法**

在 `const doctorApi = { ... }` 中（约 180-201 行）追加：

```typescript
  getLlmConfig,
  saveLlmConfig,
  listLlmModels,
  testLlmConnection,
```

- [ ] **Step 3: Commit**

```bash
git add FitMate-frontend/src/services/doctorApi.ts
git commit -m "feat(api): add 4 LLM config/proxy methods to doctorApi"
```

---

## Task 13: llmConfig.ts — 前端 LLM 配置状态管理

**Files:**
- Create: `FitMate-frontend/src/services/llmConfig.ts`

- [ ] **Step 1: 创建 llmConfig.ts**

```typescript
// FitMate-frontend/src/services/llmConfig.ts
import doctorApi from "./doctorApi";
import type {
  LlmConfig,
  LlmModelOption,
  LlmTestResult,
} from "../types/settings";
import { DEFAULT_LLM_CONFIG } from "../types/settings";

const STORAGE_KEY = "fitmate_llm_config";

interface LlmConfigState {
  config: LlmConfig;
  models: LlmModelOption[];
}

const state: LlmConfigState = {
  config: { ...DEFAULT_LLM_CONFIG },
  models: [],
};

const subscribers: Array<() => void> = [];

function notify(): void {
  for (let i = 0; i < subscribers.length; i++) {
    try {
      subscribers[i]();
    } catch (e) {
      console.error("llmConfig subscriber error", e);
    }
  }
}

function persistLocal(): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state.config));
  } catch (e) {
    // ignore
  }
}

function restoreLocal(): void {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === "object") {
      state.config = Object.assign({}, DEFAULT_LLM_CONFIG, parsed);
    }
  } catch (e) {
    // ignore
  }
}

/** 从后端加载 LLM 配置（登录后调用） */
async function load(): Promise<void> {
  try {
    const res = await doctorApi.getLlmConfig();
    if (res && res.status === 200 && res.data) {
      state.config = Object.assign({}, DEFAULT_LLM_CONFIG, res.data);
      persistLocal();
      notify();
    }
  } catch (e) {
    console.error("加载 LLM 配置失败", e);
    restoreLocal();
  }
}

/** 获取当前配置 */
function getConfig(): LlmConfig {
  return state.config;
}

/** 获取当前模型列表 */
function getModels(): LlmModelOption[] {
  return state.models;
}

/** 保存配置（PUT 后端，apiKey 为空表示不修改） */
async function save(patch: Partial<LlmConfig>): Promise<void> {
  const merged = Object.assign({}, state.config, patch);
  await doctorApi.saveLlmConfig(merged);
  // 保存成功后重新拉取脱敏配置
  try {
    const res = await doctorApi.getLlmConfig();
    if (res && res.status === 200 && res.data) {
      state.config = Object.assign({}, DEFAULT_LLM_CONFIG, res.data);
    } else {
      state.config = merged;
    }
  } catch (e) {
    state.config = merged;
  }
  persistLocal();
  notify();
}

/** 拉取模型列表（用当前配置或传入的临时配置） */
async function fetchModels(
  override?: Partial<LlmConfig>
): Promise<LlmModelOption[]> {
  const payload = override
    ? Object.assign({}, state.config, override)
    : {};
  const res = await doctorApi.listLlmModels(payload);
  if (res && res.status === 200 && Array.isArray(res.data)) {
    state.models = res.data;
    notify();
    return res.data;
  }
  return [];
}

/** 测活（用当前配置或传入的临时配置） */
async function testConnection(
  override?: Partial<LlmConfig>
): Promise<LlmTestResult> {
  const payload = override
    ? Object.assign({}, state.config, override)
    : {};
  const res = await doctorApi.testLlmConnection(payload);
  if (res && res.status === 200 && res.data) {
    return res.data as LlmTestResult;
  }
  return { ok: false, error: res && res.msg ? res.msg : "测活失败" };
}

/** 订阅配置变更 */
function subscribe(cb: () => void): () => void {
  subscribers.push(cb);
  return function () {
    const idx = subscribers.indexOf(cb);
    if (idx >= 0) subscribers.splice(idx, 1);
  };
}

restoreLocal();

export const llmConfig = {
  load,
  getConfig,
  getModels,
  save,
  fetchModels,
  testConnection,
  subscribe,
};

export default llmConfig;
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/services/llmConfig.ts
git commit -m "feat(service): add llmConfig state management service"
```

---

## Task 14: AppLayout 挂载后调 llmConfig.load()

**Files:**
- Modify: `FitMate-frontend/src/layouts/AppLayout.vue`

- [ ] **Step 1: 在 AppLayout 引入 llmConfig 并在 mounted 调用 load**

在 `<script>` 的 import 区（约 22-25 行）追加：

```typescript
import { llmConfig } from "../services/llmConfig";
```

在 `methods` 后追加 `mounted` 钩子（约 58 行 `},` 后、`};` 前）：

```typescript
  mounted() {
    llmConfig.load();
  },
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/layouts/AppLayout.vue
git commit -m "feat(layout): load llmConfig on AppLayout mount"
```

---

## Task 15: SettingsSectionNav 新增「配置」导航项

**Files:**
- Modify: `FitMate-frontend/src/pages/settings/components/SettingsSectionNav.vue`

- [ ] **Step 1: 在 sections 数组 appearance 后、about 前插入 llm 项**

将 `sections` 数组（约 30-34 行）改为：

```javascript
      sections: [
        { id: "profile", label: "个人信息", icon: "person" },
        { id: "appearance", label: "外观", icon: "palette" },
        { id: "llm", label: "配置", icon: "tune" },
        { id: "about", label: "关于", icon: "info" },
      ],
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/pages/settings/components/SettingsSectionNav.vue
git commit -m "feat(settings): add llm nav item to SettingsSectionNav"
```

---

## Task 16: SettingsPage 路由分支新增 llm 区块

**Files:**
- Modify: `FitMate-frontend/src/pages/settings/SettingsPage.vue`

- [ ] **Step 1: 引入 LlmConfigSection 组件**

在 `<script>` import 区（约 22-27 行）追加：

```typescript
import LlmConfigSection from "./components/LlmConfigSection.vue";
```

修改 `components` 注册（约 31 行）：

```javascript
  components: { SettingsSectionNav, ProfileSection, AppearanceSection, LlmConfigSection, AboutSection },
```

- [ ] **Step 2: 模板新增 llm 区块分支**

在 `<AppearanceSection>` 后、`<AboutSection>` 前插入（约 15-16 行间）：

```html
      <LlmConfigSection v-else-if="activeSection === 'llm'" />
```

- [ ] **Step 3: applyHash 白名单加入 llm**

将 `applyHash` 方法（约 53-58 行）的白名单改为：

```javascript
      if (hash && ["profile", "appearance", "llm", "about"].includes(hash)) {
        this.activeSection = hash;
      }
```

- [ ] **Step 4: Commit**

```bash
git add FitMate-frontend/src/pages/settings/SettingsPage.vue
git commit -m "feat(settings): add llm section route branch to SettingsPage"
```

---

## Task 17: LlmConfigSection.vue — LLM 配置表单

**Files:**
- Create: `FitMate-frontend/src/pages/settings/components/LlmConfigSection.vue`

- [ ] **Step 1: 创建 LlmConfigSection.vue**

```vue
<!-- FitMate-frontend/src/pages/settings/components/LlmConfigSection.vue -->
<template>
  <section class="llm-config-section">
    <header class="mb-lg">
      <h2 class="font-headline-sm text-headline-sm text-on-surface">配置</h2>
      <p class="font-inter text-label-xs text-on-surface-variant uppercase tracking-widest opacity-70 mt-xs">
        Manage DeepSeek API connection &amp; model parameters
      </p>
    </header>

    <div class="llm-config-card">
      <!-- API 地址 -->
      <div class="llm-field">
        <label class="llm-label">API 地址 <span class="llm-required">*</span></label>
        <input
          v-model="form.baseUrl"
          type="text"
          class="llm-input"
          placeholder="https://api.deepseek.com"
        />
      </div>

      <!-- API Key -->
      <div class="llm-field">
        <label class="llm-label">API Key <span class="llm-required">*</span></label>
        <input
          v-model="form.apiKey"
          type="password"
          class="llm-input"
          :placeholder="apiKeyPlaceholder"
          @focus="onApiKeyFocus"
        />
        <p class="llm-hint">留空表示不修改原 Key（显示为脱敏值）</p>
      </div>

      <!-- 模型 -->
      <div class="llm-field">
        <label class="llm-label">模型</label>
        <div class="llm-model-row">
          <select v-model="form.model" class="llm-select">
            <option value="" disabled>请选择模型</option>
            <option v-for="m in models" :key="m.id" :value="m.id">{{ m.id }}</option>
          </select>
          <button type="button" class="llm-btn-secondary" :disabled="fetchingModels" @click="onFetchModels">
            {{ fetchingModels ? "拉取中..." : "拉取列表" }}
          </button>
          <button type="button" class="llm-btn-secondary" :disabled="testing || !form.model" @click="onTest">
            {{ testing ? "测活中..." : "测活" }}
          </button>
        </div>
        <p v-if="testResult" class="llm-test-result" :class="testResult.ok ? 'llm-test-ok' : 'llm-test-fail'">
          {{ testResult.ok ? '✓ 连通 (' + testResult.latencyMs + 'ms)' : '✗ ' + (testResult.error || '失败') }}
        </p>
      </div>

      <!-- 输入上下文最大值 -->
      <div class="llm-field">
        <label class="llm-label">输入上下文最大值（token）</label>
        <input v-model.number="form.maxInputContextTokens" type="number" min="1" class="llm-input" />
      </div>

      <!-- 输出上下文最大值 -->
      <div class="llm-field">
        <label class="llm-label">输出上下文最大值（token）</label>
        <input v-model.number="form.maxOutputContextTokens" type="number" min="1" class="llm-input" />
      </div>

      <!-- 思考模式 -->
      <div class="llm-field llm-field-inline">
        <label class="llm-label">思考模式</label>
        <button
          type="button"
          class="llm-toggle"
          :class="{ 'llm-toggle-on': form.thinkingEnabled }"
          @click="form.thinkingEnabled = !form.thinkingEnabled"
        >
          <span class="llm-toggle-knob"></span>
        </button>
      </div>

      <!-- 推理强度 -->
      <div class="llm-field">
        <label class="llm-label">推理强度</label>
        <div class="llm-radio-group">
          <label class="llm-radio">
            <input v-model="form.reasoningEffort" type="radio" value="high" />
            <span>high</span>
          </label>
          <label class="llm-radio">
            <input v-model="form.reasoningEffort" type="radio" value="max" />
            <span>max</span>
          </label>
        </div>
      </div>

      <div class="llm-actions">
        <button type="button" class="llm-btn-primary" :disabled="saving" @click="onSave">
          {{ saving ? "保存中..." : "保存" }}
        </button>
      </div>
    </div>
  </section>
</template>

<script lang="ts">
import { llmConfig } from "../../../services/llmConfig";
import type { LlmConfig, LlmModelOption, LlmTestResult } from "../../../types/settings";
import { DEFAULT_LLM_CONFIG } from "../../../types/settings";

export default {
  name: "LlmConfigSection",
  data() {
    return {
      form: { ...DEFAULT_LLM_CONFIG } as LlmConfig,
      models: [] as LlmModelOption[],
      fetchingModels: false,
      testing: false,
      saving: false,
      testResult: null as LlmTestResult | null,
      apiKeyEdited: false,
    };
  },
  computed: {
    apiKeyPlaceholder() {
      const current = llmConfig.getConfig().apiKey;
      if (current && !this.apiKeyEdited) {
        return current;
      }
      return "输入 API Key（留空不修改）";
    },
  },
  mounted() {
    this.syncFromStore();
  },
  methods: {
    syncFromStore() {
      const cfg = llmConfig.getConfig();
      this.form = { ...cfg, apiKey: "" };
      this.models = llmConfig.getModels();
      this.apiKeyEdited = false;
    },
    onApiKeyFocus() {
      this.apiKeyEdited = true;
    },
    async onFetchModels() {
      this.fetchingModels = true;
      try {
        const override = this.form.apiKey ? this.form : undefined;
        this.models = await llmConfig.fetchModels(override);
        if (this.models.length === 0) {
          this.$message && this.$message.warning && this.$message.warning("未拉取到模型");
        }
      } catch (e) {
        this.$message && this.$message.error && this.$message.error("拉取模型列表失败");
      } finally {
        this.fetchingModels = false;
      }
    },
    async onTest() {
      if (!this.form.model) return;
      this.testing = true;
      this.testResult = null;
      try {
        const override = this.form.apiKey ? this.form : { ...this.form, apiKey: undefined };
        this.testResult = await llmConfig.testConnection(override);
      } catch (e) {
        this.testResult = { ok: false, error: "测活请求失败" };
      } finally {
        this.testing = false;
      }
    },
    async onSave() {
      if (!this.form.baseUrl || !this.form.baseUrl.trim()) {
        this.$message && this.$message.error && this.$message.error("API 地址不能为空");
        return;
      }
      const stored = llmConfig.getConfig();
      if (!this.form.apiKey && !stored.apiKey) {
        this.$message && this.$message.error && this.$message.error("API Key 不能为空");
        return;
      }
      this.saving = true;
      try {
        await llmConfig.save(this.form);
        this.syncFromStore();
        this.$message && this.$message.success && this.$message.success("LLM 配置已保存");
      } catch (e) {
        this.$message && this.$message.error && this.$message.error("保存失败");
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.llm-config-section {
  max-width: 640px;
}
.llm-config-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 12px;
  background: var(--color-surface-container-lowest, var(--color-background));
}
.llm-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.llm-field-inline {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.llm-label {
  font-size: 13px;
  color: var(--color-on-surface-variant);
  font-weight: 600;
}
.llm-required {
  color: var(--color-error, #ef4444);
}
.llm-hint {
  font-size: 11px;
  color: var(--color-on-surface-variant);
  opacity: 0.7;
  margin: 0;
}
.llm-input,
.llm-select {
  padding: 8px 12px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  background: var(--color-background);
  color: var(--color-on-surface);
  font-size: 14px;
  font-family: "Inter", sans-serif;
  outline: none;
  transition: border-color 0.2s ease;
}
.llm-input:focus,
.llm-select:focus {
  border-color: var(--color-primary);
}
.llm-model-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.llm-model-row .llm-select {
  flex: 1;
}
.llm-btn-secondary {
  padding: 8px 14px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  background: transparent;
  color: var(--color-on-surface-variant);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease;
}
.llm-btn-secondary:hover:not(:disabled) {
  color: var(--color-primary);
  border-color: var(--color-primary);
}
.llm-btn-secondary:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.llm-test-result {
  font-size: 12px;
  margin: 0;
}
.llm-test-ok {
  color: var(--color-primary);
}
.llm-test-fail {
  color: var(--color-error, #ef4444);
}
.llm-toggle {
  width: 44px;
  height: 24px;
  border-radius: 999px;
  border: none;
  background: var(--color-outline-variant);
  position: relative;
  cursor: pointer;
  transition: background 0.2s ease;
}
.llm-toggle-on {
  background: var(--color-primary);
}
.llm-toggle-knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.2s ease;
}
.llm-toggle-on .llm-toggle-knob {
  transform: translateX(20px);
}
.llm-radio-group {
  display: flex;
  gap: 16px;
}
.llm-radio {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-on-surface);
  cursor: pointer;
}
.llm-radio input {
  cursor: pointer;
}
.llm-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
.llm-btn-primary {
  padding: 9px 24px;
  border: none;
  border-radius: 8px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s ease;
}
.llm-btn-primary:hover:not(:disabled) {
  opacity: 0.85;
}
.llm-btn-primary:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/pages/settings/components/LlmConfigSection.vue
git commit -m "feat(settings): add LlmConfigSection component"
```

---

## Task 18: ModelSelector.vue — 聊天页模型选择下拉

**Files:**
- Create: `FitMate-frontend/src/pages/chat/components/ModelSelector.vue`

- [ ] **Step 1: 创建 ModelSelector.vue**

```vue
<!-- FitMate-frontend/src/pages/chat/components/ModelSelector.vue -->
<template>
  <div class="model-selector" ref="root">
    <button
      type="button"
      class="model-selector-trigger"
      @click="toggle"
    >
      <span class="material-symbols-outlined model-selector-icon">neurology</span>
      <span class="model-selector-name">{{ model || "未选择" }}</span>
      <span class="material-symbols-outlined model-selector-chevron">
        {{ open ? "expand_less" : "expand_more" }}
      </span>
    </button>
    <div v-if="open" class="model-selector-dropdown">
      <div v-if="!models || models.length === 0" class="model-selector-empty">
        暂无模型，请到设置页拉取
      </div>
      <button
        v-for="m in models"
        :key="m.id"
        type="button"
        class="model-option"
        :class="{ 'model-option-active': m.id === model }"
        @click="select(m.id)"
      >
        {{ m.id }}
      </button>
    </div>
  </div>
</template>

<script lang="ts">
import type { LlmModelOption } from "../../../types/settings";

export default {
  name: "ModelSelector",
  props: {
    model: {
      type: String,
      default: "",
    },
    models: {
      type: Array as () => LlmModelOption[],
      default: () => [],
    },
  },
  emits: ["select"],
  data() {
    return {
      open: false,
    };
  },
  mounted() {
    document.addEventListener("click", this.onDocumentClick);
  },
  beforeUnmount() {
    document.removeEventListener("click", this.onDocumentClick);
  },
  methods: {
    toggle() {
      this.open = !this.open;
    },
    select(modelId: string) {
      this.open = false;
      this.$emit("select", modelId);
    },
    onDocumentClick(e: MouseEvent) {
      const root = this.$refs.root as HTMLElement | null;
      if (root && !root.contains(e.target as Node)) {
        this.open = false;
      }
    },
  },
};
</script>

<style scoped>
.model-selector {
  position: relative;
  display: inline-flex;
}
.model-selector-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-on-surface-variant);
  font-size: 11px;
  font-family: "Inter", sans-serif;
  cursor: pointer;
  transition: color 0.2s ease, background 0.2s ease;
}
.model-selector-trigger:hover {
  color: var(--color-on-surface);
  background: var(--color-surface-container);
}
.model-selector-icon {
  font-size: 14px !important;
}
.model-selector-name {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.model-selector-chevron {
  font-size: 14px !important;
}
.model-selector-dropdown {
  position: absolute;
  bottom: calc(100% + 4px);
  left: 0;
  min-width: 180px;
  max-height: 240px;
  overflow-y: auto;
  padding: 4px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  background: var(--color-surface-container-lowest, var(--color-background));
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  z-index: 100;
}
.model-selector-empty {
  padding: 8px 12px;
  font-size: 11px;
  color: var(--color-on-surface-variant);
  opacity: 0.7;
}
.model-option {
  display: block;
  width: 100%;
  text-align: left;
  padding: 6px 10px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-on-surface);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.model-option:hover {
  background: var(--color-surface-container);
}
.model-option-active {
  color: var(--color-primary);
  font-weight: 600;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/pages/chat/components/ModelSelector.vue
git commit -m "feat(chat): add ModelSelector dropdown component"
```

---

## Task 19: ChatInput.vue 改造 — 删 disclaimer、加 ModelSelector、placeholder 改淡

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/components/ChatInput.vue`

- [ ] **Step 1: 引入 ModelSelector 并注册组件**

在 `<script>` import 区（约 75 行）追加：

```typescript
import ModelSelector from "./ModelSelector.vue";
```

修改 `components` 注册（约 79-81 行）：

```javascript
  components: {
    TokenUsageIndicator,
    ModelSelector,
  },
```

- [ ] **Step 2: emits 与 props 新增 model 相关**

修改 `emits`（约 82-88 行）追加 `"select-model"`：

```javascript
  emits: [
    "update:modelValue",
    "send",
    "select-model",
    "toggle-internet-search",
    "toggle-knowledge-base",
    "toggle-rag",
  ],
```

在 `props` 末尾（约 117 行 `tokenUsage` 之后）追加：

```javascript
    currentModel: {
      type: String,
      default: "",
    },
    availableModels: {
      type: Array,
      default: () => [],
    },
```

- [ ] **Step 3: footer 区删 disclaimer 改为 ModelSelector**

将 footer 区（约 64-70 行）替换为：

```html
    <!-- Footer: model selector 左对齐 + token 用量右对齐 -->
    <div class="chat-input-footer">
      <ModelSelector
        :model="currentModel"
        :models="availableModels"
        @select="$emit('select-model', $event)"
      />
      <TokenUsageIndicator :token-usage="tokenUsage" />
    </div>
```

- [ ] **Step 4: 删除 .chat-input-disclaimer 样式**

删除约 314-321 行的 `.chat-input-disclaimer { ... }` 整段样式块。

- [ ] **Step 5: placeholder 颜色改淡**

将 `.chat-input-field::placeholder` 样式（约 273-275 行）改为：

```css
.chat-input-field::placeholder {
  color: color-mix(in srgb, var(--color-on-surface-variant) 50%, transparent);
}
```

- [ ] **Step 6: Commit**

```bash
git add FitMate-frontend/src/pages/chat/components/ChatInput.vue
git commit -m "feat(chat): ChatInput replaces disclaimer with ModelSelector, lighter placeholder"
```

---

## Task 20: ChatLogicBase 订阅 llmConfig + onModelSelect

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`

- [ ] **Step 1: 引入 llmConfig**

在 import 区（约 13 行后）追加：

```typescript
import { llmConfig } from "../../services/llmConfig";
```

- [ ] **Step 2: data 追加 currentModel / availableModels**

在 `data()` 返回对象末尾（约 88 行 `lastTtft: null,` 后）追加：

```javascript
      currentModel: "",
      availableModels: [] as Array<{ id: string; ownedBy: string }>,
```

- [ ] **Step 3: created 中订阅 llmConfig 变更**

在 `created()` 钩子末尾（约 166 行 `}` 前，即 `} else {` 块之后）追加：

```javascript
    this.currentModel = llmConfig.getConfig().model;
    this.availableModels = llmConfig.getModels();
    this._llmConfigUnsub = llmConfig.subscribe(() => {
      this.currentModel = llmConfig.getConfig().model;
      this.availableModels = llmConfig.getModels();
    });
    if (this.availableModels.length === 0) {
      llmConfig.fetchModels().catch(() => {});
    }
```

- [ ] **Step 4: beforeUnmount 取消订阅**

在 `beforeUnmount()` 钩子（约 171-173 行）追加取消订阅：

```javascript
  beforeUnmount() {
    this.teardownSSE({ clearPending: true });
    if (this._llmConfigUnsub) {
      this._llmConfigUnsub();
      this._llmConfigUnsub = null;
    }
  },
```

- [ ] **Step 5: methods 追加 onModelSelect**

在 `methods` 末尾（约 2386 行 `doRag` 方法后、`},` 前）追加：

```javascript
    async onModelSelect(modelId) {
      if (!modelId || modelId === this.currentModel) {
        return;
      }
      try {
        await llmConfig.save({ model: modelId });
        this.currentModel = llmConfig.getConfig().model;
      } catch (e) {
        this.showUiMessage("error", "切换模型失败");
      }
    },
```

- [ ] **Step 6: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "feat(chat): ChatLogicBase subscribes llmConfig and handles model select"
```

---

## Task 21: ChatPage 传入新 props 与监听 select-model

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatPage.vue`

- [ ] **Step 1: ChatInput 传入 currentModel/availableModels 并监听 select-model**

将 `<ChatInput>` 标签（约 70-83 行）改为：

```html
      <ChatInput
        ref="chatInputPanel"
        v-model="draftMessage"
        :internet-search-selected="internetSearchSelected"
        :knowledge-base-selected="knowledgeBaseSelected"
        :rag-selected="ragSelected"
        :is-sending="isSending"
        :is-streaming="isStreaming"
        :token-usage="tokenUsage"
        :current-model="currentModel"
        :available-models="availableModels"
        @send="doChat"
        @select-model="onModelSelect"
        @toggle-internet-search="doInternetSearch"
        @toggle-knowledge-base="doKnowledgeBase"
        @toggle-rag="doRag"
      />
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/pages/chat/ChatPage.vue
git commit -m "feat(chat): ChatPage passes model props and listens select-model"
```

---

## Task 22: 联调验证 — 全流程

- [ ] **Step 1: 启动后端**

Run: 在 `FitMate-backend` 目录下 `mvn -pl FitMate-api spring-boot:run`
Expected: 启动成功，日志含 "LlmConfigCipher 初始化成功"

- [ ] **Step 2: 启动前端**

Run: 在 `FitMate-frontend` 目录下 `npm run dev`
Expected: 前端启动成功

- [ ] **Step 3: 验证 DB 无值回退 env**

登录 → 发一条聊天消息 → 确认正常回复（用 env 默认 model/key）
Expected: 聊天正常，未因 ReasoningChatClient 改造破坏

- [ ] **Step 4: 验证设置页配置区块**

进入设置页 → 点「配置」导航 → 确认表单显示（baseUrl/apiKey 脱敏/model 等）
Expected: 表单渲染正常

- [ ] **Step 5: 验证拉取模型列表**

填入有效 baseUrl + apiKey → 点「拉取列表」→ 确认下拉填充模型
Expected: 下拉出现 deepseek-v4-flash 等

- [ ] **Step 6: 验证测活**

选模型 → 点「测活」→ 确认显示 ✓ + 耗时
Expected: 显示成功结果

- [ ] **Step 7: 验证保存**

调整参数 → 点「保存」→ 刷新页面 → 确认配置持久化
Expected: 保存后刷新仍显示已保存配置（apiKey 脱敏）

- [ ] **Step 8: 验证聊天页模型选择**

进入聊天页 → 输入框左下角点模型名 → 切换模型 → 确认立即生效
Expected: 模型名更新，disclaimer 消失，placeholder 更淡

- [ ] **Step 9: 验证配置生效**

保存配置后发消息 → 确认用新 model/参数请求
Expected: 聊天使用新配置

- [ ] **Step 10: Final commit（如有遗漏修复）**

```bash
git add -A
git commit -m "chore: llm config full-flow integration verified"
```

---

## Self-Review 结果

**1. Spec coverage:**
- §1.2 目标 1-9 全部由 Task 1-22 覆盖 ✓
- §3 数据结构 → Task 2,3,11 ✓
- §4 后端设计 → Task 4-10 ✓
- §5 前端设计 → Task 11-21 ✓
- §6 默认值 → Task 4,7,11 ✓
- §8 数据流 → Task 22 验证 ✓

**2. Placeholder scan:** 无 TBD/TODO，所有步骤含完整代码 ✓

**3. Type consistency:**
- `LlmConfig` 前后端字段名一致（baseUrl/apiKey/model/maxInputContextTokens/maxOutputContextTokens/thinkingEnabled/reasoningEffort）✓
- `LlmConfigResolver.resolveForCurrentUser()` / `resolveByUserId()` / `getByUserId()` / `saveByUserId()` 签名一致 ✓
- 前端 `llmConfig.load/save/fetchModels/testConnection/subscribe` 在 Task 13 定义，Task 14/17/20 调用一致 ✓
- `ModelSelector` props（model/models）与 `ChatInput` 传入一致 ✓
- `select-model` emit 在 ChatInput 定义、ChatLogicBase onModelSelect、ChatPage 监听一致 ✓

**4. 已知简化：**
- Task 10 中 UserController 用全限定类名而非新增 import（避免与现有 import 冲突，可读性略降但编译无误）
- 前端无测试框架，Task 17-21 未强制加测试（遵循项目约定）
