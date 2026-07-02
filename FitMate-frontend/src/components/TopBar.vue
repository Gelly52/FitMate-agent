<template>
  <header
    class="flex justify-between items-center px-lg w-full h-16 bg-transparent z-40 sticky top-0 backdrop-blur-md border-b border-outline-variant/30"
  >
    <div>
      <h2 class="font-headline-md text-headline-md text-on-surface tracking-tight">
        FitMate <span class="text-outline-variant font-normal">/ {{ title }}</span>
      </h2>
    </div>
    <div class="flex items-center gap-margin">
      <button
        type="button"
        class="text-on-surface-variant hover:text-primary transition-colors"
        title="Notifications"
      >
        <span class="material-symbols-outlined">notifications</span>
      </button>
      <UserMenu @logout="$emit('logout')" />
    </div>
  </header>
</template>

<script lang="ts">
import UserMenu from "./UserMenu.vue";

export default {
  name: "TopBar",
  components: { UserMenu },
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
.material-symbols-outlined {
  font-size: 22px;
}
</style>
