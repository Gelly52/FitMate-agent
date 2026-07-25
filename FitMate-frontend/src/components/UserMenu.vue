<!-- FitMate-frontend/src/components/UserMenu.vue -->
<template>
  <div class="user-menu-root" @mouseenter="onMouseEnter" @mouseleave="onMouseLeave">
    <button
      type="button"
      class="user-menu-trigger"
      :class="open ? 'user-menu-trigger-active' : ''"
      :title="displayName"
      @click="toggleOpen"
    >
      <span v-if="avatarLetter" class="user-menu-avatar-text">{{ avatarLetter }}</span>
      <span v-else class="material-symbols-outlined user-menu-avatar-icon">person</span>
    </button>

    <transition name="user-menu-fade">
      <div v-if="open" class="user-menu-dropdown" @click="keepOpenOnDropdownClick">
        <!-- 头部（只读） -->
        <div class="user-menu-header">
          <div class="user-menu-header-avatar">
            <span v-if="avatarLetter">{{ avatarLetter }}</span>
            <span v-else class="material-symbols-outlined" style="font-size:18px;">person</span>
          </div>
          <div class="user-menu-header-info">
            <div class="user-menu-header-name">{{ displayName }}</div>
            <div class="user-menu-header-email">{{ displayEmail }}</div>
          </div>
        </div>

        <!-- 导航项 -->
        <div class="user-menu-items">
          <router-link to="/settings#profile" class="user-menu-item" @click="close">
            <span class="material-symbols-outlined user-menu-item-icon">person</span>
            <span>个人信息</span>
          </router-link>
          <router-link to="/settings#appearance" class="user-menu-item" @click="close">
            <span class="material-symbols-outlined user-menu-item-icon">settings</span>
            <span>设置</span>
          </router-link>
        </div>

        <!-- 分隔线 + 退出 -->
        <div class="user-menu-divider"></div>
        <div class="user-menu-items">
          <button type="button" class="user-menu-item user-menu-item-logout" :disabled="loggingOut" @click="onLogout">
            <span class="material-symbols-outlined user-menu-item-icon">logout</span>
            <span>{{ loggingOut ? "退出中..." : "退出登录" }}</span>
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script lang="ts">
import { getUserInfo, USER_INFO_CHANGED_EVENT } from "../services/http";

export default {
  name: "UserMenu",
  emits: ["logout"],
  data() {
    return {
      open: false,
      hoverCloseTimer: null as ReturnType<typeof setTimeout> | null,
      loggingOut: false,
      userInfoVersion: 0,
    };
  },
  computed: {
    userInfo(): Record<string, unknown> | null {
      // 依赖 userInfoVersion 以在收到变更事件后强制重新求值
      void this.userInfoVersion;
      const info = getUserInfo();
      return info || null;
    },
    displayName(): string {
      const u = this.userInfo as Record<string, unknown> | null;
      if (!u) return "用户";
      return (u.nickname as string) || (u.username as string) || "用户";
    },
    displayEmail(): string {
      const u = this.userInfo as Record<string, unknown> | null;
      if (!u) return "—";
      return (u.email as string) || "—";
    },
    avatarLetter(): string {
      const name = this.displayName;
      if (!name || name === "用户") return "";
      return name.charAt(0).toUpperCase();
    },
  },
  mounted() {
    document.addEventListener("click", this.onDocClick);
    document.addEventListener("keydown", this.onKeydown);
    window.addEventListener(USER_INFO_CHANGED_EVENT, this.onUserInfoChanged);
  },
  beforeUnmount() {
    document.removeEventListener("click", this.onDocClick);
    document.removeEventListener("keydown", this.onKeydown);
    window.removeEventListener(USER_INFO_CHANGED_EVENT, this.onUserInfoChanged);
    if (this.hoverCloseTimer) clearTimeout(this.hoverCloseTimer);
  },
  watch: {
    $route() {
      this.open = false;
    },
  },
  methods: {
    toggleOpen() {
      this.open = !this.open;
    },
    close() {
      this.open = false;
    },
    onMouseEnter() {
      if (this.hoverCloseTimer) {
        clearTimeout(this.hoverCloseTimer);
        this.hoverCloseTimer = null;
      }
      this.open = true;
    },
    onMouseLeave() {
      this.hoverCloseTimer = setTimeout(() => {
        this.open = false;
      }, 200);
    },
    keepOpenOnDropdownClick(e: MouseEvent) {
      e.stopPropagation();
    },
    onDocClick(e: MouseEvent) {
      const root = this.$el as HTMLElement;
      if (root && !root.contains(e.target as Node)) {
        this.open = false;
      }
    },
    onKeydown(e: KeyboardEvent) {
      if (e.key === "Escape") this.open = false;
    },
    onUserInfoChanged() {
      this.userInfoVersion++;
    },
    onLogout() {
      if (this.loggingOut) return;
      this.loggingOut = true;
      this.$emit("logout");
      // AppLayout 处理实际 logout，完成后会跳转，组件销毁
    },
  },
};
</script>

<style scoped>
.user-menu-root {
  position: relative;
  display: inline-block;
}

.user-menu-trigger {
  width: 32px;
  height: 32px;
  border-radius: 0;
  background: var(--color-surface-container-high);
  border: 2px solid var(--color-outline);
  box-shadow: 2px 2px 0 0 #101010;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
  transition: background-color 0.1s, transform 0.1s, box-shadow 0.1s;
}

.user-menu-trigger:hover,
.user-menu-trigger-active {
  background: var(--color-surface-container-highest);
  transform: scale(1.05);
}

.user-menu-trigger:active {
  transform: translate(2px, 2px);
  box-shadow: 0 0 0 0 #101010;
}

.user-menu-avatar-text {
  color: var(--color-primary);
  font-size: 15px;
  font-weight: 600;
}

.user-menu-avatar-icon {
  color: var(--color-on-surface);
  font-size: 18px;
}

.user-menu-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 240px;
  background: var(--color-surface-container);
  border: 3px solid var(--color-outline);
  border-radius: 0;
  box-shadow: 4px 4px 0 0 #101010;
  z-index: 50;
  overflow: hidden;
}

.user-menu-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 2px solid var(--color-outline);
}

.user-menu-header-avatar {
  width: 36px;
  height: 36px;
  border-radius: 0;
  border: 2px solid var(--color-outline);
  background: var(--color-secondary-container);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-on-secondary-container);
  font-weight: 600;
  font-size: 16px;
  flex-shrink: 0;
}

.user-menu-header-info {
  min-width: 0;
}

.user-menu-header-name {
  color: var(--color-on-surface);
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-menu-header-email {
  color: var(--color-on-surface-variant);
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-menu-items {
  padding: 6px;
}

.user-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  color: var(--color-on-surface);
  font-size: 15px;
  border-radius: 0;
  border: 2px solid transparent;
  cursor: pointer;
  text-decoration: none;
  background: transparent;
  width: 100%;
  text-align: left;
  font-family: inherit;
  transition: background-color 0.1s, color 0.1s, border-color 0.1s;
}

.user-menu-item:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
}

.user-menu-item:active {
  transform: translate(2px, 2px);
}

.user-menu-item-icon {
  font-size: 16px;
  color: var(--color-on-surface-variant);
}

.user-menu-item-logout {
  color: var(--color-error);
}

.user-menu-item-logout .user-menu-item-icon {
  color: var(--color-error);
}

.user-menu-item-logout:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.user-menu-divider {
  border-top: 2px solid var(--color-outline-variant);
}

.user-menu-fade-enter-active,
.user-menu-fade-leave-active {
  transition: opacity 0.1s steps(2), transform 0.1s steps(2);
}

.user-menu-fade-enter-from,
.user-menu-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
