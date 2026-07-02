<template>
  <div class="dash-page">
    <!-- Metric overview -->
    <section class="dash-metrics">
      <div class="dash-metric">
        <span class="dash-metric-label">Training Days</span>
        <span class="dash-metric-value">{{ weekSummary.trainingDays || 0 }}</span>
        <div class="dash-metric-rule"></div>
      </div>
      <div class="dash-metric">
        <span class="dash-metric-label">Total Volume</span>
        <span class="dash-metric-value">{{ formatVolume(weekSummary.totalVolume) }}</span>
        <div class="dash-metric-rule"></div>
      </div>
      <div class="dash-metric">
        <span class="dash-metric-label">Last Training</span>
        <span class="dash-metric-value dash-metric-accent">{{ lastTrainingDate || "--" }}</span>
        <div class="dash-metric-rule"></div>
      </div>
      <div class="dash-metric">
        <span class="dash-metric-label">Last Task</span>
        <span class="dash-metric-value dash-metric-accent">{{ lastExecTime || "--" }}</span>
        <div class="dash-metric-rule"></div>
      </div>
    </section>

    <!-- Two columns: Training Overview + User Profile (placeholder) -->
    <section class="dash-columns">
      <!-- Training Overview -->
      <div class="dash-col">
        <div class="dash-col-head">
          <h3 class="dash-col-title">Training Overview</h3>
          <router-link to="/training" class="dash-col-action">LOG TRAINING</router-link>
        </div>

        <div v-if="recentTraining.length > 0" class="dash-training-list">
          <div
            v-for="(record, idx) in recentTraining"
            :key="idx"
            class="dash-training-item"
          >
            <span class="material-symbols-outlined dash-training-icon">fitness_center</span>
            <div class="dash-training-body">
              <span class="dash-training-date">{{ record.date || "--" }}</span>
              <span class="dash-training-summary">{{ record.summary || "—" }}</span>
            </div>
          </div>
        </div>
        <div v-else class="dash-empty">暂无训练记录，去记录第一次训练吧</div>
      </div>

      <!-- User Profile (placeholder, reserved for future) -->
      <div class="dash-col">
        <div class="dash-col-head">
          <h3 class="dash-col-title">User Profile</h3>
          <span class="dash-col-tag">SOON</span>
        </div>
        <div class="dash-placeholder">
          <span class="material-symbols-outlined dash-placeholder-icon">account_circle</span>
          <span class="dash-placeholder-text">用户画像内容即将上线</span>
          <span class="dash-placeholder-hint">此处将展示用户个性化画像与建议</span>
        </div>
      </div>
    </section>

    <!-- Quick actions -->
    <section class="dash-actions">
      <h3 class="dash-col-title">Quick Actions</h3>
      <div class="dash-actions-grid">
        <button
          v-for="action in quickActions"
          :key="action.text"
          type="button"
          class="dash-action-card"
          @click="handleDirectTask(action.text)"
        >
          <span class="material-symbols-outlined dash-action-icon">{{ action.icon }}</span>
          <span class="dash-action-label">{{ action.label }}</span>
          <span class="dash-action-desc">{{ action.desc }}</span>
        </button>
      </div>
    </section>

    <!-- Status bar -->
    <footer class="dash-status">
      <span>SYSTEM STATUS: {{ sseStatusLabel }}</span>
      <span>MODE: {{ activeModeLabel }}</span>
    </footer>
  </div>
</template>

<script lang="ts">
import ChatLogicBase from "../chat/ChatLogicBase.vue";
import doctorApi from "../../services/doctorApi";

export default {
  name: "DashboardPage",
  extends: ChatLogicBase,
  data() {
    return {
      recentTraining: [] as Array<{ date: string; summary: string }>,
      quickActions: [
        {
          label: "分析本周训练",
          desc: "查看训练数据与趋势分析",
          icon: "search",
          text: "分析我这周的训练情况",
        },
        {
          label: "恢复状态评估",
          desc: "评估疲劳与恢复建议",
          icon: "favorite",
          text: "帮我看下最近的恢复状态",
        },
        {
          label: "生成本周周报",
          desc: "分析并发送训练周报",
          icon: "description",
          text: "分析我这周的训练情况，生成周报发到我的邮箱",
        },
      ],
    };
  },
  computed: {
    sseStatusLabel() {
      if (this.sseState === "connected") {
        return "OPERATIONAL";
      }
      if (this.sseState === "connecting") {
        return "CONNECTING";
      }
      if (this.sseState === "disconnected" || this.sseState === "unsupported") {
        return "OFFLINE";
      }
      return "STANDBY";
    },
    lastTrainingDate(): string {
      if (!this.recentTraining || this.recentTraining.length === 0) return "";
      const first = this.recentTraining[0];
      if (!first || !first.date) return "";
      // 只取 MM-DD
      const parts = String(first.date).split("-");
      if (parts.length >= 3) {
        return parts[1] + "." + parts[2];
      }
      return String(first.date);
    },
  },
  mounted() {
    this.fetchRecentTraining();
  },
  methods: {
    formatVolume(volume) {
      var v = Number(volume) || 0;
      if (v >= 1000) {
        return (v / 1000).toFixed(1) + "k";
      }
      return String(v);
    },
    fetchRecentTraining() {
      var me = this;
      doctorApi
        .getRecentTraining(5)
        .then(function (res) {
          var data = res && res.data;
          if (Array.isArray(data)) {
            me.recentTraining = data;
          }
        })
        .catch(function () {
          me.recentTraining = [];
        });
    },
  },
};
</script>

<style scoped>
.dash-page {
  display: flex;
  flex-direction: column;
  gap: 64px;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  padding: 32px 48px 48px;
  background: var(--color-background);
}

.dash-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 32px;
}

.dash-metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dash-metric-label {
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

.dash-metric-value {
  font-size: 40px;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: -0.02em;
  color: var(--color-on-surface);
  font-variant-numeric: tabular-nums;
}

.dash-metric-accent {
  color: var(--color-primary-fixed-dim);
}

.dash-metric-rule {
  height: 1px;
  background: var(--color-surface-container);
  margin-top: 8px;
}

.dash-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 64px;
}

.dash-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dash-col-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dash-col-title {
  font-size: 24px;
  font-weight: 500;
  letter-spacing: -0.01em;
  color: var(--color-on-surface);
  margin: 0;
}

.dash-col-action {
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
  text-decoration: none;
  transition: color 0.2s ease;
}

.dash-col-action:hover {
  color: var(--color-primary-fixed-dim);
}

.dash-training-list {
  display: flex;
  flex-direction: column;
}

.dash-training-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-surface-container);
}

.dash-training-icon {
  font-size: 18px;
  color: var(--color-primary-fixed-dim);
  flex-shrink: 0;
  margin-top: 2px;
}

.dash-training-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.dash-training-date {
  font-size: 9px;
  letter-spacing: 0.08em;
  color: var(--color-on-surface-variant);
  font-variant-numeric: tabular-nums;
  font-family: ui-monospace, monospace;
}

.dash-training-summary {
  font-size: 13px;
  color: var(--color-on-surface);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.dash-empty {
  padding: 24px 0;
  font-size: 13px;
  color: var(--color-on-surface-variant);
}

.dash-col-tag {
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
  border: 1px solid var(--color-outline-variant);
  padding: 2px 6px;
  border-radius: 2px;
}

.dash-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 48px 24px;
  border: 1px dashed var(--color-outline-variant);
  border-radius: 8px;
  background: var(--color-surface-container-low);
  flex: 1;
  min-height: 200px;
}

.dash-placeholder-icon {
  font-size: 36px;
  color: var(--color-on-surface-variant);
  opacity: 0.5;
}

.dash-placeholder-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.dash-placeholder-hint {
  font-size: 11px;
  color: var(--color-on-surface-variant);
}

.dash-actions {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dash-actions-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.dash-action-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 20px;
  border: 1px solid var(--color-surface-container);
  border-radius: 8px;
  background: var(--color-surface-container-low);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.dash-action-card:hover {
  border-color: var(--color-outline-variant);
  transform: translateY(-2px);
}

.dash-action-icon {
  font-size: 22px;
  color: var(--color-primary-fixed-dim);
}

.dash-action-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.dash-action-desc {
  font-size: 12px;
  color: var(--color-on-surface-variant);
}

.dash-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 16px;
  border-top: 1px solid var(--color-surface-container);
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

@media (max-width: 900px) {
  .dash-page {
    padding: 24px;
    gap: 40px;
  }
  .dash-metrics {
    grid-template-columns: repeat(2, 1fr);
    gap: 24px;
  }
  .dash-columns {
    grid-template-columns: 1fr;
    gap: 40px;
  }
  .dash-actions-grid {
    grid-template-columns: 1fr;
  }
}
</style>
