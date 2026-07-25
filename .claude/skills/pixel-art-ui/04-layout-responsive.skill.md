# Layout and Responsive Design

---

## Page Architecture

### Overall Structure

```
<html lang="...">
  <body class="min-h-screen">
    <ClientLayout>
      <Header />          <!-- Desktop only -->
      <main>              <!-- Primary content area -->
        {children}
      </main>
      <Footer />          <!-- Desktop only -->
      <MobileNav />       <!-- Mobile only, fixed bottom -->
    </ClientLayout>
  </body>
</html>
```

The layout wrapper handles:
- Responsive mode detection
- Route-based layout variations
- Authentication guards
- Display mode state (professional vs traditional, normal vs care)

---

## Desktop Layouts

### Professional Mode (Centered)

```css
main {
  max-width: 80rem; /* 1280px, equiv. to max-w-7xl */
  margin: 0 auto;
  padding: 1rem;
  background: pixel-white;
  min-height: calc(100vh - 120px); /* subtract header + footer */
}
```

Content is centered with a max-width constraint. Grid layouts within use `md:grid-cols-2`.

### Traditional Mode (Sidebar + Full-width)

A persistent left sidebar with adjustable width:

```
+------------------+-------------------------------------------+
| Sidebar          | Main Content                              |
| (236-420px)      | (full remaining width)                    |
| fixed, h-screen  | padding-left: sidebar-width + gap         |
+------------------+-------------------------------------------+
```

**Sidebar specifications:**
- Default width: 292px
- Min width: 236px
- Max width: 420px
- Gap between sidebar and content: 10px
- Background: `pixel-black`
- Border: `border-r-4 border-pixel-black`
- Shadow: `6px 0 0 #101010` (right shadow)
- Resizable via drag handle on right edge
- Collapsible (slides left with opacity transition)

**Sidebar sections:**
1. Brand header (logo + app name + collapse button)
2. Navigation links (icon tile + label)
3. Recent items section (with separator line)
4. Footer with version info

**Toggle button (collapsed state):**
- Fixed to left edge
- `h-16 w-9`
- `bg-pixel-yellow text-pixel-black`
- Arrow icon pointing right
- Hover: `bg-pixel-green`

---

## Mobile Layout

### Structure

No header or footer. Navigation is a fixed bottom tab bar.

```
+-------------------------------------------+
| Sticky Section Header                     |
| (title, stats, icon)                      |
+-------------------------------------------+
| Content Panels                            |
| (stacked sections with borders)           |
|                                           |
+-------------------------------------------+
| Bottom Tab Bar (fixed)                    |
| [Tab] [Tab] [Tab] [Tab] [Tab]            |
+-------------------------------------------+
```

### Bottom Tab Bar

```css
[data-mobile-app-nav] {
  min-height: 62px;
  position: fixed;
  left: 0; right: 0; bottom: 0;
  z-index: 40;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  border-top: 4px solid pixel-black;
  background: pixel-white;
  box-shadow: 0 -4px 0 0 pixel-black;
}
```

Each tab item:
- Flex column, centered
- Min-height: 62px (care mode: 104px)
- Icon: 28x28 container (care mode: clamp 48-62px)
- Label: 0.66rem, single line, centered
- Separator: `border-right: 2px solid pixel-black/10`
- Active state: tone-colored icon or background

### Content Padding

Content must have bottom padding to clear the fixed nav:
- Normal: `pb-24` (~6rem)
- Care mode: `pb-36` (~9rem)

---

## Header

### Desktop Header

```css
header {
  background: pixel-black;
  border-bottom: 4px solid pixel-white; /* or pixel-red for accent */
  padding: 0.75rem 1rem;
}
```

**Layout:**
```
[Logo + Brand]                    [Actions: toggle, auth buttons]
```

**Brand section:**
- Logo: `h-12 w-12` square, `bg-pixel-red border-4 border-pixel-white`
- Title: `chinese-large` for CJK name + `font-pixel text-xl` for English subtitle

**Action buttons:**
- Display mode toggle: `h-10 w-10` icon button
- Auth links: colored inline buttons with shadow
- User menu: avatar initial + username dropdown

---

## Footer

```css
footer {
  background: pixel-black;
  border-top: 4px solid pixel-red;
  padding: 1rem 0;
  text-align: center;
}
```

Content: centered text in `font-pixel text-xs text-pixel-white`.

---

## Grid Systems

### Action Grid (Desktop Home)

```css
/* 4-column stats */
grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4

/* 2-column action groups */
grid grid-cols-2 gap-4

/* Mixed content (sidebar + main) */
grid xl:grid-cols-[minmax(330px,0.72fr)_minmax(0,1.28fr)] gap-5
```

### Card Grid (Projects/Agents)

```css
/* 2 columns for cards */
grid grid-cols-1 xl:grid-cols-2 gap-3

/* 3 columns for compact cards */
grid grid-cols-3 gap-2
```

### Mobile List

Mobile uses full-width stacked items:
- Each item: `flex items-center min-h-[72px] gap-2 px-3 py-2.5`
- Care mode: `min-h-[116px] gap-3 px-4 py-4`
- Separator: `border-b-2 border-pixel-black/10`

---

## Responsive Breakpoints

| Breakpoint | Width | Purpose |
|-----------|-------|--------|
| (default) | < 768px | Mobile layout, no header/footer |
| md | >= 768px | Desktop layout activates |
| lg | >= 1024px | Sidebar content expands |
| xl | >= 1280px | Multi-column grids, wider sidebar content |
| 2xl | >= 1536px | Maximum spacing, ultrawide optimization |

### Key Responsive Patterns

```css
/* Hide on mobile, show on desktop */
.desktop-only { display: none; }
@media (min-width: 768px) { .desktop-only { display: block; } }

/* Hide on desktop, show on mobile */
.mobile-only { }
@media (min-width: 768px) { .mobile-only { display: none; } }
```

With Tailwind: `hidden md:block` and `md:hidden`.

---

## Navigation Patterns

### Sidebar Navigation Item

```
[Link]
  flex items-center min-h-[56px] gap-3
  border-2 px-3
  font-pixel text-base
  |
  +-- Icon Tile (h-8 w-8, tone-colored, border-2)
  +-- Label (truncate)
```

**States:**
- Default: `border-transparent text-pixel-white/72`
- Hover: `border-pixel-gray bg-pixel-white/10 text-pixel-white`
- Active: `border-pixel-gray bg-pixel-white/10 text-pixel-white`

### Mobile List Row

```
[Link]
  flex items-center min-h-[72px] gap-2 px-3 py-2.5
  border-b-2 border-pixel-black/10
  bg-pixel-white
  active:bg-pixel-yellow/40
  |
  +-- Icon Tile (colored square, optional badge)
  +-- Text Column (title bold + description muted)
  +-- Arrow indicator (border-2 square with ">>")
```

---

## Spacing Reference

| Usage | Value |
|-------|-------|
| Card internal padding | p-3 to p-4 (12-16px) |
| Section gap | space-y-4 to space-y-6 (16-24px) |
| Grid gap | gap-2 to gap-5 (8-20px) |
| Page padding | p-4 (mobile) to px-8 py-6 (desktop traditional) |
| Minimum touch target | 44px (normal), 52-64px (care mode) |
| Border width standard | 4px |
| Shadow offset standard | 4px (matches border) |

---

## Scrollbar

Custom scrollbar matching the pixel aesthetic:

```css
::-webkit-scrollbar {
  width: 14px;
}

::-webkit-scrollbar-track {
  background: #D8D8D8;
  border: 2px solid #101010;
}

::-webkit-scrollbar-thumb {
  background: #6B6B6B;
  border: 2px solid #101010;
}

::-webkit-scrollbar-thumb:hover {
  background: #3A5BA0;
}
```

---

## Focus Management

```css
*:focus {
  outline: 3px solid #3A5BA0;
  outline-offset: 2px;
}
```

No focus rings on mouse interaction (consider `:focus-visible` for keyboard-only).

---

## Touch Interaction (Mobile)

```css
button, a, input, textarea, select {
  -webkit-tap-highlight-color: rgba(212, 165, 51, 0.35);
}

input, textarea, select {
  min-height: 44px;
  font-size: 15px;
}

button {
  min-height: 44px;
}
```

All interactive elements must meet 44px minimum touch target. Care mode increases to 52-64px.

---

## Data Attributes for Context

Use data attributes to enable CSS-only conditional styling:

| Attribute | Purpose |
|-----------|--------|
| `data-app-main` | Main content area identifier |
| `data-mobile-app-nav` | Mobile bottom navigation |
| `data-mobile-display-mode="care"` | Large text accessibility mode |
| `data-traditional-desktop-mode` | Full-width sidebar layout active |
| `data-project-workspace-page` | White background override for workspace |

This avoids JavaScript-driven style changes for layout-level concerns.
