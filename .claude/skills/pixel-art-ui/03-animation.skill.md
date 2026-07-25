# Animation and Motion Design

Motion in a pixel art UI must feel snappy and game-like. Animations use discrete positional changes, spring physics for bounce, and short durations that echo the responsiveness of retro game interfaces.

---

## Motion Library

Framer Motion is the primary animation layer. All interactive elements use `motion.*` wrappers.

```typescript
import { motion, AnimatePresence } from 'framer-motion';
```

---

## Interaction Feedback

### Button/Clickable Hover

```typescript
// Standard button
whileHover={{ scale: 1.05 }}
whileTap={{ scale: 0.95 }}

// Subtle directional hint (sidebar toggle, arrows)
whileHover={{ x: 2 }}  // or y: -1
whileTap={{ x: 0, scale: 0.96 }}

// Card with lift effect
whileHover={{ y: -4, x: 2 }}
whileTap={{ y: 1, scale: 0.99 }}
```

### CSS Active State (supplement to motion)

For pixel-perfect press simulation, add CSS:

```css
.pixel-button:active {
  position: relative;
  top: 2px;
  left: 2px;
}
```

This creates the illusion that the button "sinks" into the shadow.

---

## Page & Element Transitions

### Enter Animation (Standard)

```typescript
initial={{ opacity: 0, y: 18 }}
animate={{ opacity: 1, y: 0 }}
transition={{ duration: 0.28 }}
```

### Enter Animation (Hero/Full-page)

```typescript
initial={{ opacity: 0, y: 18, scale: 0.985 }}
animate={{ opacity: 1, y: 0, scale: 1 }}
transition={{ duration: 0.28, delay: 0.25 }}
```

### Exit Animation

```typescript
exit={{ opacity: 0, y: -18, scale: 0.985 }}
transition={{ duration: 0.28 }}
```

### Slide In (Sidebar/Panel)

```typescript
initial={{ opacity: 0, x: -16 }}
animate={{ opacity: 1, x: 0 }}
transition={{ delay: 0.035 * index }}  // stagger
```

### Spring Entry (Logo/Mascot)

```typescript
initial={{ scale: 0, rotate: -180 }}
animate={{ scale: 1, rotate: 0 }}
transition={{
  type: 'spring',
  stiffness: 100,
  damping: 15,
  delay: 0.15,
}}
```

---

## Stagger Patterns

For lists of items, apply incremental delay:

```typescript
// Navigation items
transition={{ delay: 0.035 * index }}

// Grid cards
transition={{ delay: 0.04 * index }}

// Heavier items (project cards)
transition={{ delay: 0.06 * index }}
```

---

## Looping Animations

### Floating (idle state, mascots)

```typescript
animate={{ y: [0, -12, 0] }}
transition={{ duration: 2.5, repeat: Infinity, ease: 'easeInOut' }}
```

### Pulse/Breathe (online indicators, profile images)

```typescript
animate={{ scale: [1, 1.075, 1] }}
transition={{ duration: 2.4, repeat: Infinity, ease: 'easeInOut' }}
```

### Blinking Text (call-to-action)

```typescript
animate={{ opacity: [1, 0.5, 1] }}
transition={{ duration: 1.2, repeat: Infinity }}
```

### Network Node Pulse

```typescript
animate={{ opacity: [0.4, 1, 0.4], scale: [1, 1.3, 1] }}
transition={{ duration: 2 + Math.random() * 2, repeat: Infinity, ease: 'easeInOut' }}
```

---

## CSS Keyframe Animations

For non-interactive decorative effects, CSS animations are lighter weight:

### Pixel Bounce

```css
@keyframes bounce-pixel {
  0%, 100% { transform: translateY(0); }
  50%      { transform: translateY(-8px); }
}

.animate-bounce-pixel {
  animation: bounce-pixel 1s ease-in-out infinite;
}
```

### Float

```css
@keyframes float {
  0%, 100% { transform: translateY(0); }
  50%      { transform: translateY(-4px); }
}

.animate-float {
  animation: float 3s ease-in-out infinite;
}
```

### Shake (error/attention)

```css
@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25%      { transform: translateX(-2px); }
  75%      { transform: translateX(2px); }
}

.animate-shake {
  animation: shake 0.3s ease-in-out infinite;
}
```

### Typewriter Cursor

```css
@keyframes typewriter {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0; }
}

.animate-typewriter {
  animation: typewriter 0.5s steps(1) infinite;
}
```

---

## AnimatePresence Usage

For mount/unmount transitions (modals, page switches, conditional content):

```tsx
<AnimatePresence mode="wait" initial={false}>
  {condition && (
    <motion.div
      key="unique-key"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -20 }}
    >
      {content}
    </motion.div>
  )}
</AnimatePresence>
```

Use `mode="wait"` when switching between mutually exclusive views.

---

## Motion Value Guidelines

| Property | Allowed Values |
|----------|---------------|
| translateX | -2, 0, 2, -8, -16 px |
| translateY | -4, -8, -12, -18, -20, 0, 1, 18, 20 px |
| scale | 0.95, 0.96, 0.99, 1, 1.02, 1.05, 1.075, 1.3 |
| opacity | 0, 0.2, 0.4, 0.5, 0.6, 0.7, 1 |
| rotate | -180, 0 (for spring entry only) |

All motion values should feel "quantized" - prefer integers and simple fractions over smooth gradients.

---

## Duration & Easing Reference

| Context | Duration | Easing |
|---------|----------|--------|
| UI interaction (hover, tap) | 100-150ms | default (Framer) |
| Element entry | 280ms | default or easeOut |
| Page transition | 280-400ms | default |
| Content reveal delay | 100ms | - |
| Spring bounce | type: spring | stiffness: 100, damping: 15 |
| Decorative loop | 2-3s | easeInOut |
| Slow ambient | 4-10s | easeInOut |
| Stagger per item | 35-60ms | - |

---

## Delayed Content Pattern

For dialog or panel content, delay rendering by 100ms after the container animates in. This creates a typewriter-like progressive reveal:

```typescript
const [showContent, setShowContent] = useState(false);

useEffect(() => {
  if (isOpen) {
    const timer = setTimeout(() => setShowContent(true), 100);
    return () => clearTimeout(timer);
  } else {
    setShowContent(false);
  }
}, [isOpen]);
```

---

## Decorative Backgrounds

For hero sections or landing pages, animated network-graph backgrounds add visual interest:

- Random nodes with pulsing opacity and scale
- SVG lines connecting nodes with animated opacity
- Floating particles with slow drift (4-8s cycles)
- Grid pattern overlay at very low opacity (3-5%)

These should always be wrapped in `pointer-events-none` and use `position: absolute; inset: 0`.
