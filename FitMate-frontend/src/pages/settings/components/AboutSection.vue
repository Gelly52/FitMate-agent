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
      // 占位：实际仓库地址可后续配置
      window.open("https://github.com/", "_blank", "noopener");
    },
  },
};
</script>

<style scoped>
.settings-card {
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  padding: 16px;
}

.about-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-size: 12px;
}

.about-row + .about-row {
  border-top: 1px solid var(--color-outline-variant);
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
}

.about-link:hover {
  text-decoration: underline;
}
</style>
