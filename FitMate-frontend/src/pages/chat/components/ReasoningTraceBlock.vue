<template>
  <div v-if="hasContent()" class="reasoning-trace-block">
    <!-- 折叠/展开 header -->
    <div
      class="reasoning-trace-header"
      :class="{ 'is-active': isThinking }"
      @click="$emit('toggle-thinking')"
    >
      <div class="reasoning-trace-title">
        <span v-if="isThinking" class="reasoning-trace-dot"></span>
        <span class="reasoning-trace-label">{{ headerTitle() }}</span>
        <span v-if="resolveMeta()" class="reasoning-trace-meta">
          · {{ resolveMeta() }}
        </span>
      </div>
      <span class="material-symbols-outlined reasoning-trace-chevron">
        {{ expanded ? "expand_less" : "expand_more" }}
      </span>
    </div>

    <!-- 折叠态：摘要显示在下一行（字体更小，无前缀符） -->
    <div
      v-if="!expanded && collapsedText()"
      class="reasoning-trace-collapsed-hint"
    >
      {{ collapsedText() }}
    </div>

    <!-- 展开状态：融合时间线（LLM 决策 + 可折叠思考，按轮次交错） -->
    <div v-if="expanded" class="reasoning-trace-body">
      <div v-if="mergedTimeline.length > 0" class="reasoning-merged-timeline">
        <template v-for="(entry, idx) in mergedTimeline">
          <!-- Turn：LLM 决策 + 可折叠思考内容 -->
          <div v-if="entry.kind === 'turn'" :key="entry.key" class="reasoning-turn-entry">
            <div class="reasoning-turn-indicator">
              <span
                class="reasoning-turn-dot"
                :class="{
                  'is-collapsed': isTurnCollapsed(entry.key),
                  'is-streaming': entry.segment && entry.segment.isStreaming
                }"
                @click="toggleTurn(entry.key)"
              ></span>
              <span
                v-if="idx < mergedTimeline.length - 1"
                class="reasoning-turn-line"
              ></span>
            </div>
            <div class="reasoning-turn-content">
              <div class="reasoning-turn-step" @click="toggleTurn(entry.key)">
                <span class="reasoning-merged-step-label">
                  {{ resolveStepLabel(entry.step)
                  }}<span
                    v-if="resolveStepMeta(entry.step)"
                    class="reasoning-merged-step-meta"
                  >
                    · {{ resolveStepMeta(entry.step) }}</span
                  >
                </span>
              </div>
              <div
                v-if="entry.segment"
                class="reasoning-turn-thinking"
                :class="{ 'is-collapsed': isTurnCollapsed(entry.key) }"
              >
                <div
                  v-if="entry.segment.content"
                  class="reasoning-merged-thinking-content"
                >{{ entry.segment.content }}</div>
                <div
                  v-else-if="entry.segment.isStreaming"
                  class="reasoning-merged-thinking-content reasoning-merged-thinking-muted"
                >
                  正在思考中...
                </div>
              </div>
            </div>
          </div>

          <!-- Standalone step：工具调用 / 最终答案 / run_started 等（无圆点） -->
          <div v-else-if="entry.kind === 'step'" :key="entry.key" class="reasoning-step-entry">
            <div class="reasoning-step-placeholder"></div>
            <div class="reasoning-step-body">
              <span class="reasoning-merged-step-label">
                {{ resolveStepLabel(entry.step)
                }}<span
                  v-if="resolveStepMeta(entry.step)"
                  class="reasoning-merged-step-meta"
                >
                  · {{ resolveStepMeta(entry.step) }}</span
                >
              </span>
              <!-- kb.search 子步骤 -->
              <div
                v-if="isKbSearchStep(entry.step) && hasSubSteps(entry.step)"
                class="kb-substeps"
              >
                <div
                  v-for="(sub, subIdx) in resolveSubSteps(entry.step)"
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
              <!-- Sub-Agent 嵌套渲染：subagent_started step 下方嵌入子 run trace -->
              <sub-agent-trace-block
                v-if="isSubAgentStartedStep(entry.step) && resolveSubAgentRun(entry.step)"
                :run="resolveSubAgentRun(entry.step)"
              />
            </div>
          </div>

          <!-- Orphan thinking：无 LLM 锚点的兜底思考（降级场景） -->
          <div v-else :key="entry.key" class="reasoning-step-entry">
            <div class="reasoning-step-placeholder"></div>
            <div class="reasoning-step-body">
              <div class="reasoning-merged-thinking-label">
                <span v-if="entry.segment.isStreaming" class="reasoning-trace-dot"></span>
                <span>{{ entry.segment.isStreaming ? "思考中..." : "思考内容" }}</span>
              </div>
              <div
                v-if="entry.segment.content"
                class="reasoning-merged-thinking-content"
              >{{ entry.segment.content }}</div>
              <div
                v-else-if="entry.segment.isStreaming"
                class="reasoning-merged-thinking-content reasoning-merged-thinking-muted"
              >
                正在思考中...
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- 兜底：既无 steps 也无 segments，但有 thinkingContent 字符串（旧数据/恢复场景） -->
      <div v-else-if="thinkingContent" class="reasoning-merged-thinking-content">
        {{ thinkingContent }}
      </div>
      <div v-else-if="isThinking" class="reasoning-merged-thinking-content reasoning-merged-thinking-muted">
        正在思考中...
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import SubAgentTraceBlock from "./SubAgentTraceBlock.vue";

export default {
  name: "ReasoningTraceBlock",
  components: {
    SubAgentTraceBlock,
  },
  emits: ["toggle-thinking"],
  data() {
    return {
      // 每轮思考的折叠状态：{ turnKey: true } 表示已折叠
      collapsedTurns: {} as Record<string, boolean>,
    };
  },
  props: {
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
    thinkingLoaded: {
      type: Boolean,
      default: false,
    },
    thinkingLoading: {
      type: Boolean,
      default: false,
    },
    messageId: {
      type: [Number, String],
      default: null,
    },
    activeAgentRuns: {
      type: Object,
      default: function () {
        return {};
      },
    },
  },
  computed: {
    // 把 steps 和 thinkingSegments 交错合并成单一时间线
    // 策略：LLM 锚点 step + 对应 segment 合并为一个 turn（可折叠）；
    //       非锚点 step（工具调用/最终答案等）作为独立条目
    // 锚点事件：llm_started（实时流式）或 llm_finished（历史消息，DB 里 eventType 被 finish 覆盖）
    // 输出每项: { kind: 'turn'|'step'|'thinking', key, step?, segment? }
    mergedTimeline() {
      var steps = Array.isArray(this.steps) ? this.steps : [];
      var segments = Array.isArray(this.thinkingSegments)
        ? this.thinkingSegments
        : [];
      var result = [];

      function isLlmAnchor(step) {
        if (!step || !step.eventType) return false;
        var et = String(step.eventType).toLowerCase();
        return et === "llm_started" || et === "llm_finished";
      }

      var segByIter = {};
      var segOrder = [];
      var usedSegIters = {};
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

      function findSegmentForIteration(iterNo) {
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
            // 后端 markStepSuccess 会把同一 step 的 eventType 从 llm_started 更新为 llm_finished
            // （Object.assign 覆盖，steps 数组中始终只有一个锚点 per round）。
            // 因此 llm_finished 也必须标记对应 segment 为 used，
            // 否则当 step 从 started 变为 finished 后，该轮 segment 丢失 used 标记，
            // 会被 orphan 循环以"思考内容"块重复追加到时间线末尾。
            matchedSeg = findSegmentForIteration(iterNo);
            if (!matchedSeg && lastMappedIter != null) {
              matchedSeg = findSegmentForIteration(lastMappedIter);
            }
          }

          result.push({
            kind: "turn",
            key: "turn-" + (iterNo || turnCounter),
            step: step,
            segment: matchedSeg,
          });
        } else {
          result.push({
            kind: "step",
            key: "step-" + (step.id || step.stepNo || i),
            step: step,
          });
        }
      }

      for (var ri = 0; ri < segOrder.length; ri++) {
        var remainIter = segOrder[ri];
        if (!usedSegIters[remainIter]) {
          result.push({
            kind: "thinking",
            key: "thinking-orphan-" + remainIter,
            segment: segByIter[remainIter],
          });
        }
      }

      if (steps.length === 0 && segments.length > 0) {
        result = [];
        for (var k = 0; k < segOrder.length; k++) {
          result.push({
            kind: "thinking",
            key: "thinking-only-" + segOrder[k],
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
        (this.steps && this.steps.length > 0) ||
        (this.thinkingSegments && this.thinkingSegments.length > 0) ||
        (this.thinkingContent && this.thinkingContent.length > 0) ||
        this.isThinking ||
        this.thinkingLoading ||
        !!this.messageId
      );
    },
    isTurnCollapsed(turnKey) {
      return !!this.collapsedTurns[turnKey];
    },
    toggleTurn(turnKey) {
      // Vue 3 响应式：直接赋值即可（reactive object 自动追踪新 key）
      this.collapsedTurns[turnKey] = !this.collapsedTurns[turnKey];
    },
    headerTitle() {
      if (this.isThinking) {
        return "思考中...";
      }
      if (this.thinkingLoading) {
        return "加载思考内容...";
      }
      if (
        (this.thinkingSegments && this.thinkingSegments.length > 0) ||
        (this.thinkingContent && this.thinkingContent.length > 0)
      ) {
        return "思考过程";
      }
      return "思考过程";
    },
    resolveMeta() {
      var stepCount = this.steps ? this.steps.length : 0;
      var segCount = this.thinkingSegments
        ? this.thinkingSegments.length
        : 0;
      if (stepCount > 0) {
        return stepCount + " 步" + (segCount > 0 ? " · " + segCount + " 段思考" : "");
      }
      if (segCount > 0) {
        return segCount + " 段思考";
      }
      return "";
    },
    collapsedText() {
      if (this.thinkingLoading) {
        return "正在加载思考内容...";
      }
      // 优先用最新 segment 的内容做摘要
      var segments = Array.isArray(this.thinkingSegments)
        ? this.thinkingSegments
        : [];
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
      if (this.thinkingContent) {
        var text2 = String(this.thinkingContent).replace(/\s+/g, " ").trim();
        if (text2.length > 60) {
          return text2.slice(0, 60) + "...";
        }
        return text2;
      }
      if (this.isThinking) {
        return "正在思考中...";
      }
      return "点击展开查看思考内容";
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
    resolveStepMeta(step) {
      if (!step) {
        return "";
      }
      var parts = [];
      // 第 0 轮是初始化阶段，不显示轮次标签
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
    isSubAgentStartedStep(step) {
      if (!step || !step.eventType) return false;
      var et = String(step.eventType).toLowerCase();
      // 与 isLlmAnchor 一致：subagent_started 和 subagent_finished 复用同一 step 记录，
      // 后端 markStepSuccess 会把 eventType 从 started 覆盖为 finished，
      // 前端需同时接受两者以保持锚点稳定
      return et === "subagent_started" || et === "subagent_finished";
    },
    resolveSubAgentRun(step) {
      if (!step || step.subagentRunId == null) return null;
      var key = String(step.subagentRunId);
      return this.activeAgentRuns ? (this.activeAgentRuns[key] || null) : null;
    },
  },
};
</script>

<style scoped>
/* 外层仅作容器，无 padding/border */
.reasoning-trace-block {
  margin-bottom: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* ===== Header：可点击折叠/展开（纯文字 + 下拉箭头，无背景框） ===== */
.reasoning-trace-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 0;
  cursor: pointer;
  user-select: none;
  background: transparent;
  border: none;
  border-radius: 0;
  transition: color 0.2s ease;
}

.reasoning-trace-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.reasoning-trace-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-primary-fixed-dim);
  animation: reasoning-pulse 1.4s ease-in-out infinite;
  flex-shrink: 0;
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
  transition: color 0.2s ease;
}

.reasoning-trace-meta {
  font-family: "Inter", sans-serif;
  font-size: 11px;
  color: var(--color-on-surface-variant);
  opacity: 0.7;
}

.reasoning-trace-chevron {
  flex-shrink: 0;
  font-size: 16px;
  color: var(--color-on-surface-variant);
  transition: transform 0.2s ease, color 0.2s ease;
}

.reasoning-trace-header:hover .reasoning-trace-label,
.reasoning-trace-header:hover .reasoning-trace-chevron {
  color: var(--color-primary);
}

.reasoning-trace-header.is-active .reasoning-trace-label {
  color: var(--color-primary);
}

/* ===== Body：融合时间线（无背景框，与 header 风格一致） ===== */
.reasoning-trace-body {
  padding: 4px 0 4px 4px;
}

.reasoning-merged-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
  /* 不设 max-height / overflow：让思考内容流式撑开，由外层聊天窗口滚动 */
}

/* ===== Turn：LLM 决策 + 可折叠思考内容 ===== */
.reasoning-turn-entry {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.reasoning-turn-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  padding-top: 4px;
}

.reasoning-turn-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-surface);
  border: 1.5px solid color-mix(in srgb, var(--color-primary) 35%, transparent);
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
  flex-shrink: 0;
}

.reasoning-turn-dot:hover {
  border-color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 12%, transparent);
}

.reasoning-turn-dot.is-collapsed {
  background: var(--color-primary);
  border-color: var(--color-primary);
  transform: scale(0.85);
}

.reasoning-turn-dot.is-streaming {
  animation: reasoning-pulse 1.4s ease-in-out infinite;
}

.reasoning-turn-line {
  width: 1.5px;
  flex: 1;
  min-height: 12px;
  background: var(--color-surface-container);
  margin-top: 4px;
}

.reasoning-turn-content {
  flex: 1;
  min-width: 0;
  padding-bottom: 6px;
}

.reasoning-turn-step {
  cursor: pointer;
  transition: color 0.2s ease;
  padding-top: 1px;
}

.reasoning-turn-step:hover .reasoning-merged-step-label {
  color: var(--color-primary);
}

.reasoning-turn-thinking {
  margin-top: 4px;
  padding-left: 12px;
  border-left: 1px dashed var(--color-surface-container);
  overflow: hidden;
  max-height: 2000px;
  transition: max-height 0.25s ease, opacity 0.2s ease, margin 0.2s ease,
    padding 0.2s ease, border-color 0.2s ease;
}

.reasoning-turn-thinking.is-collapsed {
  max-height: 0;
  opacity: 0;
  margin: 0;
  padding: 0 0 0 12px;
  border-left-color: transparent;
}

/* ===== Standalone step：工具调用 / 最终答案等（无圆点） ===== */
.reasoning-step-entry {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.reasoning-step-placeholder {
  width: 8px;
  flex-shrink: 0;
}

.reasoning-step-body {
  flex: 1;
  min-width: 0;
  padding-top: 1px;
  padding-bottom: 6px;
}

/* ===== 共用文本样式 ===== */
.reasoning-merged-step-label {
  font-size: 12px;
  font-family: "Inter", sans-serif;
  color: var(--color-on-surface-variant);
  line-height: 1.4;
}

.reasoning-merged-step-meta {
  margin-left: 4px;
  font-size: 11px;
  color: var(--color-on-surface-variant);
  opacity: 0.7;
}

.reasoning-merged-thinking-label {
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

.reasoning-merged-thinking-content {
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

.reasoning-merged-thinking-muted {
  color: color-mix(in srgb, var(--color-on-surface-variant) 60%, transparent);
  font-style: italic;
}

.reasoning-trace-collapsed-hint {
  margin-top: 2px;
  font-size: 11px;
  color: var(--color-on-surface-variant);
  opacity: 0.6;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ===== kb.search 子步骤 ===== */
.kb-substeps {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 6px;
  margin-left: 4px;
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
  animation: reasoning-pulse 1.4s ease-in-out infinite;
}

.kb-substep-done {
  color: var(--color-primary-fixed-dim);
  font-size: 11px;
}

.kb-substep-detail {
  color: var(--color-on-surface-variant);
  font-size: 11px;
  margin-left: 4px;
  opacity: 0.7;
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
