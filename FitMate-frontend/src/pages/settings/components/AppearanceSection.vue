<!-- FitMate-frontend/src/pages/settings/components/AppearanceSection.vue -->
<template>
  <div>
    <h2 class="text-primary font-inter text-body-base font-semibold mb-md"># 外观</h2>
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
        blue: "#adc6ff",
        green: "#7ee787",
        orange: "#ffb595",
        purple: "#c4a7e7",
      };
      return map[accent];
    },
  },
};
</script>

<style scoped>
.settings-card {
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  padding: 16px;
}

.settings-sublabel {
  color: var(--color-on-surface-variant);
  font-size: 11px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.settings-btn-group {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.settings-mode-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 4px;
  border: 1px solid var(--color-outline-variant);
  color: var(--color-on-surface-variant);
  cursor: pointer;
  background: transparent;
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.settings-mode-btn:hover {
  color: var(--color-on-surface);
}

.settings-mode-btn-active {
  color: var(--color-primary);
  border-color: var(--color-primary);
  background: var(--color-surface-container-high);
}

.settings-accent-row {
  display: flex;
  gap: 10px;
}

.settings-accent-swatch {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  padding: 0;
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.settings-accent-swatch:hover {
  transform: scale(1.1);
}

.settings-accent-swatch-active {
  border-color: var(--color-on-surface);
}
</style>
