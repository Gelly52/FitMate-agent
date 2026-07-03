# 用户下拉菜单与设置页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 FitMate 添加右上角用户下拉菜单（导航到个人信息/设置/退出登录）与统一设置页 `/settings`（含个人信息编辑、主题模式与强调色切换、关于），设置项前端 localStorage 即时生效 + 后端异步同步。

**Architecture:** 主题系统采用全量 CSS 变量重构（方案 B）——把 `tailwind.config.js` 硬编码 Material 3 token 迁移到 `tokens.css` 的 CSS 变量，用 `<html>` 的 `data-theme`（light/dark）与 `data-accent`（blue/green/orange/purple）属性切换。后端扩展 `auth` 模块，新增 `t_user_preference` 表（JSON 字段）与 4 个 user 端点。前端用 Options API，新增 `UserMenu` 组件 + `SettingsPage` 页面。

**Tech Stack:** Vue 3 (Options API) + TypeScript + Tailwind CSS + Vue Router 4 / Spring Boot + MyBatis-Plus + Hutool + Lombok

**Spec:** `docs/superpowers/specs/2026-07-02-user-menu-settings-design.md`

**Testing note:** 前端无测试框架，采用手动验证步骤；后端用 Maven 构建 + 手动接口验证。

---

## 文件结构总览

### 前端新增
| 文件 | 职责 |
|---|---|
| `src/styles/tokens.css` | Material 3 token CSS 变量（亮/暗 + 4 强调色） |
| `src/services/theme.ts` | 主题状态管理（get/set mode+accent、应用 DOM、localStorage、系统偏好监听、防抖同步） |
| `src/types/settings.ts` | ThemeMode / AccentColor / UserSettings / UserProfile 类型 |
| `src/components/UserMenu.vue` | 右上角头像 + 下拉菜单 |
| `src/pages/settings/SettingsPage.vue` | /settings 页面容器 |
| `src/pages/settings/components/SettingsSectionNav.vue` | 顶部 sticky 锚点导航 |
| `src/pages/settings/components/ProfileSection.vue` | #profile 区块 |
| `src/pages/settings/components/AppearanceSection.vue` | #appearance 区块 |
| `src/pages/settings/components/AboutSection.vue` | #about 区块 |

### 前端修改
| 文件 | 改动 |
|---|---|
| `tailwind.config.js` | colors 改引用 CSS 变量；移除 darkMode |
| `src/styles/base.css` | 引入 tokens.css；toast 硬编码色改 CSS 变量 |
| `src/components/SideNav.vue` | 底部 Settings to 改 /settings；scoped 硬编码色改 CSS 变量 |
| `src/components/TopBar.vue` | 用 UserMenu 替换头像 div + 移除 Logout 按钮 |
| `src/layouts/AppLayout.vue` | 接 UserMenu logout 事件 |
| `src/router/index.ts` | 新增 /settings 路由 |
| `src/services/doctorApi.ts` | 追加 4 个 API 函数 |
| `src/main.ts` | 挂载前调 theme.initTheme() |
| `fitmate-vite.html` | 移除 class="dark"；head 加防闪烁脚本 |

### 后端新增
| 文件 | 职责 |
|---|---|
| `auth/infrastructure/entity/UserPreference.java` | t_user_preference 实体 |
| `auth/infrastructure/mapper/UserPreferenceMapper.java` | MyBatis-Plus mapper |
| `auth/dto/UserProfileResponse.java` | 用户资料响应 |
| `auth/dto/UserProfileUpdateRequest.java` | 资料更新请求 |
| `auth/dto/UserPreferenceItem.java` | 偏好设置项 |

### 后端修改
| 文件 | 改动 |
|---|---|
| `auth/controller/UserController.java` | 追加 4 个端点 |
| `auth/application/UserService.java` | 追加 4 个方法签名 |
| `auth/application/impl/UserServiceImpl.java` | 实现 4 个方法 |
| `FitMate-mcpServer/.../sql/fitmate_init.sql` | 追加 t_user_preference DDL |

---

## P1：主题基础设施

### Task 1.1：创建 tokens.css（Material 3 CSS 变量）

**Files:**
- Create: `FitMate-frontend/src/styles/tokens.css`

- [ ] **Step 1: 创建 tokens.css 文件，写入完整 CSS 变量**

```css
/* FitMate-frontend/src/styles/tokens.css */
/* Material Design 3 tokens as CSS variables. Light/dark driven by [data-theme], accent by [data-accent]. */

:root,
[data-theme="dark"] {
  --color-surface: #10131b;
  --color-surface-dim: #10131b;
  --color-surface-bright: #363942;
  --color-surface-container-lowest: #0b0e16;
  --color-surface-container-low: #181c23;
  --color-surface-container: #1c2028;
  --color-surface-container-high: #272a32;
  --color-surface-container-highest: #31353d;
  --color-on-surface: #e0e2ed;
  --color-on-surface-variant: #c1c6d7;
  --color-inverse-surface: #e0e2ed;
  --color-inverse-on-surface: #2d3039;
  --color-outline: #8b90a0;
  --color-outline-variant: #414755;
  --color-background: #10131b;
  --color-on-background: #e0e2ed;
  --color-surface-variant: #31353d;
  --color-secondary: #adc6ff;
  --color-on-secondary: #082f65;
  --color-secondary-container: #26467d;
  --color-on-secondary-container: #98b5f3;
  --color-tertiary: #ffb595;
  --color-on-tertiary: #571e00;
  --color-tertiary-container: #ef6719;
  --color-on-tertiary-container: #4c1a00;
  --color-error: #ffb4ab;
  --color-on-error: #690005;
  --color-error-container: #93000a;
  --color-on-error-container: #ffdad6;
  --color-primary-fixed: #d8e2ff;
  --color-primary-fixed-dim: #adc6ff;
  --color-on-primary-fixed: #001a41;
  --color-on-primary-fixed-variant: #004493;
  --color-secondary-fixed: #d8e2ff;
  --color-secondary-fixed-dim: #adc6ff;
  --color-on-secondary-fixed: #001a41;
  --color-on-secondary-fixed-variant: #26467d;
  --color-tertiary-fixed: #ffdbcc;
  --color-tertiary-fixed-dim: #ffb595;
  --color-on-tertiary-fixed: #351000;
  --color-on-tertiary-fixed-variant: #7c2e00;

  /* blue accent (default dark) */
  --color-primary: #adc6ff;
  --color-on-primary: #002e69;
  --color-primary-container: #4b8eff;
  --color-on-primary-container: #00285c;
  --color-surface-tint: #adc6ff;
  --color-inverse-primary: #005bc1;
}

[data-theme="light"] {
  --color-surface: #fdfbff;
  --color-surface-dim: #dad9dd;
  --color-surface-bright: #fdfbff;
  --color-surface-container-lowest: #ffffff;
  --color-surface-container-low: #f4f3f7;
  --color-surface-container: #eeedf1;
  --color-surface-container-high: #e8e7eb;
  --color-surface-container-highest: #e2e2e6;
  --color-on-surface: #1a1c1e;
  --color-on-surface-variant: #43474e;
  --color-inverse-surface: #2f3033;
  --color-inverse-on-surface: #f1f0f4;
  --color-outline: #74777f;
  --color-outline-variant: #c4c6cf;
  --color-background: #fdfbff;
  --color-on-background: #1a1c1e;
  --color-surface-variant: #e0e2ec;
  --color-secondary: #565f71;
  --color-on-secondary: #ffffff;
  --color-secondary-container: #dae3f9;
  --color-on-secondary-container: #131c2b;
  --color-tertiary: #705574;
  --color-on-tertiary: #ffffff;
  --color-tertiary-container: #fad8fc;
  --color-on-tertiary-container: #29132e;
  --color-error: #ba1a1a;
  --color-on-error: #ffffff;
  --color-error-container: #ffdad6;
  --color-on-error-container: #410002;
  --color-primary-fixed: #d8e2ff;
  --color-primary-fixed-dim: #adc6ff;
  --color-on-primary-fixed: #001a41;
  --color-on-primary-fixed-variant: #004493;
  --color-secondary-fixed: #d8e2ff;
  --color-secondary-fixed-dim: #adc6ff;
  --color-on-secondary-fixed: #001a41;
  --color-on-secondary-fixed-variant: #26467d;
  --color-tertiary-fixed: #ffdbcc;
  --color-tertiary-fixed-dim: #ffb595;
  --color-on-tertiary-fixed: #351000;
  --color-on-tertiary-fixed-variant: #7c2e00;

  /* blue accent (default light) */
  --color-primary: #005bc1;
  --color-on-primary: #ffffff;
  --color-primary-container: #d8e2ff;
  --color-on-primary-container: #001a41;
  --color-surface-tint: #005bc1;
  --color-inverse-primary: #adc6ff;
}

/* ===== Accent overrides (primary family) ===== */

[data-theme="dark"][data-accent="green"] {
  --color-primary: #7ee787;
  --color-on-primary: #003915;
  --color-primary-container: #2daa3e;
  --color-on-primary-container: #00210b;
  --color-surface-tint: #7ee787;
  --color-inverse-primary: #006e1c;
}

[data-theme="light"][data-accent="green"] {
  --color-primary: #006e1c;
  --color-on-primary: #ffffff;
  --color-primary-container: #8ff8a1;
  --color-on-primary-container: #002106;
  --color-surface-tint: #006e1c;
  --color-inverse-primary: #7ee787;
}

[data-theme="dark"][data-accent="orange"] {
  --color-primary: #ffb595;
  --color-on-primary: #571e00;
  --color-primary-container: #ef6719;
  --color-on-primary-container: #4c1a00;
  --color-surface-tint: #ffb595;
  --color-inverse-primary: #904a00;
}

[data-theme="light"][data-accent="orange"] {
  --color-primary: #8d4e00;
  --color-on-primary: #ffffff;
  --color-primary-container: #ffdcbf;
  --color-on-primary-container: #2e1500;
  --color-surface-tint: #8d4e00;
  --color-inverse-primary: #ffb595;
}

[data-theme="dark"][data-accent="purple"] {
  --color-primary: #c4a7e7;
  --color-on-primary: #2a0e5a;
  --color-primary-container: #8a5fc4;
  --color-on-primary-container: #1a0040;
  --color-surface-tint: #c4a7e7;
  --color-inverse-primary: #6b3fa0;
}

[data-theme="light"][data-accent="purple"] {
  --color-primary: #6b3fa0;
  --color-on-primary: #ffffff;
  --color-primary-container: #ecdcff;
  --color-on-primary-container: #220054;
  --color-surface-tint: #6b3fa0;
  --color-inverse-primary: #c4a7e7;
}
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/styles/tokens.css
git commit -m "feat(theme): add Material 3 CSS variables for light/dark + accent colors"
```

---

### Task 1.2：改造 tailwind.config.js 引用 CSS 变量

**Files:**
- Modify: `FitMate-frontend/tailwind.config.js`

- [ ] **Step 1: 修改 tailwind.config.js**

把 `darkMode: "class"` 删除，把 `colors` 里所有硬编码 hex 值改为 `var(--color-<token>)`：

```js
/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./fitmate-vite.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        "surface": "var(--color-surface)",
        "surface-dim": "var(--color-surface-dim)",
        "surface-bright": "var(--color-surface-bright)",
        "surface-container-lowest": "var(--color-surface-container-lowest)",
        "surface-container-low": "var(--color-surface-container-low)",
        "surface-container": "var(--color-surface-container)",
        "surface-container-high": "var(--color-surface-container-high)",
        "surface-container-highest": "var(--color-surface-container-highest)",
        "on-surface": "var(--color-on-surface)",
        "on-surface-variant": "var(--color-on-surface-variant)",
        "inverse-surface": "var(--color-inverse-surface)",
        "inverse-on-surface": "var(--color-inverse-on-surface)",
        "outline": "var(--color-outline)",
        "outline-variant": "var(--color-outline-variant)",
        "surface-tint": "var(--color-surface-tint)",
        "primary": "var(--color-primary)",
        "on-primary": "var(--color-on-primary)",
        "primary-container": "var(--color-primary-container)",
        "on-primary-container": "var(--color-on-primary-container)",
        "inverse-primary": "var(--color-inverse-primary)",
        "secondary": "var(--color-secondary)",
        "on-secondary": "var(--color-on-secondary)",
        "secondary-container": "var(--color-secondary-container)",
        "on-secondary-container": "var(--color-on-secondary-container)",
        "tertiary": "var(--color-tertiary)",
        "on-tertiary": "var(--color-on-tertiary)",
        "tertiary-container": "var(--color-tertiary-container)",
        "on-tertiary-container": "var(--color-on-tertiary-container)",
        "error": "var(--color-error)",
        "on-error": "var(--color-on-error)",
        "error-container": "var(--color-error-container)",
        "on-error-container": "var(--color-on-error-container)",
        "primary-fixed": "var(--color-primary-fixed)",
        "primary-fixed-dim": "var(--color-primary-fixed-dim)",
        "on-primary-fixed": "var(--color-on-primary-fixed)",
        "on-primary-fixed-variant": "var(--color-on-primary-fixed-variant)",
        "secondary-fixed": "var(--color-secondary-fixed)",
        "secondary-fixed-dim": "var(--color-secondary-fixed-dim)",
        "on-secondary-fixed": "var(--color-on-secondary-fixed)",
        "on-secondary-fixed-variant": "var(--color-on-secondary-fixed-variant)",
        "tertiary-fixed": "var(--color-tertiary-fixed)",
        "tertiary-fixed-dim": "var(--color-tertiary-fixed-dim)",
        "on-tertiary-fixed": "var(--color-on-tertiary-fixed)",
        "on-tertiary-fixed-variant": "var(--color-on-tertiary-fixed-variant)",
        "background": "var(--color-background)",
        "on-background": "var(--color-on-background)",
        "surface-variant": "var(--color-surface-variant)",
      },
      borderRadius: {
        DEFAULT: "0.125rem",
        lg: "0.25rem",
        xl: "0.5rem",
        full: "0.75rem",
      },
      spacing: {
        xs: "4px",
        sm: "8px",
        md: "16px",
        lg: "32px",
        xl: "64px",
        unit: "4px",
        gutter: "16px",
        margin: "24px",
      },
      fontFamily: {
        inter: ["Inter", "sans-serif"],
      },
      fontSize: {
        "display-lg": ["40px", { lineHeight: "1.1", letterSpacing: "-0.02em", fontWeight: "600" }],
        "headline-md": ["24px", { lineHeight: "1.2", letterSpacing: "-0.01em", fontWeight: "500" }],
        "body-base": ["15px", { lineHeight: "1.5", letterSpacing: "0", fontWeight: "400" }],
        "label-sm": ["11px", { lineHeight: "1.2", letterSpacing: "0.05em", fontWeight: "600" }],
        "label-xs": ["9px", { lineHeight: "1", letterSpacing: "0.08em", fontWeight: "500" }],
      },
    },
  },
  plugins: [],
};
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/tailwind.config.js
git commit -m "feat(theme): switch tailwind colors to CSS variables, remove darkMode:class"
```

---

### Task 1.3：base.css 引入 tokens.css + 修复硬编码色

**Files:**
- Modify: `FitMate-frontend/src/styles/base.css`

- [ ] **Step 1: 在 base.css 顶部引入 tokens.css**

在 `@tailwind base;` 之前加一行：

```css
@import "./tokens.css";
@tailwind base;
@tailwind components;
@tailwind utilities;
```

- [ ] **Step 2: 把 toast 的硬编码色改为 CSS 变量**

替换 `.fa-toast` 块及其子选择器中的硬编码色：

```css
.fa-toast {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 8px;
  border: 1px solid var(--color-outline-variant);
  background: var(--color-surface-container);
  color: var(--color-on-surface);
  font-size: 13px;
  font-family: "Inter", sans-serif;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
  opacity: 0;
  transform: translateY(-8px);
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fa-toast-visible {
  opacity: 1;
  transform: translateY(0);
}

.fa-toast-icon {
  font-size: 18px;
}

.fa-toast-success .fa-toast-icon {
  color: #22c55e;
}

.fa-toast-error .fa-toast-icon {
  color: var(--color-error);
}

.fa-toast-warning .fa-toast-icon {
  color: var(--color-tertiary);
}

.fa-toast-info .fa-toast-icon {
  color: var(--color-primary);
}
```

> 注：success 绿色 `#22c55e` 保留（非 Material token，且各主题下含义一致）。

- [ ] **Step 3: Commit**

```bash
git add FitMate-frontend/src/styles/base.css
git commit -m "feat(theme): import tokens.css and replace hardcoded toast colors"
```

---

### Task 1.4：SideNav.vue 修复 scoped 硬编码色

**Files:**
- Modify: `FitMate-frontend/src/components/SideNav.vue`

- [ ] **Step 1: 把 `<style scoped>` 里所有硬编码 hex 改为 CSS 变量**

替换整个 `<style scoped>` 块中的硬编码颜色：

```css
.side-nav-link {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  padding: 8px 0;
  position: relative;
  cursor: pointer;
  color: var(--color-on-surface-variant);
  border-right: 2px solid transparent;
  transition: color 0.2s ease, border-color 0.2s ease;
}

.side-nav-link-collapsed {
  flex-direction: column;
  justify-content: center;
}

.side-nav-link-expanded {
  flex-direction: row;
  justify-content: flex-start;
  gap: 12px;
  padding: 10px 14px;
}

.side-nav-link:hover {
  color: var(--color-primary);
  border-right-color: var(--color-primary);
}

.side-nav-link-active {
  color: var(--color-primary);
  border-right-color: var(--color-primary);
}

.side-nav-brand-text {
  flex: 1;
  color: var(--color-on-surface);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.side-nav-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: var(--color-on-surface-variant);
  border: 1px solid var(--color-outline-variant);
  border-radius: 999px;
  background: var(--color-surface-container);
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.side-nav-toggle:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
  background: var(--color-surface-container-low);
}

.side-nav-label {
  min-width: 0;
  overflow: hidden;
  color: inherit;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-nav-tooltip {
  font-size: 11px;
  line-height: 1.2;
  letter-spacing: 0.05em;
  font-weight: 600;
  opacity: 0;
  position: absolute;
  left: 100%;
  margin-left: 16px;
  white-space: nowrap;
  background: var(--color-surface-container);
  padding: 4px 8px;
  border-radius: 2px;
  border: 1px solid var(--color-outline-variant);
  transition: opacity 0.2s ease;
  pointer-events: none;
  z-index: 50;
}

.side-nav-link:hover .side-nav-tooltip {
  opacity: 1;
}

.material-symbols-outlined {
  font-size: 24px;
}
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/components/SideNav.vue
git commit -m "feat(theme): replace SideNav hardcoded colors with CSS variables"
```

---

### Task 1.5：创建 types/settings.ts

**Files:**
- Create: `FitMate-frontend/src/types/settings.ts`

- [ ] **Step 1: 创建类型定义文件**

```ts
// FitMate-frontend/src/types/settings.ts

/** 主题模式：亮色 / 暗色 / 跟随系统 */
export type ThemeMode = "light" | "dark" | "auto";

/** 强调色预设 */
export type AccentColor = "blue" | "green" | "orange" | "purple";

/** 用户偏好设置（前后端共享结构） */
export interface UserSettings {
  themeMode: ThemeMode;
  accentColor: AccentColor;
}

/** 用户资料（设置页展示 + 部分编辑） */
export interface UserProfile {
  nickname: string | null;
  phone: string | null;
  email: string | null;
  username: string | null;
  createdAt: string | null;
  lastLoginAt: string | null;
}

/** 用户资料更新请求（仅允许 nickname / phone） */
export interface UserProfileUpdate {
  nickname?: string;
  phone?: string;
}

/** 默认设置 */
export const DEFAULT_USER_SETTINGS: UserSettings = {
  themeMode: "dark",
  accentColor: "blue",
};

/** 可选强调色列表（供 UI 渲染色板） */
export const ACCENT_COLOR_OPTIONS: { value: AccentColor; label: string }[] = [
  { value: "blue", label: "蓝" },
  { value: "green", label: "绿" },
  { value: "orange", label: "橙" },
  { value: "purple", label: "紫" },
];

/** 主题模式选项（供 UI 渲染按钮组） */
export const THEME_MODE_OPTIONS: { value: ThemeMode; label: string; icon: string }[] = [
  { value: "light", label: "亮色", icon: "light_mode" },
  { value: "dark", label: "暗色", icon: "dark_mode" },
  { value: "auto", label: "跟随系统", icon: "desktop_windows" },
];
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/types/settings.ts
git commit -m "feat(settings): add ThemeMode/AccentColor/UserSettings/UserProfile types"
```

---

### Task 1.6：创建 services/theme.ts

**Files:**
- Create: `FitMate-frontend/src/services/theme.ts`

- [ ] **Step 1: 创建主题服务**

```ts
// FitMate-frontend/src/services/theme.ts
import type { ThemeMode, AccentColor, UserSettings } from "../types/settings";
import { DEFAULT_USER_SETTINGS } from "../types/settings";

const STORAGE_KEY_MODE = "fitmate_theme_mode";
const STORAGE_KEY_ACCENT = "fitmate_accent_color";

let mediaQuery: MediaQueryList | null = null;
let mediaListener: ((e: MediaQueryListEvent) => void) | null = null;
let syncCallback: ((settings: UserSettings) => void) | null = null;
let syncTimer: ReturnType<typeof setTimeout> | null = null;

/** 读取 localStorage 的 theme mode，缺省 dark */
export function getStoredMode(): ThemeMode {
  const v = localStorage.getItem(STORAGE_KEY_MODE);
  if (v === "light" || v === "dark" || v === "auto") return v;
  return DEFAULT_USER_SETTINGS.themeMode;
}

/** 读取 localStorage 的 accent color，缺省 blue */
export function getStoredAccent(): AccentColor {
  const v = localStorage.getItem(STORAGE_KEY_ACCENT);
  if (v === "blue" || v === "green" || v === "orange" || v === "purple") return v;
  return DEFAULT_USER_SETTINGS.accentColor;
}

/** 把 mode 解析为实际 light/dark（auto 跟随系统） */
function resolveMode(mode: ThemeMode): "light" | "dark" {
  if (mode === "auto") {
    return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }
  return mode;
}

/** 把 mode + accent 应用到 <html> data 属性 */
function applyToDom(mode: ThemeMode, accent: AccentColor): void {
  const resolved = resolveMode(mode);
  document.documentElement.dataset.theme = resolved;
  document.documentElement.dataset.accent = accent;
}

/** 监听系统主题变化（仅 mode=auto 时生效） */
function bindMediaListener(mode: ThemeMode): void {
  if (mediaQuery && mediaListener) {
    mediaQuery.removeEventListener("change", mediaListener);
  }
  if (mode !== "auto") {
    mediaQuery = null;
    mediaListener = null;
    return;
  }
  mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
  mediaListener = (e: MediaQueryListEvent) => {
    document.documentElement.dataset.theme = e.matches ? "dark" : "light";
  };
  mediaQuery.addEventListener("change", mediaListener);
}

/** 初始化主题（在 Vue 挂载前调用，配合 fitmate-vite.html 防闪烁脚本） */
export function initTheme(): void {
  const mode = getStoredMode();
  const accent = getStoredAccent();
  applyToDom(mode, accent);
  bindMediaListener(mode);
}

/** 设置主题模式，即时生效 + 持久化 + 防抖同步 */
export function setMode(mode: ThemeMode): void {
  localStorage.setItem(STORAGE_KEY_MODE, mode);
  applyToDom(mode, getStoredAccent());
  bindMediaListener(mode);
  scheduleSync();
}

/** 设置强调色，即时生效 + 持久化 + 防抖同步 */
export function setAccent(accent: AccentColor): void {
  localStorage.setItem(STORAGE_KEY_ACCENT, accent);
  applyToDom(getStoredMode(), accent);
  scheduleSync();
}

/** 从后端加载设置覆盖本地（登录后调用） */
export function applyRemoteSettings(settings: UserSettings): void {
  localStorage.setItem(STORAGE_KEY_MODE, settings.themeMode);
  localStorage.setItem(STORAGE_KEY_ACCENT, settings.accentColor);
  applyToDom(settings.themeMode, settings.accentColor);
  bindMediaListener(settings.themeMode);
}

/** 注册同步回调（设置页或 AppLayout 注入后端保存函数） */
export function onSettingsChange(cb: (settings: UserSettings) => void): void {
  syncCallback = cb;
}

/** 防抖同步：800ms 内多次变更只触发一次后端保存 */
function scheduleSync(): void {
  if (syncTimer) clearTimeout(syncTimer);
  syncTimer = setTimeout(() => {
    if (syncCallback) {
      syncCallback({
        themeMode: getStoredMode(),
        accentColor: getStoredAccent(),
      });
    }
  }, 800);
}

export default {
  initTheme,
  getStoredMode,
  getStoredAccent,
  setMode,
  setAccent,
  applyRemoteSettings,
  onSettingsChange,
};
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/services/theme.ts
git commit -m "feat(theme): add theme service with localStorage + DOM sync + debounce"
```

---

### Task 1.7：fitmate-vite.html 加防闪烁脚本 + 移除 class="dark"

**Files:**
- Modify: `FitMate-frontend/fitmate-vite.html`

- [ ] **Step 1: 修改 html 标签与 head**

把 `<html lang="zh-CN" class="dark">` 改为 `<html lang="zh-CN">`，并在 `<head>` 最前面（meta charset 之后）加内联脚本：

```html
<!doctype html>
<html lang="zh-CN">
    <head>
        <meta charset="UTF-8" />
        <script>
            (function () {
                var mode = localStorage.getItem("fitmate_theme_mode") || "dark";
                var accent = localStorage.getItem("fitmate_accent_color") || "blue";
                if (mode === "auto") {
                    mode = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
                }
                document.documentElement.dataset.theme = mode;
                document.documentElement.dataset.accent = accent;
            })();
        </script>
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <title>FitMate</title>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet" />
        <link href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:wght,FILL@100..700,0..1&display=swap" rel="stylesheet" />
    </head>
    <body>
        <div id="app"></div>
        <script type="module" src="/src/main.ts"></script>
    </body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/fitmate-vite.html
git commit -m "feat(theme): add FOUC prevention script, remove class=dark from html"
```

---

### Task 1.8：main.ts 调用 theme.initTheme()

**Files:**
- Modify: `FitMate-frontend/src/main.ts`

- [ ] **Step 1: 在 main.ts 引入并调用 initTheme**

```ts
import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import toast from "./services/toast";
import { initTheme } from "./services/theme";
import "./styles/base.css";
import "./config/runtime";
import "./services/http";
import "./services/doctorApi";
import "./services/sseService";

initTheme();

const app = createApp(App);

app.config.globalProperties.$message = toast;
app.use(router);

app.mount("#app");
```

- [ ] **Step 2: 启动前端 dev server 验证现有页面视觉无破损**

Run: `cd FitMate-frontend && npm run dev`

打开浏览器访问应用，逐页检查（Chat / Training / Body Metrics / Knowledge / Dashboard / Login）：
- 暗色下视觉与改动前一致
- 在浏览器 DevTools 把 `<html data-theme="dark">` 改为 `data-theme="light"` → 整个应用应切换为亮色
- 把 `data-accent="blue"` 改为 `data-accent="green"` → primary 色应变为绿色
- 无未定义 CSS 变量警告

- [ ] **Step 3: Commit**

```bash
git add FitMate-frontend/src/main.ts
git commit -m "feat(theme): call initTheme before app mount"
```

---

## P2：后端偏好与资料接口

### Task 2.1：t_user_preference 表 DDL

**Files:**
- Modify: `FitMate-backend/FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql`

- [ ] **Step 1: 在 fitmate_init.sql 末尾追加 DDL**

```sql

-- ============================================================
-- 用户偏好设置
-- ============================================================

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

- [ ] **Step 2: 在数据库执行该 DDL（或等待项目初始化脚本执行）**

Run（按实际数据库连接参数替换）:
```bash
mysql -u <user> -p <database> < FitMate-backend/FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql
```
Expected: Query OK

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql
git commit -m "feat(user): add t_user_preference table DDL"
```

---

### Task 2.2：UserPreference 实体 + Mapper

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/infrastructure/entity/UserPreference.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/infrastructure/mapper/UserPreferenceMapper.java`

- [ ] **Step 1: 创建 UserPreference 实体**

```java
package com.itgeo.fitmate.api.auth.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 用户偏好设置实体。
 */
@Data
@ToString
@TableName(value = "t_user_preference", autoResultMap = true)
public class UserPreference {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    /** 偏好 JSON，原始字符串读写，由 service 层序列化/反序列化。 */
    private String preferencesJson;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 UserPreferenceMapper**

```java
package com.itgeo.fitmate.api.auth.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.auth.infrastructure.entity.UserPreference;

/**
 * 用户偏好设置 Mapper。
 */
public interface UserPreferenceMapper extends BaseMapper<UserPreference> {
}
```

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/infrastructure/entity/UserPreference.java
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/infrastructure/mapper/UserPreferenceMapper.java
git commit -m "feat(user): add UserPreference entity and mapper"
```

---

### Task 2.3：后端 DTO

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/dto/UserProfileResponse.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/dto/UserProfileUpdateRequest.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/dto/UserPreferenceItem.java`

- [ ] **Step 1: 创建 UserProfileResponse**

```java
package com.itgeo.fitmate.api.auth.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户资料响应。
 */
@Data
public class UserProfileResponse {
    private String nickname;
    private String phone;
    private String email;
    private String username;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}
```

- [ ] **Step 2: 创建 UserProfileUpdateRequest**

```java
package com.itgeo.fitmate.api.auth.dto;

import lombok.Data;

/**
 * 用户资料更新请求。仅允许 nickname / phone，email/username 不可改。
 */
@Data
public class UserProfileUpdateRequest {
    /** 昵称，非空时长度 1-100。 */
    private String nickname;
    /** 手机号，非空时校验格式。 */
    private String phone;
}
```

- [ ] **Step 3: 创建 UserPreferenceItem**

```java
package com.itgeo.fitmate.api.auth.dto;

import lombok.Data;

/**
 * 用户偏好设置项，对应 preferences_json 的结构。
 */
@Data
public class UserPreferenceItem {
    /** 主题模式：light / dark / auto */
    private String themeMode;
    /** 强调色：blue / green / orange / purple */
    private String accentColor;
}
```

- [ ] **Step 4: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/dto/UserProfileResponse.java
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/dto/UserProfileUpdateRequest.java
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/dto/UserPreferenceItem.java
git commit -m "feat(user): add profile/preference DTOs"
```

---

### Task 2.4：UserService 接口追加方法

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/application/UserService.java`

- [ ] **Step 1: 在 UserService 接口末尾追加 4 个方法签名**

在 `void logout(String token);` 之后追加：

```java
    /**
     * 获取当前登录用户的完整资料。
     *
     * @param userId 用户主键
     * @return 用户资料响应
     */
    UserProfileResponse getProfile(Long userId);

    /**
     * 更新当前登录用户的昵称/手机号。
     *
     * @param userId  用户主键
     * @param request 更新请求
     * @return 更新后的用户资料
     */
    UserProfileResponse updateProfile(Long userId, UserProfileUpdateRequest request);

    /**
     * 获取用户偏好设置，无记录返回默认值。
     *
     * @param userId 用户主键
     * @return 偏好设置项
     */
    UserPreferenceItem getPreferences(Long userId);

    /**
     * 保存用户偏好设置（upsert）。
     *
     * @param userId 用户主键
     * @param item   偏好设置项
     * @return 保存后的偏好设置项
     */
    UserPreferenceItem savePreferences(Long userId, UserPreferenceItem item);
```

同时在文件顶部补 import：

```java
import com.itgeo.fitmate.api.auth.dto.UserPreferenceItem;
import com.itgeo.fitmate.api.auth.dto.UserProfileResponse;
import com.itgeo.fitmate.api.auth.dto.UserProfileUpdateRequest;
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/application/UserService.java
git commit -m "feat(user): add profile/preference methods to UserService interface"
```

---

### Task 2.5：UserServiceImpl 实现 4 个方法

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/application/impl/UserServiceImpl.java`

- [ ] **Step 1: 注入 UserPreferenceMapper 与 ObjectMapper**

在 `UserServiceImpl` 类的字段区追加：

```java
    @Resource
    private UserPreferenceMapper userPreferenceMapper;

    @Resource
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
```

在文件顶部补 import：

```java
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itgeo.fitmate.api.auth.dto.UserPreferenceItem;
import com.itgeo.fitmate.api.auth.dto.UserProfileResponse;
import com.itgeo.fitmate.api.auth.dto.UserProfileUpdateRequest;
import com.itgeo.fitmate.api.auth.infrastructure.entity.UserPreference;
import com.itgeo.fitmate.api.auth.infrastructure.mapper.UserPreferenceMapper;
```

- [ ] **Step 2: 实现 getProfile**

```java
    @Override
    public UserProfileResponse getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        UserProfileResponse resp = new UserProfileResponse();
        resp.setNickname(user.getNickname());
        resp.setPhone(user.getPhone());
        resp.setEmail(user.getEmail());
        resp.setUsername(user.getUsername());
        resp.setCreatedAt(user.getCreatedAt());
        resp.setLastLoginAt(user.getLastLoginAt());
        return resp;
    }
```

- [ ] **Step 3: 实现 updateProfile**

```java
    @Override
    public UserProfileResponse updateProfile(Long userId, UserProfileUpdateRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, userId);
        boolean changed = false;
        if (request != null && request.getNickname() != null) {
            String nickname = request.getNickname().trim();
            if (nickname.isEmpty() || nickname.length() > 100) {
                throw new IllegalArgumentException("昵称长度需为 1-100");
            }
            updateWrapper.set(User::getNickname, nickname);
            changed = true;
        }
        if (request != null && request.getPhone() != null) {
            String phone = request.getPhone().trim();
            if (!phone.isEmpty() && !phone.matches("^1[3-9]\\d{9}$")) {
                throw new IllegalArgumentException("手机号格式不正确");
            }
            updateWrapper.set(User::getPhone, phone);
            changed = true;
        }
        if (changed) {
            userMapper.update(null, updateWrapper);
        }
        return getProfile(userId);
    }
```

- [ ] **Step 4: 实现 getPreferences**

```java
    @Override
    public UserPreferenceItem getPreferences(Long userId) {
        LambdaQueryWrapper<UserPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPreference::getUserId, userId);
        UserPreference pref = userPreferenceMapper.selectOne(wrapper);
        UserPreferenceItem item = new UserPreferenceItem();
        item.setThemeMode("dark");
        item.setAccentColor("blue");
        if (pref != null && pref.getPreferencesJson() != null) {
            try {
                UserPreferenceItem parsed = objectMapper.readValue(pref.getPreferencesJson(), UserPreferenceItem.class);
                if (parsed != null) {
                    if (parsed.getThemeMode() != null) item.setThemeMode(parsed.getThemeMode());
                    if (parsed.getAccentColor() != null) item.setAccentColor(parsed.getAccentColor());
                }
            } catch (Exception e) {
                log.warn("解析用户偏好 JSON 失败，回退默认值: userId={}, json={}", userId, pref.getPreferencesJson(), e);
            }
        }
        return item;
    }
```

- [ ] **Step 5: 实现 savePreferences**

```java
    @Override
    public UserPreferenceItem savePreferences(Long userId, UserPreferenceItem item) {
        if (item == null) {
            throw new IllegalArgumentException("偏好设置不能为空");
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(item);
        } catch (Exception e) {
            throw new IllegalArgumentException("偏好设置序列化失败");
        }
        LambdaQueryWrapper<UserPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPreference::getUserId, userId);
        UserPreference existing = userPreferenceMapper.selectOne(wrapper);
        if (existing == null) {
            UserPreference pref = new UserPreference();
            pref.setUserId(userId);
            pref.setPreferencesJson(json);
            userPreferenceMapper.insert(pref);
        } else {
            existing.setPreferencesJson(json);
            userPreferenceMapper.updateById(existing);
        }
        return item;
    }
```

- [ ] **Step 6: 构建验证编译通过**

Run: `cd FitMate-backend && mvn -pl FitMate-api -am compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/application/impl/UserServiceImpl.java
git commit -m "feat(user): implement profile/preference methods in UserServiceImpl"
```

---

### Task 2.6：UserController 追加 4 个端点

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/controller/UserController.java`

- [ ] **Step 1: 在 UserController 末尾（resolveClientIp 方法之前）追加 4 个端点**

```java
    /**
     * 获取当前登录用户的完整资料。
     *
     * @return 通用响应结果
     */
    @GetMapping("/profile")
    public LeeResult getProfile() {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(userService.getProfile(userId));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("获取用户资料失败", e);
            return LeeResult.errorException("获取用户资料失败");
        }
    }

    /**
     * 更新当前登录用户的昵称/手机号。
     *
     * @param request 更新请求体
     * @return 通用响应结果
     */
    @PutMapping("/profile")
    public LeeResult updateProfile(@RequestBody UserProfileUpdateRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(userService.updateProfile(userId, request));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("更新用户资料失败", e);
            return LeeResult.errorException("更新用户资料失败");
        }
    }

    /**
     * 获取当前登录用户的偏好设置。
     *
     * @return 通用响应结果
     */
    @GetMapping("/preferences")
    public LeeResult getPreferences() {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(userService.getPreferences(userId));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("获取用户偏好失败", e);
            return LeeResult.errorException("获取用户偏好失败");
        }
    }

    /**
     * 保存当前登录用户的偏好设置。
     *
     * @param request 偏好设置请求体
     * @return 通用响应结果
     */
    @PutMapping("/preferences")
    public LeeResult savePreferences(@RequestBody UserPreferenceItem request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(userService.savePreferences(userId, request));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("保存用户偏好失败", e);
            return LeeResult.errorException("保存用户偏好失败");
        }
    }
```

在文件顶部补 import：

```java
import com.itgeo.fitmate.api.auth.dto.UserPreferenceItem;
import com.itgeo.fitmate.api.auth.dto.UserProfileUpdateRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
```

- [ ] **Step 2: 构建并启动后端，验证接口**

Run: `cd FitMate-backend && mvn -pl FitMate-api -am spring-boot:run -q`

用已登录用户的 token 调用（替换 `<token>` 与 `<userId>`）：

```bash
curl -H "headerUserToken: <token>" -H "headerUserId: <userId>" http://localhost:8080/user/profile
```
Expected: `{"status":200,"data":{"nickname":"...","email":"...",...}}`

```bash
curl -X PUT -H "Content-Type: application/json" -H "headerUserToken: <token>" -H "headerUserId: <userId>" -d '{"themeMode":"dark","accentColor":"blue"}' http://localhost:8080/user/preferences
```
Expected: `{"status":200,"data":{"themeMode":"dark","accentColor":"blue"}}`

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/controller/UserController.java
git commit -m "feat(user): add profile/preference endpoints to UserController"
```

---

## P3：设置页

### Task 3.1：doctorApi.ts 追加 API 函数

**Files:**
- Modify: `FitMate-frontend/src/services/doctorApi.ts`

- [ ] **Step 1: 在 doctorApi.ts 追加 4 个函数**

在 `getUploadedDocs` 函数之后、`const doctorApi = {` 之前追加：

```ts
export function getUserProfile() {
  return instance({
    url: "/user/profile",
    method: "get",
  });
}

export function updateUserProfile(bo) {
  return instance({
    url: "/user/profile",
    method: "put",
    data: bo,
  });
}

export function getUserPreferences() {
  return instance({
    url: "/user/preferences",
    method: "get",
  });
}

export function saveUserPreferences(bo) {
  return instance({
    url: "/user/preferences",
    method: "put",
    data: bo,
  });
}
```

在 `const doctorApi = { ... }` 对象里追加这 4 个函数引用：

```ts
const doctorApi = {
  doChat,
  ragSearch,
  internetSearch,
  sendUserCode,
  userLogin,
  userLogout,
  createSseTicket,
  getRecords,
  uploadRagDoc,
  agentExecute,
  getAgentRuns,
  getAgentRunDetail,
  ragConfig,
  benchmarkEvaluate,
  logTraining,
  logBodyMetrics,
  getRecentTraining,
  getRecentMetrics,
  getUploadedDocs,
  getUserProfile,
  updateUserProfile,
  getUserPreferences,
  saveUserPreferences,
};
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/services/doctorApi.ts
git commit -m "feat(settings): add profile/preference API functions"
```

---

### Task 3.2：新增 /settings 路由 + 修复 SideNav 指向

**Files:**
- Modify: `FitMate-frontend/src/router/index.ts`
- Modify: `FitMate-frontend/src/components/SideNav.vue`

- [ ] **Step 1: 在 router/index.ts 的 children 数组追加 /settings 路由**

在 `dashboard` 路由之后追加：

```ts
      {
        path: "settings",
        name: "settings",
        component: () => import("../pages/settings/SettingsPage.vue"),
        meta: { title: "Settings" },
      },
```

- [ ] **Step 2: 修改 SideNav.vue 底部 Settings 链接**

把底部的 `to="/dashboard"` 改为 `to="/settings"`，把 `isActive('/dashboard')` 改为 `isActive('/settings')`：

```html
    <!-- Settings (bottom) -->
    <div class="mt-auto w-full flex" :class="expanded ? 'px-3' : 'justify-center'">
      <router-link
        to="/settings"
        class="side-nav-link group"
        :class="[
          expanded ? 'side-nav-link-expanded' : 'side-nav-link-collapsed',
          isActive('/settings') ? 'side-nav-link-active' : '',
        ]"
        :title="expanded ? '' : 'Settings'"
      >
        <span class="material-symbols-outlined transition-colors">settings</span>
        <span v-if="expanded" class="side-nav-label">Settings</span>
        <span v-else class="side-nav-tooltip">Settings</span>
      </router-link>
    </div>
```

- [ ] **Step 3: Commit**

```bash
git add FitMate-frontend/src/router/index.ts FitMate-frontend/src/components/SideNav.vue
git commit -m "feat(settings): add /settings route, fix SideNav Settings link"
```

---

### Task 3.3：创建 SettingsPage.vue

**Files:**
- Create: `FitMate-frontend/src/pages/settings/SettingsPage.vue`
- Create: `FitMate-frontend/src/pages/settings/components/SettingsSectionNav.vue`

- [ ] **Step 1: 创建 SettingsSectionNav.vue（顶部 sticky 锚点导航）**

```vue
<!-- FitMate-frontend/src/pages/settings/components/SettingsSectionNav.vue -->
<template>
  <nav class="settings-section-nav">
    <a
      v-for="section in sections"
      :key="section.id"
      :href="'#' + section.id"
      class="settings-section-tab"
      :class="active === section.id ? 'settings-section-tab-active' : ''"
      @click.prevent="$emit('navigate', section.id)"
    >
      <span class="material-symbols-outlined" style="font-size:16px;">{{ section.icon }}</span>
      <span>{{ section.label }}</span>
    </a>
  </nav>
</template>

<script lang="ts">
export default {
  name: "SettingsSectionNav",
  props: {
    active: {
      type: String,
      default: "profile",
    },
  },
  emits: ["navigate"],
  data() {
    return {
      sections: [
        { id: "profile", label: "个人信息", icon: "person" },
        { id: "appearance", label: "外观", icon: "palette" },
        { id: "about", label: "关于", icon: "info" },
      ],
    };
  },
};
</script>

<style scoped>
.settings-section-nav {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--color-outline-variant);
  padding-bottom: 8px;
  margin-bottom: 24px;
  position: sticky;
  top: 0;
  background: var(--color-background);
  z-index: 10;
}

.settings-section-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 4px;
  color: var(--color-on-surface-variant);
  cursor: pointer;
  transition: color 0.2s ease, background 0.2s ease;
}

.settings-section-tab:hover {
  color: var(--color-on-surface);
  background: var(--color-surface-container);
}

.settings-section-tab-active {
  color: var(--color-primary);
  background: var(--color-surface-container);
}
</style>
```

- [ ] **Step 2: 创建 SettingsPage.vue**

```vue
<!-- FitMate-frontend/src/pages/settings/SettingsPage.vue -->
<template>
  <div class="settings-page px-lg py-lg overflow-y-auto">
    <header class="mb-xl">
      <h1 class="font-headline-md text-headline-md text-on-surface tracking-tight">设置</h1>
      <p class="font-inter text-label-xs text-on-surface-variant uppercase tracking-widest opacity-70 mt-xs">
        Manage your account &amp; appearance
      </p>
    </header>

    <SettingsSectionNav :active="activeSection" @navigate="scrollToSection" />

    <div class="flex flex-col gap-lg max-w-3xl">
      <section id="profile" ref="profileRef" class="settings-section">
        <ProfileSection :profile="profile" @updated="onProfileUpdated" />
      </section>
      <section id="appearance" ref="appearanceRef" class="settings-section">
        <AppearanceSection />
      </section>
      <section id="about" ref="aboutRef" class="settings-section">
        <AboutSection />
      </section>
    </div>
  </div>
</template>

<script lang="ts">
import doctorApi from "../../services/doctorApi";
import SettingsSectionNav from "./components/SettingsSectionNav.vue";
import ProfileSection from "./components/ProfileSection.vue";
import AppearanceSection from "./components/AppearanceSection.vue";
import AboutSection from "./components/AboutSection.vue";
import type { UserProfile } from "../../types/settings";

export default {
  name: "SettingsPage",
  components: { SettingsSectionNav, ProfileSection, AppearanceSection, AboutSection },
  data() {
    return {
      activeSection: "profile",
      profile: null as UserProfile | null,
      profileRef: null as HTMLElement | null,
      appearanceRef: null as HTMLElement | null,
      aboutRef: null as HTMLElement | null,
    };
  },
  mounted() {
    this.loadProfile();
    this.applyHash();
  },
  methods: {
    async loadProfile() {
      try {
        const res = await doctorApi.getUserProfile();
        if (res && res.status === 200) {
          this.profile = res.data as UserProfile;
        }
      } catch (e) {
        console.error("加载用户资料失败", e);
      }
    },
    applyHash() {
      const hash = (this.$route && this.$route.hash || "").replace("#", "");
      if (hash) {
        this.$nextTick(() => this.scrollToSection(hash));
      }
    },
    scrollToSection(id: string) {
      this.activeSection = id;
      const el = document.getElementById(id);
      if (el) {
        el.scrollIntoView({ behavior: "smooth", block: "start" });
      }
    },
    onProfileUpdated(updated: UserProfile) {
      this.profile = updated;
    },
  },
};
</script>

<style scoped>
.settings-page {
  height: 100%;
}

.settings-section {
  scroll-margin-top: 60px;
}
</style>
```

> 注：`ProfileSection` / `AppearanceSection` / `AboutSection` 在后续 Task 创建。本 Task 先创建占位会导致编译失败，可在 Step 3 后立即继续 Task 3.4-3.6。

- [ ] **Step 3: Commit（与 3.4-3.6 一起提交，或先 commit 本文件占位）**

```bash
git add FitMate-frontend/src/pages/settings/SettingsPage.vue
git add FitMate-frontend/src/pages/settings/components/SettingsSectionNav.vue
git commit -m "feat(settings): add SettingsPage shell and section nav"
```

---

### Task 3.4：创建 ProfileSection.vue

**Files:**
- Create: `FitMate-frontend/src/pages/settings/components/ProfileSection.vue`

- [ ] **Step 1: 创建 ProfileSection.vue（昵称/手机可编辑，其余只读）**

```vue
<!-- FitMate-frontend/src/pages/settings/components/ProfileSection.vue -->
<template>
  <div>
    <h2 class="text-primary font-inter text-body-base font-semibold mb-md"># 个人信息</h2>
    <div class="settings-card">
      <!-- 头像 + 昵称 + 邮箱 -->
      <div class="flex items-center gap-md mb-lg">
        <div class="settings-avatar">
          <span v-if="avatarLetter">{{ avatarLetter }}</span>
          <span v-else class="material-symbols-outlined" style="font-size:28px;">person</span>
        </div>
        <div class="min-w-0">
          <div class="text-on-surface font-inter text-body-base font-semibold truncate">
            {{ displayNickname }}
          </div>
          <div class="text-on-surface-variant font-inter text-label-xs truncate">
            {{ profile && profile.email ? profile.email : "—" }}
          </div>
        </div>
      </div>

      <!-- 字段网格 -->
      <div class="settings-field-grid">
        <!-- 昵称（可编辑） -->
        <div class="settings-field">
          <div class="settings-field-label">昵称</div>
          <div v-if="!editing.nickname" class="settings-field-value">
            <span>{{ displayNickname }}</span>
            <button class="settings-edit-btn" @click="startEdit('nickname')" title="编辑">
              <span class="material-symbols-outlined" style="font-size:14px;">edit</span>
            </button>
          </div>
          <div v-else class="settings-field-edit">
            <input v-model="forms.nickname" class="settings-input" maxlength="100" />
            <button class="settings-save-btn" @click="saveField('nickname')" :disabled="saving">保存</button>
            <button class="settings-cancel-btn" @click="cancelEdit('nickname')">取消</button>
          </div>
        </div>

        <!-- 手机号（可编辑） -->
        <div class="settings-field">
          <div class="settings-field-label">手机号</div>
          <div v-if="!editing.phone" class="settings-field-value">
            <span>{{ maskedPhone }}</span>
            <button class="settings-edit-btn" @click="startEdit('phone')" title="编辑">
              <span class="material-symbols-outlined" style="font-size:14px;">edit</span>
            </button>
          </div>
          <div v-else class="settings-field-edit">
            <input v-model="forms.phone" class="settings-input" maxlength="11" placeholder="13800138000" />
            <button class="settings-save-btn" @click="saveField('phone')" :disabled="saving">保存</button>
            <button class="settings-cancel-btn" @click="cancelEdit('phone')">取消</button>
          </div>
        </div>

        <!-- 邮箱（只读） -->
        <div class="settings-field">
          <div class="settings-field-label">邮箱（登录账号）</div>
          <div class="settings-field-value settings-field-readonly">
            <span>{{ profile && profile.email ? profile.email : "—" }}</span>
            <span class="material-symbols-outlined settings-lock-icon" title="只读">lock</span>
          </div>
        </div>

        <!-- 用户名（只读） -->
        <div class="settings-field">
          <div class="settings-field-label">用户名</div>
          <div class="settings-field-value settings-field-readonly">
            <span>{{ profile && profile.username ? profile.username : "—" }}</span>
            <span class="material-symbols-outlined settings-lock-icon" title="只读">lock</span>
          </div>
        </div>

        <!-- 注册时间（只读） -->
        <div class="settings-field">
          <div class="settings-field-label">注册时间</div>
          <div class="settings-field-value settings-field-readonly">
            <span>{{ profile && profile.createdAt ? formatDate(profile.createdAt) : "—" }}</span>
            <span class="material-symbols-outlined settings-lock-icon" title="只读">lock</span>
          </div>
        </div>

        <!-- 最近登录（只读） -->
        <div class="settings-field">
          <div class="settings-field-label">最近登录</div>
          <div class="settings-field-value settings-field-readonly">
            <span>{{ profile && profile.lastLoginAt ? formatDate(profile.lastLoginAt) : "—" }}</span>
            <span class="material-symbols-outlined settings-lock-icon" title="只读">lock</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import doctorApi from "../../../services/doctorApi";
import toast from "../../../services/toast";
import type { UserProfile } from "../../../types/settings";

export default {
  name: "ProfileSection",
  props: {
    profile: {
      type: Object as () => UserProfile | null,
      default: null,
    },
  },
  emits: ["updated"],
  data() {
    return {
      editing: { nickname: false, phone: false },
      forms: { nickname: "", phone: "" },
      saving: false,
    };
  },
  computed: {
    displayNickname(): string {
      const p = this.profile;
      if (!p) return "—";
      return p.nickname || p.username || "—";
    },
    avatarLetter(): string {
      const name = this.displayNickname;
      if (!name || name === "—") return "";
      return name.charAt(0).toUpperCase();
    },
    maskedPhone(): string {
      const phone = this.profile && this.profile.phone;
      if (!phone) return "—";
      if (phone.length < 7) return phone;
      return phone.substring(0, 3) + "****" + phone.substring(phone.length - 4);
    },
  },
  methods: {
    formatDate(iso: string): string {
      if (!iso) return "—";
      try {
        return new String(iso).replace("T", " ").substring(0, 19);
      } catch {
        return iso;
      }
    },
    startEdit(field: "nickname" | "phone") {
      if (field === "nickname") {
        this.forms.nickname = this.profile?.nickname || "";
        this.editing.nickname = true;
      } else {
        this.forms.phone = this.profile?.phone || "";
        this.editing.phone = true;
      }
    },
    cancelEdit(field: "nickname" | "phone") {
      if (field === "nickname") this.editing.nickname = false;
      else this.editing.phone = false;
    },
    async saveField(field: "nickname" | "phone") {
      if (this.saving) return;
      this.saving = true;
      try {
        const payload: Record<string, string> = {};
        if (field === "nickname") {
          const v = (this.forms.nickname || "").trim();
          if (!v || v.length > 100) {
            toast.error("昵称长度需为 1-100");
            return;
          }
          payload.nickname = v;
        } else {
          const v = (this.forms.phone || "").trim();
          if (v && !/^1[3-9]\d{9}$/.test(v)) {
            toast.error("手机号格式不正确");
            return;
          }
          payload.phone = v;
        }
        const res = await doctorApi.updateUserProfile(payload);
        if (res && res.status === 200) {
          toast.success("已保存");
          this.editing[field] = false;
          this.$emit("updated", res.data as UserProfile);
        } else {
          toast.error((res && res.msg) || "保存失败");
        }
      } catch (e) {
        toast.error("保存失败，请稍后重试");
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.settings-card {
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  padding: 16px;
}

.settings-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--color-secondary-container);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  font-weight: 600;
  font-size: 18px;
  flex-shrink: 0;
}

.settings-field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

@media (max-width: 640px) {
  .settings-field-grid {
    grid-template-columns: 1fr;
  }
}

.settings-field {
  background: var(--color-surface);
  border: 1px solid var(--color-outline-variant);
  border-radius: 4px;
  padding: 10px 12px;
}

.settings-field-label {
  color: var(--color-on-surface-variant);
  font-size: 11px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  margin-bottom: 4px;
}

.settings-field-value {
  color: var(--color-on-surface);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.settings-field-readonly {
  color: var(--color-on-surface-variant);
}

.settings-lock-icon {
  font-size: 13px;
  color: var(--color-outline);
  margin-left: auto;
}

.settings-edit-btn {
  margin-left: auto;
  background: transparent;
  border: none;
  color: var(--color-on-surface-variant);
  cursor: pointer;
  padding: 2px;
  border-radius: 2px;
  transition: color 0.2s ease;
}

.settings-edit-btn:hover {
  color: var(--color-primary);
}

.settings-field-edit {
  display: flex;
  align-items: center;
  gap: 6px;
}

.settings-input {
  flex: 1;
  background: var(--color-surface-container-low);
  border: 1px solid var(--color-outline-variant);
  border-radius: 2px;
  padding: 4px 8px;
  color: var(--color-on-surface);
  font-size: 13px;
  font-family: inherit;
  outline: none;
}

.settings-input:focus {
  border-color: var(--color-primary);
}

.settings-save-btn,
.settings-cancel-btn {
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 2px;
  cursor: pointer;
  border: 1px solid var(--color-outline-variant);
  background: transparent;
  color: var(--color-on-surface-variant);
}

.settings-save-btn {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border-color: var(--color-primary);
}

.settings-save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/pages/settings/components/ProfileSection.vue
git commit -m "feat(settings): add ProfileSection with inline editable nickname/phone"
```

---

### Task 3.5：创建 AppearanceSection.vue

**Files:**
- Create: `FitMate-frontend/src/pages/settings/components/AppearanceSection.vue`

- [ ] **Step 1: 创建 AppearanceSection.vue**

```vue
<!-- FitMate-frontend/src/pages/settings/components/AppearanceSection.vue -->
<template>
  <div>
    <h2 class="text-primary font-inter text-body-base font-semibold mb-md"># 外观</h2>
    <div class="settings-card">
      <!-- 主题模式 -->
      <div class="mb-lg">
        <div class="settings-sublabel mb-sm">主题模式</div>
        <div class="settings-btn-group">
          <button
            v-for="opt in themeModeOptions"
            :key="opt.value"
            class="settings-mode-btn"
            :class="currentMode === opt.value ? 'settings-mode-btn-active' : ''"
            @click="setMode(opt.value)"
          >
            <span class="material-symbols-outlined" style="font-size:16px;">{{ opt.icon }}</span>
            <span>{{ opt.label }}</span>
          </button>
        </div>
      </div>

      <!-- 强调色 -->
      <div>
        <div class="settings-sublabel mb-sm">强调色</div>
        <div class="settings-accent-row">
          <button
            v-for="opt in accentOptions"
            :key="opt.value"
            class="settings-accent-swatch"
            :class="currentAccent === opt.value ? 'settings-accent-swatch-active' : ''"
            :style="{ background: accentSwatchColor(opt.value) }"
            :title="opt.label"
            @click="setAccent(opt.value)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { THEME_MODE_OPTIONS, ACCENT_COLOR_OPTIONS } from "../../../types/settings";
import type { ThemeMode, AccentColor } from "../../../types/settings";
import theme from "../../../services/theme";

export default {
  name: "AppearanceSection",
  data() {
    return {
      themeModeOptions: THEME_MODE_OPTIONS,
      accentOptions: ACCENT_COLOR_OPTIONS,
      currentMode: theme.getStoredMode() as ThemeMode,
      currentAccent: theme.getStoredAccent() as AccentColor,
    };
  },
  methods: {
    setMode(mode: ThemeMode) {
      this.currentMode = mode;
      theme.setMode(mode);
    },
    setAccent(accent: AccentColor) {
      this.currentAccent = accent;
      theme.setAccent(accent);
    },
    accentSwatchColor(accent: AccentColor): string {
      const map: Record<AccentColor, string> = {
        blue: "#adc6ff",
        green: "#7ee787",
        orange: "#ffb595",
        purple: "#c4a7e7",
      };
      return map[accent];
    },
  },
};
</script>

<style scoped>
.settings-card {
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  padding: 16px;
}

.settings-sublabel {
  color: var(--color-on-surface-variant);
  font-size: 11px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.settings-btn-group {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.settings-mode-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 4px;
  border: 1px solid var(--color-outline-variant);
  color: var(--color-on-surface-variant);
  cursor: pointer;
  background: transparent;
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.settings-mode-btn:hover {
  color: var(--color-on-surface);
}

.settings-mode-btn-active {
  color: var(--color-primary);
  border-color: var(--color-primary);
  background: var(--color-surface-container-high);
}

.settings-accent-row {
  display: flex;
  gap: 10px;
}

.settings-accent-swatch {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  padding: 0;
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.settings-accent-swatch:hover {
  transform: scale(1.1);
}

.settings-accent-swatch-active {
  border-color: var(--color-on-surface);
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/pages/settings/components/AppearanceSection.vue
git commit -m "feat(settings): add AppearanceSection with theme mode and accent color"
```

---

### Task 3.6：创建 AboutSection.vue

**Files:**
- Create: `FitMate-frontend/src/pages/settings/components/AboutSection.vue`

- [ ] **Step 1: 创建 AboutSection.vue**

```vue
<!-- FitMate-frontend/src/pages/settings/components/AboutSection.vue -->
<template>
  <div>
    <h2 class="text-primary font-inter text-body-base font-semibold mb-md"># 关于</h2>
    <div class="settings-card">
      <div class="about-row">
        <span class="about-label">应用</span>
        <span class="about-value">FitMate OS</span>
      </div>
      <div class="about-row">
        <span class="about-label">构建号</span>
        <span class="about-value">Build 4.2.1</span>
      </div>
      <div class="about-row">
        <span class="about-label">源码</span>
        <a class="about-link" href="#" @click.prevent="openRepo">查看源码</a>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
export default {
  name: "AboutSection",
  methods: {
    openRepo() {
      // 占位：实际仓库地址可后续配置
      window.open("https://github.com/", "_blank", "noopener");
    },
  },
};
</script>

<style scoped>
.settings-card {
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  padding: 16px;
}

.about-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-size: 12px;
}

.about-row + .about-row {
  border-top: 1px solid var(--color-outline-variant);
}

.about-label {
  color: var(--color-on-surface-variant);
}

.about-value {
  color: var(--color-on-surface);
}

.about-link {
  color: var(--color-primary);
  text-decoration: none;
}

.about-link:hover {
  text-decoration: underline;
}
</style>
```

- [ ] **Step 2: 启动前端验证 /settings 页面**

Run: `cd FitMate-frontend && npm run dev`

- 登录后访问 `#/settings`
- 三个区块正常渲染
- 顶部锚点 tab 点击可平滑滚动
- 个人信息区块：昵称/手机可编辑，保存成功后刷新
- 外观区块：切换主题模式 → 立即生效；切换强调色 → primary 色立即变化
- 关于区块正常显示

- [ ] **Step 3: Commit**

```bash
git add FitMate-frontend/src/pages/settings/components/AboutSection.vue
git commit -m "feat(settings): add AboutSection"
```

---

## P4：用户下拉菜单

### Task 4.1：创建 UserMenu.vue

**Files:**
- Create: `FitMate-frontend/src/components/UserMenu.vue`

- [ ] **Step 1: 创建 UserMenu.vue**

```vue
<!-- FitMate-frontend/src/components/UserMenu.vue -->
<template>
  <div class="user-menu-root" @mouseenter="onMouseEnter" @mouseleave="onMouseLeave">
    <button
      type="button"
      class="user-menu-trigger"
      :class="open ? 'user-menu-trigger-active' : ''"
      :title="displayName"
      @click="toggleOpen"
    >
      <span v-if="avatarLetter" class="user-menu-avatar-text">{{ avatarLetter }}</span>
      <span v-else class="material-symbols-outlined user-menu-avatar-icon">person</span>
    </button>

    <transition name="user-menu-fade">
      <div v-if="open" class="user-menu-dropdown" @click="keepOpenOnDropdownClick">
        <!-- 头部（只读） -->
        <div class="user-menu-header">
          <div class="user-menu-header-avatar">
            <span v-if="avatarLetter">{{ avatarLetter }}</span>
            <span v-else class="material-symbols-outlined" style="font-size:18px;">person</span>
          </div>
          <div class="user-menu-header-info">
            <div class="user-menu-header-name">{{ displayName }}</div>
            <div class="user-menu-header-email">{{ displayEmail }}</div>
          </div>
        </div>

        <!-- 导航项 -->
        <div class="user-menu-items">
          <router-link to="/settings#profile" class="user-menu-item" @click="close">
            <span class="material-symbols-outlined user-menu-item-icon">person</span>
            <span>个人信息</span>
          </router-link>
          <router-link to="/settings#appearance" class="user-menu-item" @click="close">
            <span class="material-symbols-outlined user-menu-item-icon">settings</span>
            <span>设置</span>
          </router-link>
        </div>

        <!-- 分隔线 + 退出 -->
        <div class="user-menu-divider"></div>
        <div class="user-menu-items">
          <button type="button" class="user-menu-item user-menu-item-logout" :disabled="loggingOut" @click="onLogout">
            <span class="material-symbols-outlined user-menu-item-icon">logout</span>
            <span>{{ loggingOut ? "退出中..." : "退出登录" }}</span>
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script lang="ts">
import { getUserInfo } from "../services/http";

export default {
  name: "UserMenu",
  emits: ["logout"],
  data() {
    return {
      open: false,
      hoverCloseTimer: null as ReturnType<typeof setTimeout> | null,
      loggingOut: false,
    };
  },
  computed: {
    userInfo(): Record<string, unknown> | null {
      const info = getUserInfo();
      return info || null;
    },
    displayName(): string {
      const u = this.userInfo as Record<string, unknown> | null;
      if (!u) return "用户";
      return (u.nickname as string) || (u.username as string) || "用户";
    },
    displayEmail(): string {
      const u = this.userInfo as Record<string, unknown> | null;
      if (!u) return "—";
      return (u.email as string) || "—";
    },
    avatarLetter(): string {
      const name = this.displayName;
      if (!name || name === "用户") return "";
      return name.charAt(0).toUpperCase();
    },
  },
  mounted() {
    document.addEventListener("click", this.onDocClick);
    document.addEventListener("keydown", this.onKeydown);
  },
  beforeUnmount() {
    document.removeEventListener("click", this.onDocClick);
    document.removeEventListener("keydown", this.onKeydown);
    if (this.hoverCloseTimer) clearTimeout(this.hoverCloseTimer);
  },
  watch: {
    $route() {
      this.open = false;
    },
  },
  methods: {
    toggleOpen() {
      this.open = !this.open;
    },
    close() {
      this.open = false;
    },
    onMouseEnter() {
      if (this.hoverCloseTimer) {
        clearTimeout(this.hoverCloseTimer);
        this.hoverCloseTimer = null;
      }
      this.open = true;
    },
    onMouseLeave() {
      this.hoverCloseTimer = setTimeout(() => {
        this.open = false;
      }, 200);
    },
    keepOpenOnDropdownClick(e: MouseEvent) {
      e.stopPropagation();
    },
    onDocClick(e: MouseEvent) {
      const root = this.$el as HTMLElement;
      if (root && !root.contains(e.target as Node)) {
        this.open = false;
      }
    },
    onKeydown(e: KeyboardEvent) {
      if (e.key === "Escape") this.open = false;
    },
    onLogout() {
      if (this.loggingOut) return;
      this.loggingOut = true;
      this.$emit("logout");
      // AppLayout 处理实际 logout，完成后会跳转，组件销毁
    },
  },
};
</script>

<style scoped>
.user-menu-root {
  position: relative;
  display: inline-block;
}

.user-menu-trigger {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--color-surface-container-high);
  border: 1px solid var(--color-outline-variant);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
  transition: opacity 0.2s ease;
}

.user-menu-trigger:hover,
.user-menu-trigger-active {
  opacity: 0.7;
}

.user-menu-avatar-text {
  color: var(--color-primary);
  font-size: 13px;
  font-weight: 600;
}

.user-menu-avatar-icon {
  color: var(--color-on-surface);
  font-size: 18px;
}

.user-menu-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 240px;
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-variant);
  border-radius: 6px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
  z-index: 50;
  overflow: hidden;
}

.user-menu-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--color-outline-variant);
}

.user-menu-header-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--color-secondary-container);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  font-weight: 600;
  font-size: 14px;
  flex-shrink: 0;
}

.user-menu-header-info {
  min-width: 0;
}

.user-menu-header-name {
  color: var(--color-on-surface);
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-menu-header-email {
  color: var(--color-on-surface-variant);
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-menu-items {
  padding: 6px;
}

.user-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  color: var(--color-on-surface);
  font-size: 13px;
  border-radius: 4px;
  cursor: pointer;
  text-decoration: none;
  background: transparent;
  border: none;
  width: 100%;
  text-align: left;
  font-family: inherit;
  transition: background 0.2s ease, color 0.2s ease;
}

.user-menu-item:hover {
  background: var(--color-surface-container-high);
}

.user-menu-item-icon {
  font-size: 16px;
  color: var(--color-on-surface-variant);
}

.user-menu-item-logout {
  color: var(--color-error);
}

.user-menu-item-logout .user-menu-item-icon {
  color: var(--color-error);
}

.user-menu-item-logout:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.user-menu-divider {
  border-top: 1px solid var(--color-outline-variant);
}

.user-menu-fade-enter-active,
.user-menu-fade-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.user-menu-fade-enter-from,
.user-menu-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/components/UserMenu.vue
git commit -m "feat(user-menu): add UserMenu dropdown component"
```

---

### Task 4.2：TopBar.vue 接入 UserMenu

**Files:**
- Modify: `FitMate-frontend/src/components/TopBar.vue`

- [ ] **Step 1: 替换 TopBar 模板**

把原头像 div + Logout 按钮替换为 UserMenu 组件：

```vue
<template>
  <header
    class="flex justify-between items-center px-lg w-full h-16 bg-transparent z-40 sticky top-0 backdrop-blur-md border-b border-outline-variant/30"
  >
    <div>
      <h2 class="font-headline-md text-headline-md text-on-surface tracking-tight">
        FitMate <span class="text-outline-variant font-normal">/ {{ title }}</span>
      </h2>
    </div>
    <div class="flex items-center gap-margin">
      <button
        type="button"
        class="text-on-surface-variant hover:text-primary transition-colors"
        title="Notifications"
      >
        <span class="material-symbols-outlined">notifications</span>
      </button>
      <UserMenu @logout="$emit('logout')" />
    </div>
  </header>
</template>

<script lang="ts">
import UserMenu from "./UserMenu.vue";

export default {
  name: "TopBar",
  components: { UserMenu },
  props: {
    title: {
      type: String,
      default: "FitMate",
    },
  },
};
</script>

<style scoped>
.material-symbols-outlined {
  font-size: 22px;
}
</style>
```

> 注：移除了原 `isLoggingOut` data 和 `logout` emit 声明。`logout` emit 由 UserMenu 触发，TopBar 透传给 AppLayout。TopBar 仍需声明 `emits: ["logout"]`：

修正 script 部分：

```ts
export default {
  name: "TopBar",
  components: { UserMenu },
  props: {
    title: {
      type: String,
      default: "FitMate",
    },
  },
  emits: ["logout"],
};
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/components/TopBar.vue
git commit -m "feat(user-menu): integrate UserMenu into TopBar, remove standalone Logout button"
```

---

### Task 4.3：AppLayout.vue 接收 logout（重置 loggingOut 状态）

**Files:**
- Modify: `FitMate-frontend/src/layouts/AppLayout.vue`

- [ ] **Step 1: 确认 AppLayout 的 handleLogout 仍可用**

AppLayout 现有 `handleLogout` 方法已处理 logout 流程（调 `doctorApi.userLogout` + `clearUserSession` + 跳 `/login`）。TopBar 的 `@logout` 已绑定到 `handleLogout`，无需改动。

但需确认 `isLoggingOut` 状态不再由 TopBar 持有（已移到 UserMenu）。AppLayout 的 `isLoggingOut` 仍保留用于防止重复触发：

```ts
async handleLogout() {
  if (this.isLoggingOut) return;
  this.isLoggingOut = true;
  try {
    await doctorApi.userLogout();
  } catch (e) {
    // ignore network errors during logout
  } finally {
    clearUserSession();
    this.isLoggingOut = false;
    this.$router.push("/login");
  }
},
```

**无需改动**——AppLayout 现有代码已正确。本 Task 仅做验证。

- [ ] **Step 2: 启动前端验证下拉菜单**

Run: `cd FitMate-frontend && npm run dev`

- 登录后，右上角头像显示昵称首字母
- hover 头像 → 下拉菜单展开，显示昵称 + 邮箱 + 3 个导航项 + 退出登录
- 点击"个人信息" → 跳转 `/settings#profile`，菜单收起
- 点击"设置" → 跳转 `/settings#appearance`，菜单收起
- 点击"退出登录" → 触发 logout，跳转 `/login`
- 点击下拉菜单外部 → 菜单收起
- 按 ESC → 菜单收起

- [ ] **Step 3: Commit（如有改动）**

```bash
# 仅当 AppLayout 有改动时
git add FitMate-frontend/src/layouts/AppLayout.vue
git commit -m "feat(user-menu): verify AppLayout logout wiring"
```

---

## P5：联调与回归

### Task 5.1：端到端验证

**Files:** 无（纯验证）

- [ ] **Step 1: 主题切换全流程**

1. 打开应用（自动暗色 + 蓝色强调）
2. 进入 `/settings#appearance`
3. 切换到"亮色" → 整个应用立即变亮色，刷新页面后仍为亮色（防闪烁生效）
4. 切换到"跟随系统" → 按系统主题切换
5. 切换强调色为"绿" → primary 色立即变绿，刷新后仍为绿
6. 检查所有页面（Chat/Training/Metrics/Knowledge/Dashboard/Settings）在亮色+绿色下视觉正常

- [ ] **Step 2: 跨设备同步**

1. 在浏览器 A 登录，设置主题为"亮色 + 橙色"
2. 等待 1 秒（防抖同步）
3. 在浏览器 B 登录同一账号 → 应自动变为"亮色 + 橙色"（后端覆盖本地）
4. 在浏览器 A 检查 `localStorage` 与后端一致

- [ ] **Step 3: 个人信息编辑**

1. 进入 `/settings#profile`
2. 编辑昵称 → 保存 → toast 成功 → 头部昵称更新 → 右上角头像首字母更新
3. 编辑手机号为非法格式 → 保存 → toast 报错
4. 编辑手机号为合法格式 → 保存 → 脱敏展示更新
5. 邮箱/用户名/注册时间/最近登录显示为只读（有 lock 图标）

- [ ] **Step 4: 下拉菜单 + SideNav 一致性**

1. 右上角"设置" → 跳 `/settings#appearance`
2. 左下角"Settings" → 跳 `/settings`（默认定位 profile）
3. 两者都到达同一 `/settings` 页面

- [ ] **Step 5: 移动端验证（响应式）**

1. 浏览器 DevTools 切换到移动端视口
2. 设置页字段网格变为单列
3. 下拉菜单 hover 不可用，但 click 可正常展开/收起

- [ ] **Step 6: Final commit（如有修复）**

```bash
git add -A
git commit -m "test: end-to-end verification of user menu and settings"
```

---

## Self-Review

### Spec coverage
- §1.2 目标 1-7：全部有对应 Task ✓
  - 目标1（下拉菜单）→ P4
  - 目标2（跳转个人信息/设置）→ P4 Task 4.1
  - 目标3（统一设置页3区块）→ P3
  - 目标4（主题模式+强调色）→ P1 + Task 3.5
  - 目标5（修复 SideNav）→ Task 3.2
  - 目标6（前端优先+后端同步）→ P1 Task 1.6 + P5 Task 5.1 Step 2
  - 目标7（部分可编辑）→ Task 3.4
- §10 风险 1-8：均已在对应 Task 处理
  - 风险2（硬编码色残留）→ Task 1.3 + 1.4
  - 风险3（FOUC）→ Task 1.7
  - 风险4（JSON 字段）→ Task 2.2 + 2.5
  - 风险7（移动端 hover）→ Task 4.1（click 为主）+ Task 5.1 Step 5
  - 风险8（路由锚点）→ Task 3.3 SettingsPage.applyHash + scrollToSection

### Placeholder scan
- 无 TBD/TODO/未定义引用 ✓
- TopBar emits 修正已在 Task 4.2 Step 1 内联说明 ✓

### Type consistency
- `ThemeMode` / `AccentColor` / `UserSettings` / `UserProfile` 在 types/settings.ts 定义，theme.ts 与各 Section 一致引用 ✓
- 后端 `UserPreferenceItem` 字段名 `themeMode`/`accentColor` 与前端 `UserSettings` 一致 ✓
- `doctorApi` 函数名 `getUserProfile`/`updateUserProfile`/`getUserPreferences`/`saveUserPreferences` 与各调用点一致 ✓
- `theme.setMode`/`setAccent`/`getStoredMode`/`getStoredAccent`/`applyRemoteSettings`/`onSettingsChange` 定义与调用一致 ✓
