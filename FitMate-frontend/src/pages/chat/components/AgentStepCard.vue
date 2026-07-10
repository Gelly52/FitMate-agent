<template>
  <div
    v-if="showCard()"
    class="agent-step-card"
    :class="{ 'is-active': isThinking || (isSending && !isStreaming) }"
    role="region"
    aria-label="Agent 执行轨迹与思考过程"
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
    </div>

    <!-- 融合时间线：复用 ReasoningTraceBlock，按轮次交错显示步骤与思考 -->
    <reasoning-trace-block
      :thinking-content="thinkingContent"
      :thinking-segments="thinkingSegments"
      :steps="steps"
      :is-thinking="isThinking || (isSending && !isStreaming)"
      :expanded="thinkingExpanded"
      :active-agent-runs="activeAgentRuns"
      @toggle-thinking="$emit('toggle-thinking')"
    />
  </div>
</template>

<script lang="ts">
import ReasoningTraceBlock from "./ReasoningTraceBlock.vue";

export default {
  name: "AgentStepCard",
  components: {
    ReasoningTraceBlock,
  },
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
    thinkingSegments: {
      type: Array,
      default: function () {
        return [];
      },
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
    activeAgentRuns: {
      type: Object,
      default: function () {
        return {};
      },
    },
  },
  methods: {
    showCard() {
      return (
        (this.steps && this.steps.length > 0) ||
        (this.thinkingSegments && this.thinkingSegments.length > 0) ||
        !!(this.thinkingContent && this.thinkingContent.length > 0) ||
        (this.isSending && !this.isStreaming)
      );
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
        return "LLM 决策";
      }
      if (eventType === "llm_finished") {
        return "LLM 决策完成";
      }
      if (eventType === "final_answer" || eventType === "run_finished") {
        return "最终答案已生成";
      }
      if (eventType === "run_failed") {
        return "任务执行失败";
      }
      return step.message || "未命名事件";
    },
    findStepByStatus(status) {
      for (var i = 0; i < this.steps.length; i++) {
        if (this.normalizeStepStatus(this.steps[i]) === status) {
          return this.steps[i];
        }
      }
      return null;
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
      if (
        (this.thinkingSegments && this.thinkingSegments.length > 0) ||
        (this.thinkingContent && this.thinkingContent.length > 0)
      ) {
        return "可查看本轮进度摘要";
      }
      return "等待开始";
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
  margin-bottom: 12px;
}

/* ReasoningTraceBlock 在卡片内取消自身底部 margin，避免双重间距 */
.agent-step-card :deep(.reasoning-trace-block) {
  margin-bottom: 0;
}
</style>
