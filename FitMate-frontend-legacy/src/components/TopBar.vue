<template>
  <header
    class="flex justify-between items-center px-lg w-full h-16 bg-transparent z-40 sticky top-0 backdrop-blur-md border-b border-outline-variant/30"
  >
    <div class="topbar-title">
      <span class="topbar-title-brand">FitMate</span>
      <span class="topbar-title-sep">/</span>
      <span class="topbar-title-page">{{ title }}</span>
    </div>
    <div class="flex items-center gap-margin">
      <AgentRunsPanel />
      <UserMenu @logout="$emit('logout')" />
    </div>
  </header>
</template>

<script lang="ts">
import UserMenu from "./UserMenu.vue";
import AgentRunsPanel from "./AgentRunsPanel.vue";

export default {
  name: "TopBar",
  components: { UserMenu, AgentRunsPanel },
  props: {
    title: {
      type: String,
      default: "FitMate",
    },
  },
  emits: ["logout"],
};
</script>

<style scoped>
.topbar-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-family: "Manrope", system-ui, sans-serif;
}
.topbar-title-brand {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--color-on-surface);
}
.topbar-title-sep {
  font-size: 16px;
  color: var(--color-outline-variant);
  font-weight: 400;
}
.topbar-title-page {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-on-surface-variant);
}
</style>
