<template>
  <div class="flex flex-col items-center justify-center h-full text-center px-md gap-lg">
    <div class="flex flex-col items-center gap-md">
      <div
        class="welcome-avatar w-16 h-16 flex items-center justify-center animate-float"
      >
        <span
          class="material-symbols-outlined text-primary"
          style="font-size: 32px; font-variation-settings: 'FILL' 1;"
          >fitness_center</span
        >
      </div>
      <h2 class="text-headline-md text-on-surface">
        你好，我是 FitMate
      </h2>
      <p class="text-body-base text-on-surface-variant max-w-md">
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
.welcome-avatar {
  background: var(--color-surface-container);
  border: 3px solid var(--color-outline);
  border-radius: 0;
  box-shadow: 4px 4px 0 0 #101010;
}

.welcome-prompt-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border: 2px solid var(--color-outline);
  border-radius: 0;
  background: var(--color-surface);
  color: var(--color-on-surface);
  font-size: 15px;
  letter-spacing: 0;
  cursor: pointer;
  box-shadow: 2px 2px 0 0 #101010;
  transition: border-color 0.1s, color 0.1s, background-color 0.1s;
}

.welcome-prompt-btn:hover {
  border-color: var(--pixel-blue);
  color: var(--color-primary);
}

.welcome-prompt-btn:active {
  transform: translate(2px, 2px);
  box-shadow: 0 0 0 0 #101010;
}
</style>
