<template>
  <div class="form-page">
    <div class="form-main">
      <!-- Header -->
      <header class="form-header">
        <h1 class="text-display-lg text-on-surface tracking-tight">
          Body Metrics
        </h1>
        <p class="text-body-base text-on-surface-variant">
          记录今天的身体指标与恢复状态。
        </p>
      </header>

      <!-- Tab Bar -->
      <div class="tab-bar">
        <button
          v-for="tab in [
            { key: 'body', label: '身体指标' },
            { key: 'heart-rate', label: '心率' }
          ]"
          :key="tab.key"
          class="tab-btn"
          :class="{ 'tab-btn-active': activeMetricsTab === tab.key }"
          @click="switchMetricsTab(tab.key)"
        >{{ tab.label }}</button>
      </div>

      <div v-if="activeMetricsTab === 'body'">
      <!-- Metrics -->
      <section class="form-section">
        <div class="form-section-head">
          <span class="material-symbols-outlined text-primary" style="font-size: 20px;">monitor_weight</span>
          <h2 class="text-label-sm text-on-surface uppercase tracking-widest">
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

          <div class="metric-field metric-field-wide">
            <label class="metric-label"><span>Girth (cm)</span></label>
            <div class="form-row">
              <div class="girth-item">
                <input v-model.number="form.chestGirth" class="metric-input" type="number" step="0.1" placeholder="胸围" />
              </div>
              <div class="girth-item">
                <input v-model.number="form.waistGirth" class="metric-input" type="number" step="0.1" placeholder="腰围" />
              </div>
              <div class="girth-item">
                <input v-model.number="form.hipGirth" class="metric-input" type="number" step="0.1" placeholder="臀围" />
              </div>
              <div class="girth-item">
                <input v-model.number="form.armGirth" class="metric-input" type="number" step="0.1" placeholder="臂围" />
              </div>
              <div class="girth-item">
                <input v-model.number="form.thighGirth" class="metric-input" type="number" step="0.1" placeholder="大腿围" />
              </div>
            </div>
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
          <h2 class="text-label-sm text-on-surface uppercase tracking-widest">
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
      </div><!-- end body tab -->

      <!-- Heart Rate Tab -->
      <div v-if="activeMetricsTab === 'heart-rate'" class="heart-rate-section">
        <div class="form-block">
          <div class="form-label">DATE</div>
          <input v-model="heartRateForm.date" class="metric-input" type="date" />
        </div>
        <div class="form-row">
          <div class="form-block">
            <div class="form-label">RESTING HR</div>
            <input v-model.number="heartRateForm.restingHr" class="metric-input" type="number" placeholder="bpm" />
          </div>
          <div class="form-block">
            <div class="form-label">MAX HR</div>
            <input v-model.number="heartRateForm.maxHr" class="metric-input" type="number" placeholder="bpm" />
          </div>
        </div>
        <div class="form-block">
          <div class="form-label">HRV (MS)</div>
          <input v-model.number="heartRateForm.hrv" class="metric-input" type="number" placeholder="ms" />
        </div>
        <div class="form-block">
          <div class="form-label">NOTE</div>
          <textarea v-model="heartRateForm.note" class="metric-input" rows="2"></textarea>
        </div>
        <button class="form-submit-btn" @click="submitHeartRate">COMMIT LOG</button>

        <div class="history-section">
          <div class="section-title">RECENT · 心率</div>
          <div v-for="(record, idx) in recentHeartRate" :key="idx" class="history-item">
            <span class="history-date">{{ record.date }}</span>
            <span class="history-detail">{{ record.summary }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Today snapshot -->
    <aside class="form-aside">
      <h3 class="text-label-sm text-on-surface-variant uppercase tracking-widest aside-title">
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
      <div v-if="bodyMetricsSummary" class="summary-block">
        <div class="summary-item">
          <div class="summary-label">BMI</div>
          <div class="summary-value">{{ bodyMetricsSummary.bmi || '未设置身高' }}</div>
        </div>
        <div class="summary-item">
          <div class="summary-label">WEIGHT CHANGE</div>
          <div class="summary-value">{{ bodyMetricsSummary.weightChangeRate ? bodyMetricsSummary.weightChangeRate + '%' : '-' }}</div>
        </div>
      </div>
      <!-- 身高设置 -->
      <div class="height-setting">
        <div class="summary-label">HEIGHT (CM)</div>
        <div class="height-row">
          <input
            v-model.number="heightCm"
            class="metric-input height-input"
            type="number"
            min="80"
            max="250"
            step="1"
            placeholder="输入身高"
            @blur="saveHeight"
            @keyup.enter="saveHeight"
          />
          <button
            v-if="heightCm !== savedHeightCm"
            class="height-save-btn"
            @click="saveHeight"
          >SAVE</button>
        </div>
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
        chestGirth: null,
        waistGirth: null,
        hipGirth: null,
        armGirth: null,
        thighGirth: null,
      },
      fatigueOptions: ["低", "中", "高"],
      activeMetricsTab: 'body',
      heartRateForm: {
        date: new Date().toISOString().slice(0, 10),
        restingHr: null,
        maxHr: null,
        hrv: null,
        note: ''
      },
      heightCm: null as number | null,
      savedHeightCm: null as number | null,
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
    this.fetchBodyMetricsSummary();
    this.loadHeight();
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
        chestGirth: this.form.chestGirth,
        waistGirth: this.form.waistGirth,
        hipGirth: this.form.hipGirth,
        armGirth: this.form.armGirth,
        thighGirth: this.form.thighGirth,
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
          me.fetchBodyMetricsSummary();
        })
        .catch(function () {
          window.sessionStorage.setItem(
            "fitmate:pending-draft",
            me.buildBodyMetricsPrompt(formData)
          );
          me.$router.push("/chat");
        });
    },
    switchMetricsTab: function (tab) {
      this.activeMetricsTab = tab;
      if (tab === 'heart-rate' && this.recentHeartRate.length === 0) this.fetchRecentHeartRate();
    },
    submitHeartRate: function () {
      var me = this;
      var formData = {
        date: this.heartRateForm.date,
        restingHr: this.heartRateForm.restingHr,
        maxHr: this.heartRateForm.maxHr,
        hrv: this.heartRateForm.hrv,
        note: this.heartRateForm.note
      };
      doctorApi.logHeartRate(formData).then(function () {
        me.fetchRecentHeartRate();
      }).catch(function () {
        window.sessionStorage.setItem("fitmate:pending-draft", me.buildHeartRatePrompt(formData));
        me.$router.push("/chat");
      });
    },
    buildHeartRatePrompt: function (data) {
      var parts = [];
      if (data.restingHr) parts.push('静息心率' + data.restingHr);
      if (data.maxHr) parts.push('最大心率' + data.maxHr);
      if (data.hrv) parts.push('HRV ' + data.hrv + 'ms');
      return '我今天测了' + parts.join('，') + '，请帮我记录';
    },
    loadHeight() {
      var me = this;
      doctorApi.getUserPreferences().then(function (res) {
        var data = res && res.data;
        if (data && data.heightCm) {
          me.heightCm = data.heightCm;
          me.savedHeightCm = data.heightCm;
        }
      }).catch(function () {});
    },
    saveHeight() {
      var v = this.heightCm;
      if (v == null || v < 80 || v > 250) {
        return;
      }
      if (v === this.savedHeightCm) return;
      var me = this;
      // 先获取已有偏好，合并 heightCm 后再保存，避免覆盖 themeMode/accentColor
      doctorApi.getUserPreferences().then(function (res) {
        var existing = (res && res.data) || {};
        existing.heightCm = v;
        return doctorApi.saveUserPreferences(existing);
      }).then(function () {
        me.savedHeightCm = v;
        me.showUiMessage("success", "身高已保存，BMI已更新");
        me.fetchBodyMetricsSummary();
      }).catch(function () {
        me.showUiMessage("error", "保存身高失败");
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
  border-bottom: 3px solid var(--color-outline);
  padding-bottom: 20px;
  position: relative;
}
.form-header::after {
  content: "";
  position: absolute;
  bottom: -3px;
  left: 0;
  width: 64px;
  height: 6px;
  background: var(--color-primary);
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
  border-bottom: 2px solid var(--color-outline-variant);
  padding-bottom: 32px;
}

.form-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  position: relative;
  padding-left: 12px;
}
.form-section-head::before {
  content: "";
  position: absolute;
  left: 0;
  top: 2px;
  bottom: 2px;
  width: 4px;
  background: var(--color-primary);
}
.form-section-head h2 {
  letter-spacing: 0.14em;
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
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

.metric-current {
  color: var(--color-on-surface-variant);
}

.group:focus-within .metric-current {
  color: var(--color-primary);
}

.metric-input {
  background: var(--color-surface);
  border: 3px solid var(--color-outline);
  box-shadow: inset 2px 2px 0 0 rgba(16, 16, 16, 0.25);
  color: var(--color-on-surface);
  font-size: 16px;
  padding: 6px 10px;
  outline: none;
  transition: border-color 0.1s;
}

.metric-input:focus {
  border-color: var(--pixel-blue);
}

.metric-input::placeholder {
  color: var(--color-on-surface-variant);
  opacity: 0.6;
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
  background: var(--color-surface);
  border: 3px solid var(--color-outline);
  box-shadow: inset 2px 2px 0 0 rgba(16, 16, 16, 0.25);
  color: var(--color-on-surface);
  font-size: 16px;
  padding: 10px 12px;
  outline: none;
  resize: vertical;
  transition: border-color 0.1s;
}

.metric-textarea:focus {
  border-color: var(--pixel-blue);
}

.metric-textarea::placeholder {
  color: var(--color-on-surface-variant);
  opacity: 0.6;
}

.fatigue-toggle {
  display: flex;
  gap: 8px;
}

.fatigue-pill {
  flex: 1;
  padding: 8px 0;
  border: 2px solid var(--color-outline);
  background: var(--color-surface);
  color: var(--color-on-surface-variant);
  font-size: 15px;
  cursor: pointer;
  box-shadow: 2px 2px 0 0 #101010;
  transition: background-color 0.1s, color 0.1s, transform 0.1s, box-shadow 0.1s;
}

.fatigue-pill:hover {
  color: var(--color-on-surface);
  transform: translate(-1px, -1px);
  box-shadow: 3px 3px 0 0 #101010;
}

.fatigue-pill:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}

.fatigue-pill-active {
  color: var(--color-on-primary);
  background: var(--color-primary);
}

.fatigue-pill-active:hover {
  color: var(--color-on-primary);
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
  border-bottom: 2px solid var(--color-outline-variant);
}

.history-date {
  font-size: 14px;
  color: var(--color-on-surface-variant);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.history-detail {
  font-size: 15px;
  color: var(--color-on-surface-variant);
}

.form-submit-bar {
  display: flex;
  justify-content: flex-end;
}

.form-submit-btn {
  padding: 10px 28px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  border: 3px solid var(--color-outline);
  box-shadow: 4px 4px 0 0 #101010;
  font-size: 14px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  cursor: pointer;
  transition: transform 0.1s, box-shadow 0.1s, opacity 0.1s;
}

.form-submit-btn:hover:not(:disabled) {
  transform: translate(-1px, -1px);
  box-shadow: 5px 5px 0 0 #101010;
}

.form-submit-btn:active:not(:disabled) {
  transform: translate(2px, 2px);
  box-shadow: 2px 2px 0 0 #101010;
}

.form-submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: 2px 2px 0 0 #666666;
}

.form-aside {
  width: 280px;
  flex-shrink: 0;
  border-left: 3px solid var(--color-outline);
  padding: 32px 24px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  position: relative;
}

.aside-title {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--color-outline-variant);
}

.aside-stat {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
}

.aside-stat-label {
  font-size: 14px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

.aside-stat-value {
  font-size: 17px;
  color: var(--color-on-surface);
  font-variant-numeric: tabular-nums;
}

.aside-stat-value small {
  font-size: 13px;
  color: var(--color-on-surface-variant);
}

.status-good {
  color: var(--color-secondary);
}

.status-warn {
  color: var(--color-tertiary);
}

.status-alert {
  color: var(--color-error);
}

.tab-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.tab-btn {
  padding: 6px 16px;
  border: 2px solid var(--color-outline);
  background: var(--color-surface);
  color: var(--color-on-surface-variant);
  cursor: pointer;
  font-size: 15px;
  letter-spacing: 0.04em;
  box-shadow: 2px 2px 0 0 #101010;
  transition: background-color 0.1s, color 0.1s, transform 0.1s, box-shadow 0.1s;
}

.tab-btn:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}

.tab-btn-active {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.heart-rate-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

.form-row {
  display: flex;
  gap: 12px;
}

.form-row .form-block,
.form-row .girth-item {
  flex: 1;
  min-width: 60px;
}

.girth-item {
  flex: 1;
  min-width: 60px;
}

.history-section {
  margin-top: 24px;
}

.section-title {
  font-size: 14px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
  margin-bottom: 8px;
}

.summary-block {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 2px solid var(--color-outline-variant);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.summary-label {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

.summary-value {
  font-size: 20px;
  color: var(--color-on-surface);
  font-variant-numeric: tabular-nums;
}

.height-setting {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 2px solid var(--color-outline-variant);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.height-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.height-input {
  flex: 1;
  font-size: 16px;
}
.height-save-btn {
  padding: 4px 12px;
  border: 2px solid var(--color-outline);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  cursor: pointer;
  box-shadow: 2px 2px 0 0 #101010;
  transition: transform 0.1s, box-shadow 0.1s;
  flex-shrink: 0;
}
.height-save-btn:hover {
  transform: translate(-1px, -1px);
  box-shadow: 3px 3px 0 0 #101010;
}
.height-save-btn:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}

@media (max-width: 900px) {
  .form-page {
    flex-direction: column;
  }
  .form-aside {
    width: auto;
    border-left: none;
    border-top: 3px solid var(--color-outline);
  }
  .metrics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
