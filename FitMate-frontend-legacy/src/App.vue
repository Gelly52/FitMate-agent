<template>
  <router-view />
</template>

<script lang="ts">
export default {
  name: "FitMateApp",
  created() {
    this.applyDarkTheme();
  },
  methods: {
    applyDarkTheme() {
      if (typeof document === "undefined") {
        return;
      }
      // Obsidian Precision is dark-first; enable Tailwind's class-based dark mode.
      document.documentElement.classList.add("dark");
    },
  },
};
</script>
