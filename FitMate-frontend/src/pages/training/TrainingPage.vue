<template>
  <div class="form-page">
    <div class="form-main">
      <!-- Header -->
      <header class="form-header">
        <h1 class="font-inter text-display-lg text-on-surface tracking-tight">
          Training Log
        </h1>
        <p class="font-inter text-body-base text-on-surface-variant">
          记录今天的力量训练动作与表现。
        </p>
      </header>

      <!-- Strength Protocol -->
      <section class="form-section">
        <div class="form-section-head">
          <span class="material-symbols-outlined text-primary" style="font-size: 20px;">exercise</span>
          <h2 class="font-inter text-label-sm text-on-surface uppercase tracking-widest">
            Strength Protocol
          </h2>
        </div>

        <div class="protocol-table">
          <div class="protocol-row protocol-row-head">
            <span class="protocol-col-movement">Movement</span>
            <span class="protocol-col-num">Sets</span>
            <span class="protocol-col-num">Reps</span>
            <span class="protocol-col-num">Load (kg)</span>
            <span class="protocol-col-action"></span>
          </div>

          <div
            v-for="(ex, idx) in exercises"
            :key="idx"
            class="protocol-row"
          >
            <input
              v-model="ex.name"
              class="protocol-input protocol-col-movement"
              type="text"
              placeholder="卧推 / 深蹲 / 硬拉"
            />
            <input
              v-model.number="ex.sets"
              class="protocol-input protocol-col-num"
              type="number"
              min="1"
              max="99"
              placeholder="0"
            />
            <input
              v-model.number="ex.reps"
              class="protocol-input protocol-col-num"
              type="number"
              min="1"
              max="999"
              placeholder="0"
            />
            <input
              v-model.number="ex.weight"
              class="protocol-input protocol-col-num"
              type="number"
              min="0"
              max="9999"
              step="2.5"
              placeholder="0"
            />
            <button
              type="button"
              class="protocol-remove"
              :disabled="exercises.length <= 1"
              title="删除"
              @click="removeExercise(idx)"
            >
              <span class="material-symbols-outlined" style="font-size: 18px;">close</span>
            </button>
          </div>
        </div>

        <button type="button" class="form-add-btn" @click="addExercise">
          <span class="material-symbols-outlined" style="font-size: 16px;">add</span>
          ADD MOVEMENT
        </button>
      </section>

      <!-- Recent -->
      <section v-if="recentTraining.length > 0" class="form-section">
        <div class="form-section-head">
          <span class="material-symbols-outlined text-primary" style="font-size: 20px;">history</span>
          <h2 class="font-inter text-label-sm text-on-surface uppercase tracking-widest">
            Recent Sessions
          </h2>
        </div>
        <div class="history-list">
          <div
            v-for="(record, idx) in recentTraining"
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
          @click="submitTraining"
        >
          COMMIT LOG
        </button>
      </div>
    </div>

    <!-- Week at a glance -->
    <aside class="form-aside">
      <h3 class="font-inter text-label-sm text-on-surface-variant uppercase tracking-widest aside-title">
        Week at a Glance
      </h3>
      <div class="aside-stat">
        <span class="aside-stat-label">Sessions</span>
        <span class="aside-stat-value">{{ weekSummary.trainingDays || 0 }}</span>
      </div>
      <div class="aside-stat">
        <span class="aside-stat-label">Total Volume</span>
        <span class="aside-stat-value">{{ weekSummary.totalVolume || 0 }} <small>kg</small></span>
      </div>
      <div class="aside-stat">
        <span class="aside-stat-label">Trend</span>
        <span class="aside-stat-value" :class="trendClass">{{ weekSummary.trend || "暂无数据" }}</span>
      </div>
    </aside>
  </div>
</template>

<script lang="ts">
import ChatLogicBase from "../chat/ChatLogicBase.vue";
import doctorApi from "../../services/doctorApi";

export default {
  name: "TrainingPage",
  extends: ChatLogicBase,
  data() {
    return {
      exercises: [{ name: "", sets: 4, reps: 10, weight: 0 }],
    };
  },
  computed: {
    canSubmit() {
      for (var i = 0; i < this.exercises.length; i++) {
        if ((this.exercises[i].name || "").trim()) {
          return true;
        }
      }
      return false;
    },
    trendClass() {
      var trend = this.weekSummary.trend || "";
      if (trend.indexOf("上升") >= 0 || trend.indexOf("增长") >= 0) {
        return "trend-up";
      }
      if (trend.indexOf("下降") >= 0 || trend.indexOf("减少") >= 0) {
        return "trend-down";
      }
      return "trend-stable";
    },
  },
  mounted() {
    this.fetchRecentTraining();
  },
  methods: {
    addExercise() {
      this.exercises.push({ name: "", sets: 4, reps: 10, weight: 0 });
    },
    removeExercise(idx) {
      if (this.exercises.length <= 1) {
        return;
      }
      this.exercises.splice(idx, 1);
    },
    submitTraining() {
      var valid = [];
      for (var i = 0; i < this.exercises.length; i++) {
        var ex = this.exercises[i];
        if ((ex.name || "").trim()) {
          valid.push({
            name: ex.name.trim(),
            sets: ex.sets || 1,
            reps: ex.reps || 1,
            weight: ex.weight || 0,
          });
        }
      }
      if (valid.length === 0) {
        return;
      }
      var formData = {
        exercises: valid,
        date: new Date().toISOString().slice(0, 10),
      };
      var me = this;
      doctorApi
        .logTraining(formData)
        .then(function () {
          me.showUiMessage("success", "训练记录已保存");
          me.exercises = [{ name: "", sets: 4, reps: 10, weight: 0 }];
          me.fetchRecentTraining();
        })
        .catch(function () {
          // API unavailable: fall back to agent chat with prefilled prompt
          window.sessionStorage.setItem(
            "fitmate:pending-draft",
            me.buildTrainingPrompt(valid)
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

.protocol-table {
  display: flex;
  flex-direction: column;
}

.protocol-row {
  display: grid;
  grid-template-columns: 1fr 70px 70px 90px 32px;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-surface-container);
}

.protocol-row-head {
  border-bottom: 1px solid var(--color-surface-container-high);
}

.protocol-row-head span {
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

.protocol-col-num {
  text-align: right;
}

.protocol-input {
  background: transparent;
  border: none;
  border-bottom: 1px solid transparent;
  color: var(--color-on-surface);
  font-size: 15px;
  font-family: "Inter", sans-serif;
  padding: 4px 0;
  outline: none;
  transition: border-color 0.2s ease;
  width: 100%;
}

.protocol-input.protocol-col-num {
  text-align: right;
}

.protocol-input:focus {
  border-bottom-color: var(--color-primary-fixed-dim);
}

.protocol-input::placeholder {
  color: var(--color-surface-bright);
}

.protocol-input::-webkit-outer-spin-button,
.protocol-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.protocol-input[type="number"] {
  -moz-appearance: textfield;
}

.protocol-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-on-surface-variant);
  cursor: pointer;
  transition: color 0.2s ease, background 0.2s ease;
}

.protocol-remove:hover:not(:disabled) {
  color: var(--color-error);
  background: color-mix(in srgb, var(--color-error) 8%, transparent);
}

.protocol-remove:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.form-add-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  background: transparent;
  border: none;
  color: var(--color-primary-fixed-dim);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.05em;
  font-family: "Inter", sans-serif;
  cursor: pointer;
  padding: 4px 0;
}

.form-add-btn:hover {
  color: var(--color-primary-fixed);
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

.trend-up {
  color: var(--color-tertiary);
}

.trend-down {
  color: var(--color-error);
}

.trend-stable {
  color: var(--color-on-surface-variant);
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
}
</style>
