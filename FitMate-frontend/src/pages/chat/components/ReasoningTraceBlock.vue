<template>
  <div
    v-if="hasContent()"
    class="reasoning-trace-block"
    :class="{ 'is-active': isThinking }"
  >
    <div class="reasoning-trace-header" @click="$emit('toggle-thinking')">
      <div class="reasoning-trace-title">
        <span v-if="isThinking" class="reasoning-trace-dot"></span>
        <span class="reasoning-trace-label">{{ headerTitle() }}</span>
      </div>
      <button
        type="button"
        class="reasoning-trace-toggle"
        :aria-expanded="expanded ? 'true' : 'false'"
      >
        {{ expanded ? "收起" : "展开" }}
      </button>
    </div>

    <div v-if="expanded" class="reasoning-trace-body">
      <div v-if="steps && steps.length > 0" class="reasoning-trace-timeline">
        <div
          v-for="(step, index) in steps"
          :key="step.id || step.stepNo || index"
          class="reasoning-trace-step"
          :class="stepClass(step)"
        >
          <div class="reasoning-trace-indicator">
            <span class="reasoning-trace-step-dot"></span>
            <span v-if="index < steps.length - 1" class="reasoning-trace-line"></span>
          </div>
          <span class="reasoning-trace-step-label">{{ resolveStepLabel(step) }}</span>
        </div>
      </div>

      <div v-if="thinkingContent" class="reasoning-trace-content">{{ thinkingContent }}</div>
    </div>

    <div v-else class="reasoning-trace-collapsed">
      {{ collapsedText() }}
    </div>
  </div>
</template>

<script lang="ts">
export default {
  name: "ReasoningTraceBlock",
  emits: ["toggle-thinking"],
  props: {
    thinkingContent: {
      type: String,
      default: "",
    },
    steps: {
      type: Array,
      default: function () {
        return [];
      },
    },
    isThinking: {
      type: Boolean,
      default: false,
    },
    expanded: {
      type: Boolean,
      default: true,
    },
  },
  methods: {
    hasContent() {
      return (
        (this.thinkingContent && this.thinkingContent.length > 0) ||
        (this.steps && this.steps.length > 0) ||
        this.isThinking
      );
    },
    headerTitle() {
      if (this.isThinking) {
        return "思考中...";
      }
      if (this.thinkingContent) {
        return "思考过程";
      }
      if (this.steps && this.steps.length > 0) {
        return "执行轨迹 · " + this.steps.length + " 步";
      }
      return "思考过程";
    },
    collapsedText() {
      if (this.thinkingContent) {
        var text = String(this.thinkingContent).replace(/\s+/g, " ").trim();
        if (text.length > 60) {
          return text.slice(0, 60) + "...";
        }
        return text;
      }
      if (this.isThinking) {
        return "正在思考中...";
      }
      return "点击展开查看";
    },
    normalizeStepStatus(step) {
      var rawStatus = step && (step.status != null ? step.status : step.stepStatus);
      var status = rawStatus == null ? "pending" : String(rawStatus).toLowerCase();
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
    stepClass(step) {
      var status = this.normalizeStepStatus(step);
      return {
        "step-completed": status === "completed",
        "step-running": status === "running",
        "step-pending": status === "pending",
        "step-failed": status === "failed",
      };
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
  },
};
</script>

<style scoped>
.reasoning-trace-block {
  margin-bottom: 10px;
  padding: 10px 14px;
  border: 1px solid var(--color-surface-container);
  border-radius: 8px;
  background: var(--color-surface-container-low);
  font-size: 13px;
}

.reasoning-trace-block.is-active {
  border-color: color-mix(in srgb, var(--color-primary) 40%, transparent);
}

.reasoning-trace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  user-select: none;
}

.reasoning-trace-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.reasoning-trace-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary-fixed-dim);
  animation: reasoning-pulse 1.4s ease-in-out infinite;
}

@keyframes reasoning-pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}

.reasoning-trace-label {
  font-family: "Inter", sans-serif;
  font-size: 12px;
  color: var(--color-on-surface-variant);
  letter-spacing: 0.03em;
}

.reasoning-trace-toggle {
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

.reasoning-trace-toggle:hover {
  color: var(--color-primary-fixed);
}

.reasoning-trace-body {
  margin-top: 10px;
}

.reasoning-trace-timeline {
  display: flex;
  flex-direction: column;
  margin-bottom: 10px;
}

.reasoning-trace-step {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-height: 28px;
}

.reasoning-trace-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.reasoning-trace-step-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-outline-variant);
  margin-top: 4px;
  transition: background 0.2s ease;
}

.reasoning-trace-line {
  width: 1px;
  flex: 1;
  min-height: 14px;
  background: var(--color-surface-container);
  margin-top: 2px;
}

.reasoning-trace-step-label {
  font-size: 12px;
  font-family: "Inter", sans-serif;
  color: var(--color-on-surface-variant);
  padding-top: 1px;
}

.reasoning-trace-step.step-completed .reasoning-trace-step-dot {
  background: var(--color-primary-fixed-dim);
}

.reasoning-trace-step.step-completed .reasoning-trace-step-label {
  color: var(--color-on-surface-variant);
}

.reasoning-trace-step.step-running .reasoning-trace-step-dot {
  background: var(--color-primary-fixed-dim);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary) 20%, transparent);
}

.reasoning-trace-step.step-running .reasoning-trace-step-label {
  color: var(--color-on-surface);
}

.reasoning-trace-step.step-failed .reasoning-trace-step-dot {
  background: var(--color-error);
}

.reasoning-trace-step.step-failed .reasoning-trace-step-label {
  color: var(--color-error);
}

.reasoning-trace-content {
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-on-surface-variant);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "Inter", sans-serif;
  max-height: 200px;
  overflow-y: auto;
}

.reasoning-trace-collapsed {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-on-surface-variant);
  font-family: "Inter", sans-serif;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
