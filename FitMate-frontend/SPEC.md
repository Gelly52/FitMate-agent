# FitMate-AI 前端代码规范 SPEC

适用范围：`FitMate-frontend` 前端工程的页面、组件、路由、服务层、样式、构建配置与类型定义。  
当前技术栈：Vue 3、TypeScript、Vite、Vue Router、Axios、Tailwind CSS、marked。

---

## 1. 当前前端工程结构

```text
FitMate-frontend/
├─ fitmate-vite.html        # Vite HTML 入口，vite.config.ts 已显式引用
├─ package.json              # npm 依赖与脚本
├─ package-lock.json         # npm 锁文件，保留
├─ vite.config.ts            # Vite 构建与开发服务器配置
├─ tsconfig.json             # TypeScript 配置
├─ tailwind.config.js        # Tailwind 主题与扫描范围
├─ postcss.config.js         # PostCSS 配置
├─ src/
│  ├─ main.ts                # Vue 应用入口
│  ├─ App.vue                # 根组件
│  ├─ router/                # 路由配置与鉴权守卫
│  ├─ layouts/               # 页面布局
│  ├─ components/            # 跨页面通用组件
│  ├─ pages/                 # 页面级模块
│  ├─ services/              # HTTP、SSE、业务 API 服务
│  ├─ config/                # 运行时配置
│  ├─ utils/                 # 纯工具函数
│  ├─ types/                 # 全局或共享类型声明
│  └─ styles/                # 全局样式
└─ SPEC.md
```

## 2. 命名规范

### 2.1 文件与目录

- 页面组件使用 `XxxPage.vue`，放在 `src/pages/<feature>/` 下。
- 通用组件使用 UpperCamelCase，例如 `SideNav.vue`、`TopBar.vue`。
- 页面内私有组件放在页面目录的 `components/` 下。
- 服务文件使用 lowerCamelCase，例如 `doctorApi.ts`、`sseService.ts`。
- 工具文件使用 lowerCamelCase，例如 `sourceNormalizer.ts`。
- 全局样式放在 `src/styles/`。
- 不新增中文、空格、短横线混用的源码文件名。

### 2.2 代码命名

- Vue 组件名使用 UpperCamelCase。
- 函数、变量、参数使用 lowerCamelCase。
- 常量使用 UPPER_SNAKE_CASE 或明确语义的只读对象。
- 布尔值使用 `is`、`has`、`can`、`should` 等语义前缀。
- API 方法名使用动作 + 领域名，例如 `createSseTicket`、`getAgentRuns`。

---

## 3. 目录职责

### 3.1 `src/main.ts`

- 只负责创建 Vue 应用、注册全局插件、挂载应用。
- 不写业务逻辑。
- 全局副作用导入应保持少量且明确，例如运行时配置、HTTP 实例、SSE 服务。

### 3.2 `src/router/`

- 只维护路由表、页面懒加载、路由守卫和页面标题。
- 鉴权判断只读取会话状态，不直接发起登录、登出或业务请求。
- 新页面必须配置明确的 `name` 与 `meta.title`。
- 路由路径使用小写短横线或稳定业务名。

### 3.3 `src/pages/`

- 页面级组件负责组织页面状态、调用服务、编排页面内组件。
- 页面不应直接写底层 `axios` 配置。
- 页面内复杂 UI 拆到 `components/` 子目录。
- 页面目录按功能划分，例如 `chat/`、`login/`、`training/`、`metrics/`、`knowledge/`、`dashboard/`。

### 3.4 `src/components/`

- 只放跨页面复用组件。
- 组件通过 props/emits 与父级通信。
- 组件不直接依赖具体页面状态，除非其职责明确是布局级组件。

### 3.5 `src/services/`

- `http.ts` 只负责 Axios 实例、请求头注入、会话 Cookie 读写和拦截器。
- `doctorApi.ts` 只封装后端 HTTP API 方法，不承载页面状态。
- `sseService.ts` 只封装 SSE URL 构建、事件绑定、连接关闭。
- 新增 API 时优先在服务层封装，再由页面调用。

### 3.6 `src/config/`

- 只放运行时配置解析。
- API baseURL 由运行环境推导或集中配置，不在页面组件中硬编码。

### 3.7 `src/utils/`

- 只放无副作用或低副作用的纯工具函数。
- 不访问 DOM、不发 HTTP 请求、不读写全局会话。

### 3.8 `src/types/`

- 放共享类型、全局声明和接口模型。
- 不放运行时代码。

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
- 业务 API 统一在 `doctorApi.ts` 或按领域拆分后的服务文件中封装。
- 认证信息继续使用当前约定：
  - Cookie：`user_token`
  - Cookie：`user_info`
  - Header：`headerUserToken`
  - Header：`headerUserId`
- 页面不直接拼接认证 Header。
- API 方法只返回请求结果，不在服务层直接操作路由或组件状态。
- 错误处理需要保留可读信息；不要吞掉异常后返回空对象。

---

## 6. SSE 规范

- 浏览器 SSE 统一通过 `src/services/sseService.ts` 创建和关闭。
- 连接路径保持集中定义：`/sse/connect`。
- SSE ticket 通过 `createSseTicket` 获取，不在页面中手写 ticket 接口。
- 事件名集中维护在 `SSE_EVENT_NAMES`。
- 页面卸载或会话切换时必须关闭 SSE 连接。
- SSE 处理函数只处理事件解析和 UI 更新，不直接改写底层连接实现。

---

## 7. 状态与持久化规范

- 当前项目没有集中状态库，页面状态优先放在页面组件内部。
- 跨页面会话状态通过 `http.ts` 中的 Cookie helper 读取。
- 不新增多个分散的 token/user 存储实现。
- 如后续引入状态库，必须先统一会话状态来源，避免 Cookie、localStorage、内存状态互相覆盖。

---

## 8. 样式规范

- 全局样式放在 `src/styles/base.css`。
- Tailwind 主题扩展集中在 `tailwind.config.js`。
- 优先使用已有主题色、间距、字体和圆角 token。
- 页面私有样式可以放在对应 Vue 文件中，但不要重复定义全局 token。
- 暗色模式由根节点 `dark` class 控制。
- 不在组件中散落大量无法复用的魔法颜色值；新增颜色先评估是否应进入 Tailwind theme。

---

## 9. TypeScript 规范

- 继续使用 `@/*` 指向 `src/*` 的路径别名。
- 新增类型优先显式定义接口，不用 `any` 表达稳定结构。
- 后端响应、SSE payload、页面表单数据应逐步补齐类型。
- 不为了一次性局部逻辑创建过度泛型。
- 修改旧 JavaScript 风格代码时，只补当前改动需要的类型，不做大范围重构。

---

## 10. 构建与运行

常用命令：

```bash
npm install
npm run dev
npm run build
npm run preview
```

当前 Vite 开发服务器配置：

```text
host: 127.0.0.1
port: 5500
strictPort: true
```

构建入口是 `fitmate-vite.html`。不要在未同步 `vite.config.ts` 的情况下重命名或删除该文件。

---

## 11. 变更检查清单

- [ ] 新页面是否在 `src/router/index.ts` 注册，并配置 `meta.title`？
- [ ] 新 API 是否先封装到服务层，而不是在页面中直接写 Axios？
- [ ] 是否复用了 `http.ts` 的认证 Header 注入？
- [ ] SSE 连接是否在页面卸载时关闭？
- [ ] 新组件是否放在合适目录：通用组件进 `components/`，页面私有组件进页面目录？
- [ ] 修改构建配置后是否运行 `npm run build`？
- [ ] 是否没有引入真实 Token、API Key、Cookie 等敏感信息？
