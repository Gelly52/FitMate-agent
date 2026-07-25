---
name: pixel-art-ui
description: Retro-gaming (8-bit/16-bit) pixel art design system for web UI — stack-agnostic (reference snippets use React + Tailwind; translate to the project's framework). Use when the user asks for pixel art, retro, or 8-bit style — whether building new pages from scratch or restyling an existing frontend. Covers palette, typography, components, animation, layout.
---

# Pixel Art UI Design System

## Rule zero: user first

Priority order: **explicit user requirements > project's existing constraints (stack, theme system, accessibility) > this skill's defaults.**
If the user wants something that breaks pixel style (e.g. rounded corners), follow the user; note the deviation once at most. Never push the style back.

## Negotiable vs not

- **Invariants** (what makes it pixel art): hard edges, offset shadows without blur, flat colors without gradients, discrete pixel-grid values.
- **Defaults** (user may override): the 7 palette hex values, border weights, font pairing, page background.
- **Examples** (translate freely): all React / Tailwind / Framer Motion snippets in the reference files — keep the numbers, rewrite the code for the project's stack.

## Fit

Good fit: dashboards, tools, chat UIs, landing pages, games.
Poor fit: dense data tables and long-form reading (pixel fonts cost readability) — a hybrid is fine: pixel shell + conventional content area.

## Minimum viable kit

Palette + pixel font + border/shadow rules + Button + Card already reads as pixel art. Pull in layout architecture, animation, and other components only when the task needs them — don't impose the full system on a small request.

## Workflow

Building from scratch:
1. Confirm with the user: scope, stack, light/dark, which pages or components.
2. Set up tokens and fonts (`templates/`, `01-foundation.skill.md`).
3. Build primitives first: Button, Card, Input (`02-components.skill.md`).
4. Assemble layout (`04-layout-responsive.skill.md`), then pages.

Restyling an existing app:
1. Inventory the existing token/theme system; don't break its API.
2. Map existing semantic tokens → pixel palette, keeping variable names.
3. Convert the foundation (tokens, fonts, radius, shadows), then components.
4. Touch styles only — preserve all logic, bindings, and toggled class names.

## Reference files

| Task | Read |
|------|------|
| Colors, fonts, design tokens | `01-foundation.skill.md` |
| UI primitives (Button, Card, Dialog, Input) | `02-components.skill.md` |
| Motion, transitions, keyframes | `03-animation.skill.md` |
| Page structure, breakpoints, navigation | `04-layout-responsive.skill.md` |

## Acceptance checklist

- No border-radius anywhere (unless the user asked for it)
- No blurred shadows — hard offsets only; no gradients on UI surfaces
- Text contrast ≥ 4.5:1 (black text on yellow; on dark backgrounds, lighten palette hues for text)
- Every interactive element has visible hover and press states; touch targets ≥ 44px
- Spacing and sizes stay on the pixel grid (2/4/8px steps)
- Icon fonts (e.g. Material Symbols) keep their own font-family — never overridden by the pixel font
