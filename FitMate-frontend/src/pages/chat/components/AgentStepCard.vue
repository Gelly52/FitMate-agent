<template>
  <div
    v-if="showCard()"
    class="agent-step-card"
    :class="{ 'is-active': isThinking || (isSending && !isStreaming) }"
    role="region"
    aria-label="Agent 执行轨迹与进度摘要"
  >
    <div class="agent-step-header">
      <div class="flex flex-col gap-xs">
        <h4
          class="font-inter text-label-sm text-on-surface uppercase tracking-widest"
        >
          Agent 执行轨迹
        </h4>
        <p class="font-inter text-label-xs text-on-surface-variant uppercase tracking-wider">
          {{ resolveCardSummary() }}
        </p>
      </div>
      <button
        type="button"
        class="agent-step-toggle"
        :aria-expanded="thinkingExpanded ? 'true' : 'false'"
        @click="$emit('toggle-thinking')"
      >
        {{ thinkingExpanded ? "收起" : "展开" }}
      </button>
    </div>

    <!-- Dynamic trace timeline -->
    <div v-if="steps.length > 0" class="agent-step-timeline">
      <div
        v-for="(step, index) in steps"
        :key="step.id || step.stepNo || index"
        class="agent-step-item"
        :class="stepClass(step)"
      >
        <div class="agent-step-indicator">
          <span class="agent-step-dot"></span>
          <span v-if="index < steps.length - 1" class="agent-step-line"></span>
        </div>
        <span class="agent-step-label">
          {{ resolveStepLabel(step) }}
          <span v-if="resolveStepMeta(step)" class="agent-step-meta">
            {{ resolveStepMeta(step) }}
          </span>
        </span>
        <!-- kb.search 子步骤 -->
        <div
          v-if="isKbSearchStep(step) && hasSubSteps(step)"
          class="kb-substeps"
        >
          <div
            v-for="(sub, subIdx) in resolveSubSteps(step)"
            :key="subIdx"
            class="kb-substep"
            :class="subStepClass(sub)"
          >
            <span class="kb-substep-icon">{{ subStepIcon(sub.type) }}</span>
            <span class="kb-substep-label">{{ subStepLabel(sub.type) }}</span>
            <span v-if="isSubStepRunning(sub)" class="kb-substep-running">...</span>
            <span v-else-if="isSubStepDone(sub)" class="kb-substep-done">✓</span>
            <span v-if="sub && sub.detail" class="kb-substep-detail">{{ sub.detail }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Thinking panel -->
    <div class="agent-thinking">
      <div class="agent-thinking-header">
        <div class="agent-thinking-title">
          <span
            v-if="isThinking || (isSending && !isStreaming)"
            class="agent-thinking-dot"
          ></span>
          {{ thinkingStatusTitle() }}
        </div>
        <span v-if="resolveThinkingMeta()" class="agent-thinking-meta">
          {{ resolveThinkingMeta() }}
        </span>
      </div>

      <div v-if="thinkingExpanded" class="agent-thinking-body">
        <template v-if="hasThinkingContent()">
          <div class="agent-thinking-content">{{ thinkingContent }}</div>
        </template>
        <template v-else>
          <div class="agent-thinking-placeholder">
            <span class="agent-thinking-dot"></span>
            正在执行任务，请稍候...
          </div>
        </template>
      </div>

      <div v-else class="agent-thinking-collapsed">
        {{ resolveCollapsedThinkingText() }}
      </div>
    </div>
  </div>
</template>

<script lang="ts">
export default {
  name: "AgentStepCard",
  emits: ["toggle-thinking"],
  props: {
    steps: {
      type: Array,
      default: function () {
        return [];
      },
    },
    thinkingContent: {
      type: String,
      default: "",
    },
    isThinking: {
      type: Boolean,
      default: false,
    },
    thinkingExpanded: {
      type: Boolean,
      default: true,
    },
    isSending: {
      type: Boolean,
      default: false,
    },
    isStreaming: {
      type: Boolean,
      default: false,
    },
  },
  methods: {
    showCard() {
      return (
        this.steps.length > 0 ||
        this.hasThinkingContent() ||
        (this.isSending && !this.isStreaming)
      );
    },
    hasThinkingContent() {
      return !!(this.thinkingContent && this.thinkingContent.length > 0);
    },
    normalizeStepStatus(step) {
      var rawStatus =
        step && (step.status != null ? step.status : step.stepStatus);
      var status =
        rawStatus == null ? "pending" : String(rawStatus).toLowerCase();
      if (status === "success" || status === "completed") {
        return "completed";
      }
      if (status === "running") {
        return "running";
      }
      if (status === "failed" || status === "error") {
        return "failed";
      }
      return "pending";
    },
    resolveStepLabel(step) {
      if (!step) {
        return "未命名步骤";
      }
      if (step.label || step.stepName) {
        return step.label || step.stepName;
      }
      var eventType = step.eventType ? String(step.eventType).toLowerCase() : "";
      if (eventType === "tool_call_started" && step.toolName) {
        return "调用工具：" + step.toolName;
      }
      if (eventType === "tool_call_finished" && step.toolName) {
        return "工具完成：" + step.toolName;
      }
      if (eventType === "tool_call_failed" && step.toolName) {
        return "工具失败：" + step.toolName;
      }
      if (eventType === "llm_started") {
        return "LLM 正在分析";
      }
      if (eventType === "llm_finished") {
        return "LLM 分析完成";
      }
      if (eventType === "final_answer" || eventType === "run_finished") {
        return "最终答案已生成";
      }
      if (eventType === "run_failed") {
        return "任务执行失败";
      }
      return step.message || "未命名事件";
    },
    resolveStepMeta(step) {
      if (!step) {
        return "";
      }
      var parts = [];
      if (step.iterationNo != null) {
        parts.push("第 " + step.iterationNo + " 轮");
      }
      if (step.durationMs != null) {
        parts.push(step.durationMs + "ms");
      }
      if (step.errorMessage) {
        parts.push(String(step.errorMessage));
      } else if (step.message && step.message !== this.resolveStepLabel(step)) {
        parts.push(String(step.message));
      }
      return parts.join(" · ");
    },
    stepClass(step) {
      var status = this.normalizeStepStatus(step);
      return {
        "step-completed": status === "completed",
        "step-running": status === "running",
        "step-pending": status === "pending",
        "step-failed": status === "failed",
      };
    },
    completedStepCount() {
      var count = 0;
      for (var i = 0; i < this.steps.length; i++) {
        if (this.normalizeStepStatus(this.steps[i]) === "completed") {
          count += 1;
        }
      }
      return count;
    },
    findStepByStatus(status) {
      for (var i = 0; i < this.steps.length; i++) {
        if (this.normalizeStepStatus(this.steps[i]) === status) {
          return this.steps[i];
        }
      }
      return null;
    },
    resolveCardSummary() {
      var failedStep = this.findStepByStatus("failed");
      if (failedStep) {
        return this.resolveStepLabel(failedStep) + " · 失败";
      }
      var runningStep = this.findStepByStatus("running");
      if (runningStep) {
        return this.resolveStepLabel(runningStep) + " · 执行中";
      }
      if (this.steps.length > 0) {
        var completedCount = this.completedStepCount();
        if (completedCount >= this.steps.length) {
          return "全部事件已完成";
        }
        return "已记录 " + this.steps.length + " 条事件";
      }
      if (this.isThinking || (this.isSending && !this.isStreaming)) {
        return "正在等待 Agent 执行事件";
      }
      if (this.hasThinkingContent()) {
        return "可查看本轮进度摘要";
      }
      return "等待开始";
    },
    thinkingStatusTitle() {
      if (this.isThinking || (this.isSending && !this.isStreaming)) {
        return "执行中...";
      }
      if (this.hasThinkingContent()) {
        return "进度摘要";
      }
      return "等待进度摘要";
    },
    resolveThinkingMeta() {
      var failedStep = this.findStepByStatus("failed");
      if (failedStep) {
        return "失败于「" + this.resolveStepLabel(failedStep) + "」";
      }
      var runningStep = this.findStepByStatus("running");
      if (runningStep) {
        return "当前步骤：" + this.resolveStepLabel(runningStep);
      }
      if (this.steps.length > 0) {
        return "共 " + this.steps.length + " 条事件";
      }
      if (this.isThinking || (this.isSending && !this.isStreaming)) {
        return "任务进行中";
      }
      if (this.hasThinkingContent()) {
        return "可回看";
      }
      return "";
    },
    resolveCollapsedThinkingText() {
      if (this.hasThinkingContent()) {
        var text = String(this.thinkingContent).replace(/\s+/g, " ").trim();
        if (text.length > 48) {
          return text.slice(0, 48) + "...";
        }
        return text || "可展开查看完整进度摘要";
      }
      if (this.isThinking || (this.isSending && !this.isStreaming)) {
        return "正在执行任务，请稍候...";
      }
      return this.resolveCardSummary();
    },
    isKbSearchStep(step) {
      if (!step) {
        return false;
      }
      return step.toolName === "kb.search";
    },
    hasSubSteps(step) {
      return (
        !!step &&
        Array.isArray(step.subSteps) &&
        step.subSteps.length > 0
      );
    },
    resolveSubSteps(step) {
      return step && Array.isArray(step.subSteps) ? step.subSteps : [];
    },
    subStepLabel(type) {
      var map = {
        wiki_search: "检索知识库 Wiki",
        query_rewrite: "改写查询",
        rag_search: "检索原始文档",
      };
      return map[type] || type || "子步骤";
    },
    subStepIcon(type) {
      var map = {
        wiki_search: "📚",
        query_rewrite: "✏️",
        rag_search: "📄",
      };
      return map[type] || "•";
    },
    subStepClass(sub) {
      var status =
        sub && sub.status ? String(sub.status).toLowerCase() : "pending";
      if (
        status === "done" ||
        status === "completed" ||
        status === "success"
      ) {
        return "sub-done";
      }
      if (status === "running") {
        return "sub-running";
      }
      if (status === "failed" || status === "error") {
        return "sub-failed";
      }
      return "sub-pending";
    },
    isSubStepRunning(sub) {
      return this.subStepClass(sub) === "sub-running";
    },
    isSubStepDone(sub) {
      return this.subStepClass(sub) === "sub-done";
    },
  },
};
</script>

<style scoped>
.agent-step-card {
  margin: 16px 0;
  padding: 16px 20px;
  border: 1px solid var(--color-surface-container);
  border-radius: 8px;
  background: var(--color-surface-container-low);
}

.agent-step-card.is-active {
  border-color: color-mix(in srgb, var(--color-primary) 40%, transparent);
}

.agent-step-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.agent-step-toggle {
  flex-shrink: 0;
  background: transparent;
  border: none;
  color: var(--color-primary-fixed-dim);
  font-size: 11px;
  font-family: "Inter", sans-serif;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  cursor: pointer;
}

.agent-step-toggle:hover {
  color: var(--color-primary-fixed);
}

.agent-step-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin-bottom: 16px;
}

.agent-step-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-height: 32px;
}

.agent-step-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.agent-step-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-outline-variant);
  margin-top: 4px;
  transition: background 0.2s ease;
}

.agent-step-line {
  width: 1px;
  flex: 1;
  min-height: 16px;
  background: var(--color-surface-container);
  margin-top: 2px;
}

.agent-step-label {
  font-size: 13px;
  font-family: "Inter", sans-serif;
  color: var(--color-on-surface-variant);
  padding-top: 1px;
  transition: color 0.2s ease;
}

.agent-step-meta {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--color-on-surface-variant);
}

.agent-step-item.step-completed .agent-step-dot {
  background: var(--color-primary-fixed-dim);
}

.agent-step-item.step-completed .agent-step-label {
  color: var(--color-on-surface-variant);
}

.agent-step-item.step-running .agent-step-dot {
  background: var(--color-primary-fixed-dim);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary) 20%, transparent);
}

.agent-step-item.step-running .agent-step-label {
  color: var(--color-on-surface);
}

.agent-step-item.step-failed .agent-step-dot {
  background: var(--color-error);
}

.agent-step-item.step-failed .agent-step-label {
  color: var(--color-error);
}

.agent-thinking {
  border-top: 1px solid var(--color-surface-container);
  padding-top: 12px;
}

.agent-thinking-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.agent-thinking-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-family: "Inter", sans-serif;
  color: var(--color-on-surface-variant);
}

.agent-thinking-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary-fixed-dim);
  animation: agent-pulse 1.4s ease-in-out infinite;
}

@keyframes agent-pulse {
  0%,
  100% {
    opacity: 0.3;
  }
  50% {
    opacity: 1;
  }
}

.agent-thinking-meta {
  font-size: 11px;
  color: var(--color-on-surface-variant);
  font-family: "Inter", sans-serif;
}

.agent-thinking-content {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-on-surface-variant);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "Inter", sans-serif;
}

.agent-thinking-placeholder {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-on-surface-variant);
  font-family: "Inter", sans-serif;
}

.agent-thinking-collapsed {
  font-size: 12px;
  color: var(--color-on-surface-variant);
  font-family: "Inter", sans-serif;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kb-substeps {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 6px;
  margin-left: 20px;
  padding-left: 10px;
  border-left: 1px solid var(--color-surface-container);
}

.kb-substep {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-family: "Inter", sans-serif;
  color: var(--color-on-surface-variant);
}

.kb-substep-icon {
  font-size: 12px;
  line-height: 1;
}

.kb-substep-label {
  color: var(--color-on-surface-variant);
  transition: color 0.2s ease;
}

.kb-substep-running {
  color: var(--color-primary-fixed-dim);
  font-size: 11px;
  animation: agent-pulse 1.4s ease-in-out infinite;
}

.kb-substep-done {
  color: var(--color-primary-fixed-dim);
  font-size: 11px;
}

.kb-substep-detail {
  color: var(--color-on-surface-variant);
  font-size: 11px;
  margin-left: 4px;
}

.kb-substep.sub-running .kb-substep-label {
  color: var(--color-on-surface);
}

.kb-substep.sub-done .kb-substep-label {
  color: var(--color-on-surface-variant);
}

.kb-substep.sub-failed .kb-substep-label {
  color: var(--color-error);
}
</style>
