<!-- FitMate-frontend/src/pages/settings/components/SkillsSection.vue -->
<template>
  <section class="skills-section">
    <div class="skills-header">
      <h2 class="skills-title">技能</h2>
      <p class="skills-subtitle">
        可用技能列表。Agent 在对话中匹配触发条件时会自动加载技能内容并按步骤执行。
      </p>
    </div>

    <div v-if="loading" class="skills-loading">加载中...</div>
    <div v-else-if="skills.length === 0" class="skills-empty">暂无可用技能</div>
    <div v-else class="skills-list">
      <div
        v-for="(skill, index) in skills"
        :key="index"
        class="skill-card"
      >
        <div class="skill-card-head">
          <span class="skill-index">#{{ index + 1 }}</span>
          <span class="skill-name">{{ skill.name }}</span>
        </div>
        <div class="skill-desc">{{ skill.description }}</div>
        <div class="skill-trigger">
          <span class="skill-trigger-label">触发条件</span>
          <span class="skill-trigger-text">{{ skill.trigger }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<script lang="ts">
import { defineComponent } from "vue";
import doctorApi from "../../../services/doctorApi";
import type { SkillInfo } from "../../../types/settings";

export default defineComponent({
  name: "SkillsSection",
  data() {
    return {
      loading: true,
      skills: [] as SkillInfo[],
    };
  },
  mounted() {
    this.loadSkills();
  },
  methods: {
    async loadSkills() {
      try {
        const res = await doctorApi.getSkillList();
        this.skills = (res && (res as any).data) || [];
      } catch (e) {
        console.error("加载技能列表失败", e);
      } finally {
        this.loading = false;
      }
    },
  },
});
</script>

<style scoped>
.skills-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.skills-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.skills-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.skills-subtitle {
  margin: 0;
  font-size: 13px;
  opacity: 0.6;
  line-height: 1.5;
}

.skills-loading,
.skills-empty {
  padding: 32px 0;
  text-align: center;
  opacity: 0.5;
  font-size: 14px;
}

.skills-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skill-card {
  padding: 14px 16px;
  border: 1px solid var(--border-color, rgba(255, 255, 255, 0.08));
  border-radius: 10px;
  background: var(--card-bg, rgba(255, 255, 255, 0.02));
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skill-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.skill-index {
  font-size: 12px;
  opacity: 0.4;
  font-variant-numeric: tabular-nums;
}

.skill-name {
  font-size: 15px;
  font-weight: 600;
}

.skill-desc {
  font-size: 13px;
  opacity: 0.75;
  line-height: 1.5;
}

.skill-trigger {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 12px;
  line-height: 1.5;
}

.skill-trigger-label {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--accent-color-soft, rgba(100, 149, 237, 0.15));
  color: var(--accent-color, #6495ed);
  font-size: 11px;
}

.skill-trigger-text {
  opacity: 0.6;
}
</style>
