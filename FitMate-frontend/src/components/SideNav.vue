<template>
  <nav
    class="side-nav-root fixed left-0 top-0 h-full flex flex-col py-8 bg-background z-50 transition-all duration-150 ease-out"
    :class="expanded ? 'w-60' : 'w-20 items-center'"
  >
    <!-- Brand -->
    <div
      class="side-nav-brand mb-xl flex w-full gap-sm px-4"
      :class="expanded ? 'items-center' : 'flex-col items-center'"
    >
      <div class="side-nav-brand-mark">
        <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="4" y="4" width="8" height="24" fill="currentColor" />
          <rect x="12" y="4" width="10" height="8" fill="currentColor" />
          <rect x="12" y="16" width="8" height="8" fill="currentColor" />
          <rect x="26" y="4" width="6" height="6" fill="#D4A533" />
        </svg>
      </div>
      <span v-if="expanded" class="side-nav-brand-text">FitMate</span>
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

    <!-- Bottom: Settings + Collapse toggle -->
    <div class="mt-auto w-full flex flex-col gap-md" :class="expanded ? 'px-3' : 'items-center'">
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
      <button
        type="button"
        class="side-nav-toggle"
        :class="expanded ? 'side-nav-toggle-expanded' : 'side-nav-toggle-collapsed'"
        :aria-label="expanded ? 'Collapse sidebar' : 'Expand sidebar'"
        :title="expanded ? 'Collapse sidebar' : 'Expand sidebar'"
        @click="$emit('toggle')"
      >
        <span class="material-symbols-outlined">{{ expanded ? "chevron_left" : "chevron_right" }}</span>
        <span v-if="expanded" class="side-nav-toggle-label">Collapse</span>
      </button>
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
        { to: "/chat", icon: "chat_bubble", label: "Agent Chat" },
        { to: "/training", icon: "fitness_center", label: "Training Log" },
        { to: "/body-metrics", icon: "monitor_weight", label: "Body Metrics" },
        { to: "/wiki", icon: "menu_book", label: "Wiki" },
        { to: "/upload", icon: "folder_open", label: "Knowledge Base" },
      ],
      fillStyle: "font-variation-settings: 'FILL' 1;",
      emptyStyle: "font-variation-settings: 'FILL' 0;",
    };
  },
  methods: {
    isActive(path) {
      var current = (this.$route && this.$route.path) || "";
      if (current === path) {
        return true;
      }
      // 子路径匹配：/chat/123 也高亮 /chat 链接
      return current.indexOf(path + "/") === 0;
    },
  },
};
</script>

<style scoped>
.side-nav-root {
  border-right: 4px solid var(--color-outline);
}

.side-nav-link {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  padding: 8px 0;
  position: relative;
  cursor: pointer;
  color: var(--color-on-surface-variant);
  border: 2px solid transparent;
  transition: color 0.1s, border-color 0.1s, background-color 0.1s;
}

.side-nav-link:active {
  transform: translate(2px, 2px);
}

.side-nav-link-collapsed {
  flex-direction: column;
  justify-content: center;
}

.side-nav-link-expanded {
  flex-direction: row;
  justify-content: flex-start;
  gap: 12px;
  padding: 8px 12px;
}

.side-nav-link:hover {
  color: var(--color-primary);
  border-color: var(--color-outline);
  background: var(--color-outline-variant);
}

.side-nav-link-active {
  color: var(--color-primary);
  border-color: var(--color-outline);
  background: var(--color-outline-variant);
}

/* Icon tile: icon sits in a small bordered square */
.side-nav-link .material-symbols-outlined {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--color-outline);
  background: var(--color-surface-container);
  font-size: 22px;
}

.side-nav-link:hover .material-symbols-outlined {
  border-color: var(--color-primary);
}

.side-nav-link-active .material-symbols-outlined {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border-color: var(--color-outline);
}

.side-nav-brand-text {
  flex: 1;
  color: var(--color-on-surface);
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0;
}

.side-nav-brand-mark {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  padding: 3px;
  color: var(--color-primary);
  border: 2px solid var(--color-outline);
  background: var(--color-surface-container);
  display: flex;
  align-items: center;
  justify-content: center;
}
.side-nav-brand-mark svg {
  width: 100%;
  height: 100%;
}

.side-nav-toggle {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: var(--color-on-surface-variant);
  background: transparent;
  border: 2px solid transparent;
  padding: 8px 0;
  transition: color 0.1s, border-color 0.1s, background-color 0.1s;
  font-family: inherit;
}

.side-nav-toggle-expanded {
  flex-direction: row;
  gap: 12px;
  padding: 8px 12px;
  width: 100%;
}

.side-nav-toggle-collapsed {
  flex-direction: column;
  justify-content: center;
  width: 100%;
}

.side-nav-toggle:hover {
  color: var(--color-primary);
  border-color: var(--color-outline);
  background: var(--color-outline-variant);
}

.side-nav-toggle:active {
  transform: translate(2px, 2px);
}

.side-nav-toggle-label {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.2;
  white-space: nowrap;
}

.side-nav-toggle .material-symbols-outlined {
  font-size: 24px;
}

.side-nav-label {
  min-width: 0;
  overflow: hidden;
  color: inherit;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-nav-tooltip {
  font-size: 13px;
  line-height: 1.2;
  letter-spacing: 0;
  font-weight: 600;
  opacity: 0;
  position: absolute;
  left: 100%;
  margin-left: 16px;
  white-space: nowrap;
  background: var(--color-surface-container);
  color: var(--color-on-surface);
  padding: 4px 8px;
  border-radius: 0;
  border: 2px solid var(--color-outline);
  box-shadow: 2px 2px 0 0 #101010;
  transition: opacity 0.1s;
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
