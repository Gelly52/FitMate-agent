<!-- FitMate-frontend/src/pages/settings/components/AppearanceSection.vue -->
<template>
  <div>
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
        blue: "#3A5BA0",
        green: "#2D7D46",
        orange: "#D4A533",
        purple: "#A83232",
        light: "#E8E8E8",
        dark: "#101010",
      };
      return map[accent];
    },
  },
};
</script>

<style scoped>
.settings-card {
  background: var(--color-surface-container);
  border: 4px solid var(--color-outline);
  border-radius: 0;
  padding: 16px;
  box-shadow: 6px 6px 0 0 #101010;
}

.settings-sublabel {
  color: var(--color-on-surface-variant);
  font-size: 13px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.settings-btn-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.settings-mode-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  padding: 6px 12px;
  border-radius: 0;
  border: 2px solid var(--color-outline);
  color: var(--color-on-surface-variant);
  cursor: pointer;
  background: var(--color-surface);
  box-shadow: 2px 2px 0 0 #101010;
  transition: color 0.1s, background-color 0.1s, transform 0.1s;
}

.settings-mode-btn:hover {
  color: var(--color-on-surface);
}

.settings-mode-btn:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}

.settings-mode-btn-active {
  color: var(--color-on-primary);
  border-color: var(--color-outline);
  background: var(--color-primary);
}

.settings-mode-btn-active:hover {
  color: var(--color-on-primary);
}

.settings-accent-row {
  display: flex;
  gap: 10px;
}

.settings-accent-swatch {
  width: 28px;
  height: 28px;
  border-radius: 0;
  border: 2px solid var(--color-outline);
  cursor: pointer;
  padding: 0;
  box-shadow: 2px 2px 0 0 #101010;
  transition: transform 0.1s, box-shadow 0.1s;
}

.settings-accent-swatch:hover {
  transform: scale(1.1);
}

.settings-accent-swatch:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}

.settings-accent-swatch-active {
  border: 3px solid var(--color-on-surface);
  box-shadow: 3px 3px 0 0 #101010;
}
</style>
