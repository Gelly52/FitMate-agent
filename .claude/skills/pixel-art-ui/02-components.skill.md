# Component Design Patterns

All components follow a shared visual contract: thick black borders, hard offset shadows, flat colors, pixel font, and motion feedback via Framer Motion.

---

## Shared Conventions

### Border Rules

| Weight | Usage |
|--------|-------|
| 4px solid `pixel-black` | Primary containers (cards, dialogs, panels, buttons) |
| 3px solid `pixel-black` | Medium containers (stat cards, action tiles) |
| 2px solid `pixel-black` | Secondary elements (icon boxes, badges, inline controls) |
| 2px solid `pixel-black/10` | Subtle dividers between list items |

All borders are straight (zero border-radius).

### Shadow Scale

| Level | Value | Usage |
|-------|-------|-------|
| sm | `2px 2px 0px 0px #101010` | Small badges, inline buttons |
| md | `3px 3px 0px 0px #101010` | Stat cards, medium panels |
| default | `4px 4px 0px 0px #101010` | Standard buttons, cards |
| lg | `6px 6px 0px 0px #101010` | Large cards, primary panels |
| xl | `8px 8px 0px 0px #101010` | Dialogs, hero elements |
| inset | `inset 2px 2px 0px 0px #101010` | Input fields |
| disabled | `2px 2px 0px 0px #666` | Disabled buttons |

Shadows never use blur. They only use offset to simulate pixel-art depth.

### Disabled State

- `opacity: 0.5`
- `cursor: not-allowed`
- Shadow degrades to `2px 2px 0px 0px #666`

---

## Button

### Anatomy

```
[motion.button]
  font-pixel
  border-4 border-pixel-black
  variant background + text color
  size padding
  box-shadow: 4px 4px 0px 0px #101010
```

### Variants

| Variant | Background | Text | Hover |
|---------|-----------|------|-------|
| primary | `pixel-red` | `pixel-white` | shifts toward orange/lighter |
| secondary | `pixel-yellow` | `pixel-black` | shifts toward orange |
| danger | `pixel-black` | `pixel-white` | shifts toward red |

### Sizes

| Size | Padding | Font size |
|------|---------|----------|
| sm | `px-3 py-1` | text-sm |
| md | `px-4 py-2` | text-base |
| lg | `px-6 py-3` | text-lg |

### Motion

- **Hover**: `scale: 1.05` (disabled: no scale)
- **Tap/Active**: `scale: 0.95` + CSS `top: 2px; left: 2px` (simulates press-down)
- **Transition**: `transition-colors duration-100`

### Props Interface

```typescript
interface ButtonProps {
  children: ReactNode;
  onClick?: () => void;
  variant?: 'primary' | 'secondary' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  disabled?: boolean;
  className?: string;
  type?: 'button' | 'submit' | 'reset';
  title?: string;
}
```

---

## Card

### Anatomy

```
[motion.div]
  bg-pixel-white
  border-4 border-pixel-black
  box-shadow: 6px 6px 0px 0px #101010
  |
  +-- [optional] Title Bar
  |     bg-pixel-blue text-pixel-white
  |     p-2 border-b-4 border-pixel-black
  |
  +-- Content Area
        p-4 flex-1 flex flex-col min-h-0
```

### Title Bar Colors

The title bar color can vary by context:
- Information/default: `pixel-blue`
- Success/dialog: `pixel-green`
- Warning: `pixel-yellow`
- Error/critical: `pixel-red`

### Motion

- **Hover** (if hoverable): `scale: 1.02`
- Cards with links may use: `whileHover={{ y: -4, x: 2 }}`

### Props Interface

```typescript
interface CardProps {
  children: ReactNode;
  title?: string;
  onClick?: () => void;
  className?: string;
  hoverable?: boolean;
}
```

---

## Dialog (Modal)

### Anatomy

```
[AnimatePresence]
  [ModalPortal -> document.body]
    [Backdrop] fixed inset-0 bg-pixel-black/70
      [Dialog Container]
        border-4 border-pixel-black
        bg-pixel-white
        box-shadow: 8px 8px 0px 0px #101010
        max-w-lg w-full
        |
        +-- Title Bar
        |     bg-pixel-green text-pixel-white
        |     p-3 border-b-4 border-pixel-black
        |     flex justify-between items-center
        |     |
        |     +-- Title text (font-pixel text-xl)
        |     +-- Close button [X] (bg-pixel-red, 32x32px)
        |
        +-- Content Area
              p-4 font-pixel text-pixel-black
```

### Animation

- **Enter**: `scale: 0.8 -> 1`, `y: 20 -> 0`, `opacity: 0 -> 1`
- **Exit**: reverse of enter
- **Content**: delayed render by ~100ms (typewriter reveal feel)
- **Backdrop click**: closes dialog
- **Inner click**: `stopPropagation` prevents backdrop dismiss

### Close Button Pattern

A square button in the title bar:
- Size: `w-8 h-8`
- Style: `bg-pixel-red text-pixel-white border-2 border-pixel-black`
- Shadow: `2px 2px 0px 0px #101010`
- Content: the character "X"

### Props Interface

```typescript
interface DialogProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  children: ReactNode;
}
```

---

## Input

### Anatomy

```
[input / textarea]
  w-full
  bg-pixel-white
  border-4 border-pixel-black
  font-pixel text-pixel-black
  px-4 py-2
  box-shadow: inset 2px 2px 0px 0px #101010
```

### States

| State | Style |
|-------|-------|
| Default | `border-pixel-black` |
| Focus | `border-pixel-blue` (no outline ring, only border color change) |
| Disabled | `opacity-50 cursor-not-allowed` |
| Placeholder | `text-pixel-black/50` |

### Multiline Variant

- Default: `min-h-[96px] resize-y`
- Compact: `min-h-0 resize-none` (for inline chat-style inputs)

### Props Interface

```typescript
interface InputProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  disabled?: boolean;
  multiline?: boolean;
  rows?: number;
  type?: 'text' | 'password' | 'email' | 'url' | 'date';
}
```

---

## Panel (Section Container)

### Standard Panel

```
[section]
  overflow-hidden
  border-4 border-pixel-black
  bg-pixel-white
  box-shadow: 5px 5px 0 #101010
  |
  +-- Header strip (tone-colored, border-b-4)
  +-- Content (list items or grid)
```

### Panel Header

- Full-width strip with tone background color
- Contains: title (font-pixel bold) + optional action link
- Border: `border-b-4 border-pixel-black`

---

## Stat Card

### Anatomy

```
[motion.div]
  border-[3px] border-pixel-black
  border-t-[5px] {tone border color}  // colored top accent
  bg-pixel-white p-3
  box-shadow: 3px 3px 0px 0px #101010
  |
  +-- Label (text-xs uppercase, muted)
  +-- Value (text-3xl font-bold)
  +-- Note  (text-xs, tone text color)
```

---

## Badge / Tag

### Standard Badge

```
[span]
  border-2 border-pixel-black
  bg-{tone-color}
  px-2 py-0.5
  font-pixel text-xs
  text-pixel-white (or pixel-black for yellow)
```

Badges on yellow background use `text-pixel-black` for contrast.

---

## Delete Button (Contextual)

A pattern for destructive actions on list items:

- Hidden by default: `opacity-0 pointer-events-none`
- Shown on parent hover: `group-hover:opacity-100 group-hover:pointer-events-auto`
- Position: absolute, top-left corner of parent
- Style: `bg-pixel-red text-pixel-white border-2 border-pixel-black` with "X" character
- Shadow: `2px 2px 0 #101010`
- Size: `h-7 w-7` (normal) or `h-8 w-8` (desktop)

---

## Icon Containers

Icons are always wrapped in a colored square:

```
[span]
  flex items-center justify-center
  h-8 w-8 (or h-10 w-10, h-11 w-11)
  border-2 border-pixel-black
  bg-{tone-color}
  text-pixel-white (or pixel-black for yellow)
```

This creates the "pixel tile" look for navigation and action items.
