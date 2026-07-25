<template>
  <header
    class="topbar-root flex justify-between items-center px-lg w-full h-16 z-40 sticky top-0"
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
.topbar-root {
  background: var(--color-background);
  border-bottom: 3px solid var(--color-outline);
}
.topbar-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.topbar-title-brand {
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0;
  color: var(--color-on-surface);
}
.topbar-title-sep {
  font-size: 18px;
  color: var(--color-on-surface-variant);
  font-weight: 400;
}
.topbar-title-page {
  font-size: 18px;
  font-weight: 500;
  color: var(--color-on-surface-variant);
}
</style>
