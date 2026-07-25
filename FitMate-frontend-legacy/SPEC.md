# FitMate-AI 前端代码规范 SPEC

适用范围：`FitMate-frontend` 前端工程的页面、组件、路由、服务层、样式、构建配置与类型定义。  
当前技术栈：Vue 3、TypeScript、Vite、Vue Router（Hash 模式）、Axios、Tailwind CSS、marked。

---

## 1. 前端工程结构

```text
FitMate-frontend/
├─ fitmate-vite.html        # Vite HTML 入口（非默认 index.html）
├─ package.json              # npm 依赖与脚本
├─ package-lock.json         # npm 锁文件
├─ vite.config.ts            # Vite 构建与开发服务器配置
├─ tsconfig.json             # TypeScript 配置
├─ tailwind.config.js        # Tailwind 主题与扫描范围
├─ postcss.config.js         # PostCSS 配置
├─ src/
│  ├─ main.ts                # Vue 应用入口
│  ├─ App.vue                # 根组件
│  ├─ env.d.ts               # 环境类型声明
│  ├─ router/                # 路由配置与鉴权守卫
│  ├─ layouts/               # 页面布局
│  ├─ components/            # 跨页面通用组件
│  ├─ pages/                 # 页面级模块
│  ├─ services/              # HTTP、SSE、业务 API、缓存、主题、通知
│  ├─ config/                # 运行时配置
│  ├─ utils/                 # 纯工具函数
│  ├─ types/                 # 全局或共享类型声明
│  └─ styles/                # 全局样式与 token
└─ SPEC.md
```

### 1.1 页面结构

```text
src/pages/
├─ chat/
│  ├─ ChatLogicBase.vue           # 聊天逻辑基类
│  ├─ ChatPage.vue                # 聊天页面入口
│  └─ components/                 # 页面私有组件
│     ├─ AgentStepCard.vue
│     ├─ ChatInput.vue
│     ├─ ChatMessageList.vue
│     ├─ ModelSelector.vue
│     ├─ ReasoningTraceBlock.vue
│     ├─ SourceCardList.vue
│     ├─ SubAgentTraceBlock.vue
│     ├─ TokenUsageIndicator.vue
│     └─ WelcomePanel.vue
├─ dashboard/
│  ├─ DashboardPage.vue
│  └─ components/
│     └─ UserProfilePanel.vue
├─ knowledge/
│  └─ KnowledgePage.vue
├─ login/
│  └─ LoginPage.vue
├─ metrics/
│  └─ MetricsPage.vue
├─ settings/
│  ├─ SettingsPage.vue
│  └─ components/
│     ├─ AboutSection.vue
│     ├─ AppearanceSection.vue
│     ├─ LlmConfigSection.vue
│     ├─ McpConfigSection.vue
│     ├─ MemorySection.vue
│     ├─ ProfileSection.vue
│     ├─ SettingsSectionNav.vue
│     └─ SkillsSection.vue
├─ training/
│  └─ TrainingPage.vue
└─ wiki/
   └─ WikiPage.vue
```

> `training`、`metrics`、`knowledge`、`login`、`wiki` 均为单文件页面，无 `components/` 子目录。仅 `chat`、`dashboard`、`settings` 有 `components/` 子目录。

---

## 2. 命名规范

### 2.1 文件与目录

- 页面组件使用 `XxxPage.vue`，放在 `src/pages/<feature>/` 下。
- 通用组件使用 UpperCamelCase，例如 `SideNav.vue`、`TopBar.vue`、`UserMenu.vue`。
- 页面内私有组件放在页面目录的 `components/` 下。
- 服务文件使用 lowerCamelCase，例如 `doctorApi.ts`、`sseService.ts`、`thinkingCache.ts`。
- 工具文件使用 lowerCamelCase，例如 `agentEventAdapter.ts`、`sourceNormalizer.ts`。
- 全局样式放在 `src/styles/`。
- 不新增中文、空格、短横线混用的源码文件名。

### 2.2 代码命名

- Vue 组件名使用 UpperCamelCase。
- 函数、变量、参数使用 lowerCamelCase。
- 常量使用 UPPER_SNAKE_CASE 或明确语义的只读对象。
- 布尔值使用 `is`、`has`、`can`、`should` 等语义前缀。
- API 方法名使用动作 + 领域名，例如 `createSseTicket`、`getAgentRuns`、`deleteRagDoc`。

---

## 3. 目录职责

### 3.1 `src/main.ts`

- 只负责创建 Vue 应用、注册全局插件、挂载应用。
- 不写业务逻辑。
- 全局副作用导入应保持少量且明确。

### 3.2 `src/router/`

- 使用 `createWebHashHistory()`（Hash 路由模式）。
- 只维护路由表、页面懒加载、路由守卫和页面标题。
- `beforeEach` 检查 `getToken() && getUserInfo()`，无会话则跳转 `/login`。
- `afterEach` 设置 `document.title = "FitMate / ${title}"`。
- 新页面必须配置明确的 `name` 与 `meta.title`。
- 路由路径使用小写短横线或稳定业务名。

当前路由表：

| 路径 | name | meta.title | 说明 |
| --- | --- | --- | --- |
| `/login` | `login` | Authenticate | `public: true` |
| `/` | — | — | AppLayout 外壳，redirect 到 `/chat` |
| `/chat/:sessionId?` | `chat` | Agent Chat | |
| `/training` | `training` | Training Log | `forceView: "training-log"` |
| `/body-metrics` | `body-metrics` | Body Metrics | `forceView: "body-metrics"` |
| `/wiki` | `wiki` | Wiki | |
| `/upload` | `upload` | Knowledge Base | |
| `/dashboard` | `dashboard` | Dashboard | |
| `/settings` | `settings` | Settings | |
| `/:pathMatch(.*)*` | — | — | 兜底 redirect 到 `/chat` |

### 3.3 `src/pages/`

- 页面级组件负责组织页面状态、调用服务、编排页面内组件。
- 页面不应直接写底层 `axios` 配置。
- 页面内复杂 UI 拆到 `components/` 子目录。
- 页面目录按功能划分：`chat/`、`login/`、`training/`、`metrics/`、`knowledge/`、`wiki/`、`dashboard/`、`settings/`。

### 3.4 `src/components/`

- 只放跨页面复用组件：`SideNav.vue`、`TopBar.vue`、`UserMenu.vue`。
- 组件通过 props/emits 与父级通信。
- 组件不直接依赖具体页面状态，除非其职责明确是布局级组件。

### 3.5 `src/services/`

| 文件 | 职责 |
| --- | --- |
| `http.ts` | Axios 实例、请求头注入、会话 Cookie 读写、拦截器。 |
| `doctorApi.ts` | 主 API 聚合，封装所有后端 HTTP 接口。 |
| `sseService.ts` | SSE URL 构建、事件绑定、连接关闭。 |
| `llmConfig.ts` | LLM 配置状态管理（发布订阅模式 + localStorage）。 |
| `memoryApi.ts` | 记忆系统 API。 |
| `wikiApi.ts` | Wiki 知识库 API。 |
| `theme.ts` | 主题模式与强调色管理（防抖同步后端）。 |
| `thinkingCache.ts` | 思考内容 sessionStorage 缓存。 |
| `toast.ts` | 轻量 Toast 通知。 |
| `http.test.ts` | http.ts 测试文件。 |

规则：

- `http.ts` 只负责 Axios 实例、请求头注入、会话 Cookie 读写和拦截器。
- `doctorApi.ts` 只封装后端 HTTP API 方法，不承载页面状态。
- `sseService.ts` 只封装 SSE URL 构建、事件绑定、连接关闭。
- 新增 API 时优先在服务层封装，再由页面调用。

### 3.6 `src/utils/`

| 文件 | 职责 |
| --- | --- |
| `agentEventAdapter.ts` | Agent 事件归一化工具，统一后端多种字段命名为 `AgentTraceEvent`。 |
| `sourceNormalizer.ts` | RAG 来源归一化工具，兼容多种字段命名。 |

- 只放无副作用或低副作用的纯工具函数。
- 不访问 DOM、不发 HTTP 请求、不读写全局会话。

### 3.7 `src/config/`

- `runtime.ts` 只放运行时配置解析。
- 本地开发（127.0.0.1/localhost）时 `API_BASE` 指向 `http://127.0.0.1:7070`。
- 部署到非本地域名时 `API_BASE` 为空串（同源，走反向代理）。
- API baseURL 由运行环境推导，不在页面组件中硬编码。

### 3.8 `src/types/`

| 文件 | 内容 |
| --- | --- |
| `agent.ts` | Agent 执行与追踪类型（`AgentRunStatus`、`AgentTraceEvent`、`AgentRunDetail` 等）。 |
| `settings.ts` | 用户设置、LLM 配置、MCP 配置、技能类型。 |
| `memory.ts` | 记忆系统类型（`MemoryItem`、`ProfileResponse` 等）。 |
| `global.d.ts` | 全局类型声明（Vue 组件属性、路由 meta、Window 接口扩展）。 |

- 放共享类型、全局声明和接口模型。
- 不放运行时代码。

### 3.9 `src/styles/`

- `base.css` 全局基础样式。
- `tokens.css` Obsidian Precision token 体系（Material Design 3 色板）。

---

## 4. Vue 组件规范

- 新组件优先使用 `<script setup lang="ts">`。
- 现有 Options API 组件可以保留；修改时不因风格偏好强制重写。
- 单文件组件顺序推荐：`template`、`script`、`style`。
- 组件 props 必须有明确名称和类型。
- 事件名使用 kebab-case 或项目已有约定，保持同一组件内一致。
- 组件不要直接修改父组件传入的对象。
- 大段异步流程不要写在模板表达式中。
- 页面组件可以处理业务编排；通用组件只处理展示和局部交互。

---

## 5. HTTP 与认证规范

- 所有 HTTP 请求通过 `src/services/http.ts` 创建的 Axios 实例发出。
- Axios `withCredentials: true`，`timeout: 120000`（120s）。
- 响应拦截器直接返回 `response.data`（后端 LeeResult body）。
- 业务 API 统一在 `doctorApi.ts` 或按领域拆分后的服务文件中封装。
- 认证信息使用当前约定：
  - Cookie：`user_token`（常量 `TOKEN_COOKIE_KEY`，maxAge 7 天）
  - Cookie：`user_info`（常量 `USER_INFO_COOKIE_KEY`）
  - Header：`headerUserId`（取自 `userInfo.userKey || userInfo.id`）
  - Header：`headerUserToken`（取自 token cookie）
- 页面不直接拼接认证 Header。
- API 方法只返回请求结果，不在服务层直接操作路由或组件状态。
- 错误处理需要保留可读信息；不要吞掉异常后返回空对象。
- `clearUserSession` 登出时清理 cookie + localStorage `fitmate_llm_config` + sessionStorage `fitmate:pending-draft`。
- `USER_INFO_CHANGED_EVENT = "fitmate:user-info-changed"` 事件通知组件刷新。

---

## 6. SSE 规范

- 浏览器 SSE 统一通过 `src/services/sseService.ts` 创建和关闭。
- 连接路径集中定义：`SSE_CONNECT_PATH = "/sse/connect"`。
- URL 形如 `{API_BASE}/sse/connect?ticket={ticket}`。
- SSE ticket 通过 `createSseTicket` 获取，不在页面中手写 ticket 接口。
- 事件名集中维护在 `SSE_EVENT_NAMES`（`Object.freeze`）：

| 常量 | 值 |
| --- | --- |
| `OPEN` | `open` |
| `MESSAGE` | `message` |
| `ADD` | `add` |
| `THINKING` | `thinking` |
| `FINISH` | `finish` |
| `ERROR` | `error` |
| `CUSTOM_EVENT` | `customEvent` |
| `CUSTOM_EVENT_SNAKE` | `custom_event` |
| `AGENT_EVENT` | `agent_event` |
| `TRACE_EVENT` | `trace_event` |
| `AGENT_STEP` | `agentStep` |

- 核心函数：`buildSseConnectPath`、`buildSseConnectUrl`、`bindSseListeners`、`connectSse`、`closeSse`。
- 页面卸载或会话切换时必须关闭 SSE 连接。
- SSE 处理函数只处理事件解析和 UI 更新，不直接改写底层连接实现。

---

## 7. 状态与持久化规范

- 当前项目没有集中状态库，页面状态优先放在页面组件内部。
- 跨页面会话状态通过 `http.ts` 中的 Cookie helper 读取。
- LLM 配置状态通过 `llmConfig.ts` 发布订阅模式管理，localStorage key：`fitmate_llm_config`。
- 主题状态通过 `theme.ts` 管理，localStorage keys：`fitmate_theme_mode`、`fitmate_accent_color`。
- 思考内容缓存通过 `thinkingCache.ts` 管理，sessionStorage key 格式：`fitmate:thinking-cache:{userKey}:{sessionId}:{botMsgId}`。
- 不新增多个分散的 token/user 存储实现。
- 如后续引入状态库，必须先统一会话状态来源，避免 Cookie、localStorage、内存状态互相覆盖。

### 7.1 思考内容缓存约定

- TTL：1 小时。
- schema 版本：`v: 2`，字段升级时整体失效。
- Entry 结构含 `subRuns` 字段（子 Agent run 数据）。
- 失效策略：删除会话时按前缀批量删，登出/换号时清理全部。

---

## 8. 样式规范

- 全局样式放在 `src/styles/base.css` 与 `src/styles/tokens.css`。
- Tailwind 主题扩展集中在 `tailwind.config.js`。
- 颜色体系使用 Obsidian Precision token（Material Design 3 色板），全部映射到 CSS 变量。
- 涵盖 surface、primary、secondary、tertiary、error、background 等色系。
- 优先使用已有主题色、间距、字体和圆角 token。
- 页面私有样式可以放在对应 Vue 文件中，但不要重复定义全局 token。
- 暗色模式由根节点 `dark` class 控制。
- 不在组件中散落大量无法复用的魔法颜色值；新增颜色先评估是否应进入 Tailwind theme。
- borderRadius 偏紧凑风格：DEFAULT=0.125rem、lg=0.25rem、xl=0.5rem、full=0.75rem。
- fontFamily：Inter。
- 配合 `fitmate-vite.html` 防闪烁脚本使用主题。

---

## 9. TypeScript 规范

- 继续使用 `@/*` 指向 `src/*` 的路径别名。
- 新增类型优先显式定义接口，不用 `any` 表达稳定结构。
- 后端响应、SSE payload、页面表单数据应逐步补齐类型。
- 不为了一次性局部逻辑创建过度泛型。
- 修改旧 JavaScript 风格代码时，只补当前改动需要的类型，不做大范围重构。
- `global.d.ts` 扩展 `Window` 接口添加 `API_BASE`、`doctorApi`、`sseService` 等。
- `global.d.ts` 扩展 Vue 组件属性添加 `$message`（toast）。
- `global.d.ts` 扩展 `RouteMeta` 添加 `public`、`title`、`forceView` 字段。

---

## 10. 构建与运行

npm 脚本：

```bash
npm install      # 安装依赖
npm run dev      # 启动开发服务器
npm run build    # 构建生产包
npm run preview  # 预览构建产物
```

> 当前无 `type-check`、`test`、`lint` 脚本（虽已安装 vue-tsc 与 vitest）。

Vite 开发服务器配置：

```text
host: 127.0.0.1
port: 5500
strictPort: true
```

构建配置要点：

- `base: "./"`（相对路径，便于嵌入后端 static 资源）。
- `outDir: "../FitMate-backend/FitMate-api/src/main/resources/static"`（直接构建到后端资源目录）。
- `emptyOutDir: true`。
- 入口 HTML：`fitmate-vite.html`（非默认 index.html），不要在未同步 `vite.config.ts` 的情况下重命名或删除。
- `manualChunks` 分包：`vendor-vue`、`vendor-axios`、`vendor-marked`、`vendor`。
- 未配置 proxy；本地开发靠 `runtime.ts` 让 axios 直连 `http://127.0.0.1:7070`。

---

## 11. 删除确认规范

- 删除操作使用 `window.confirm()` 弹出确认对话框，保证跨页面一致性。
- 不依赖模板组件 `showConfirmDialog`（部分页面未渲染该组件会导致 Promise 不 resolve）。
- RAG 文档删除与 Wiki 页面删除均遵循此约定。

---

## 12. 变更检查清单

- [ ] 新页面是否在 `src/router/index.ts` 注册，并配置 `meta.title`？
- [ ] 新 API 是否先封装到服务层，而不是在页面中直接写 Axios？
- [ ] 是否复用了 `http.ts` 的认证 Header 注入？
- [ ] SSE 连接是否在页面卸载时关闭？
- [ ] 新组件是否放在合适目录：通用组件进 `components/`，页面私有组件进页面目录？
- [ ] 新增颜色是否进入 Tailwind theme 而非散落魔法值？
- [ ] 删除操作是否使用 `window.confirm()`？
- [ ] 思考内容缓存是否在会话切换/登出时失效？
- [ ] 修改构建配置后是否运行 `npm run build`？
- [ ] 是否没有引入真实 Token、API Key、Cookie 等敏感信息？
