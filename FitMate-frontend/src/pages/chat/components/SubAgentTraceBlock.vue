<template>
  <div v-if="hasContent()" class="subagent-trace-block">
    <!-- 折叠/展开 header（带嵌套容器边框） -->
    <div
      class="subagent-trace-header"
      :class="{ 'is-active': isRunning }"
      @click="toggleExpand"
    >
      <div class="subagent-trace-title">
        <span v-if="isRunning" class="subagent-trace-dot"></span>
        <span class="subagent-trace-label">{{ headerTitle }}</span>
        <span v-if="metaText" class="subagent-trace-meta">· {{ metaText }}</span>
      </div>
      <span class="material-symbols-outlined subagent-trace-chevron">
        {{ expanded ? "expand_less" : "expand_more" }}
      </span>
    </div>

    <!-- 折叠态：摘要 -->
    <div v-if="!expanded && collapsedHint" class="subagent-trace-collapsed-hint">
      {{ collapsedHint }}
    </div>

    <!-- 展开态：嵌套时间线 -->
    <div v-if="expanded" class="subagent-trace-body">
      <div v-if="mergedTimeline.length > 0" class="subagent-merged-timeline">
        <template v-for="(entry, idx) in mergedTimeline">
          <!-- Turn：LLM 决策 + 可折叠思考 -->
          <div v-if="entry.kind === 'turn'" :key="entry.key" class="subagent-turn-entry">
            <div class="subagent-turn-indicator">
              <span
                class="subagent-turn-dot"
                :class="{
                  'is-collapsed': isTurnCollapsed(entry.key),
                  'is-streaming': entry.segment && entry.segment.isStreaming
                }"
                @click="toggleTurn(entry.key)"
              ></span>
              <span
                v-if="idx < mergedTimeline.length - 1"
                class="subagent-turn-line"
              ></span>
            </div>
            <div class="subagent-turn-content">
              <div class="subagent-turn-step" @click="toggleTurn(entry.key)">
                <span class="subagent-merged-step-label">
                  {{ resolveStepLabel(entry.step)
                  }}<span
                    v-if="resolveStepMeta(entry.step)"
                    class="subagent-merged-step-meta"
                  >
                    · {{ resolveStepMeta(entry.step) }}</span
                  >
                </span>
              </div>
              <div
                v-if="entry.segment"
                class="subagent-turn-thinking"
                :class="{ 'is-collapsed': isTurnCollapsed(entry.key) }"
              >
                <div
                  v-if="entry.segment.content"
                  class="subagent-merged-thinking-content"
                >{{ entry.segment.content }}</div>
                <div
                  v-else-if="entry.segment.isStreaming"
                  class="subagent-merged-thinking-content subagent-merged-thinking-muted"
                >
                  Thinking...
                </div>
              </div>
            </div>
          </div>

          <!-- Standalone step：工具调用 / 最终答案等 -->
          <div v-else-if="entry.kind === 'step'" :key="entry.key" class="subagent-step-entry">
            <div class="subagent-step-placeholder"></div>
            <div class="subagent-step-body">
              <span class="subagent-merged-step-label">
                {{ resolveStepLabel(entry.step)
                }}<span
                  v-if="resolveStepMeta(entry.step)"
                  class="subagent-merged-step-meta"
                >
                  · {{ resolveStepMeta(entry.step) }}</span
                >
              </span>
            </div>
          </div>

          <!-- Orphan thinking -->
          <div v-else :key="entry.key" class="subagent-step-entry">
            <div class="subagent-step-placeholder"></div>
            <div class="subagent-step-body">
              <div class="subagent-merged-thinking-label">
                <span v-if="entry.segment.isStreaming" class="subagent-trace-dot"></span>
                <span>{{ entry.segment.isStreaming ? "Thinking..." : "Thoughts" }}</span>
              </div>
              <div
                v-if="entry.segment.content"
                class="subagent-merged-thinking-content"
              >{{ entry.segment.content }}</div>
              <div
                v-else-if="entry.segment.isStreaming"
                class="subagent-merged-thinking-content subagent-merged-thinking-muted"
              >
                Thinking...
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- Sub-Agent 最终答案内容 -->
      <div v-if="subAgentContent" class="subagent-final-content">
        <div class="subagent-final-label">Sub-agent Output</div>
        <div class="subagent-final-text">{{ subAgentContent }}</div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
export default {
  name: "SubAgentTraceBlock",
  props: {
    /** 子 run entry 对象（来自 activeAgentRuns[runId]） */
    run: {
      type: Object,
      default: null,
    },
  },
  data() {
    return {
      expanded: false,
      collapsedTurns: {} as Record<string, boolean>,
    };
  },
  computed: {
    steps() {
      return (this.run && Array.isArray(this.run.steps)) ? this.run.steps : [];
    },
    thinkingSegments() {
      return (this.run && Array.isArray(this.run.runThinkingSegments))
        ? this.run.runThinkingSegments
        : (this.run && Array.isArray(this.run.thinkingSegments))
        ? this.run.thinkingSegments
        : [];
    },
    thinkingContent() {
      return this.run ? (this.run.thinkingContent || "") : "";
    },
    subAgentContent() {
      return this.run ? (this.run.subAgentContent || "") : "";
    },
    status() {
      return this.run ? (this.run.status || "pending") : "pending";
    },
    isRunning() {
      var s = String(this.status).toLowerCase();
      return s === "running" || s === "pending";
    },
    headerTitle() {
      if (this.isRunning) {
        return "Sub-agent: Working...";
      }
      return "Sub-agent Reasoning";
    },
    metaText() {
      var stepCount = this.steps.length;
      var segCount = this.thinkingSegments.length;
      if (stepCount > 0) {
        return stepCount + " steps" + (segCount > 0 ? " · " + segCount + " thoughts" : "");
      }
      if (segCount > 0) {
        return segCount + " thoughts";
      }
      return "";
    },
    collapsedHint() {
      if (this.isRunning) {
        return "";
      }
      var segments = this.thinkingSegments;
      if (segments.length > 0) {
        var lastSeg = segments[segments.length - 1];
        if (lastSeg && lastSeg.content) {
          var text = String(lastSeg.content).replace(/\s+/g, " ").trim();
          if (text.length > 60) {
            return text.slice(0, 60) + "...";
          }
          return text;
        }
      }
      if (this.subAgentContent) {
        var text2 = String(this.subAgentContent).replace(/\s+/g, " ").trim();
        if (text2.length > 60) {
          return text2.slice(0, 60) + "...";
        }
        return text2;
      }
      return "点击展开";
    },
    mergedTimeline() {
      var steps = this.steps;
      var segments = this.thinkingSegments;
      var result: any[] = [];

      function isLlmAnchor(step: any) {
        if (!step || !step.eventType) return false;
        var et = String(step.eventType).toLowerCase();
        return et === "llm_started" || et === "llm_finished";
      }

      var segByIter: Record<number, any> = {};
      var segOrder: number[] = [];
      var usedSegIters: Record<number, boolean> = {};
      for (var s = 0; s < segments.length; s++) {
        var seg = segments[s];
        if (!seg) continue;
        var segIter = seg.iteration != null ? Number(seg.iteration) : (s + 1);
        if (!segByIter[segIter]) {
          segByIter[segIter] = seg;
          segOrder.push(segIter);
        }
      }

      var fallbackSegIdx = 0;
      function findSegmentForIteration(iterNo: any) {
        if (iterNo != null && segByIter[iterNo]) {
          if (!usedSegIters[iterNo]) {
            usedSegIters[iterNo] = true;
          }
          return segByIter[iterNo];
        }
        while (fallbackSegIdx < segOrder.length) {
          var candidateIter = segOrder[fallbackSegIdx++];
          if (!usedSegIters[candidateIter]) {
            usedSegIters[candidateIter] = true;
            return segByIter[candidateIter];
          }
        }
        return null;
      }

      var turnCounter = 0;
      var lastMappedIter = null;

      for (var i = 0; i < steps.length; i++) {
        var step = steps[i];
        if (!step) continue;

        if (isLlmAnchor(step)) {
          var eventType = String(step.eventType).toLowerCase();
          var iterNo = step.iterationNo != null ? Number(step.iterationNo) : null;
          var matchedSeg = null;

          if (eventType === "llm_started") {
            turnCounter++;
            matchedSeg = findSegmentForIteration(iterNo);
            lastMappedIter = iterNo;
          } else if (eventType === "llm_finished") {
            matchedSeg = findSegmentForIteration(iterNo);
            if (!matchedSeg && lastMappedIter != null) {
              matchedSeg = findSegmentForIteration(lastMappedIter);
            }
          }

          result.push({
            kind: "turn",
            key: "sub-turn-" + (iterNo || turnCounter),
            step: step,
            segment: matchedSeg,
          });
        } else {
          result.push({
            kind: "step",
            key: "sub-step-" + (step.id || step.stepNo || i),
            step: step,
          });
        }
      }

      for (var ri = 0; ri < segOrder.length; ri++) {
        var remainIter = segOrder[ri];
        if (!usedSegIters[remainIter]) {
          result.push({
            kind: "thinking",
            key: "sub-thinking-orphan-" + remainIter,
            segment: segByIter[remainIter],
          });
        }
      }

      if (steps.length === 0 && segments.length > 0) {
        result = [];
        for (var k = 0; k < segOrder.length; k++) {
          result.push({
            kind: "thinking",
            key: "sub-thinking-only-" + segOrder[k],
            segment: segByIter[segOrder[k]],
          });
        }
      }

      return result;
    },
  },
  methods: {
    hasContent() {
      return (
        this.steps.length > 0 ||
        this.thinkingSegments.length > 0 ||
        !!this.thinkingContent ||
        !!this.subAgentContent ||
        this.isRunning
      );
    },
    isTurnCollapsed(turnKey: string) {
      return !!this.collapsedTurns[turnKey];
    },
    toggleTurn(turnKey: string) {
      this.collapsedTurns[turnKey] = !this.collapsedTurns[turnKey];
    },
    toggleExpand() {
      this.expanded = !this.expanded;
    },
    resolveStepLabel(step: any) {
      if (!step) return "Step";
      var eventType = step.eventType ? String(step.eventType).toLowerCase() : "";
      if (eventType === "llm_started") return "Sub-agent: Let me think...";
      if (eventType === "llm_finished") return "Sub-agent done thinking";
      if (eventType === "tool_call_started" && step.toolName) return "Calling tool: " + step.toolName;
      if (eventType === "tool_call_finished" && step.toolName) return "Tool finished: " + step.toolName;
      if (eventType === "tool_call_failed" && step.toolName) return "Tool failed: " + step.toolName;
      if (eventType === "final_answer" || eventType === "run_finished") return "Sub-agent ready";
      if (eventType === "run_failed") return "Sub-agent failed";
      if (step.label || step.stepName) return step.label || step.stepName;
      return step.message || "Event";
    },
    resolveStepMeta(step: any) {
      if (!step) return "";
      var parts: string[] = [];
      if (step.iterationNo != null && step.iterationNo > 0) {
        parts.push("第 " + step.iterationNo + " 轮");
      }
      if (step.durationMs != null) {
        parts.push(step.durationMs + "ms");
      }
      if (step.errorMessage) {
        parts.push(String(step.errorMessage));
      }
      return parts.join(" · ");
    },
  },
};
</script>

<style scoped>
/* ===== 嵌套容器：带边框区分层级 ===== */
.subagent-trace-block {
  margin-top: 6px;
  margin-left: 10px;
  padding: 8px 10px;
  border: 1px solid var(--color-surface-container);
  border-left: 2px solid color-mix(in srgb, var(--color-primary) 40%, transparent);
  border-radius: 6px;
  background: color-mix(in srgb, var(--color-surface) 50%, transparent);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.subagent-trace-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 0;
  cursor: pointer;
  user-select: none;
  transition: color 0.2s ease;
}

.subagent-trace-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.subagent-trace-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary-fixed-dim);
  animation: subagent-pulse 1.4s ease-in-out infinite;
  flex-shrink: 0;
}

@keyframes subagent-pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}

.subagent-trace-label {
  font-family: "Inter", sans-serif;
  font-size: 12px;
  color: var(--color-on-surface-variant);
  letter-spacing: 0.03em;
  transition: color 0.2s ease;
}

.subagent-trace-meta {
  font-family: "Inter", sans-serif;
  font-size: 11px;
  color: var(--color-on-surface-variant);
  opacity: 0.7;
}

.subagent-trace-chevron {
  flex-shrink: 0;
  font-size: 16px;
  color: var(--color-on-surface-variant);
  transition: transform 0.2s ease, color 0.2s ease;
}

.subagent-trace-header:hover .subagent-trace-label,
.subagent-trace-header:hover .subagent-trace-chevron {
  color: var(--color-primary);
}

.subagent-trace-header.is-active .subagent-trace-label {
  color: var(--color-primary);
}

/* ===== Body ===== */
.subagent-trace-body {
  padding: 4px 0 4px 4px;
}

.subagent-merged-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
}

/* ===== Turn ===== */
.subagent-turn-entry {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.subagent-turn-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  padding-top: 4px;
}

.subagent-turn-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-surface);
  border: 1.5px solid color-mix(in srgb, var(--color-primary) 35%, transparent);
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
  flex-shrink: 0;
}

.subagent-turn-dot:hover {
  border-color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 12%, transparent);
}

.subagent-turn-dot.is-collapsed {
  background: var(--color-primary);
  border-color: var(--color-primary);
  transform: scale(0.85);
}

.subagent-turn-dot.is-streaming {
  animation: subagent-pulse 1.4s ease-in-out infinite;
}

.subagent-turn-line {
  width: 1.5px;
  flex: 1;
  min-height: 12px;
  background: var(--color-surface-container);
  margin-top: 4px;
}

.subagent-turn-content {
  flex: 1;
  min-width: 0;
  padding-bottom: 6px;
}

.subagent-turn-step {
  cursor: pointer;
  transition: color 0.2s ease;
  padding-top: 1px;
}

.subagent-turn-step:hover .subagent-merged-step-label {
  color: var(--color-primary);
}

.subagent-turn-thinking {
  margin-top: 4px;
  padding-left: 12px;
  border-left: 1px dashed var(--color-surface-container);
  overflow: hidden;
  max-height: 2000px;
  transition: max-height 0.25s ease, opacity 0.2s ease, margin 0.2s ease,
    padding 0.2s ease, border-color 0.2s ease;
}

.subagent-turn-thinking.is-collapsed {
  max-height: 0;
  opacity: 0;
  margin: 0;
  padding: 0 0 0 12px;
  border-left-color: transparent;
}

/* ===== Standalone step ===== */
.subagent-step-entry {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.subagent-step-placeholder {
  width: 8px;
  flex-shrink: 0;
}

.subagent-step-body {
  flex: 1;
  min-width: 0;
  padding-top: 1px;
  padding-bottom: 6px;
}

/* ===== 文本样式 ===== */
.subagent-merged-step-label {
  font-size: 12px;
  font-family: "Inter", sans-serif;
  color: var(--color-on-surface-variant);
  line-height: 1.4;
}

.subagent-merged-step-meta {
  margin-left: 4px;
  font-size: 11px;
  color: var(--color-on-surface-variant);
  opacity: 0.7;
}

.subagent-merged-thinking-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-family: "Inter", sans-serif;
  color: var(--color-on-surface-variant);
  letter-spacing: 0.03em;
  opacity: 0.8;
  margin-bottom: 4px;
}

.subagent-merged-thinking-content {
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-on-surface-variant);
  white-space: pre-wrap;
  word-break: break-word;
  font-style: italic;
  background: color-mix(in srgb, var(--color-primary) 6%, transparent);
  border-radius: 6px;
  padding: 8px 12px;
}

.subagent-merged-thinking-muted {
  color: color-mix(in srgb, var(--color-on-surface-variant) 60%, transparent);
  font-style: italic;
}

.subagent-trace-collapsed-hint {
  margin-top: 2px;
  font-size: 11px;
  color: var(--color-on-surface-variant);
  opacity: 0.6;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ===== Sub-Agent 最终输出 ===== */
.subagent-final-content {
  margin-top: 8px;
  padding: 8px 10px;
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
  border-radius: 6px;
  border-left: 2px solid var(--color-primary);
}

.subagent-final-label {
  font-size: 11px;
  font-family: "Inter", sans-serif;
  color: var(--color-primary);
  letter-spacing: 0.03em;
  margin-bottom: 4px;
}

.subagent-final-text {
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-on-surface);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
