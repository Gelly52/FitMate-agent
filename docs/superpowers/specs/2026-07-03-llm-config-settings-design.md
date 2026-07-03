# LLM 配置与聊天页输入框改造设计

- **状态**：Draft（待用户最终审查）
- **创建日期**：2026-07-03
- **作者**：协作设计（用户 + AI 助手）
- **适用范围**：FitMate-AI 前端（Vue 3 + TS + Tailwind）+ 后端（Spring Boot，`auth` + `chat` 模块）
- **关联**：
  - 复用 `2026-07-02-user-menu-settings-design.md` 的设置页区块结构
  - 复用 `t_user_preference` 表与 `UserPreference` 实体
  - 复用 `ReasoningChatClient` 调用链
  - 参考 DeepSeek API 文档：https://api-docs.deepseek.com/zh-cn/

---

## 1. 背景与目标

### 1.1 现状问题

- **LLM 配置仅来自环境变量**：`application.yml` 中 `spring.ai.openai.{api-key,base-url,chat.options.model}` 与 `reasoning.effort` 全局硬编码，用户无法自行配置
- **`ReasoningChatClient` 用 `@Value` 静态注入**：运行时无法按用户切换 URL/Key/模型
- **设置页缺少 LLM 配置区块**：现有 profile/appearance/about 三区块无 LLM 相关项
- **聊天页输入框左下角是英文 disclaimer**：`FIT-AGENT MAY PRODUCE INACCURATE INFORMATION ABOUT WORKOUTS OR NUTRITION.` 占位，无模型选择能力
- **placeholder 字体颜色偏重**：`.chat-input-field::placeholder` 使用 `var(--color-on-surface-variant)`，视觉上不够淡
- **无测活能力**：用户改完配置无法验证 URL/Key/模型是否可用

### 1.2 目标

1. 设置页新增「配置」区块（#llm），允许用户自行配置 DeepSeek API URL、API Key、模型、输入/输出上下文最大值、思考模式、推理强度
2. URL 与 Key 为必填，其余字段留空时使用默认值
3. 模型选择支持通过 `GET /models` 拉取可用模型列表（后端代理）
4. 选定模型后提供「测活」按钮，用极简 chat completion（max_tokens=1）验证可达性
5. 用户配置落库（per-user），后端 `ReasoningChatClient` 按当前登录用户动态解析配置，DB 无值时回退 env
6. API Key 服务端 AES 加密存储，GET 接口返回脱敏值
7. 聊天页输入框左下角改为模型选择下拉，切换后立即同步设置页配置并生效
8. 删除输入框底部英文 disclaimer
9. 输入框 placeholder 字体颜色改淡

### 1.3 非目标（YAGNI）

- 不做输入上下文压缩（maxInputContextTokens 本阶段仅存值与展示，压缩留待后续）
- 不暴露 `temperature` / `top_p`（用户未要求）
- 不做多 LLM Provider 切换（仅 DeepSeek 系）
- 不做配置导入/导出
- 不做配置变更审计日志

---

## 2. 关键设计决策（已与用户确认）

| 决策点 | 选择 | 理由 |
|---|---|---|
| 配置存储架构 | 方案 A：扩展 `t_user_preference` 新增 `llm_config_json` 列 | 复用现有表/实体/Mapper，改动最小；API Key 独立列+AES 加密，与主题色安全解耦 |
| 配置覆盖语义 | per-user DB 覆盖 env | DB 有值优先用 DB，DB 无值回退 env 默认，行为与现状一致 |
| 输入上下文最大值含义 | 截断阈值 + 窗口展示（本阶段仅存值展示） | 后续做上下文压缩时复用此值，现阶段不实现压缩 |
| 额外可配置项 | thinking + reasoning_effort | 用户明确要求，不含 temperature/top_p |
| 输入框模型选择 | 可直接切换的下拉 | 体验好，切换后即时同步设置页配置 |
| 测活方式 | 极简 chat completion（max_tokens=1） | 验证完整链路（URL+Key+模型），比 list-models 更严格 |
| 保存方式 | 点「保存」按钮 | 用户明确要求，不采用失焦自动保存 |
| 默认值 | 输入 200K、输出 64K | 用户指定 |

---

## 3. 数据结构

### 3.1 前端类型（`src/types/settings.ts` 追加）

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

### 3.2 后端 DTO

```java
// auth/dto/LlmConfigItem.java（GET 返回用，apiKey 脱敏）
@Data
public class LlmConfigItem {
    private String baseUrl;
    private String apiKey;          // 脱敏：sk-****e05f
    private String model;
    private Integer maxInputContextTokens;
    private Integer maxOutputContextTokens;
    private Boolean thinkingEnabled;
    private String reasoningEffort; // high / max
}

// auth/dto/LlmConfigSaveRequest.java（PUT 请求用，apiKey 明文）
@Data
public class LlmConfigSaveRequest {
    private String baseUrl;
    private String apiKey;          // 明文，可为空（空表示不修改原 key）
    private String model;
    private Integer maxInputContextTokens;
    private Integer maxOutputContextTokens;
    private Boolean thinkingEnabled;
    private String reasoningEffort;
}

// chat/dto/LlmModelItem.java
@Data
public class LlmModelItem {
    private String id;
    private String ownedBy;
}

// chat/dto/LlmTestResult.java
@Data
public class LlmTestResult {
    private Boolean ok;
    private String model;
    private Long latencyMs;
    private String error;
}
```

### 3.3 数据库变更

`t_user_preference` 追加一列：

```sql
ALTER TABLE `t_user_preference`
  ADD COLUMN `llm_config_json` JSON NULL COMMENT 'LLM 配置 JSON，apiKey 字段为 AES 加密密文' AFTER `preferences_json`;
```

`llm_config_json` 结构（落库形态，apiKey 为密文）：

```json
{
  "baseUrl": "https://api.deepseek.com",
  "apiKey": "<AES密文Base64>",
  "model": "deepseek-v4-pro",
  "maxInputContextTokens": 204800,
  "maxOutputContextTokens": 65536,
  "thinkingEnabled": true,
  "reasoningEffort": "high"
}
```

`fitmate_init.sql` 中 `t_user_preference` 建表语句同步追加 `llm_config_json` 列定义。

---

## 4. 后端设计

### 4.1 配置加密

新增 `fitmate.llm.encryption-key` 配置项（env 注入，32 字节 Base64），用于 AES-256 加密 apiKey。

- 新增 `LlmConfigCipher`（`chat/infrastructure`）：
  - `encrypt(plainKey)` → AES/GCM 加密 + Base64
  - `decrypt(cipherText)` → 解密还原
  - `mask(plainKey)` → 脱敏（保留前 3 + 后 4，中间用 `****`）
- 启动时校验 `fitmate.llm.encryption-key` 非空，否则启动失败（fail-fast）

### 4.2 配置解析器

新增 `LlmConfigResolver`（`chat/application`，`@Component`）：

```java
@Component
public class LlmConfigResolver {
    private final UserPreferenceMapper userPreferenceMapper;
    private final LlmConfigCipher cipher;
    private final LlmConfigEnvDefaults envDefaults;  // @ConfigurationProperties

    /** 解析当前登录用户的 LLM 配置（DB 覆盖 env） */
    public ResolvedLlmConfig resolveForCurrentUser() {
        Long userId = UserContextHolder.getRequired().getUserId();
        return resolveByUserId(userId);
    }

    /** 解析指定用户的 LLM 配置 */
    public ResolvedLlmConfig resolveByUserId(Long userId);

    /** 保存用户 LLM 配置（apiKey 加密落库） */
    public void saveByUserId(Long userId, LlmConfigSaveRequest request);

    /** 获取用户 LLM 配置（脱敏，供 GET 接口返回） */
    public LlmConfigItem getByUserId(Long userId);
}
```

`ResolvedLlmConfig`（内部使用，apiKey 为明文）：

```java
@Data
public class ResolvedLlmConfig {
    private String baseUrl;
    private String apiKey;       // 明文（已解密）
    private String model;
    private Integer maxInputContextTokens;
    private Integer maxOutputContextTokens;
    private Boolean thinkingEnabled;
    private String reasoningEffort;
}
```

env 默认值通过 `@ConfigurationProperties("fitmate.llm")` 注入：

```yaml
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
```

### 4.3 ReasoningChatClient 改造

`ReasoningChatClient` 移除 `@Value` 静态注入，改为注入 `LlmConfigResolver`，每次调用动态解析：

```java
@Component
public class ReasoningChatClient {
    private final LlmConfigResolver llmConfigResolver;
    private final HttpClient httpClient;

    public Flux<ReasoningStreamChunk> stream(String prompt) {
        ResolvedLlmConfig config = llmConfigResolver.resolveForCurrentUser();
        String requestBody = buildRequestBody(prompt, config);
        // ... 用 config.getBaseUrl() / config.getApiKey() 构造请求
    }

    private String buildRequestBody(String prompt, ResolvedLlmConfig config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));
        body.put("reasoning_effort", config.getReasoningEffort());
        body.put("thinking", Map.of("type", config.getThinkingEnabled() ? "enabled" : "disabled"));
        body.put("max_tokens", config.getMaxOutputContextTokens());
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        return JSONUtil.toJsonStr(body);
    }

    private Integer resolveContextWindow(ResolvedLlmConfig config) {
        return config.getMaxInputContextTokens();
    }
}
```

**兼容性**：当 `UserContextHolder` 中无用户（如系统级调用）时，`LlmConfigResolver.resolveForCurrentUser()` 回退到 env 默认值，保证现有非用户态调用不破坏。

### 4.4 新增接口

追加到 `UserController`，前缀 `/user`：

| 方法 | 路径 | 说明 | 请求体 | 返回 |
|---|---|---|---|---|
| GET | `/user/llm-config` | 获取当前用户 LLM 配置（apiKey 脱敏） | — | `LlmConfigItem` |
| PUT | `/user/llm-config` | 保存 LLM 配置（apiKey 为空表示不修改原值） | `LlmConfigSaveRequest` | `LlmConfigItem` |
| POST | `/user/llm/models` | 代理调用 DeepSeek `GET /models` | `{"baseUrl":"...","apiKey":"..."}` 或空（用已存配置） | `List<LlmModelItem>` |
| POST | `/user/llm/test` | 测活：极简 chat completion（max_tokens=1） | `{"baseUrl":"...","apiKey":"...","model":"..."}` 或空 | `LlmTestResult` |

实现要点：
- list-models 与 test 接口接收请求体中的 baseUrl/apiKey/model；若请求体为空或字段为空，则用当前用户已存配置
- 代理请求超时 10 秒
- test 接口构造请求：`{model, messages:[{role:"user",content:"ping"}], max_tokens:1, stream:false, thinking:{type:"disabled"}}`（关闭思考以加速）
- 成功返回 `{ok:true, model, latencyMs}`，失败返回 `{ok:false, error:"<status 4xx/5xx 或异常信息>"}`
- 全部用 `LeeResult` 包装响应
- 复用 `UserContextHolder.getRequired()` 取当前用户

### 4.5 调用链影响

`ReasoningChatClient` 被 `ChatServiceImpl`（3 处）与 `SpringAiLlmGateway`（agent 网关）调用，二者均在 HTTP 请求线程内，`UserContextHolder` ThreadLocal 可用。改造后调用方无需修改签名。

---

## 5. 前端设计

### 5.1 LLM 配置服务（新增 `src/services/llmConfig.ts`）

```typescript
// 仿 theme.ts 模式
export const llmConfig = {
  state: { config: DEFAULT_LLM_CONFIG, models: [] as LlmModelOption[] },
  async load() { /* GET /user/llm-config → 更新 state + localStorage 缓存 */ },
  get(): LlmConfig { return state.config; },
  async save(patch: Partial<LlmConfig>): Promise<void> { /* PUT /user/llm-config */ },
  async fetchModels(): Promise<LlmModelOption[]> { /* POST /user/llm/models */ },
  async testConnection(payload): Promise<LlmTestResult> { /* POST /user/llm/test */ },
  subscribe(cb): () => void { /* 配置变更通知 */ },
};
```

- `AppLayout` 挂载后并行调 `loadLlmConfig()` 与 `getUserPreferences()`
- 配置变更时通知所有订阅者（ChatPage 订阅以更新模型选择）

### 5.2 设置页「配置」区块

#### 5.2.1 SettingsSectionNav 新增项

```js
{ id: "llm", label: "配置", icon: "tune" }
```

插入位置：appearance 之后、about 之前。

#### 5.2.2 新增 `LlmConfigSection.vue`

表单卡片布局：

```
┌─────────────────────────────────────────┐
│ 配置                                     │
│ 管理 DeepSeek API 连接与模型参数          │
├─────────────────────────────────────────┤
│ API 地址 *                               │
│ [https://api.deepseek.com          ]    │
│                                          │
│ API Key *                                │
│ [sk-****e05f （点击编辑）          ]    │
│                                          │
│ 模型                          [拉取列表] │
│ [deepseek-v4-pro          ▼]    [测活]   │
│                                          │
│ 输入上下文最大值（token）                 │
│ [204800]                                 │
│                                          │
│ 输出上下文最大值（token）                 │
│ [65536]                                  │
│                                          │
│ 思考模式                    [开关 ON]    │
│                                          │
│ 推理强度                    ( )high ( )max│
│                                          │
│                          [保存]          │
└─────────────────────────────────────────┘
```

交互：
- API 地址：文本输入，必填校验，placeholder `https://api.deepseek.com`
- API Key：密码输入，必填。GET 返回脱敏值；编辑时点击输入框清空让用户重填；保存时若输入框为空则后端保留原密文
- 模型：下拉选择。「拉取列表」按钮调 `fetchModels()`，用当前表单中的 baseUrl+apiKey（若为空用已存配置）请求后端代理，成功后填充下拉选项
- 测活按钮：用当前表单中的 baseUrl+apiKey+model 调 `testConnection()`，显示结果（成功 ✓ + 耗时，失败 ✗ + 错误）
- 输入/输出上下文最大值：数字输入，最小值校验（>0）
- 思考模式：开关
- 推理强度：high/max 单选按钮组
- 保存按钮：校验通过后调 `save()`，成功 toast 提示
- 加载时调 `llmConfig.load()` 填充表单

### 5.3 聊天页输入框改造（ChatInput.vue）

#### 5.3.1 删除 disclaimer

移除：
```html
<p class="chat-input-disclaimer">FIT-AGENT MAY PRODUCE INACCURATE INFORMATION ABOUT WORKOUTS OR NUTRITION.</p>
```
及对应 `.chat-input-disclaimer` 样式。

#### 5.3.2 新增模型选择下拉（左下角）

新增组件 `src/pages/chat/components/ModelSelector.vue`：

```vue
<template>
  <div class="model-selector">
    <button class="model-selector-trigger" @click="toggle">
      <span class="material-symbols-outlined">neurology</span>
      <span class="model-name">{{ model }}</span>
      <span class="material-symbols-outlined chevron">{{ open ? 'expand_less' : 'expand_more' }}</span>
    </button>
    <div v-if="open" class="model-selector-dropdown">
      <button v-for="m in models" :key="m.id"
        class="model-option"
        :class="{ active: m.id === model }"
        @click="select(m.id)">
        {{ m.id }}
      </button>
    </div>
  </div>
</template>
```

ChatInput footer 改造：
```html
<div class="chat-input-footer">
  <ModelSelector
    :model="currentModel"
    :models="availableModels"
    @select="onModelSelect"
  />
  <TokenUsageIndicator :token-usage="tokenUsage" />
</div>
```

ChatInput 新增 props：`currentModel`（string）、`availableModels`（LlmModelOption[]）；新增 emit：`select-model`（modelId）。

#### 5.3.3 placeholder 字体颜色改淡

```css
.chat-input-field::placeholder {
  color: color-mix(in srgb, var(--color-on-surface-variant) 50%, transparent);
}
```

（从原 `var(--color-on-surface-variant)` 改为 50% 透明度混合，视觉更淡）

### 5.4 ChatPage/ChatLogicBase 接入

- ChatLogicBase `data` 追加：`currentModel`、`availableModels`
- `created` 中订阅 `llmConfig` 变更，更新 `currentModel` 与 `availableModels`
- `onModelSelect(modelId)` 调 `llmConfig.save({ model: modelId })`，保存成功后即时更新本地状态（无需刷新页面）
- ChatPage 模板中给 `<ChatInput>` 传入新 props 与监听 `select-model` 事件
- `availableModels` 初始为空，进入聊天页时若为空调 `llmConfig.fetchModels()` 填充

---

## 6. 默认值汇总

| 字段 | 默认值 | 来源 |
|---|---|---|
| baseUrl | `https://api.deepseek.com` | env `OPENAI_BASE_URL` |
| apiKey | env 中的 key（无用户配置时） | env `OPENAI_API_KEY` |
| model | `deepseek-v4-flash` | env `OPENAI_MODEL` |
| maxInputContextTokens | 204800（200K） | 硬编码（用户指定） |
| maxOutputContextTokens | 65536（64K） | 硬编码（用户指定） |
| thinkingEnabled | true | 硬编码 |
| reasoningEffort | `high` | env `REASONING_EFFORT` |

---

## 7. 代码结构与命名规范

遵循现有约定：Vue Options API、`<Feature>Page.vue` 命名、扁平 `components/`、`doctorApi.ts` 集中 API、后端 DDD 分层。

### 7.1 前端新增/修改

```
src/
  pages/
    settings/
      SettingsPage.vue                 # 修改 · 新增 llm 区块分支
      components/
        SettingsSectionNav.vue         # 修改 · 新增 llm 导航项
        LlmConfigSection.vue           # 新增 · LLM 配置区块
    chat/
      components/
        ChatInput.vue                  # 修改 · 删除 disclaimer、新增 ModelSelector、placeholder 改淡
        ModelSelector.vue              # 新增 · 模型选择下拉
      ChatLogicBase.vue                # 修改 · 订阅 llmConfig、新增 currentModel/availableModels
      ChatPage.vue                     # 修改 · 传入新 props
  services/
    llmConfig.ts                       # 新增 · LLM 配置状态管理
    doctorApi.ts                       # 修改 · 追加 getLlmConfig/saveLlmConfig/listLlmModels/testLlmConnection
  types/
    settings.ts                        # 修改 · 追加 LlmConfig/LlmModelOption/LlmTestResult/DEFAULT_LLM_CONFIG
  layouts/
    AppLayout.vue                      # 修改 · 挂载后并行调 llmConfig.load()
```

### 7.2 后端新增/修改

```
com.itgeo.fitmate.api/
  auth/
    dto/
      LlmConfigItem.java               # 新增
      LlmConfigSaveRequest.java        # 新增
    controller/
      UserController.java              # 修改 · 追加 4 个 LLM 接口
    application/
      UserService.java                 # 修改 · 追加 LLM 配置方法签名（委托 LlmConfigResolver）
      impl/UserServiceImpl.java        # 修改 · 实现
  chat/
    application/
      LlmConfigResolver.java           # 新增 · 配置解析（DB 覆盖 env）
    infrastructure/
      LlmConfigCipher.java             # 新增 · AES 加解密 + 脱敏
      ReasoningChatClient.java         # 修改 · 移除 @Value，注入 LlmConfigResolver
    dto/
      LlmModelItem.java                # 新增
      LlmTestResult.java               # 新增
  config/
    LlmConfigProperties.java           # 新增 · @ConfigurationProperties("fitmate.llm")
```

### 7.3 配置文件修改

```
FitMate-api/src/main/resources/
  application.yml                      # 修改 · 新增 fitmate.llm 配置块
  application-dev.yml                  # 修改 · 新增 fitmate.llm.encryption-key 默认值
FitMate-mcpServer/src/main/resources/sql/
  fitmate_init.sql                     # 修改 · t_user_preference 追加 llm_config_json 列
```

---

## 8. 数据流

### 8.1 配置加载流

1. `AppLayout` 挂载 → 并行调 `getUserPreferences()` 与 `llmConfig.load()`
2. `llmConfig.load()` 调 `GET /user/llm-config` → 后端读 DB（脱敏）→ 返回 `LlmConfigItem`
3. 前端缓存到内存 state + localStorage，通知订阅者

### 8.2 配置保存流（设置页）

1. 用户在 `LlmConfigSection` 编辑表单 → 点「保存」
2. 前端校验 baseUrl/apiKey 必填 → 调 `PUT /user/llm-config`（apiKey 为空表示不改）
3. 后端加密 apiKey 落库 → 返回脱敏 `LlmConfigItem`
4. 前端更新 state + 通知订阅者（ChatPage 收到通知更新模型选择）

### 8.3 模型拉取流

1. 用户点「拉取列表」→ 前端用当前表单的 baseUrl+apiKey（空则用已存）调 `POST /user/llm/models`
2. 后端代理调 DeepSeek `GET /models`（10s 超时）→ 返回模型列表
3. 前端填充下拉选项

### 8.4 测活流

1. 用户点「测活」→ 前端用当前表单的 baseUrl+apiKey+model 调 `POST /user/llm/test`
2. 后端代理调 DeepSeek `POST /chat/completions`（max_tokens=1, thinking=disabled, 10s 超时）
3. 成功返回 `{ok:true, model, latencyMs}`，失败返回 `{ok:false, error}`
4. 前端显示结果

### 8.5 聊天页模型切换流

1. 用户在输入框左下角点模型名 → 展开下拉 → 选新模型
2. ChatInput emit `select-model` → ChatLogicBase `onModelSelect`
3. 调 `llmConfig.save({ model: modelId })` → PUT 后端 → 返回脱敏配置
4. 前端更新 state → ChatInput 收到新 `currentModel` 立即显示
5. 下次发消息时 `ReasoningChatClient` 解析到新 model 生效

### 8.6 聊天请求执行流（改造后）

1. 用户发消息 → `doctorApi.agentExecute` → 后端 Agent 循环
2. `SpringAiLlmGateway` 调 `reasoningChatClient.stream(prompt)`
3. `ReasoningChatClient` 内部调 `LlmConfigResolver.resolveForCurrentUser()`
4. 从 DB 读用户配置（apiKey 解密）→ DB 无值回退 env
5. 用解析到的 baseUrl/apiKey/model/thinking/reasoningEffort/maxOutputContextTokens 构造请求
6. 流式返回 → SSE 推送前端

---

## 9. 分阶段计划

| 阶段 | 内容 | 产出 |
|---|---|---|
| **P1 后端配置基础设施** | `LlmConfigCipher` + `LlmConfigProperties` + `LlmConfigResolver` + DDL 加列 + `application.yml` 配置块 + `UserPreference` 实体加字段 | 加解密、配置解析可独立测通 |
| **P2 后端 ReasoningChatClient 改造** | 移除 `@Value`，注入 `LlmConfigResolver`，动态解析配置 | 现有聊天/Agent 流程不破坏，DB 无值行为同 env |
| **P3 后端 LLM 接口** | `UserController` 追加 4 个接口 + DTO + list-models/test 代理实现 | 4 个接口可独立测通 |
| **P4 前端配置服务与类型** | `llmConfig.ts` + `settings.ts` 类型 + `doctorApi` 追加方法 + `AppLayout` 加载 | 配置可加载、可保存、可订阅 |
| **P5 前端设置页配置区块** | `LlmConfigSection.vue` + `SettingsSectionNav` 加项 + `SettingsPage` 路由分支 | 设置页可配置 LLM、可拉取模型、可测活 |
| **P6 前端聊天页输入框改造** | 删除 disclaimer + `ModelSelector.vue` + `ChatInput` 改造 + `ChatLogicBase`/`ChatPage` 接入 + placeholder 改淡 | 输入框左下角可切模型、disclaimer 消失、placeholder 变淡 |
| **P7 联调与回归** | 全流程：设置→保存→聊天生效；DB 无值回退 env；测活；模型切换；加解密 | 全流程验收 |

### 9.1 执行注意事项

- **P1 优先**：加密基础设施与 DDL 是后续阶段前提，需先验证加解密与配置解析正确
- **P2 兼容性验证**：ReasoningChatClient 改造后，必须验证 DB 无用户配置时行为与现状一致（回退 env）
- **P3 超时控制**：list-models 与 test 代理请求必须设短超时（10s），避免拖死设置页
- **P4/P5 可与 P2/P3 并行**：前端配置服务依赖 P3 接口，但类型与状态管理可先行
- **P6 依赖 P4**：ModelSelector 依赖 `llmConfig` 服务的模型列表与订阅能力

---

## 10. 风险与注意事项

1. **API Key 加密密钥管理**：`fitmate.llm.encryption-key` 必须通过 env 注入，禁止硬编码。启动时 fail-fast 校验非空。密钥丢失将导致已加密 key 无法解密。

2. **UserContextHolder 线程传播**：`ReasoningChatClient` 改造后依赖 `UserContextHolder.getRequired()`。Agent 异步执行若跨线程（如 `@Async` 或线程池），需确认 ThreadLocal 传播。现有 `SpringAiLlmGateway` 在 HTTP 请求线程内调用，应无问题，但 P2 阶段需验证 Agent 异步路径。

3. **脱敏 key 编辑体验**：GET 返回 `sk-****e05f`，编辑时若用户不改 key，输入框为空，PUT 时 apiKey 字段为空 → 后端保留原密文。若用户输入新值，则加密覆盖。需在 UI 上明确提示「留空表示不修改」。

4. **list-models/test CORS**：前端直连 DeepSeek 会有 CORS 与 key 暴露问题，必须走后端代理。已设计为后端代理。

5. **测活请求计费**：测活会发送一个 max_tokens=1 的请求，会产生极小费用（约 0.000002 元）。属可接受范围，但 UI 应提示用户这是真实请求。

6. **配置覆盖语义清晰性**：DB 有值时完全覆盖 env（不是合并）。即用户一旦保存任何字段，所有字段都以 DB 为准。默认值在 DB 无值时由 env 提供。需在 `LlmConfigResolver` 中实现：DB 有 `llm_config_json` → 解密 + 用 DB 值；DB 无 → 用 env 默认值。

7. **ReasoningChatClient 兼容非用户态调用**：若存在系统级（无登录用户）调用，`UserContextHolder.getRequired()` 会抛异常。`LlmConfigResolver.resolveForCurrentUser()` 需捕获并回退 env 默认值，或提供 `resolveForSystem()` 方法。

8. **数据库迁移**：`t_user_preference` 加列需在 P1 阶段执行 DDL。已有用户数据不受影响（新列默认 NULL）。

9. **模型列表缓存**：`availableModels` 在前端缓存，DeepSeek 上线新模型后需用户重新点「拉取列表」刷新。可考虑设置过期时间（如 1 小时），但本阶段简化处理。

10. **输入上下文最大值本阶段不生效**：`maxInputContextTokens` 仅存值与展示，不实现历史消息截断。后端 `ReasoningChatClient.resolveContextWindow` 用此值作为 `TokenUsage.windowSize` 展示，但不主动截断。截断留待后续阶段。

---

## 11. 待确认事项

无。所有关键决策点已在协作过程中确认（见 §2）。

---

## 12. 参考资料

- DeepSeek API 首页：https://api-docs.deepseek.com/zh-cn/
- DeepSeek 列出模型：https://api-docs.deepseek.com/zh-cn/api/list-models
- DeepSeek 对话补全：https://api-docs.deepseek.com/zh-cn/api/create-chat-completion
- DeepSeek 模型与价格：https://api-docs.deepseek.com/zh-cn/quick_start/pricing
- 前序 spec：`docs/superpowers/specs/2026-07-02-user-menu-settings-design.md`
- 现有 LLM 客户端：`FitMate-api/src/main/java/com/itgeo/fitmate/api/chat/infrastructure/ReasoningChatClient.java`
- 现有偏好表：`t_user_preference`（`fitmate_init.sql`）
- 现有偏好实体：`UserPreference.java`、`UserPreferenceItem.java`、`UserPreferenceMapper.java`
- 现有聊天页输入框：`FitMate-frontend/src/pages/chat/components/ChatInput.vue`
- 现有设置页：`FitMate-frontend/src/pages/settings/SettingsPage.vue`
