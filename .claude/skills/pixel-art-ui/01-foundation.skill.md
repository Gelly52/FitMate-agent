# Foundation: Design Philosophy, Color, and Typography

## Design Philosophy

This design system draws from 8-bit and 16-bit era gaming interfaces. The visual language communicates "lo-fi precision" - every element appears hand-crafted at the pixel level while remaining fully functional as a modern web application.

Core tenets:

- All visual elements respect a pixel grid (no sub-pixel rendering)
- UI surfaces use flat solid colors (no gradients, no glassmorphism)
- Borders are hard, thick, and black (never subtle or rounded)
- Shadows simulate depth through offset only (never blurred)
- Typography uses monospace pixel fonts exclusively
- Interactive elements provide exaggerated tactile feedback

---

## Color System

### Palette Definition

The system uses exactly 7 named colors. All UI is composed from these colors plus opacity variants.

```css
:root {
  --pixel-black:  #101010;
  --pixel-white:  #F8F8F8;
  --pixel-gray:   #6B6B6B;
  --pixel-blue:   #3A5BA0;
  --pixel-green:  #2D7D46;
  --pixel-yellow: #D4A533;
  --pixel-red:    #A83232;
}
```

### Semantic Roles

| Color | Primary Role | Secondary Role |
|-------|-------------|----------------|
| black | Text, borders, hard shadows | Dark backgrounds |
| white | Card backgrounds, light text on dark | Page background variant |
| gray | Disabled states, secondary text | Dividers, scrollbar |
| blue | Primary actions, links, info states | Focus indicators |
| green | Success, online status, confirm | Dialog title bars |
| yellow | Warnings, highlights, badges | Hover accent |
| red | Danger, brand accent, delete | Header accent border |

### Background Hierarchy

| Surface | Color |
|---------|-------|
| Page (desktop) | `#E8E8E8` |
| Page (mobile) | `#F8F8F8` |
| Card / Panel | `pixel-white` (#F8F8F8) |
| Header / Footer | `pixel-black` (#101010) |
| Overlay backdrop | `pixel-black` at 70% opacity |

### Text Color Rules

| Context | Value |
|---------|-------|
| Primary text | `pixel-black` |
| Secondary text | `pixel-black` at 60% opacity |
| Placeholder / weak text | `pixel-black` at 50% opacity |
| Metadata / eyebrow | `pixel-black` at 45% opacity |
| Text on dark background | `pixel-white` |
| Subdued text on dark | `pixel-white` at 65-72% opacity |

### Opacity Conventions

When using transparency, stick to these values:
- 70% (backdrop overlays)
- 65% (secondary text on dark)
- 60% (secondary text on light)
- 50% (placeholder, disabled)
- 45% (metadata)
- 35% (tap highlight)
- 10% (subtle dividers, hover backgrounds)

### Tone System

For content blocks that need color coding (stats, actions, categories), use a tone object:

```typescript
type Tone = 'green' | 'blue' | 'yellow' | 'red' | 'gray';

interface ToneStyle {
  bg: string;      // e.g., 'bg-pixel-green'
  border: string;  // e.g., 'border-t-pixel-green'
  text: string;    // e.g., 'text-pixel-green'
}
```

This ensures consistent color application across cards, badges, and section headers.

---

## Typography

### Font Stack

```css
--font-main: 'VT323', 'ZCOOL KuaiLe', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
```

- **VT323**: Primary pixel font for Latin characters (Google Fonts)
- **ZCOOL KuaiLe**: CJK pixel-style font for Chinese/Japanese/Korean
- Monospace fallback chain for environments where custom fonts fail

### Font Classes

| Class | Purpose | Styles |
|-------|---------|--------|
| `font-pixel` | All UI text | `font-family: var(--font-main); letter-spacing: 0` |
| `chinese-large` | CJK headlines | `font-family: var(--font-main); font-size: 1.8em; line-height: 1.08` |

### Size Scale (Desktop)

| Token | Size | Usage |
|-------|------|-------|
| text-xs | 14px | Metadata, timestamps, badges |
| text-sm | 16px | Secondary descriptions, labels |
| text-base | 18px | Body text, button text |
| text-lg | 20px | Card titles, section headers |
| text-xl | 24px | Dialog titles, page subtitles |
| text-2xl | 28px | Section headlines |
| text-3xl | 32px | Stat values, large numbers |
| text-4xl | 40px | Hero text, page titles |

Body defaults: `font-size: 20px; line-height: 1.4`

### Size Scale (Mobile, max-width: 767px)

All sizes decrease by 2-4px. Body reduces to `font-size: 16px; line-height: 1.35`.

| Token | Desktop | Mobile |
|-------|---------|--------|
| text-xs | 14px | 12px |
| text-sm | 16px | 14px |
| text-base | 18px | 15px |
| text-lg | 20px | 16px |
| text-xl | 24px | 18px |
| text-2xl | 28px | 22px |
| text-3xl | 32px | 26px |
| text-4xl | 40px | 32px |

### Accessibility Mode (Care Mode)

An optional large-text mode where all sizes scale up 40-60%. Activated via `data-*` attribute on `<html>`. Minimum touch targets increase from 44px to 52-64px.

---

## Global Rendering

### Pixelated Images

Applied globally to all elements:

```css
* {
  image-rendering: pixelated;
}
```

This ensures all images, icons, and canvas elements render with nearest-neighbor scaling.

### Selection Style

```css
::selection {
  background-color: var(--pixel-blue);
  color: var(--pixel-white);
}
```

### Base Body Style

```css
body {
  font-family: var(--font-main);
  font-size: 20px;
  line-height: 1.4;
  background-color: #E8E8E8;
  color: #101010;
  overflow-x: hidden;
}
```
