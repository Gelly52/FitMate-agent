<template>
  <div class="form-page">
    <div class="form-main">
      <!-- Header -->
      <header class="form-header">
        <h1 class="font-inter text-display-lg text-on-surface tracking-tight">
          Body Metrics
        </h1>
        <p class="font-inter text-body-base text-on-surface-variant">
          记录今天的身体指标与恢复状态。
        </p>
      </header>

      <!-- Metrics -->
      <section class="form-section">
        <div class="form-section-head">
          <span class="material-symbols-outlined text-primary" style="font-size: 20px;">monitor_weight</span>
          <h2 class="font-inter text-label-sm text-on-surface uppercase tracking-widest">
            Daily Metrics
          </h2>
        </div>

        <div class="metrics-grid">
          <div class="metric-field group">
            <label class="metric-label">
              <span>Morning Weight (kg)</span>
              <span class="metric-current">{{ todayStatus.weight || "--" }}</span>
            </label>
            <input
              v-model.number="form.weight"
              class="metric-input"
              type="number"
              min="20"
              max="300"
              step="0.1"
              placeholder="0.0"
            />
          </div>

          <div class="metric-field group">
            <label class="metric-label">
              <span>Body Fat (%)</span>
              <span class="metric-current">{{ todayStatus.bodyFat || "--" }}</span>
            </label>
            <input
              v-model.number="form.bodyFat"
              class="metric-input"
              type="number"
              min="1"
              max="60"
              step="0.1"
              placeholder="0.0"
            />
          </div>

          <div class="metric-field group">
            <label class="metric-label">
              <span>Sleep (h)</span>
              <span class="metric-current">{{ todayStatus.sleep || "--" }}</span>
            </label>
            <input
              v-model.number="form.sleep"
              class="metric-input"
              type="number"
              min="0"
              max="24"
              step="0.5"
              placeholder="0.0"
            />
          </div>

          <div class="metric-field">
            <label class="metric-label">
              <span>Fatigue</span>
            </label>
            <div class="fatigue-toggle">
              <button
                v-for="opt in fatigueOptions"
                :key="opt"
                type="button"
                class="fatigue-pill"
                :class="{ 'fatigue-pill-active': form.fatigue === opt }"
                @click="form.fatigue = opt"
              >
                {{ opt }}
              </button>
            </div>
          </div>
        </div>

        <div class="metric-field metric-field-wide">
          <label class="metric-label"><span>Note</span></label>
          <textarea
            v-model="form.note"
            class="metric-textarea"
            rows="2"
            placeholder="补充说明（可选）"
          ></textarea>
        </div>
      </section>

      <!-- Recent -->
      <section v-if="recentMetrics.length > 0" class="form-section">
        <div class="form-section-head">
          <span class="material-symbols-outlined text-primary" style="font-size: 20px;">history</span>
          <h2 class="font-inter text-label-sm text-on-surface uppercase tracking-widest">
            Recent Changes
          </h2>
        </div>
        <div class="history-list">
          <div
            v-for="(record, idx) in recentMetrics"
            :key="idx"
            class="history-item"
          >
            <span class="history-date">{{ record.date }}</span>
            <span class="history-detail">{{ record.summary }}</span>
          </div>
        </div>
      </section>

      <!-- Submit -->
      <div class="form-submit-bar">
        <button
          type="button"
          class="form-submit-btn"
          :disabled="!canSubmit"
          @click="submitMetrics"
        >
          COMMIT LOG
        </button>
      </div>
    </div>

    <!-- Today snapshot -->
    <aside class="form-aside">
      <h3 class="font-inter text-label-sm text-on-surface-variant uppercase tracking-widest aside-title">
        Today Snapshot
      </h3>
      <div class="aside-stat">
        <span class="aside-stat-label">Weight</span>
        <span class="aside-stat-value">{{ todayStatus.weight || "--" }} <small>kg</small></span>
      </div>
      <div class="aside-stat">
        <span class="aside-stat-label">Body Fat</span>
        <span class="aside-stat-value">{{ todayStatus.bodyFat || "--" }} <small>%</small></span>
      </div>
      <div class="aside-stat">
        <span class="aside-stat-label">Sleep</span>
        <span class="aside-stat-value">{{ todayStatus.sleep || "--" }} <small>h</small></span>
      </div>
      <div class="aside-stat">
        <span class="aside-stat-label">Fatigue</span>
        <span class="aside-stat-value" :class="fatigueClass">{{ todayStatus.fatigue || "--" }}</span>
      </div>
    </aside>
  </div>
</template>

<script lang="ts">
import ChatLogicBase from "../chat/ChatLogicBase.vue";
import doctorApi from "../../services/doctorApi";

export default {
  name: "MetricsPage",
  extends: ChatLogicBase,
  data() {
    return {
      form: {
        weight: null,
        bodyFat: null,
        sleep: null,
        fatigue: "",
        note: "",
      },
      fatigueOptions: ["低", "中", "高"],
    };
  },
  computed: {
    canSubmit() {
      return this.form.weight != null || this.form.bodyFat != null;
    },
    fatigueClass() {
      var fatigue = this.todayStatus.fatigue;
      if (!fatigue) {
        return "";
      }
      if (fatigue === "低" || fatigue === "low") {
        return "status-good";
      }
      if (fatigue === "中" || fatigue === "medium") {
        return "status-warn";
      }
      return "status-alert";
    },
  },
  mounted() {
    this.fetchRecentMetrics();
  },
  methods: {
    submitMetrics() {
      if (!this.canSubmit) {
        return;
      }
      var formData = {
        weight: this.form.weight,
        bodyFat: this.form.bodyFat,
        sleep: this.form.sleep,
        fatigue: this.form.fatigue,
        note: this.form.note,
        date: new Date().toISOString().slice(0, 10),
      };
      var me = this;
      doctorApi
        .logBodyMetrics(formData)
        .then(function () {
          me.showUiMessage("success", "身体指标已保存");
          if (formData.weight != null) {
            me.todayStatus.weight = formData.weight;
          }
          if (formData.bodyFat != null) {
            me.todayStatus.bodyFat = formData.bodyFat;
          }
          if (formData.sleep != null) {
            me.todayStatus.sleep = formData.sleep;
          }
          if (formData.fatigue) {
            me.todayStatus.fatigue = formData.fatigue;
          }
          me.fetchRecentMetrics();
        })
        .catch(function () {
          window.sessionStorage.setItem(
            "fitmate:pending-draft",
            me.buildBodyMetricsPrompt(formData)
          );
          me.$router.push("/chat");
        });
    },
  },
};
</script>

<style scoped>
.form-page {
  display: flex;
  flex-direction: row;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  background: var(--color-background);
}

.form-main {
  flex: 1;
  max-width: 800px;
  padding: 32px 24px 48px;
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.form-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-bottom: 1px solid var(--color-surface-container);
  padding-bottom: 24px;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
  border-bottom: 1px solid var(--color-surface-container);
  padding-bottom: 32px;
}

.form-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px 32px;
}

.metric-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric-field-wide {
  grid-column: 1 / -1;
}

.metric-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

.metric-current {
  color: var(--color-on-surface-variant);
}

.group:focus-within .metric-current {
  color: var(--color-primary-fixed-dim);
}

.metric-input {
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--color-outline-variant);
  color: var(--color-on-surface);
  font-size: 15px;
  font-family: "Inter", sans-serif;
  padding: 4px 0;
  outline: none;
  transition: border-color 0.2s ease;
}

.metric-input:focus {
  border-bottom-color: var(--color-primary);
}

.metric-input::placeholder {
  color: var(--color-surface-bright);
}

.metric-input::-webkit-outer-spin-button,
.metric-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.metric-input[type="number"] {
  -moz-appearance: textfield;
}

.metric-textarea {
  background: transparent;
  border: 1px solid var(--color-outline-variant);
  border-radius: 0.5rem;
  color: var(--color-on-surface);
  font-size: 15px;
  font-family: "Inter", sans-serif;
  padding: 10px 12px;
  outline: none;
  resize: vertical;
  transition: border-color 0.2s ease;
}

.metric-textarea:focus {
  border-color: var(--color-primary);
}

.metric-textarea::placeholder {
  color: var(--color-surface-bright);
}

.fatigue-toggle {
  display: flex;
  gap: 8px;
}

.fatigue-pill {
  flex: 1;
  padding: 8px 0;
  border: 1px solid var(--color-outline-variant);
  border-radius: 0.5rem;
  background: transparent;
  color: var(--color-on-surface-variant);
  font-size: 13px;
  font-family: "Inter", sans-serif;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.fatigue-pill:hover {
  color: var(--color-on-surface-variant);
  border-color: var(--color-on-surface-variant);
}

.fatigue-pill-active {
  color: var(--color-on-primary);
  background: var(--color-primary-fixed-dim);
  border-color: var(--color-primary-fixed-dim);
}

.history-list {
  display: flex;
  flex-direction: column;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-surface-container);
}

.history-date {
  font-size: 12px;
  color: var(--color-on-surface-variant);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.history-detail {
  font-size: 13px;
  color: var(--color-on-surface-variant);
}

.form-submit-bar {
  display: flex;
  justify-content: flex-end;
}

.form-submit-btn {
  padding: 10px 28px;
  background: var(--color-primary);
  color: var(--color-on-surface);
  border: none;
  border-radius: 0.5rem;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-family: "Inter", sans-serif;
  cursor: pointer;
  transition: background 0.2s ease, opacity 0.2s ease;
}

.form-submit-btn:hover:not(:disabled) {
  background: var(--color-primary-container);
}

.form-submit-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.form-aside {
  width: 280px;
  flex-shrink: 0;
  border-left: 1px solid var(--color-surface-container);
  padding: 32px 24px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.aside-title {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-surface-container);
}

.aside-stat {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
}

.aside-stat-label {
  font-size: 11px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

.aside-stat-value {
  font-size: 15px;
  color: var(--color-on-surface);
  font-variant-numeric: tabular-nums;
}

.aside-stat-value small {
  font-size: 11px;
  color: var(--color-on-surface-variant);
}

.status-good {
  color: var(--color-primary-fixed-dim);
}

.status-warn {
  color: var(--color-tertiary);
}

.status-alert {
  color: var(--color-error);
}

@media (max-width: 900px) {
  .form-page {
    flex-direction: column;
  }
  .form-aside {
    width: auto;
    border-left: none;
    border-top: 1px solid var(--color-surface-container);
  }
  .metrics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
