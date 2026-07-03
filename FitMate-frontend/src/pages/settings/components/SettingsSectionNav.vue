<!-- FitMate-frontend/src/pages/settings/components/SettingsSectionNav.vue -->
<template>
  <nav class="settings-section-nav">
    <a
      v-for="section in sections"
      :key="section.id"
      :href="'#' + section.id"
      class="settings-section-tab"
      :class="active === section.id ? 'settings-section-tab-active' : ''"
      @click.prevent="$emit('navigate', section.id)"
    >
      <span class="material-symbols-outlined" style="font-size:16px;">{{ section.icon }}</span>
      <span>{{ section.label }}</span>
    </a>
  </nav>
</template>

<script lang="ts">
export default {
  name: "SettingsSectionNav",
  props: {
    active: {
      type: String,
      default: "profile",
    },
  },
  emits: ["navigate"],
  data() {
    return {
      sections: [
        { id: "profile", label: "个人信息", icon: "person" },
        { id: "appearance", label: "外观", icon: "palette" },
        { id: "llm", label: "配置", icon: "tune" },
        { id: "memory", label: "记忆管理", icon: "psychology" },
        { id: "about", label: "关于", icon: "info" },
      ],
    };
  },
};
</script>

<style scoped>
.settings-section-nav {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--color-outline-variant);
  padding-bottom: 8px;
  margin-bottom: 24px;
  position: sticky;
  top: 0;
  background: var(--color-background);
  z-index: 10;
}

.settings-section-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 4px;
  color: var(--color-on-surface-variant);
  cursor: pointer;
  transition: color 0.2s ease, background 0.2s ease;
}

.settings-section-tab:hover {
  color: var(--color-on-surface);
  background: var(--color-surface-container);
}

.settings-section-tab-active {
  color: var(--color-primary);
  background: var(--color-surface-container);
}
</style>
