# 用户下拉菜单与设置页设计

- **状态**：Draft（待用户最终审查）
- **创建日期**：2026-07-02
- **作者**：协作设计（用户 + AI 助手）
- **适用范围**：FitMate-AI 前端（Vue 3 + TS + Tailwind）+ 后端（Spring Boot，`auth` 模块）
- **关联**：复用现有 Material Design 3 token 体系与 `t_user` 用户模型

---

## 1. 背景与目标

### 1.1 现状问题

- **右上角 TopBar 头像**是一个静态 `<div>`，无用户信息、无下拉、无跳转
- **左下角 SideNav 的 "Settings"** 实际指向 `/dashboard`，并非真正的设置页（占位 bug）
- **无 `/settings` 或 `/profile` 路由**，用户无法查看/编辑个人信息、无法切换主题
- **主题色不可配置**：`tailwind.config.js` 硬编码 Material 3 dark token，纯暗色，无亮色变体

### 1.2 目标

1. 右上角头像 hover/click 展开下拉菜单，显示用户基本信息与导航行项
2. 下拉菜单可跳转"个人信息""设置"（与左下角 Settings 同一页面 `/settings`）
3. 新建统一设置页 `/settings`，含个人信息、外观、关于三个区块
4. 外观区块支持主题模式（亮/暗/跟随系统）与强调色预设（蓝/绿/橙/紫）
5. 修复左下角 Settings 指向 `/settings` 而非 `/dashboard`
6. 设置项前端 localStorage 即时生效 + 登录后异步同步后端（跨设备）
7. 个人信息部分可编辑（昵称、手机），邮箱/用户名只读

### 1.3 非目标（YAGNI）

- 不做自定义取色器（仅预设色板）
- 不做邮箱修改（邮箱是登录账号，改邮箱涉及验证流程）
- 不做通知偏好（通知系统尚未存在）
- 不做头像上传（用昵称首字母 fallback 到 person 图标）

---

## 2. 关键设计决策（已与用户确认）

| 决策点 | 选择 | 理由 |
|---|---|---|
| 页面组织 | 统一一个 `/settings` 页面，内部用区块 | 与"跳到同一界面"描述一致，结构简单 |
| 区块定位 | `#hash` 锚点（下拉菜单跳到对应区块） | 下拉菜单"个人信息/设置"都到 /settings，定位不同区块 |
| 设置持久化 | 前端 localStorage 即时 + 登录后异步同步后端 | 体验即时 + 跨设备同步 |
| 主题范围 | 强调色预设 + 亮/暗模式 + 跟随系统 | 体验完整，符合 Material 3 token 规范 |
| 个人信息编辑 | 部分可编辑（昵称、手机），邮箱/用户名只读 | 邮箱是登录账号不可改，避免验证流程复杂度 |
| 主题实现方案 | 方案 B：全量 CSS 变量重构 | 单一机制统一管理亮暗+强调色，切换零重渲染 |
| 下拉菜单布局 | 方案 A：纯导航（头部+个人信息/设置/退出登录） | 菜单精简，主题切换放设置页 |
| 设置页布局 | 方案 A：单列 + 顶部 sticky 锚点导航 | 与现有页面风格一致，移动端友好，区块少时最合适 |
| 设置页区块 | 个人信息 (#profile) + 外观 (#appearance) + 关于 (#about) | 覆盖核心需求，关于区块成本极低 |
| 后端模块归属 | 扩展 `auth` 模块（不新建 preference 模块） | 与现有 User 实体同模块，内聚 |
| 偏好表结构 | `preferences_json` JSON 字段 | 未来加设置无需改表结构 |

---

## 3. 下拉菜单设计（方案 A · 纯导航）

### 3.1 行项内容

| 区域 | 内容 | 行为 |
|---|---|---|
| 头部（只读） | 头像（昵称首字母，无昵称 fallback 到 `person` 图标）+ 昵称（无则 username）+ 邮箱 | 不可点击 |
| 个人信息 | `person` 图标 + "个人信息" | 跳转 `/settings#profile` |
| 设置 | `settings` 图标 + "设置" | 跳转 `/settings#appearance`（与左下角 Settings 同一页面） |
| 分隔线 | — | — |
| 退出登录 | `logout` 图标 + "退出登录"（error 色） | 触发 logout，替换 TopBar 现有 Logout 按钮 |

### 3.2 交互

- **触发**：hover 展开 + click 切换（移动端 click）
- **收起**：点击外部 / ESC / 路由跳转后自动收起
- **登出**：复用 `AppLayout.handleLogout` 逻辑（调 `doctorApi.userLogout` + `clearUserSession` + 跳 `/login`）
- **TopBar 改造**：用 `UserMenu` 组件替换原头像 `<div>` + 移除原 Logout 按钮

---

## 4. 设置页设计（方案 A · 单列 + 顶部锚点导航）

### 4.1 布局

- 页面标题 "设置" + 副标题
- 顶部 sticky 锚点 tab 导航（个人信息 / 外观 / 关于），点击平滑滚动到对应区块
- 区块纵向单列排列，每个区块一个卡片容器

### 4.2 区块内容

**# 个人信息（#profile）**
- 头像 + 昵称 + 邮箱（顶部展示）
- 字段网格：
  - 昵称 ✏️（可编辑，行内编辑）
  - 手机号 ✏️（可编辑，行内编辑，脱敏展示 `138****8888`）
  - 邮箱 🔒（只读，登录账号）
  - 用户名 🔒（只读）
  - 注册时间 🔒（只读）
  - 最近登录 🔒（只读）

**# 外观（#appearance）**
- 主题模式：亮色 / 暗色 / 跟随系统（三选一按钮组）
- 强调色：蓝 / 绿 / 橙 / 紫（圆形色板，选中带边框）

**# 关于（#about）**
- 版本号、构建号、源码链接

### 4.3 路由

- 新增 `/settings` 路由（在 AppLayout children 下）
- meta: `{ title: 'Settings' }`
- SideNav 底部 Settings 的 `to` 从 `/dashboard` 改为 `/settings`

---

## 5. 主题系统架构（方案 B · 全量 CSS 变量重构）

### 5.1 变量分层

新建 `src/styles/tokens.css`，把 `tailwind.config.js` 里所有 Material token 硬编码值迁移为 CSS 变量：

```css
:root, [data-theme="dark"] {
  --color-primary: #adc6ff;
  --color-on-primary: #002e69;
  --color-primary-container: #4b8eff;
  --color-on-primary-container: #00285c;
  --color-surface: #10131b;
  --color-on-surface: #e0e2ed;
  --color-on-surface-variant: #c1c6d7;
  --color-outline: #8b90a0;
  --color-outline-variant: #414755;
  --color-surface-tint: #adc6ff;
  --color-inverse-primary: #005bc1;
  --color-inverse-surface: #e0e2ed;
  --color-inverse-on-surface: #2d3039;
  --color-error: #ffb4ab;
  /* ... 其余 dark tokens 全部搬入 */
}

[data-theme="light"] {
  --color-primary: #005bc1;
  --color-on-primary: #ffffff;
  --color-surface: #fbfaff;
  --color-on-surface: #1a1b22;
  --color-on-surface-variant: #43474e;
  --color-outline: #74777f;
  --color-outline-variant: #c4c6cf;
  --color-surface-tint: #005bc1;
  --color-inverse-primary: #adc6ff;
  --color-inverse-surface: #2d3039;
  --color-inverse-on-surface: #f0f0f7;
  --color-error: #ba1a1a;
  /* ... Material 3 light scheme（同源蓝色，由 Material Theme Builder 生成完整集） */
}

/* 强调色：只覆盖 primary 系列 + surface-tint + inverse-primary */
[data-accent="blue"] {
  --color-primary: #adc6ff; --color-on-primary: #002e69;
  --color-primary-container: #4b8eff; --color-on-primary-container: #00285c;
  --color-surface-tint: #adc6ff; --color-inverse-primary: #005bc1;
}
[data-accent="green"] {
  --color-primary: #7ee787; --color-on-primary: #003915;
  --color-primary-container: #2daa3e; --color-on-primary-container: #00210b;
  --color-surface-tint: #7ee787; --color-inverse-primary: #006e1c;
}
[data-accent="orange"] {
  --color-primary: #ffb595; --color-on-primary: #571e00;
  --color-primary-container: #ef6719; --color-on-primary-container: #4c1a00;
  --color-surface-tint: #ffb595; --color-inverse-primary: #904a00;
}
[data-accent="purple"] {
  --color-primary: #c4a7e7; --color-on-primary: #2a0e5a;
  --color-primary-container: #8a5fc4; --color-on-primary-container: #1a0040;
  --color-surface-tint: #c4a7e7; --color-inverse-primary: #6b3fa0;
}
```

> 注：light scheme 与各强调色的完整 token 集需由 Material Theme Builder（同源蓝色 #005bc1）生成最终值，本 spec 给出关键 token，实施时补全全部 primary/secondary/tertiary/error fixed 系列与 surface-container 层级。

### 5.2 Tailwind config 改造

`tailwind.config.js` 的 `colors` 全部改为引用 CSS 变量：

```js
colors: {
  "primary": "var(--color-primary)",
  "on-primary": "var(--color-on-primary)",
  "surface": "var(--color-surface)",
  "on-surface": "var(--color-on-surface)",
  // ... 所有 token 改为 var(--color-<token>)
}
```

- **移除** `darkMode: "class"`（改由 `data-theme` 驱动，不再用 class 切换）
- 所有现有 `bg-primary` / `text-on-surface` / `border-outline-variant` 等类名**无需改动**（底层值机械替换）
- `base.css` 顶部 `@import` 或直接 `@tailwind` 之前引入 `tokens.css`

### 5.3 防闪烁（FOUC）

在 `fitmate-vite.html` 的 `<head>` 加内联脚本，**在 Vue 挂载前**从 localStorage 读 theme mode + accent，立即设置 `document.documentElement.dataset.theme` / `dataset.accent`，避免亮暗闪烁。

```html
<script>
  (function() {
    var mode = localStorage.getItem('fitmate_theme_mode') || 'dark';
    var accent = localStorage.getItem('fitmate_accent_color') || 'blue';
    if (mode === 'auto') {
      mode = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    document.documentElement.dataset.theme = mode;
    document.documentElement.dataset.accent = accent;
  })();
</script>
```

### 5.4 跟随系统

`theme.ts` 在 mode=auto 时监听 `matchMedia('(prefers-color-scheme: dark)')` 的 `change` 事件，按系统偏好动态切换 `data-theme`。

---

## 6. 代码结构与命名规范

遵循现有约定：Vue Options API、`<Feature>Page.vue` 命名、扁平 `components/`、`doctorApi.ts` 集中 API、后端 DDD 分层（`controller/` + `application/impl/` + `infrastructure/{entity,mapper}/` + `dto/`）。

### 6.1 前端新增/修改

```
src/
  components/
    UserMenu.vue                 # 新增 · 右上角头像+下拉菜单
  pages/
    settings/
      SettingsPage.vue           # 新增 · /settings 页面（单列+顶部锚点导航）
      components/
        SettingsSectionNav.vue   # 顶部 sticky 锚点 tab
        ProfileSection.vue       # #profile 区块（昵称/手机可编辑，其余只读）
        AppearanceSection.vue    # #appearance 区块（主题模式+强调色）
        AboutSection.vue         # #about 区块
  services/
    theme.ts                     # 新增 · 主题状态管理（get/set mode+accent、应用DOM、localStorage、监听系统偏好、防抖同步后端）
    doctorApi.ts                 # 修改 · 追加 getUserProfile/updateUserProfile/getUserPreferences/saveUserPreferences
  styles/
    tokens.css                   # 新增 · Material token CSS 变量（亮/暗/强调色）
    base.css                     # 修改 · 引入 tokens.css
  types/
    settings.ts                  # 新增 · ThemeMode / AccentColor / UserSettings / UserProfile 类型
  router/
    index.ts                     # 修改 · 新增 /settings 路由
  layouts/
    AppLayout.vue                # 修改 · 接 UserMenu 的 logout 事件
  components/
    TopBar.vue                   # 修改 · 用 UserMenu 替换头像 div + 移除 Logout 按钮
    SideNav.vue                  # 修改 · 底部 Settings 的 to 从 /dashboard 改 /settings
  main.ts                        # 修改 · 挂载前调 theme.initTheme()
  fitmate-vite.html              # 修改 · head 加防闪烁内联脚本
```

### 6.2 命名规范

- **组件**：PascalCase + 语义后缀（`UserMenu`、`*Section`、`*Page`、`*Nav`）
- **服务/工具**：camelCase `.ts`（`theme.ts`、`doctorApi.ts`）
- **类型**：PascalCase（`ThemeMode`、`UserSettings`、`UserProfile`）
- **CSS 变量**：`--color-<token>`（对齐 Material 3 命名）
- **localStorage key**：`fitmate_theme_mode`、`fitmate_accent_color`（项目前缀防冲突）
- **后端类**：PascalCase + 分层后缀（`UserPreference`、`UserPreferenceMapper`、`UserProfileResponse`）

### 6.3 后端新增/修改（扩展 `auth` 模块）

```
com.itgeo.fitmate.api.auth/
  controller/
    UserController.java                # 修改 · 追加 4 个端点（见 §7.2）
  application/
    UserService.java                   # 修改 · 追加 profile/preference 方法签名
    impl/UserServiceImpl.java          # 修改 · 实现 profile/preference 方法
  infrastructure/
    entity/UserPreference.java         # 新增 · t_user_preference 实体
    mapper/UserPreferenceMapper.java   # 新增 · MyBatis-Plus mapper
  dto/
    UserProfileResponse.java           # 新增 · 含 nickname/phone/email/username/createdAt/lastLoginAt
    UserProfileUpdateRequest.java      # 新增 · 仅 nickname/phone
    UserPreferenceItem.java            # 新增 · { themeMode, accentColor }
```

---

## 7. 后端设计

### 7.1 数据表

`FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql` 追加：

```sql
CREATE TABLE IF NOT EXISTS `t_user_preference` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '偏好主键',
    `user_id` BIGINT NOT NULL COMMENT '所属用户主键',
    `preferences_json` JSON NOT NULL COMMENT '偏好设置 JSON，如 {"themeMode":"dark","accentColor":"blue"}',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_preference_user` (`user_id`),
    CONSTRAINT `fk_user_preference_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户偏好设置表';
```

### 7.2 API 端点（追加到 `UserController`，前缀 `/user`）

| 方法 | 路径 | 说明 | 请求体 | 返回 |
|---|---|---|---|---|
| GET | `/user/profile` | 获取当前用户完整资料 | — | `UserProfileResponse` |
| PUT | `/user/profile` | 更新昵称/手机 | `UserProfileUpdateRequest` | `UserProfileResponse` |
| GET | `/user/preferences` | 获取偏好设置 | — | `UserPreferenceItem`（无记录返回默认值） |
| PUT | `/user/preferences` | 保存偏好设置 | `UserPreferenceItem` | `UserPreferenceItem` |

- 复用 `UserContextHolder.getRequired()` 取当前用户
- 统一用 `LeeResult` 包装响应
- `PUT /user/profile` 仅接受 `nickname`、`phone` 字段，忽略 `email`/`username`（防篡改）
- `GET /user/preferences` 无记录时返回默认 `{ themeMode: "dark", accentColor: "blue" }`

### 7.3 DTO 定义

```java
// UserProfileResponse
String nickname, phone, email, username;
LocalDateTime createdAt, lastLoginAt;

// UserProfileUpdateRequest
String nickname;  // 可空，非空时校验长度 1-100
String phone;     // 可空，非空时校验手机号格式

// UserPreferenceItem
String themeMode;    // light / dark / auto
String accentColor;  // blue / green / orange / purple
```

---

## 8. 数据流

### 8.1 主题/强调色变更流

1. 用户在 `AppearanceSection` 改选项 → `theme.setMode(mode)` / `theme.setAccent(accent)`
2. 即时写 `<html>` 的 `data-theme` / `data-accent` + localStorage → 视觉立即生效（零等待）
3. 防抖 800ms 后调 `saveUserPreferences()` 同步后端（失败 toast 提示，不阻塞 UI）

### 8.2 登录后加载流

1. `main.ts` 挂载前 `theme.initTheme()`：先读 localStorage 应用（配合防闪烁脚本）
2. `AppLayout` 挂载后调 `getUserPreferences()` → 若后端有值且与本地不同，**后端覆盖本地**（跨设备同步）→ 重写 localStorage + data 属性
3. 设置页打开时调 `getUserProfile()` 拉取完整资料（含 createdAt/lastLoginAt）

### 8.3 资料编辑流

1. `ProfileSection` 行内编辑昵称/手机 → 失焦或点保存 → `updateUserProfile()`
2. 成功后更新本地 cookie 中的 `userInfo`（nickname）+ 刷新页面显示
3. `TopBar`/`UserMenu` 的头像昵称随之更新

---

## 9. 分阶段计划

| 阶段 | 内容 | 产出 |
|---|---|---|
| **P1 主题基础设施** | `tokens.css` 迁移 + `tailwind.config.js` 改 CSS 变量 + `theme.ts` + 防闪烁脚本 + `types/settings.ts` | 亮/暗/强调色可切换，现有页面视觉不破 |
| **P2 后端偏好与资料接口** | `t_user_preference` 表 DDL + `UserPreference` entity/mapper + `UserController` 4 个端点 + DTO + `UserService` 实现 | 接口可独立测通 |
| **P3 设置页** | `SettingsPage` + `SettingsSectionNav` + 3 个 `*Section` + `/settings` 路由 + SideNav 修复指向 | /settings 可用，资料可编辑，偏好可同步 |
| **P4 用户下拉菜单** | `UserMenu` 组件 + 接入 `TopBar` + 移除原 Logout 按钮 + `AppLayout` 接 logout | 右上角头像可交互 |
| **P5 联调与回归** | 登录态加载、跨设备同步、亮暗闪烁、移动端 hover/click、路由锚点定位 | 全流程验收 |

### 9.1 执行注意事项

- **上下文控制**：各阶段实施时使用 subagent 分工（如 P2 后端、P3 前端页面可并行），避免主上下文过载
- **P1 优先单独验证**：主题 token 迁移是机械替换但影响全局，需先确认现有页面视觉无破损再推进
- **P3/P4 依赖 P1/P2**：设置页外观区块依赖 theme.ts，资料区块依赖后端接口

---

## 10. 风险与注意事项

1. **Light token 完整性**：Material 3 light scheme 需覆盖全部 token（primary/secondary/tertiary/error 的 fixed 系列、surface-container 层级、inverse 系列）。实施时用 Material Theme Builder（源色 #005bc1）生成完整集，避免某些 token 在亮色下缺失导致视觉异常。

2. **硬编码颜色残留**：`SideNav.vue` 的 `<style scoped>` 里有硬编码 hex（如 `#c1c6d7`、`#adc6ff`、`#1c2028`），`base.css` 的 toast 也有硬编码色。P1 迁移时需把这些改为 CSS 变量引用，否则亮色下这些元素不跟随切换。

3. **FOUC 防闪烁脚本**：内联脚本须在 `<head>` 最早期执行，且只读 localStorage（不能依赖 Vue/外部脚本）。若 localStorage 被禁用，回退到 dark 默认。

4. **后端 JSON 字段处理**：MyBatis-Plus 处理 JSON 字段需配置 `JacksonTypeHandler` 或自定义。`preferences_json` 读写时反序列化为 `UserPreferenceItem`。

5. **跨设备同步冲突**：A 设备改设置 → B 设备登录后拉取会覆盖 B 的本地设置。这是预期行为（后端为准），不做合并。

6. **邮箱脱敏**：手机号在设置页可编辑但展示时脱敏（`138****8888`），编辑态显示原文。邮箱不脱敏（用户需确认登录账号）。

7. **移动端 hover**：移动端无 hover，`UserMenu` 须以 click 为主要触发，hover 仅作桌面端增强。

8. **路由锚点定位**：`/settings#profile` 跳转后需平滑滚动到对应区块。Vue Router 默认不处理 hash 滚动，需在 `SettingsPage` 的 `mounted`/`beforeRouteEnter` 里读 `$route.hash` 并 `scrollIntoView`。

---

## 11. 待确认事项

无。所有关键决策点已在协作过程中确认（见 §2）。

---

## 12. 参考资料

- 项目 SPEC：`FitMate-frontend/SPEC.md`、根目录 `SPEC.md`
- 现有 token 体系：`FitMate-frontend/tailwind.config.js`
- 现有用户模型：`t_user` 表（`fitmate_init.sql`）、`User.java`、`LoginUserInfo.java`
- 现有 auth 模块分层：`UserController.java`、`UserServiceImpl.java`、`UserContextHolder.java`
- Material Theme Builder：https://m3.material.io/theme-builder
