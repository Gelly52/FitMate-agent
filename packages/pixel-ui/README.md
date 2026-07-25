# @fitmate/pixel-ui

FitMate 像素风设计系统共享包。Web / 桌面 / 移动三端共用，保证视觉一致。

设计规范源头：`.claude/skills/pixel-art-ui/`（7 色调色板、硬边框、偏移阴影、VT323 + ZCOOL KuaiLe 像素字体）。

## 内容

| 文件 | 用途 |
|------|------|
| `tokens.css` | 语义 CSS 变量（兼容原 MD3 变量名）→ 像素调色板映射，含深/浅主题与强调色 |
| `base.css` | 全局像素基础样式：body/滚动条/选区/焦点 + `.pixel-btn` `.pixel-card` `.pixel-panel` `.pixel-input` `.pixel-press` |
| `tailwind-preset.js` | Tailwind preset：像素色、零圆角、`shadow-pixel*` 硬阴影、`border-3/5/6`、`font-pixel`、像素动画 |
| `assets/logo.svg` | F + AI 火花品牌标（`currentColor` 跟随主题） |
| `assets/logo-tile.svg` | 蓝底方形徽标（favicon / 应用图标用） |

## 接入方式

```jsonc
// package.json
"dependencies": { "@fitmate/pixel-ui": "file:../packages/pixel-ui" }
```

```js
// tailwind.config.js
import pixelPreset from "@fitmate/pixel-ui/tailwind-preset";
export default {
  presets: [pixelPreset],
  content: ["./src/**/*.{vue,ts}"],
  // 只放应用特有的 theme 扩展
};
```

```css
/* 应用样式入口，@tailwind 指令之前 */
@import "@fitmate/pixel-ui/tokens.css";
@import "@fitmate/pixel-ui/base.css";
```

```html
<!-- 字体（HTML head） -->
<link href="https://fonts.loli.net/css2?family=VT323&display=swap" rel="stylesheet" />
<link href="https://fonts.loli.net/css2?family=ZCOOL+KuaiLe&display=swap" rel="stylesheet" />
```

主题切换：`<html data-theme="dark|light" data-accent="blue|green|orange|purple|light|dark">`。
注意调色板限制：`orange` 实际渲染为像素黄，`purple` 实际渲染为像素红（UI 文案应显示"黄/红"）。

## 待办

- [ ] 字体本地化打包（桌面端离线场景，CJK 子集体积需评估）
- [ ] 抽取 Vue 组件层（PixelButton / PixelCard / PixelDialog / PixelSwitch）
