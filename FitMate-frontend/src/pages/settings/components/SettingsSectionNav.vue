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
        { id: "mcp", label: "MCP", icon: "extension" },
        { id: "skills", label: "技能", icon: "auto_awesome" },
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
  flex-wrap: wrap;
  gap: 8px;
  border-bottom: 3px solid var(--color-outline);
  padding-bottom: 12px;
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
  font-size: 15px;
  padding: 6px 12px;
  border: 2px solid var(--color-outline);
  border-radius: 0;
  background: var(--color-surface-container);
  color: var(--color-on-surface-variant);
  cursor: pointer;
  text-decoration: none;
  box-shadow: 2px 2px 0 0 #101010;
  transition: color 0.1s, background 0.1s;
}

.settings-section-tab:hover {
  color: var(--color-on-surface);
  background: var(--color-surface-container-high);
}

.settings-section-tab:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}

.settings-section-tab-active {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border-color: var(--color-outline);
  box-shadow: 2px 2px 0 0 #101010;
}

.settings-section-tab-active:hover {
  background: var(--color-primary);
  color: var(--color-on-primary);
}
</style>
