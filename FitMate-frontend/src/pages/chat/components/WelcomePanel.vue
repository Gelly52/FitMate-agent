<template>
  <div class="flex flex-col items-center justify-center h-full text-center px-md gap-lg">
    <div class="flex flex-col items-center gap-md">
      <div
        class="w-16 h-16 rounded-full bg-surface-container border border-outline-variant flex items-center justify-center"
      >
        <span
          class="material-symbols-outlined text-primary"
          style="font-size: 32px; font-variation-settings: 'FILL' 1;"
          >fitness_center</span
        >
      </div>
      <h2 class="font-inter text-headline-md text-on-surface tracking-tight">
        你好，我是 FitMate
      </h2>
      <p class="font-inter text-body-base text-on-surface-variant max-w-md">
        记住你的每一次训练，读懂你的每一次变化。
      </p>
    </div>

    <div class="flex flex-wrap items-center justify-center gap-sm max-w-lg">
      <button
        v-for="prompt in prompts"
        :key="prompt.text"
        type="button"
        class="welcome-prompt-btn"
        @click="$emit('execute-direct', prompt.text)"
      >
        <span class="material-symbols-outlined" style="font-size: 16px;">{{ prompt.icon }}</span>
        <span>{{ prompt.label }}</span>
      </button>
    </div>
  </div>
</template>

<script lang="ts">
export default {
  name: "WelcomePanel",
  emits: ["execute-direct"],
  data() {
    return {
      prompts: [
        { label: "分析本周训练", icon: "search", text: "分析我这周的训练情况" },
        {
          label: "恢复状态评估",
          icon: "favorite",
          text: "帮我看下最近的恢复状态",
        },
        {
          label: "生成本周周报",
          icon: "description",
          text: "分析我这周的训练情况，生成周报发到我的邮箱",
        },
      ],
    };
  },
};
</script>

<style scoped>
.welcome-prompt-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 0.5rem;
  background: transparent;
  color: var(--color-on-surface-variant);
  font-size: 13px;
  font-family: "Inter", sans-serif;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;
}

.welcome-prompt-btn:hover {
  border-color: var(--color-primary-fixed-dim);
  color: var(--color-primary-fixed-dim);
  background: color-mix(in srgb, var(--color-primary) 6%, transparent);
}
</style>
