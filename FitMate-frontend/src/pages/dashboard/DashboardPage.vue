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

      <!-- User Profile -->
      <div class="dash-col">
        <div class="dash-col-head">
          <h3 class="dash-col-title">User Profile</h3>
        </div>
        <UserProfilePanel />
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
import UserProfilePanel from "./components/UserProfilePanel.vue";

export default {
  name: "DashboardPage",
  components: { UserProfilePanel },
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
  gap: 40px;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  padding: 32px 48px 48px;
  background: var(--color-background);
}

/* ===== Metric overview: pixel stat cards ===== */
.dash-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.dash-metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  background: var(--color-surface);
  border: 3px solid var(--color-outline);
  border-top: 5px solid var(--pixel-blue);
  box-shadow: 3px 3px 0 0 #101010;
}
.dash-metric:nth-child(2) {
  border-top-color: var(--pixel-green);
}
.dash-metric:nth-child(3) {
  border-top-color: var(--pixel-yellow);
}
.dash-metric:nth-child(4) {
  border-top-color: var(--pixel-red);
}

.dash-metric-label {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

.dash-metric-value {
  font-size: 36px;
  line-height: 1.1;
  color: var(--color-on-surface);
  font-variant-numeric: tabular-nums;
}

.dash-metric-accent {
  color: var(--color-primary);
}

.dash-metric-rule {
  height: 3px;
  background: var(--color-outline-variant);
  margin-top: 8px;
}

/* ===== Two columns: pixel panels ===== */
.dash-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 32px;
}

.dash-col {
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
  border: 3px solid var(--color-outline);
  box-shadow: 6px 6px 0 0 #101010;
  overflow: hidden;
}

.dash-col-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: var(--color-primary);
  border-bottom: 3px solid var(--color-outline);
}

.dash-col-title {
  font-size: 22px;
  color: var(--color-on-surface);
  margin: 0;
  position: relative;
  padding-left: 18px;
  letter-spacing: 0.02em;
}
.dash-col-title::before {
  content: "";
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 10px;
  height: 10px;
  background: var(--color-primary);
  border: 2px solid var(--color-outline);
}

.dash-col-head .dash-col-title {
  color: var(--color-on-primary);
  padding-left: 0;
}
.dash-col-head .dash-col-title::before {
  display: none;
}

.dash-col-action {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface);
  background: var(--color-surface);
  border: 2px solid var(--color-outline);
  padding: 2px 8px;
  text-decoration: none;
  box-shadow: 2px 2px 0 0 #101010;
  transition: transform 0.1s, box-shadow 0.1s;
}

.dash-col-action:hover {
  transform: translate(-1px, -1px);
  box-shadow: 3px 3px 0 0 #101010;
}

.dash-col-action:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}

.dash-training-list {
  display: flex;
  flex-direction: column;
  padding: 4px 14px 14px;
}

.dash-training-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 4px;
  border-bottom: 2px solid var(--color-outline-variant);
  transition: background 0.1s;
}
.dash-training-item:hover {
  background: var(--color-surface-container-low);
}

.dash-training-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  font-size: 18px;
  color: var(--color-on-primary);
  background: var(--color-primary);
  border: 2px solid var(--color-outline);
  flex-shrink: 0;
}

.dash-training-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.dash-training-date {
  font-size: 12px;
  letter-spacing: 0.08em;
  color: var(--color-on-surface-variant);
  font-variant-numeric: tabular-nums;
}

.dash-training-summary {
  font-size: 15px;
  color: var(--color-on-surface);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.dash-empty {
  padding: 24px 14px;
  font-size: 15px;
  color: var(--color-on-surface-variant);
}

/* UserProfilePanel lives inside the panel body */
.dash-col :deep(.profile-panel) {
  padding: 12px 14px 14px;
}

.dash-col-tag {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
  border: 2px solid var(--color-outline-variant);
  padding: 2px 6px;
}

.dash-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 48px 24px;
  border: 2px dashed var(--color-outline-variant);
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
  font-size: 15px;
  color: var(--color-on-surface);
}

.dash-placeholder-hint {
  font-size: 14px;
  color: var(--color-on-surface-variant);
}

/* ===== Quick actions: pixel button cards ===== */
.dash-actions {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dash-actions-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.dash-action-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 16px;
  border: 3px solid var(--color-outline);
  background: var(--color-surface);
  color: var(--color-on-surface);
  box-shadow: 4px 4px 0 0 #101010;
  cursor: pointer;
  text-align: left;
  transition: transform 0.1s, box-shadow 0.1s;
}
.dash-action-card:hover {
  transform: translate(-2px, -2px);
  box-shadow: 6px 6px 0 0 #101010;
}
.dash-action-card:active {
  transform: translate(2px, 2px);
  box-shadow: 2px 2px 0 0 #101010;
}

.dash-action-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 20px;
  color: var(--color-on-primary);
  background: var(--color-primary);
  border: 2px solid var(--color-outline);
}

.dash-action-label {
  font-size: 17px;
  color: var(--color-on-surface);
}

.dash-action-desc {
  font-size: 14px;
  color: var(--color-on-surface-variant);
}

/* ===== Status bar ===== */
.dash-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 12px;
  border-top: 3px solid var(--color-outline);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

@media (max-width: 900px) {
  .dash-page {
    padding: 24px;
    gap: 32px;
  }
  .dash-metrics {
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
  }
  .dash-columns {
    grid-template-columns: 1fr;
    gap: 24px;
  }
  .dash-actions-grid {
    grid-template-columns: 1fr;
  }
}
</style>
