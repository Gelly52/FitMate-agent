# 用户画像面板重构设计文档

**日期**: 2026-07-06
**作者**: 基于 brainstorming + frontend-skill 流程
**状态**: 待审核

---

## 1. 背景与目标

### 1.1 现状
[UserProfilePanel.vue](file:///d:/Applications/Java/A - Learning/FitMate-AI-0/FitMate-frontend/src/pages/dashboard/components/UserProfilePanel.vue) 当前实现：
- 5 类彩色标签云（identity / goal / preference / condition / status）
- 一段纯文字段落
- 仅 1 处 hover 微交互
- 视觉单调，"卡片 + 文字"缺乏层次与质感

### 1.2 目标
重构为基于 **Liquid Glass（流动玻璃）** 视觉语言的画像面板：
- 真实折射效果（SVG `feDisplacementMap`）
- Linear 风格的克制结构（无卡片堆砌）
- 单一强调色，与项目 Material Design 3 dark theme 一致
- 限定 Dashboard 右栏约 420px 宽展示
- 真实图像作为视觉锚点

### 1.3 非目标
- 不改造后端 API
- 不修改 `ProfileResponse` / `ProfileTag` 数据结构
- 不引入第三方动画库（保持纯 CSS + SVG）
- 不做全屏沉浸式页（仅 Dashboard 右栏区域）
- **不修改 `fitmate-vite.html` 或其他全局文件**（影响范围严格限定在 `UserProfilePanel.vue` 单个组件）

---

## 2. 设计原则（来自 frontend-skill）

贯彻反"AI 味"原则：

| 原则 | 应用 |
|---|---|
| **No cards by default** | 用列表 + 分割线 + grid 列组织，不堆卡片 |
| **One accent color** | 仅项目 primary `#adc6ff`，5 类 category 不再各用一色 |
| **Image-led hierarchy** | 一张真实健身场景照片作视觉锚点 |
| **Two typefaces max** | Manrope（正文）+ JetBrains Mono（标签/数字） |
| **Restrained motion** | 入场动画 + Status 呼吸点 + LIVE 点，3 处为止 |
| **Utility copy** | "Profile / v3 · 2h ago"，不用营销腔 |

---

## 3. 视觉设计

### 3.1 整体结构（自上而下）

```
┌────────────────────────────────────┐
│ Profile          v3 · 2h ago        │ ← header (label + meta)
├────────────────────────────────────┤
│                                    │
│   [真实健身场景图 200h]            │ ← 视觉锚点
│   ┌──────────────────────────────┐ │
│   │ AI Generated                 │ │ ← Liquid Glass 卡片
│   │ 城市白领程序员 · 减脂期       │ │   (浮在图上，折射图)
│   │ 完整度 87% · 5 类标签        │ │
│   └──────────────────────────────┘ │
├────────────────────────────────────┤
│ Identity   程序员 · 25-30 · 白领   │ ← grid 列表
│ Goal       减脂 · 改善睡眠          │   (无卡片，仅分割线)
│ Preference 晨练 · 器械 · 中高强度   │
│ Condition  久坐 · 肩颈紧张          │
│ Status     ● 恢复中 · 中度疲劳      │ ← 单一蓝点
├────────────────────────────────────┤
│ 城市白领程序员，长期久坐导致...     │ ← 段落总结（无容器）
├────────────────────────────────────┤
│ ● SYNCED              MEM v3       │ ← footer meta
└────────────────────────────────────┘
```

### 3.2 Liquid Glass 实现

**唯一一处玻璃材质**：浮在图片上的核心信息卡。

技术栈：
```css
.glass-card {
  backdrop-filter: url(#glassRefraction) blur(8px) saturate(150%);
  -webkit-backdrop-filter: blur(16px) saturate(150%);
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.18);
  box-shadow:
    0 8px 24px rgba(0, 0, 0, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
}
```

SVG filter（真折射，仅 Chromium 支持，其他浏览器降级为普通毛玻璃）：
```html
<svg style="position:absolute; width:0; height:0">
  <defs>
    <filter id="glassRefraction" color-interpolation-filters="sRGB">
      <feTurbulence type="fractalNoise" baseFrequency="0.008 0.012"
                    numOctaves="2" seed="5" result="turb"/>
      <feGaussianBlur in="turb" stdDeviation="1.5" result="softTurb"/>
      <feDisplacementMap in="SourceGraphic" in2="softTurb"
                        scale="18"
                        xChannelSelector="R"
                        yChannelSelector="G"/>
    </filter>
  </defs>
</svg>
```

玻璃顶部 1px 高光线（苹果镜片边缘感）：
```css
.glass-card::before {
  content: '';
  position: absolute;
  top: 0; left: 20%; right: 20%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.5), transparent);
}
```

### 3.3 主题色映射

仅使用项目 tokens.css 中已有的 token：

| 用途 | Token | 值（dark + blue accent） |
|---|---|---|
| 背景 | `--color-background` | `#10131b` |
| 文字主色 | `--color-on-surface` | `#e0e2ed` |
| 文字次色 | `--color-on-surface-variant` | `#c1c6d7` |
| 分割线 | `--color-outline-variant` | `#414755` |
| 强调色 | `--color-primary` | `#adc6ff` |
| 状态点 | `--color-primary` | `#adc6ff` |
| LIVE 点 | 固定绿 `#7ee787` | 仅 footer 一处 |

**删除原 5 类 category 色**（identity 蓝 / goal 橙 / preference 绿 / condition 红 / status 紫）—— 信息层级靠 typography 区分，不靠颜色。

### 3.4 字体

仅使用项目已有的字体，**不新增任何字体依赖**（避免影响其他功能）：

- **正文**：`Manrope`（项目 SPEC.md 已引入，CDN: `fonts.loli.net`）
- **标签/数字/版本号**：`ui-monospace, monospace`（项目已在 [DashboardPage.vue](file:///d:/Applications/Java/A - Learning/FitMate-AI-0/FitMate-frontend/src/pages/dashboard/DashboardPage.vue) 中使用，无需引入）

不修改 `fitmate-vite.html`，不添加任何字体 CDN。

### 3.5 视觉锚点图

**用途**：让玻璃折射真实图像（不是抽象色块），同时为面板增加情境感（健身主题）。

**本期方案**：
- 直接使用 Unsplash 远程图：`https://images.unsplash.com/photo-1571902943202-507ec2618e8f?w=800&q=80&auto=format&fit=crop`
- 通过常量 `PROFILE_VISUAL_URL` 在脚本顶部声明，便于后续替换
- **后续优化**（非本期）：评估改为本地静态资源或可配置头像

**图片处理**:
- `filter: brightness(0.7) saturate(0.85) hue-rotate(-5deg)` 让图融入深色界面
- 双层渐变蒙版叠加（顶到底渐隐 + 主题色光晕），让图与界面色调统一

### 3.6 动画

仅 3 处（克制原则）：

| 位置 | 动画 | 时长 |
|---|---|---|
| 玻璃卡片入场 | `opacity: 0 → 1` + `translateY(8px → 0)` | 0.8s, delay 0.2s |
| 列表行 stagger | 同上，每行 delay +0.1s | 0.5s each |
| Status 蓝点 | `opacity 0.7 ↔ 1` pulse | 2.5s 循环 |
| LIVE 绿点 | 同上 | 2s 循环 |
| 图片 hover | `scale(1.04)` | 1.5s ease（明显但不突兀） |

**移除现有 `@keyframes spin` 旋转加载图标**——loading 态改为简单文字提示。

---

## 4. 数据契约

### 4.1 输入（不变）

```ts
interface ProfileResponse {
  profileText: string | null
  profileTagsJson: string | null  // JSON.parse 后是 ProfileTag[]
  memoryVersion: number | null
  generatedAt: string | null
}

interface ProfileTag {
  label: string
  weight: number  // 0~1
  category: 'identity' | 'goal' | 'condition' | 'preference' | 'status'
}
```

### 4.2 渲染分组

按 `category` 分组为 5 行，每行内 `tag.label` 用 ` · ` 分隔：

```ts
const grouped: Record<ProfileTag['category'], string[]> = {
  identity: [],
  goal: [],
  preference: [],
  condition: [],
  status: [],
}
profileTagsJson parsed.forEach(tag => grouped[tag.category].push(tag.label))
```

### 4.3 完整度算法

基于 5 类 category 的覆盖度计算（不依赖任意基准数）：

```ts
const completeness = computed(() => {
  const filled = CATEGORY_ORDER.filter(cat => grouped.value[cat].length > 0).length
  return Math.round((filled / CATEGORY_ORDER.length) * 100)
})
```

5 类全有标签 = 100%，缺一类 -20%，最低 0%。

### 4.4 新增字段使用

之前未使用的字段现纳入展示：
- `memoryVersion` → footer 右侧 `MEM v{memoryVersion}`
- `generatedAt` → header 右侧 `{relativeTime} ago`（需前端相对时间转换）

---

## 5. 组件结构

### 5.1 文件修改

**仅修改一个文件**：`FitMate-frontend/src/pages/dashboard/components/UserProfilePanel.vue`

完整重写 `<template>`、`<style>`，扩展 `<script>` 中的数据处理逻辑（增加按 category 分组 + 相对时间计算）。

**不修改任何其他文件**（包括 `fitmate-vite.html`、tokens.css、DashboardPage.vue、memoryApi.ts、memory.ts 等）。所有需要的样式、SVG filter、字体声明都封装在 `UserProfilePanel.vue` 的 scoped style 内。

### 5.2 模板结构

```vue
<template>
  <div class="profile-panel">
    <!-- 加载/空态 -->
    <div v-if="loading" class="profile-state">加载中</div>
    <div v-else-if="!profile || !profile.profileText" class="profile-state">
      暂无画像，开始对话后自动生成
    </div>

    <!-- 正常态 -->
    <template v-else>
      <!-- 视觉锚点 + 玻璃卡 -->
      <div class="visual-anchor">
        <img class="anchor-image" :src="imageUrl" alt="profile visual" />
        <div class="anchor-tint"></div>
        <div class="glass-card">
          <div class="glass-eyebrow">AI Generated</div>
          <div class="glass-title">{{ headline }}</div>
          <div class="glass-meta">
            完整度 <strong>{{ completeness }}%</strong> · {{ tags.length }} 项特征
          </div>
        </div>
      </div>

      <!-- 列表 -->
      <div class="profile-list">
        <div v-for="cat in CATEGORY_ORDER" :key="cat" class="profile-row">
          <div class="row-label">{{ catLabel(cat) }}</div>
          <div class="row-content">
            <span v-if="cat === 'status'" class="status-dot"></span>
            {{ grouped[cat].join(' · ') || '—' }}
          </div>
        </div>
      </div>

      <!-- 文字总结 -->
      <p class="profile-summary">{{ profile.profileText }}</p>

      <!-- Footer meta -->
      <div class="footer-meta">
        <span><span class="dot-live"></span>SYNCED</span>
        <span>MEM v{{ profile.memoryVersion || '—' }}</span>
      </div>
    </template>

    <!-- SVG filter（真折射，仅 Chromium 生效） -->
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
```

### 5.3 关键脚本逻辑

```ts
const CATEGORY_ORDER = ['identity', 'goal', 'preference', 'condition', 'status'] as const

const CATEGORY_LABELS: Record<string, string> = {
  identity: 'Identity',
  goal: 'Goal',
  preference: 'Preference',
  condition: 'Condition',
  status: 'Status',
}

// 视觉锚点图 URL（便于后续替换为本地资源或可配置头像）
const PROFILE_VISUAL_URL = 'https://images.unsplash.com/photo-1571902943202-507ec2618e8f?w=800&q=80&auto=format&fit=crop'

function catLabel(cat: string): string {
  return CATEGORY_LABELS[cat] || cat
}

// computed
const grouped = computed(() => {
  const g: Record<string, string[]> = { identity: [], goal: [], preference: [], condition: [], status: [] }
  tags.value.forEach(t => g[t.category]?.push(t.label))
  return g
})

const headline = computed(() => {
  // 从 identity + goal 拼 headline，如 "城市白领程序员 · 减脂期"
  const identity = grouped.value.identity[0] || ''
  const goal = grouped.value.goal[0] || ''
  return [identity, goal].filter(Boolean).join(' · ') || '你的画像'
})

const completeness = computed(() => {
  const filled = CATEGORY_ORDER.filter(cat => grouped.value[cat].length > 0).length
  return Math.round((filled / CATEGORY_ORDER.length) * 100)
})

const relativeTime = computed(() => {
  if (!profile.value?.generatedAt) return ''
  return formatRelativeTime(profile.value.generatedAt)
})

function formatRelativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime()
  const min = Math.floor(diff / 60000)
  if (min < 60) return `${min}m ago`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr}h ago`
  return `${Math.floor(hr / 24)}d ago`
}
```

### 5.4 移除项

- `tagStyle()` 函数（5 类颜色映射）→ 删除
- `loading` 态的旋转图标 `progress_activity` → 改为文字
- 原 `.profile-tag` chip 样式 → 删除
- `@keyframes spin` → 删除

---

## 6. 兼容性

### 6.1 浏览器支持

| 特性 | Chrome/Edge | Safari | Firefox |
|---|---|---|---|
| `backdrop-filter: blur()` | ✅ | ✅ | ✅（默认开启） |
| `backdrop-filter: url(#svgFilter)` | ✅ | ❌ 降级 | ❌ 降级 |
| `feDisplacementMap` | ✅ | ✅（独立 SVG） | ✅ |

**降级行为**：Safari/Firefox 下玻璃面板自动退化为普通毛玻璃（仍有 `blur(8px) saturate(150%)`），不影响功能，仅失去折射扭曲效果。

### 6.2 性能

- `backdrop-filter` 配合 `feDisplacementMap` 在 420px × 200px 区域内性能可控
- 滚动时建议给玻璃卡片加 `will-change: backdrop-filter`（仅在 hover/focus 时优化）
- 图片懒加载：`loading="lazy"`

### 6.3 主题切换

- 当前设计基于 dark theme（项目默认）
- light theme 下玻璃面板背景调高：`rgba(255, 255, 255, 0.4)`（通过 `[data-theme="light"]` 选择器）
- 图片 filter 在 light 下移除 `brightness(0.7)`

---

## 7. 验证清单

实施完成后需验证：

- [ ] Dashboard 右栏宽度 420px 下视觉正常
- [ ] 移动端（< 900px，参照 DashboardPage 现有断点）布局自适应：
  - 右栏宽度变为 100%
  - 图片高度不变（200px）
  - grid 列表标签列宽度从 96px → 80px
  - 字号保持，仅 padding 收紧
- [ ] Chrome 折射效果生效，Safari 降级正常
- [ ] light/dark 主题切换正常
- [ ] 加载/空态展示正确
- [ ] 数据接口字段映射正确（`profileTagsJson` 解析、`generatedAt` 相对时间）
- [ ] 标签为空的 category 显示 `—`
- [ ] SVG filter ID 在多实例下唯一（若 Dashboard 同时渲染多个面板需注意）

---

## 8. 后续可选增强（非本期）

- 鼠标移动时玻璃折射强度变化（鼠标视差）
- 标签点击跳转到对话上下文
- 图片改为用户上传或 AI 生成
- 引入 `motion-v` 做更顺滑的 stagger
- 玻璃面板可拖拽/可缩放

---

## 9. 关键文件清单

| 用途 | 路径 |
|---|---|
| **修改目标**（仅此一个） | [FitMate-frontend/src/pages/dashboard/components/UserProfilePanel.vue](file:///d:/Applications/Java/A - Learning/FitMate-AI-0/FitMate-frontend/src/pages/dashboard/components/UserProfilePanel.vue) |
| 挂载页面（参考不修改） | [FitMate-frontend/src/pages/dashboard/DashboardPage.vue](file:///d:/Applications/Java/A - Learning/FitMate-AI-0/FitMate-frontend/src/pages/dashboard/DashboardPage.vue) |
| 数据类型（参考不修改） | [FitMate-frontend/src/types/memory.ts](file:///d:/Applications/Java/A - Learning/FitMate-AI-0/FitMate-frontend/src/types/memory.ts) |
| API 服务（参考不修改） | [FitMate-frontend/src/services/memoryApi.ts](file:///d:/Applications/Java/A - Learning/FitMate-AI-0/FitMate-frontend/src/services/memoryApi.ts) |
| 主题 token（参考已存在） | [FitMate-frontend/src/styles/tokens.css](file:///d:/Applications/Java/A - Learning/FitMate-AI-0/FitMate-frontend/src/styles/tokens.css) |
