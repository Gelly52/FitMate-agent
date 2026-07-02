<template>
  <nav
    class="fixed left-0 top-0 h-full flex flex-col py-8 border-r border-outline-variant bg-background z-50 transition-all duration-200 ease-out"
    :class="expanded ? 'w-60' : 'w-20 items-center'"
  >
    <!-- Brand -->
    <div
      class="mb-xl flex w-full gap-sm px-4"
      :class="expanded ? 'items-center justify-between' : 'flex-col items-center'"
    >
      <div
        class="w-10 h-10 rounded-full bg-surface-container-high border border-outline-variant flex items-center justify-center"
      >
        <span class="material-symbols-outlined text-on-surface" style="font-variation-settings: 'FILL' 1;">robot_2</span>
      </div>
      <span v-if="expanded" class="side-nav-brand-text">FitMate</span>
      <button
        type="button"
        class="side-nav-toggle"
        :aria-label="expanded ? 'Collapse sidebar' : 'Expand sidebar'"
        :title="expanded ? 'Collapse sidebar' : 'Expand sidebar'"
        @click="$emit('toggle')"
      >
        <span class="material-symbols-outlined">{{ expanded ? "chevron_left" : "chevron_right" }}</span>
      </button>
    </div>

    <!-- Nav links -->
    <div
      class="flex flex-col gap-lg w-full"
      :class="expanded ? 'items-stretch px-3' : 'items-center'"
    >
      <router-link
        v-for="item in navItems"
        :key="item.to"
        :to="item.to"
        class="side-nav-link group"
        :class="[
          expanded ? 'side-nav-link-expanded' : 'side-nav-link-collapsed',
          isActive(item.to) ? 'side-nav-link-active' : '',
        ]"
        :title="expanded ? '' : item.label"
      >
        <span
          class="material-symbols-outlined transition-colors"
          :style="isActive(item.to) ? fillStyle : emptyStyle"
        >{{ item.icon }}</span>
        <span v-if="expanded" class="side-nav-label">{{ item.label }}</span>
        <span v-else class="side-nav-tooltip">{{ item.label }}</span>
      </router-link>
    </div>

    <!-- Settings (bottom) -->
    <div class="mt-auto w-full flex" :class="expanded ? 'px-3' : 'justify-center'">
      <router-link
        to="/settings"
        class="side-nav-link group"
        :class="[
          expanded ? 'side-nav-link-expanded' : 'side-nav-link-collapsed',
          isActive('/settings') ? 'side-nav-link-active' : '',
        ]"
        :title="expanded ? '' : 'Settings'"
      >
        <span class="material-symbols-outlined transition-colors">settings</span>
        <span v-if="expanded" class="side-nav-label">Settings</span>
        <span v-else class="side-nav-tooltip">Settings</span>
      </router-link>
    </div>
  </nav>
</template>

<script lang="ts">
export default {
  name: "SideNav",
  props: {
    expanded: {
      type: Boolean,
      default: false,
    },
  },
  emits: ["toggle"],
  data() {
    return {
      navItems: [
        { to: "/dashboard", icon: "grid_view", label: "Dashboard" },
        { to: "/chat", icon: "chat_bubble", label: "AI Chat" },
        { to: "/training", icon: "fitness_center", label: "Training Log" },
        { to: "/body-metrics", icon: "monitor_weight", label: "Body Metrics" },
        { to: "/upload", icon: "folder_open", label: "Knowledge Base" },
      ],
      fillStyle: "font-variation-settings: 'FILL' 1;",
      emptyStyle: "font-variation-settings: 'FILL' 0;",
    };
  },
  methods: {
    isActive(path) {
      return this.$route?.path === path;
    },
  },
};
</script>

<style scoped>
.side-nav-link {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  padding: 8px 0;
  position: relative;
  cursor: pointer;
  color: var(--color-on-surface-variant);
  border-right: 2px solid transparent;
  transition: color 0.2s ease, border-color 0.2s ease;
}

.side-nav-link-collapsed {
  flex-direction: column;
  justify-content: center;
}

.side-nav-link-expanded {
  flex-direction: row;
  justify-content: flex-start;
  gap: 12px;
  padding: 10px 14px;
}

.side-nav-link:hover {
  color: var(--color-primary);
  border-right-color: var(--color-primary);
}

.side-nav-link-active {
  color: var(--color-primary);
  border-right-color: var(--color-primary);
}

.side-nav-brand-text {
  flex: 1;
  color: var(--color-on-surface);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.side-nav-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: var(--color-on-surface-variant);
  border: 1px solid var(--color-outline-variant);
  border-radius: 999px;
  background: var(--color-surface-container);
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.side-nav-toggle:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
  background: var(--color-surface-container-low);
}

.side-nav-label {
  min-width: 0;
  overflow: hidden;
  color: inherit;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-nav-tooltip {
  font-size: 11px;
  line-height: 1.2;
  letter-spacing: 0.05em;
  font-weight: 600;
  opacity: 0;
  position: absolute;
  left: 100%;
  margin-left: 16px;
  white-space: nowrap;
  background: var(--color-surface-container);
  padding: 4px 8px;
  border-radius: 2px;
  border: 1px solid var(--color-outline-variant);
  transition: opacity 0.2s ease;
  pointer-events: none;
  z-index: 50;
}

.side-nav-link:hover .side-nav-tooltip {
  opacity: 1;
}

.material-symbols-outlined {
  font-size: 24px;
}
</style>
