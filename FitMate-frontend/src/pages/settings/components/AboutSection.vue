<!-- FitMate-frontend/src/pages/settings/components/AboutSection.vue -->
<template>
  <div>
    <div class="settings-card">
      <div class="about-row">
        <span class="about-label">应用</span>
        <span class="about-value">FitMate DS</span>
      </div>
      <div class="about-row">
        <span class="about-label">构建号</span>
        <span class="about-value">Build 4.2.1</span>
      </div>
      <div class="about-row">
        <span class="about-label">源码</span>
        <a class="about-link" href="#" @click.prevent="openRepo">查看源码</a>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
export default {
  name: "AboutSection",
  methods: {
    openRepo() {
      window.open("https://github.com/Gelly52/FitMate-agent", "_blank", "noopener");
    },
  },
};
</script>

<style scoped>
.settings-card {
  background: var(--color-surface-container);
  border: 4px solid var(--color-outline);
  border-radius: 0;
  padding: 16px;
  box-shadow: 6px 6px 0 0 #101010;
}

.about-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-size: 15px;
}

.about-row + .about-row {
  border-top: 2px solid var(--color-outline-variant);
}

.about-label {
  color: var(--color-on-surface-variant);
}

.about-value {
  color: var(--color-on-surface);
}

.about-link {
  color: var(--color-primary);
  text-decoration: none;
  border-bottom: 2px solid var(--color-primary);
  transition: background-color 0.1s, color 0.1s;
}

.about-link:hover {
  background: var(--color-primary);
  color: var(--color-on-primary);
}
</style>
