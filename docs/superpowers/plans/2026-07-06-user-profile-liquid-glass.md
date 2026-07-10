# User Profile Liquid Glass 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构 `UserProfilePanel.vue` 为 Liquid Glass 视觉语言（真折射玻璃 + 真实图像锚点 + Linear 风格无卡片列表），仅修改此单个文件，不影响项目其他任何功能。

**Architecture:** 完全重写 `UserProfilePanel.vue` 的 `<template>` / `<script>` / `<style>` 三段。保留现有 Options API 风格与 `memoryApi.getProfile()` 调用契约不变；新增按 category 分组的 computed、相对时间计算、完整度算法；将所有 SVG filter 与样式封装在 scoped style 内。不修改任何其他文件（包括 `fitmate-vite.html`、`tokens.css`、`DashboardPage.vue`）。

**Tech Stack:** Vue 3 Options API + TypeScript + SVG `feDisplacementMap` + CSS `backdrop-filter` + 项目已有 token CSS 变量

**约束（来自用户）:**
- 仅修改 `FitMate-frontend/src/pages/dashboard/components/UserProfilePanel.vue` 一个文件
- 不引入新字体、新依赖、新全局样式
- 不修改后端 API、数据类型、`fitmate-vite.html`、`tokens.css`、`DashboardPage.vue`

---

## File Structure

| 文件 | 操作 | 责任 |
|---|---|---|
| `FitMate-frontend/src/pages/dashboard/components/UserProfilePanel.vue` | 完全重写 | 视觉锚点 + Liquid Glass 卡片 + 5 行无卡片列表 + 文字总结 + footer meta |

**不创建任何新文件。** 不创建测试文件（视觉验证通过浏览器进行；用户明确要求不引入新文件以避免影响其他功能）。

---

## Task 1: 准备工作与现状验证

**Files:**
- Read only: `FitMate-frontend/src/pages/dashboard/components/UserProfilePanel.vue`
- Read only: `FitMate-frontend/src/types/memory.ts`
- Read only: `FitMate-frontend/src/services/memoryApi.ts`

- [ ] **Step 1: 阅读当前实现**

Read `FitMate-frontend/src/pages/dashboard/components/UserProfilePanel.vue`。确认当前实现要点：
- Options API + `defineComponent`
- `data()` 返回 `profile: ProfileResponse | null`、`tags: ProfileTag[]`、`loading: boolean`
- `mounted()` 调用 `loadProfile()`
- `loadProfile()` 调用 `memoryApi.getProfile().then(res => res.data)`，解析 `profileTagsJson` 为 `tags`
- `tagStyle(tag)` 方法用 5 种 category 颜色映射（即将删除）
- `<template>` 包含 loading / empty / content 三态
- `<style scoped>` 包含 `.profile-panel` `.profile-loading` `.profile-tag` 等样式

- [ ] **Step 2: 阅读数据类型**

Read `FitMate-frontend/src/types/memory.ts`。确认接口：
```ts
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

- [ ] **Step 3: 验证 memoryApi.getProfile 契约**

Read `FitMate-frontend/src/services/memoryApi.ts`，确认 `getProfile()` 返回 `{ data: ProfileResponse }` 形式（ axios 风格）。后续实现中沿用 `res.data` 解构。

- [ ] **Step 4: 启动开发服务器并访问 Dashboard 验证现状**

Run（在 `FitMate-frontend` 目录）:
```bash
npm run dev
```
Expected: Vite 启动，访问 `http://localhost:5173`，导航到 Dashboard 页面，右侧能看到当前 UserProfilePanel（标签云 + 文字）。这是 baseline。

- [ ] **Step 5: 记录 baseline 截图（可选但推荐）**

在浏览器 Dashboard 页面右栏截图保存。后续对比用。

---

## Task 2: 重写 UserProfilePanel.vue 的完整内容

**Files:**
- Modify (full rewrite): `FitMate-frontend/src/pages/dashboard/components/UserProfilePanel.vue`

本 task 是单步原子替换——Vue SFC 的三段（template/script/style）相互依赖，无法分多个 commit 不破坏中间状态。因此一次性写入完整文件，再分阶段浏览器验证。

- [ ] **Step 1: 用以下完整内容覆盖 `UserProfilePanel.vue`**

```vue
<template>
  <div class="profile-panel">
    <!-- 加载态 -->
    <div v-if="loading" class="profile-state">
      <span class="state-text">正在生成用户画像</span>
    </div>
    <!-- 空态 -->
    <div v-else-if="!profile || !profile.profileText" class="profile-state">
      <span class="state-text">暂无画像</span>
      <span class="state-hint">开始对话或上传文档后自动生成</span>
    </div>
    <!-- 正常态 -->
    <template v-else>
      <!-- 视觉锚点 + 玻璃卡 -->
      <div class="visual-anchor">
        <img class="anchor-image" :src="visualUrl" alt="profile visual" loading="lazy" />
        <div class="anchor-tint"></div>
        <div class="glass-card">
          <div class="glass-eyebrow">AI Generated</div>
          <div class="glass-title">{{ headline }}</div>
          <div class="glass-meta">
            完整度 <strong>{{ completeness }}%</strong> · {{ tags.length }} 项特征
          </div>
        </div>
      </div>

      <!-- 5 行无卡片列表 -->
      <div class="profile-list">
        <div
          v-for="cat in categoryOrder"
          :key="cat"
          class="profile-row"
        >
          <div class="row-label">{{ catLabel(cat) }}</div>
          <div class="row-content">
            <span v-if="cat === 'status'" class="status-dot"></span>
            <template v-if="grouped[cat] && grouped[cat].length">
              {{ grouped[cat].join(' · ') }}
            </template>
            <template v-else>—</template>
          </div>
        </div>
      </div>

      <!-- 文字总结 -->
      <p class="profile-summary">{{ profile.profileText }}</p>

      <!-- Footer meta -->
      <div class="footer-meta">
        <span><span class="dot-live"></span>SYNCED</span>
        <span>v{{ profile.memoryVersion || '—' }} · {{ relativeTime }}</span>
      </div>
    </template>

    <!-- SVG filter: Liquid Glass 真折射（仅 Chromium 生效，其他浏览器降级为普通毛玻璃）-->
    <svg class="filter-defs" aria-hidden="true">
      <defs>
        <filter id="glassRefraction" color-interpolation-filters="sRGB">
          <feTurbulence type="fractalNoise" baseFrequency="0.008 0.012"
                        numOctaves="2" seed="5" result="turb"/>
          <feGaussianBlur in="turb" stdDeviation="1.5" result="softTurb"/>
          <feDisplacementMap in="SourceGraphic" in2="softTurb"
                            scale="18" xChannelSelector="R" yChannelSelector="G"/>
        </filter>
      </defs>
    </svg>
  </div>
</template>

<script lang="ts">
import { defineComponent } from "vue";
import memoryApi from "../../../services/memoryApi";
import type { ProfileResponse, ProfileTag } from "../../../types/memory";

type Category = ProfileTag["category"];

const CATEGORY_ORDER: Category[] = [
  "identity",
  "goal",
  "preference",
  "condition",
  "status",
];

const CATEGORY_LABELS: Record<Category, string> = {
  identity: "Identity",
  goal: "Goal",
  preference: "Preference",
  condition: "Condition",
  status: "Status",
};

// 视觉锚点图 URL（便于后续替换为本地静态资源或可配置头像）
const PROFILE_VISUAL_URL =
  "https://images.unsplash.com/photo-1571902943202-507ec2618e8f?w=800&q=80&auto=format&fit=crop";

function formatRelativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  if (diff < 0) return "just now";
  const min = Math.floor(diff / 60000);
  if (min < 1) return "just now";
  if (min < 60) return min + "m ago";
  const hr = Math.floor(min / 60);
  if (hr < 24) return hr + "h ago";
  return Math.floor(hr / 24) + "d ago";
}

export default defineComponent({
  name: "UserProfilePanel",
  data() {
    return {
      profile: null as ProfileResponse | null,
      tags: [] as ProfileTag[],
      loading: true,
      categoryOrder: CATEGORY_ORDER,
      visualUrl: PROFILE_VISUAL_URL,
    };
  },
  computed: {
    grouped(): Record<Category, string[]> {
      const g: Record<Category, string[]> = {
        identity: [],
        goal: [],
        preference: [],
        condition: [],
        status: [],
      };
      for (const t of this.tags) {
        if (g[t.category]) g[t.category].push(t.label);
      }
      return g;
    },
    headline(): string {
      const identity = this.grouped.identity[0] || "";
      const goal = this.grouped.goal[0] || "";
      const parts = [identity, goal].filter(Boolean);
      return parts.length ? parts.join(" · ") : "你的画像";
    },
    completeness(): number {
      const filled = CATEGORY_ORDER.filter(
        (cat) => this.grouped[cat] && this.grouped[cat].length > 0
      ).length;
      return Math.round((filled / CATEGORY_ORDER.length) * 100);
    },
    relativeTime(): string {
      if (!this.profile || !this.profile.generatedAt) return "";
      return formatRelativeTime(this.profile.generatedAt);
    },
  },
  mounted() {
    this.loadProfile();
  },
  methods: {
    loadProfile() {
      var me = this;
      me.loading = true;
      memoryApi
        .getProfile()
        .then(function (res) {
          var data = res && res.data;
          me.profile = (data || null) as ProfileResponse | null;
          if (me.profile && me.profile.profileTagsJson) {
            try {
              me.tags = JSON.parse(me.profile.profileTagsJson);
            } catch (e) {
              me.tags = [];
            }
          } else {
            me.tags = [];
          }
        })
        .catch(function () {
          me.profile = null;
          me.tags = [];
        })
        .finally(function () {
          me.loading = false;
        });
    },
    catLabel(cat: string): string {
      return (CATEGORY_LABELS as Record<string, string>)[cat] || cat;
    },
  },
});
</script>

<style scoped>
/* === 仅使用项目已有 token，不引入新字体 === */
/* 正文: Manrope（项目已引入）  /  monospace: ui-monospace, monospace（项目已用） */

.profile-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
  flex: 1;
  position: relative;
}

/* ============================================
   加载 / 空态
   ============================================ */
.profile-state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 32px 4px;
  color: var(--color-on-surface-variant);
}
.state-text {
  font-size: 13px;
  color: var(--color-on-surface);
  font-weight: 500;
}
.state-hint {
  font-size: 11px;
  color: var(--color-on-surface-variant);
  opacity: 0.7;
  font-family: ui-monospace, monospace;
  letter-spacing: 0.04em;
}

/* ============================================
   视觉锚点：真实图 + 玻璃卡
   ============================================ */
.visual-anchor {
  position: relative;
  height: 200px;
  border-radius: 14px;
  overflow: hidden;
  margin-bottom: 24px;
  background: var(--color-surface-container-lowest);
  animation: anchor-in 0.6s cubic-bezier(0.16, 1, 0.3, 1) backwards;
}
@keyframes anchor-in {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.anchor-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: brightness(0.7) saturate(0.85) hue-rotate(-5deg);
  transition: transform 1.5s ease;
}
.visual-anchor:hover .anchor-image {
  transform: scale(1.04);
}

.anchor-tint {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, transparent 0%, rgba(16, 19, 27, 0.4) 60%, var(--color-background) 100%),
    linear-gradient(135deg, rgba(75, 142, 255, 0.15) 0%, transparent 50%);
  pointer-events: none;
}

/* === Liquid Glass 卡片（唯一一处玻璃，浮在图上）=== */
.glass-card {
  position: absolute;
  left: 16px;
  bottom: 16px;
  right: 16px;
  padding: 14px 16px;
  backdrop-filter: url(#glassRefraction) blur(8px) saturate(150%);
  -webkit-backdrop-filter: blur(16px) saturate(150%);
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 12px;
  box-shadow:
    0 8px 24px rgba(0, 0, 0, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
  animation: glass-in 0.8s cubic-bezier(0.16, 1, 0.3, 1) 0.2s backwards;
}
@keyframes glass-in {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
.glass-card::before {
  content: "";
  position: absolute;
  top: 0; left: 20%; right: 20%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.5), transparent);
  pointer-events: none;
}

.glass-eyebrow {
  font-size: 9px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.7);
  font-family: ui-monospace, monospace;
  margin-bottom: 4px;
}
.glass-title {
  font-size: 14px;
  font-weight: 600;
  color: white;
  line-height: 1.3;
  margin-bottom: 6px;
  letter-spacing: -0.01em;
}
.glass-meta {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.65);
  font-family: ui-monospace, monospace;
  letter-spacing: 0.04em;
}
.glass-meta strong {
  color: var(--color-primary);
  font-weight: 600;
}

/* ============================================
   5 行无卡片列表（Linear 风格）
   ============================================ */
.profile-list {
  padding: 0 2px;
}

.profile-row {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid var(--color-outline-variant);
  align-items: baseline;
  opacity: 0;
  transform: translateY(6px);
  animation: row-in 0.5s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
.profile-row:nth-child(1) { animation-delay: 0.4s; }
.profile-row:nth-child(2) { animation-delay: 0.5s; }
.profile-row:nth-child(3) { animation-delay: 0.6s; }
.profile-row:nth-child(4) { animation-delay: 0.7s; }
.profile-row:nth-child(5) { animation-delay: 0.8s; }
@keyframes row-in {
  to { opacity: 1; transform: translateY(0); }
}

.row-label {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
  font-family: ui-monospace, monospace;
  padding-top: 2px;
}

.row-content {
  font-size: 13px;
  line-height: 1.55;
  color: var(--color-on-surface);
  letter-spacing: -0.005em;
}

/* 单一强调色：仅用于 status 行的呼吸点 */
.status-dot {
  display: inline-block;
  width: 5px; height: 5px;
  border-radius: 50%;
  background: var(--color-primary);
  box-shadow: 0 0 6px var(--color-primary);
  margin-right: 6px;
  vertical-align: middle;
  animation: dot-pulse 2.5s ease-in-out infinite;
}
@keyframes dot-pulse {
  0%, 100% { opacity: 0.7; }
  50% { opacity: 1; }
}

/* ============================================
   文字总结（无容器，段落直接置于列表下）
   ============================================ */
.profile-summary {
  margin-top: 20px;
  padding: 14px 2px 0;
  border-top: 1px solid var(--color-outline-variant);
  font-size: 12px;
  line-height: 1.7;
  color: var(--color-on-surface-variant);
  letter-spacing: 0.005em;
  opacity: 0;
  animation: row-in 0.6s ease-out 0.9s forwards;
}

/* ============================================
   Footer meta
   ============================================ */
.footer-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding-top: 10px;
  font-size: 9px;
  font-family: ui-monospace, monospace;
  color: var(--color-on-surface-variant);
  opacity: 0.5;
  letter-spacing: 0.08em;
}
.dot-live {
  display: inline-block;
  width: 4px; height: 4px;
  border-radius: 50%;
  background: #7ee787;
  margin-right: 5px;
  box-shadow: 0 0 4px #7ee787;
  animation: dot-pulse 2s ease-in-out infinite;
  vertical-align: middle;
}

/* ============================================
   SVG filter 隐藏
   ============================================ */
.filter-defs {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
}

/* ============================================
   light theme 适配（玻璃背景在浅色下需提高不透明度）
   ============================================ */
:global([data-theme="light"]) .anchor-image {
  filter: brightness(0.95) saturate(0.9);
}
:global([data-theme="light"]) .anchor-tint {
  background:
    linear-gradient(180deg, transparent 0%, rgba(253, 251, 255, 0.4) 60%, var(--color-background) 100%),
    linear-gradient(135deg, rgba(0, 91, 193, 0.1) 0%, transparent 50%);
}
:global([data-theme="light"]) .glass-card {
  background: rgba(255, 255, 255, 0.4);
  border-color: rgba(255, 255, 255, 0.6);
  box-shadow:
    0 8px 24px rgba(0, 0, 0, 0.15),
    inset 0 1px 0 rgba(255, 255, 255, 0.5);
}
:global([data-theme="light"]) .glass-eyebrow {
  color: rgba(0, 0, 0, 0.6);
}
:global([data-theme="light"]) .glass-title {
  color: var(--color-on-surface);
}
:global([data-theme="light"]) .glass-meta {
  color: rgba(0, 0, 0, 0.55);
}

/* ============================================
   移动端适配（< 900px，参照 DashboardPage 断点）
   ============================================ */
@media (max-width: 900px) {
  .profile-row {
    grid-template-columns: 80px 1fr;
    gap: 12px;
    padding: 12px 0;
  }
}
</style>
```

- [ ] **Step 2: 验证 TypeScript 编译通过**

Run（在 `FitMate-frontend` 目录）:
```bash
npm run build
```
Expected: 编译成功，无 TypeScript 错误。如果有错误，根据错误信息修复（常见问题：`Category` 类型在模板中的作用域、`grouped[cat]` 的索引类型）。

- [ ] **Step 3: 验证 Vite 开发服务器启动**

Run（在 `FitMate-frontend` 目录，新终端）:
```bash
npm run dev
```
Expected: Vite 启动到 `http://localhost:5173`，无控制台错误。

---

## Task 3: 浏览器视觉验证

**Files:** 无修改，仅浏览器验证

- [ ] **Step 1: 验证 Dashboard 右栏正常显示**

打开 `http://localhost:5173`，导航到 Dashboard 页面。Expected:
- 右栏显示视觉锚点图（200px 高，健身场景）
- 图上左下方有玻璃卡片浮起，显示 "AI Generated / 城市白领程序员 · 减脂期 / 完整度 87% · 5 项特征"
- 5 行无卡片列表，左侧 Identity/Goal/Preference/Condition/Status 标签
- 底部一段文字总结
- 最底部 footer 显示 "SYNCED" + "v3 · 2h ago"
- Status 行有蓝色呼吸点
- footer 有绿色 LIVE 点

- [ ] **Step 2: 验证 Liquid Glass 折射效果（仅 Chrome/Edge）**

在 Chrome 或 Edge 浏览器查看玻璃卡片区域。Expected:
- 玻璃背后的图片像素被扭曲、流动（不是简单模糊）
- 玻璃顶部有一条细高光线
- 玻璃边框半透明，透出图片色彩

在 Safari 或 Firefox 查看（如有）：玻璃退化为普通毛玻璃（仍有 blur 效果），不报错。

- [ ] **Step 3: 验证入场动画**

刷新页面，观察动画序列：
1. 图片先入场（0-0.6s）
2. 玻璃卡片延迟 0.2s 入场（0.2-1.0s）
3. 5 行列表 stagger 入场（0.4-1.3s，每行间隔 0.1s）
4. 文字总结延迟 0.9s 入场
5. 所有动画在 1.5s 内完成

Expected: 动画流畅，不卡顿，cubic-bezier(0.16, 1, 0.3, 1) 的缓动曲线感觉自然。

- [ ] **Step 4: 验证 hover 交互**

鼠标悬停图片区域。Expected: 图片缓慢放大到 1.04 倍（1.5s 过渡），鼠标移开后缩回。

- [ ] **Step 5: 验证加载态**

模拟网络慢或后端未启动时，刷新页面。Expected: 显示 "正在生成用户画像" 文字（不再有旋转图标）。

- [ ] **Step 6: 验证空态**

模拟无画像数据时（profileText 为 null 或 profile 为 null）。Expected: 显示 "暂无画像" + "开始对话或上传文档后自动生成" hint。

- [ ] **Step 7: 验证主题切换（dark ↔ light）**

切换项目主题（如果有 UI 开关）或在 DevTools 中给 `<html>` 加 `data-theme="light"`。Expected:
- dark: 图较暗，玻璃透出蓝色调
- light: 图较亮，玻璃背景白色（rgba(255,255,255,0.4)），文字深色
- 切换不报错，无样式残留

- [ ] **Step 8: 验证不同 accent 切换（可选）**

切换 `data-accent` 到 green/orange/purple/light/dark。Expected: 玻璃卡内的 strong（完整度数字）和 status 蓝点跟随 `--color-primary` 变色。其他元素不变。

---

## Task 4: 数据契约验证

**Files:** 无修改，仅逻辑验证

- [ ] **Step 1: 验证 grouped 计算正确**

在浏览器 DevTools 中给 Vue 组件加 watcher，或在 `mounted` 后 `console.log(this.grouped)`。Expected: tags 数组按 category 正确分组，形如：
```js
{
  identity: ["程序员", "25-30 岁", "城市白领"],
  goal: ["减脂", "改善睡眠"],
  preference: ["晨练", "器械训练", "中高强度"],
  condition: ["久坐", "肩颈紧张"],
  status: ["恢复中", "中度疲劳"]
}
```

- [ ] **Step 2: 验证 headline 计算**

Expected: 取 identity 第一个 + goal 第一个，用 " · " 连接。如果 identity 或 goal 为空，跳过。如果都为空，返回 "你的画像"。

- [ ] **Step 3: 验证 completeness 计算**

| 场景 | 期望值 |
|---|---|
| 5 类都有标签 | 100 |
| 4 类有标签 | 80 |
| 0 类有标签 | 0 |

- [ ] **Step 4: 验证 relativeTime 计算**

| generatedAt | 期望 |
|---|---|
| 现在 - 30 秒 | "just now" |
| 现在 - 5 分钟 | "5m ago" |
| 现在 - 3 小时 | "3h ago" |
| 现在 - 2 天 | "2d ago" |
| 未来时间 | "just now" |
| null | "" |

- [ ] **Step 5: 验证空 category 显示 "—"**

构造数据让某个 category（如 condition）的 tags 为空。Expected: 对应行内容显示 "—"。

---

## Task 5: 项目其他功能不受影响验证

**Files:** 无修改，仅回归测试

- [ ] **Step 1: 验证 Dashboard 其他面板正常**

在 Dashboard 页面，确认除 UserProfilePanel 外的其他面板（如对话区、记忆列表等）显示和交互正常。

- [ ] **Step 2: 验证对话功能正常**

发起一段对话，确认对话流、Agent 推理、记忆写入等功能正常（这些不应受 UserProfilePanel 重构影响，因为数据契约未变）。

- [ ] **Step 3: 验证 fitmate-vite.html 未被修改**

Run:
```bash
git diff FitMate-frontend/fitmate-vite.html
```
Expected: 无输出（文件未修改）。

- [ ] **Step 4: 验证 tokens.css 未被修改**

Run:
```bash
git diff FitMate-frontend/src/styles/tokens.css
```
Expected: 无输出。

- [ ] **Step 5: 验证 DashboardPage.vue 未被修改**

Run:
```bash
git diff FitMate-frontend/src/pages/dashboard/DashboardPage.vue
```
Expected: 无输出。

- [ ] **Step 6: 验证 memory.ts / memoryApi.ts 未被修改**

Run:
```bash
git diff FitMate-frontend/src/types/memory.ts FitMate-frontend/src/services/memoryApi.ts
```
Expected: 无输出。

- [ ] **Step 7: 确认仅 UserProfilePanel.vue 被修改**

Run:
```bash
git status
```
Expected: 仅 `FitMate-frontend/src/pages/dashboard/components/UserProfilePanel.vue` 出现在修改列表中（除非 brainstorming 阶段产生过其他文件，但实施阶段不应再有新文件）。

---

## Task 6: 提交

**Files:**
- Commit: `FitMate-frontend/src/pages/dashboard/components/UserProfilePanel.vue`

- [ ] **Step 1: git add 仅目标文件**

Run:
```bash
git add FitMate-frontend/src/pages/dashboard/components/UserProfilePanel.vue
```

- [ ] **Step 2: 验证 staged 内容**

Run:
```bash
git diff --cached --stat
```
Expected: 仅显示 `UserProfilePanel.vue | NNN +-`（NNN 为行数，由于是完整重写，行数会较多）。

- [ ] **Step 3: 提交**

```bash
git commit -m "$(cat <<'EOF'
refactor(user-profile): 重构画像面板为 Liquid Glass 视觉语言

- 用 SVG feDisplacementMap 实现真折射玻璃（仅 Chromium 生效，其他浏览器降级为普通毛玻璃）
- 真实健身场景图作视觉锚点，玻璃卡片折射图像
- Linear 风格无卡片列表：5 行 grid + 分割线，删除原 5 种 category 色
- 单一强调色：仅项目 primary，Status 行用呼吸蓝点
- 字体仅用项目已有 Manrope + ui-monospace，不引入新依赖
- 启用 memoryVersion / generatedAt 字段（footer 显示版本与相对时间）
- 完整度算法基于 5 类 category 覆盖度（5/5=100%）
- 仅修改此单个文件，不影响其他功能
EOF
)"
```

Expected: 提交成功。

- [ ] **Step 4: 验证提交**

Run:
```bash
git log -1 --stat
```
Expected: 显示刚才的提交，仅 `UserProfilePanel.vue` 一个文件被修改。

---

## Self-Review

**1. Spec coverage 检查:**

| Spec 章节 | 对应 Task | 状态 |
|---|---|---|
| §1 背景与目标 | Task 1 (baseline) | ✅ |
| §2 设计原则 | Task 2 (实现体现) | ✅ |
| §3.1 整体结构 | Task 2 template | ✅ |
| §3.2 Liquid Glass 实现 | Task 2 style + svg | ✅ |
| §3.3 主题色映射 | Task 2 style（用已有 token） | ✅ |
| §3.4 字体（不引入新） | Task 2 style（Manrope + ui-monospace） | ✅ |
| §3.5 视觉锚点图 | Task 2 template + const PROFILE_VISUAL_URL | ✅ |
| §3.6 动画（3 处） | Task 2 style（anchor-in / glass-in / row-in / dot-pulse） | ✅ |
| §4.1 输入（不变） | Task 1 Step 2 验证 | ✅ |
| §4.2 渲染分组 | Task 2 computed grouped | ✅ |
| §4.3 完整度算法 | Task 2 computed completeness + Task 4 Step 3 验证 | ✅ |
| §4.4 新增字段使用 | Task 2 template（memoryVersion + relativeTime）+ Task 4 Step 4 验证 | ✅ |
| §5.1 仅修改 1 文件 | Task 5 Step 7 验证 | ✅ |
| §5.2 模板结构 | Task 2 template | ✅ |
| §5.3 脚本逻辑 | Task 2 script | ✅ |
| §5.4 移除项 | Task 2（tagStyle 删除、spin 动画删除、profile-tag 删除） | ✅ |
| §6.1 浏览器兼容 | Task 3 Step 2 验证 | ✅ |
| §6.2 性能 | Task 2（loading="lazy"，无 will-change） | ✅ |
| §6.3 主题切换 | Task 3 Step 7 + Task 2 light 适配样式 | ✅ |
| §7 验证清单 | Task 3 + Task 4 + Task 5 全覆盖 | ✅ |
| §9 关键文件 | Task 5 验证未修改其他文件 | ✅ |

**2. Placeholder 扫描:** 无 TBD / TODO / "implement later" / "similar to" / "add appropriate"。

**3. 类型一致性:** 
- `Category = ProfileTag["category"]` 全文件统一
- `grouped: Record<Category, string[]>` 与模板 `grouped[cat]` 一致
- `categoryOrder: Category[]` data 字段与模板 `v-for="cat in categoryOrder"` 一致
- `catLabel(cat: string)` 接受 string 避免模板类型问题
- `formatRelativeTime(iso: string)` 返回 string

**4. 用户约束确认:**
- ✅ 仅修改 `UserProfilePanel.vue`（Task 5 Step 7 验证）
- ✅ 不修改 `fitmate-vite.html`（Task 5 Step 3 验证）
- ✅ 不修改 `tokens.css`（Task 5 Step 4 验证）
- ✅ 不修改 `DashboardPage.vue`（Task 5 Step 5 验证）
- ✅ 不修改 `memory.ts` / `memoryApi.ts`（Task 5 Step 6 验证）
- ✅ 不引入新字体（Manrope 已有 + ui-monospace 已用）
- ✅ 不引入新依赖（纯 CSS + SVG）

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-06-user-profile-liquid-glass.md`.
