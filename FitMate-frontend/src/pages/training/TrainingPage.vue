<template>
  <div class="form-page">
    <div class="form-main">
      <!-- Header -->
      <header class="form-header">
        <h1 class="text-display-lg text-on-surface tracking-tight">
          Training Log
        </h1>
        <p class="text-body-base text-on-surface-variant">
          记录今天的力量训练动作与表现。
        </p>
      </header>

      <!-- Tab Bar -->
      <div class="tab-bar">
        <button
          v-for="tab in [
            { key: 'strength', label: '力量' },
            { key: 'cardio', label: '有氧' },
            { key: 'diet', label: '训练营养' }
          ]"
          :key="tab.key"
          class="tab-btn"
          :class="{ 'tab-btn-active': activeTrainingTab === tab.key }"
          @click="switchTrainingTab(tab.key)"
        >{{ tab.label }}</button>
      </div>

      <div v-if="activeTrainingTab === 'strength'">
      <!-- Strength Protocol -->
      <section class="form-section">
        <div class="form-section-head">
          <span class="material-symbols-outlined text-primary" style="font-size: 20px;">exercise</span>
          <h2 class="text-label-sm text-on-surface uppercase tracking-widest">
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
          <h2 class="text-label-sm text-on-surface uppercase tracking-widest">
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
      </div><!-- end strength tab -->

      <!-- Cardio Tab -->
      <div v-if="activeTrainingTab === 'cardio'" class="cardio-section">
        <div class="form-block">
          <div class="form-label">DATE</div>
          <input v-model="cardioForm.date" class="metric-input" type="date" />
        </div>
        <div class="form-block">
          <div class="form-label">TYPE</div>
          <select v-model="cardioForm.cardioType" class="metric-input">
            <option v-for="opt in cardioTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </div>
        <div class="form-row">
          <div class="form-block">
            <div class="form-label">DISTANCE (KM)</div>
            <input v-model.number="cardioForm.distanceKm" class="metric-input" type="number" step="0.1" placeholder="0.0" />
          </div>
          <div class="form-block">
            <div class="form-label">DURATION (MIN)</div>
            <input v-model.number="cardioForm.durationMinutes" class="metric-input" type="number" placeholder="0" />
          </div>
        </div>
        <div class="form-block">
          <div class="form-label">AVG HEART RATE</div>
          <input v-model.number="cardioForm.avgHeartRate" class="metric-input" type="number" placeholder="optional" />
        </div>
        <div class="form-block">
          <div class="form-label">NOTE</div>
          <textarea v-model="cardioForm.note" class="metric-input" rows="2"></textarea>
        </div>
        <button class="form-submit-btn" @click="submitCardio">COMMIT LOG</button>

        <div class="history-section">
          <div class="section-title">RECENT · 有氧</div>
          <div v-for="(record, idx) in recentCardio" :key="idx" class="history-item">
            <span class="history-date">{{ record.date }}</span>
            <span class="history-detail">{{ record.summary }}</span>
          </div>
        </div>
      </div>

      <!-- Diet Tab -->
      <div v-if="activeTrainingTab === 'diet'" class="diet-section">
        <div class="form-block">
          <div class="form-label">DATE</div>
          <input v-model="dietForm.date" class="metric-input" type="date" />
        </div>
        <div class="form-block">
          <div class="form-label">MEAL</div>
          <select v-model="dietForm.mealType" class="metric-input">
            <option v-for="opt in mealTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </div>
        <div class="form-block">
          <div class="form-label">FOOD ITEMS</div>
          <div v-for="(item, idx) in dietForm.items" :key="idx" class="diet-item-row">
            <input v-model="item.foodName" class="metric-input" placeholder="食物名" />
            <input v-model="item.portion" class="metric-input" placeholder="份量" />
            <input v-model.number="item.calories" class="metric-input" type="number" placeholder="kcal" />
            <input v-model.number="item.protein" class="metric-input" type="number" placeholder="蛋白g" />
            <input v-model.number="item.carbs" class="metric-input" type="number" placeholder="碳水g" />
            <input v-model.number="item.fat" class="metric-input" type="number" placeholder="脂肪g" />
            <button class="remove-item-btn" @click="dietForm.items.splice(idx, 1)">×</button>
          </div>
          <button class="add-item-btn" @click="dietForm.items.push({ foodName: '', portion: '', calories: null, protein: null, carbs: null, fat: null })">ADD ITEM</button>
        </div>
        <div class="form-block">
          <div class="form-label">NOTE</div>
          <textarea v-model="dietForm.note" class="metric-input" rows="2"></textarea>
        </div>
        <button class="form-submit-btn" @click="submitDiet">COMMIT LOG</button>

        <div class="history-section">
          <div class="section-title">RECENT · 训练营养</div>
          <div v-for="(record, idx) in recentDiet" :key="idx" class="history-item">
            <span class="history-date">{{ record.date }}</span>
            <span class="history-detail">{{ record.summary }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Week at a glance -->
    <aside class="form-aside">
      <h3 class="text-label-sm text-on-surface-variant uppercase tracking-widest aside-title">
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
      <div v-if="trainingSummary" class="summary-grid">
        <div class="summary-card">
          <div class="summary-label">WEEK VOLUME</div>
          <div class="summary-value tabular-nums">{{ trainingSummary.weekVolume || 0 }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">MONTH VOLUME</div>
          <div class="summary-value tabular-nums">{{ trainingSummary.monthVolume || 0 }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">WEEK DAYS</div>
          <div class="summary-value tabular-nums">{{ trainingSummary.weekTrainingDays || 0 }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">MONTH DAYS</div>
          <div class="summary-value tabular-nums">{{ trainingSummary.monthTrainingDays || 0 }}</div>
        </div>
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
      activeTrainingTab: 'strength',
      cardioForm: {
        date: new Date().toISOString().slice(0, 10),
        cardioType: 'running',
        distanceKm: null,
        durationMinutes: null,
        avgHeartRate: null,
        note: ''
      },
      cardioTypeOptions: [
        { value: 'running', label: '跑步' },
        { value: 'cycling', label: '骑行' },
        { value: 'swimming', label: '游泳' },
        { value: 'rowing', label: '划船' },
        { value: 'jump_rope', label: '跳绳' },
        { value: 'other', label: '其他' }
      ],
      dietForm: {
        date: new Date().toISOString().slice(0, 10),
        mealType: 'breakfast',
        items: [{ foodName: '', portion: '', calories: null, protein: null, carbs: null, fat: null }],
        note: ''
      },
      mealTypeOptions: [
        { value: 'breakfast', label: '早餐' },
        { value: 'lunch', label: '午餐' },
        { value: 'dinner', label: '晚餐' },
        { value: 'snack', label: '加餐' }
      ],
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
    this.fetchTrainingSummary();
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
    switchTrainingTab: function (tab) {
      this.activeTrainingTab = tab;
      if (tab === 'cardio' && this.recentCardio.length === 0) this.fetchRecentCardio();
      if (tab === 'diet' && this.recentDiet.length === 0) this.fetchRecentDiet();
    },
    submitCardio: function () {
      var me = this;
      var formData = {
        date: this.cardioForm.date,
        cardioType: this.cardioForm.cardioType,
        distanceKm: this.cardioForm.distanceKm,
        durationMinutes: this.cardioForm.durationMinutes,
        avgHeartRate: this.cardioForm.avgHeartRate,
        note: this.cardioForm.note
      };
      doctorApi.logCardio(formData).then(function () {
        me.fetchRecentCardio();
        me.fetchTrainingSummary();
      }).catch(function () {
        window.sessionStorage.setItem("fitmate:pending-draft", me.buildCardioPrompt(formData));
        me.$router.push("/chat");
      });
    },
    submitDiet: function () {
      var me = this;
      var validItems = this.dietForm.items.filter(function (it) { return (it.foodName || '').trim(); });
      var formData = {
        date: this.dietForm.date,
        mealType: this.dietForm.mealType,
        items: validItems,
        note: this.dietForm.note
      };
      doctorApi.logDiet(formData).then(function () {
        me.fetchRecentDiet();
      }).catch(function () {
        window.sessionStorage.setItem("fitmate:pending-draft", me.buildDietPrompt(formData));
        me.$router.push("/chat");
      });
    },
    buildCardioPrompt: function (data) {
      var typeMap = { running: '跑步', cycling: '骑行', swimming: '游泳', rowing: '划船', jump_rope: '跳绳', other: '其他' };
      var parts = [data.date, typeMap[data.cardioType] || data.cardioType];
      if (data.distanceKm) parts.push(data.distanceKm + 'km');
      if (data.durationMinutes) parts.push(data.durationMinutes + 'min');
      return '我今天做了' + parts.join(' ') + '，请帮我记录';
    },
    buildDietPrompt: function (data) {
      var mealMap = { breakfast: '早餐', lunch: '午餐', dinner: '晚餐', snack: '加餐' };
      var items = data.items.map(function (it) {
        return it.foodName + (it.portion ? '(' + it.portion + ')' : '') + (it.calories ? ' ' + it.calories + 'kcal' : '');
      }).join('、');
      return '我今天' + (mealMap[data.mealType] || data.mealType) + '吃了：' + items + '，请帮我记录';
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
  border-bottom: 2px solid var(--color-outline-variant);
}

.protocol-row-head {
  border-bottom: 3px solid var(--color-outline);
}

.protocol-row-head span {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

.protocol-col-num {
  text-align: right;
}

.protocol-input {
  background: var(--color-surface);
  border: 2px solid var(--color-outline-variant);
  color: var(--color-on-surface);
  font-size: 16px;
  padding: 4px 6px;
  outline: none;
  transition: border-color 0.1s;
  width: 100%;
}

.protocol-input.protocol-col-num {
  text-align: right;
}

.protocol-input:focus {
  border-color: var(--pixel-blue);
}

.protocol-input::placeholder {
  color: var(--color-on-surface-variant);
  opacity: 0.6;
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
  border: 2px solid var(--color-outline);
  background: var(--color-surface);
  color: var(--color-on-surface-variant);
  cursor: pointer;
  box-shadow: 2px 2px 0 0 #101010;
  transition: background-color 0.1s, color 0.1s, transform 0.1s, box-shadow 0.1s;
}

.protocol-remove:hover:not(:disabled) {
  background: var(--color-error);
  color: var(--color-on-error);
}

.protocol-remove:active:not(:disabled) {
  transform: translate(2px, 2px);
  box-shadow: none;
}

.protocol-remove:disabled {
  opacity: 0.3;
  cursor: not-allowed;
  box-shadow: 2px 2px 0 0 #666666;
}

.form-add-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  background: var(--color-tertiary);
  border: 2px solid var(--color-outline);
  color: var(--color-on-tertiary);
  font-size: 13px;
  letter-spacing: 0.05em;
  cursor: pointer;
  padding: 6px 12px;
  box-shadow: 2px 2px 0 0 #101010;
  transition: transform 0.1s, box-shadow 0.1s;
}

.form-add-btn:hover {
  transform: translate(-1px, -1px);
  box-shadow: 3px 3px 0 0 #101010;
}

.form-add-btn:active {
  transform: translate(2px, 2px);
  box-shadow: none;
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

.trend-up {
  color: var(--color-secondary);
}

.trend-down {
  color: var(--color-error);
}

.trend-stable {
  color: var(--color-on-surface-variant);
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

.cardio-section,
.diet-section {
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

.diet-item-row {
  display: flex;
  gap: 6px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.diet-item-row .metric-input {
  flex: 1;
  min-width: 80px;
}

.remove-item-btn {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  align-self: center;
  background: var(--color-error);
  border: 2px solid var(--color-outline);
  color: var(--color-on-error);
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
  padding: 0;
  box-shadow: 2px 2px 0 0 #101010;
  transition: transform 0.1s, box-shadow 0.1s;
}

.remove-item-btn:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}

.add-item-btn {
  background: var(--color-surface);
  border: 2px solid var(--color-outline);
  color: var(--color-on-surface);
  padding: 6px 12px;
  cursor: pointer;
  font-size: 13px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  box-shadow: 2px 2px 0 0 #101010;
  transition: transform 0.1s, box-shadow 0.1s;
}

.add-item-btn:hover {
  transform: translate(-1px, -1px);
  box-shadow: 3px 3px 0 0 #101010;
}

.add-item-btn:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}

.form-row {
  display: flex;
  gap: 12px;
}

.form-row .form-block {
  flex: 1;
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

.summary-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 2px solid var(--color-outline-variant);
}

.summary-card {
  background: var(--color-surface);
  padding: 10px 12px;
  border: 3px solid var(--color-outline);
  border-top: 5px solid var(--color-primary);
  box-shadow: 3px 3px 0 0 #101010;
  transition: transform 0.1s, box-shadow 0.1s;
}
.summary-card:hover {
  transform: translate(-1px, -1px);
  box-shadow: 4px 4px 0 0 #101010;
}

.summary-label {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
  margin-bottom: 4px;
}

.summary-value {
  font-size: 20px;
  color: var(--color-on-surface);
  font-variant-numeric: tabular-nums;
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
}
</style>
