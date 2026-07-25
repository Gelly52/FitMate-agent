# Pixel Art UI Design System

A retro-gaming inspired frontend design system built on pixel aesthetics. This skill set provides a complete methodology for building modern web applications with an 8-bit visual language.

## Skill Files

| File | Scope |
|------|-------|
| `01-foundation.skill.md` | Design philosophy, color palette, typography system |
| `02-components.skill.md` | Reusable component patterns (Button, Card, Dialog, Input) |
| `03-animation.skill.md` | Motion design, transitions, keyframe animations |
| `04-layout-responsive.skill.md` | Page structure, responsive breakpoints, navigation |

## Templates

| File | Purpose |
|------|--------|
| `templates/tailwind.config.ts` | Tailwind CSS configuration with pixel tokens |
| `templates/globals.css` | Global stylesheet with base styles and utilities |

## Tech Stack

- **Framework**: Next.js (App Router) + React + TypeScript
- **Styling**: Tailwind CSS + CSS Custom Properties
- **Motion**: Framer Motion
- **State**: Zustand
- **Fonts**: Monospace pixel font (VT323) + CJK pixel font (ZCOOL KuaiLe)

## Design Principles

1. **Hard Edges Only** - No border-radius, no blur shadows, no gradients on UI elements
2. **Discrete Values** - Spacing, sizing, and motion in pixel-grid increments (2, 4, 6, 8px)
3. **High Contrast Palette** - Limited 7-color system with strong black borders
4. **Tactile Feedback** - Every interactive element has visible press/hover state
5. **Pixelated Media** - All images rendered with `image-rendering: pixelated`

## Quick Start

When applying this design system to a new project:

1. Copy `templates/tailwind.config.ts` and `templates/globals.css` into the project
2. Install required fonts (VT323 via Google Fonts, ZCOOL KuaiLe for CJK support)
3. Install `framer-motion` for animation layer
4. Follow component patterns in `02-components.skill.md` for UI primitives
5. Apply layout patterns from `04-layout-responsive.skill.md`
