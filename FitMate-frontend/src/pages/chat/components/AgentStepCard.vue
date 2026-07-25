<template>
  <div
    v-if="showCard()"
    class="agent-step-card"
    :class="{ 'is-active': isThinking || (isSending && !isStreaming) }"
    role="region"
    aria-label="Agent execution trace"
  >
    <div class="agent-step-header">
      <div class="flex flex-col gap-xs">
        <h4
          class="text-label-sm text-on-surface uppercase tracking-widest"
        >
          Agent Steps
        </h4>
        <p class="text-label-xs text-on-surface-variant uppercase tracking-wider">
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
        return "Step";
      }
      var eventType = step.eventType ? String(step.eventType).toLowerCase() : "";
      if (eventType === "tool_call_started" && step.toolName) {
        return "Calling tool: " + step.toolName;
      }
      if (eventType === "tool_call_finished" && step.toolName) {
        return "Tool finished: " + step.toolName;
      }
      if (eventType === "tool_call_failed" && step.toolName) {
        return "Tool failed: " + step.toolName;
      }
      if (eventType === "llm_started") {
        return "Agent: Let me think...";
      }
      if (eventType === "llm_finished") {
        return "Done thinking";
      }
      if (eventType === "final_answer" || eventType === "run_finished") {
        return "Ready to respond";
      }
      if (eventType === "run_started") {
        return "Agent started";
      }
      if (eventType === "run_failed") {
        return "Task failed";
      }
      if (step.label || step.stepName) {
        return step.label || step.stepName;
      }
      return step.message || "Event";
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
        return this.resolveStepLabel(failedStep) + " · Failed";
      }
      var runningStep = this.findStepByStatus("running");
      if (runningStep) {
        return this.resolveStepLabel(runningStep) + " · In progress";
      }
      if (this.steps.length > 0) {
        var completedCount = this.completedStepCount();
        if (completedCount >= this.steps.length) {
          return "All steps completed";
        }
        return this.steps.length + " steps recorded";
      }
      if (this.isThinking || (this.isSending && !this.isStreaming)) {
        return "Waiting for agent...";
      }
      if (
        (this.thinkingSegments && this.thinkingSegments.length > 0) ||
        (this.thinkingContent && this.thinkingContent.length > 0)
      ) {
        return "View progress summary";
      }
      return "Waiting to start";
    },
  },
};
</script>

<style scoped>
.agent-step-card {
  margin: 16px 0;
  padding: 16px 20px;
  border: 3px solid var(--color-outline);
  border-radius: 0;
  background: var(--color-surface-container-low);
  box-shadow: 4px 4px 0 0 #101010;
}

.agent-step-card.is-active {
  border-color: var(--color-primary);
}

.agent-step-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 2px solid var(--color-outline-variant);
}

/* ReasoningTraceBlock 在卡片内取消自身底部 margin，避免双重间距 */
.agent-step-card :deep(.reasoning-trace-block) {
  margin-bottom: 0;
}
</style>
