<template>
  <div class="token-usage-indicator">
    <span class="token-label">
      <span v-if="cacheHitPercentLabel" class="token-cache"
        >缓存率 {{ cacheHitPercentLabel }} · </span
      >{{ usedTokensLabel }} / {{ windowLabel }}
    </span>

    <div class="token-ring-wrap">
      <!-- 像素风：粗边框方块进度条（已用 / 总上下文） -->
      <div class="token-bar" aria-hidden="true">
        <div
          class="token-bar-fill"
          :style="{ width: usedPercent + '%', backgroundColor: ringColor }"
        ></div>
      </div>

      <!-- 悬停弹出详情 -->
      <div v-if="tokenUsage" class="token-tooltip">
        <div class="token-tooltip-row">
          <span class="token-tooltip-key">上下文占用</span>
          <span class="token-tooltip-val"
            >{{ usedTokens }} / {{ contextWindow }}</span
          >
        </div>
        <div class="token-tooltip-row">
          <span class="token-tooltip-key">Prompt</span>
          <span class="token-tooltip-val">{{
            tokenUsage.promptTokens || 0
          }}</span>
        </div>
        <div class="token-tooltip-row">
          <span class="token-tooltip-key">Completion</span>
          <span class="token-tooltip-val">{{
            tokenUsage.completionTokens || 0
          }}</span>
        </div>
        <div v-if="tokenUsage.reasoningTokens" class="token-tooltip-row">
          <span class="token-tooltip-key">Thinking</span>
          <span class="token-tooltip-val">{{
            tokenUsage.reasoningTokens
          }}</span>
        </div>
        <div
          v-if="
            tokenUsage.cacheHitTokens != null && tokenUsage.cacheHitTokens !== 0
          "
          class="token-tooltip-row"
        >
          <span class="token-tooltip-key">缓存命中</span>
          <span class="token-tooltip-val"
            >{{ tokenUsage.cacheHitTokens }} /
            {{ tokenUsage.promptTokens || 0 }}</span
          >
        </div>
        <div
          v-if="
            tokenUsage.cacheMissTokens != null &&
            tokenUsage.cacheMissTokens !== 0
          "
          class="token-tooltip-row"
        >
          <span class="token-tooltip-key">缓存未命中</span>
          <span class="token-tooltip-val">{{
            tokenUsage.cacheMissTokens
          }}</span>
        </div>
        <div
          v-if="tokenUsage.cumulativeTotalTokens != null"
          class="token-tooltip-row"
        >
          <span class="token-tooltip-key">本轮累计</span>
          <span class="token-tooltip-val">{{
            tokenUsage.cumulativeTotalTokens
          }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { DEFAULT_LLM_MAX_INPUT_CONTEXT_TOKENS } from "../../../types/settings";

export default {
  name: "TokenUsageIndicator",
  props: {
    tokenUsage: {
      type: Object,
      default: null,
    },
  },
  computed: {
    usedTokens() {
      var u = this.tokenUsage;
      if (!u) {
        return 0;
      }
      var prompt = u.promptTokens || 0;
      var completion = u.completionTokens || 0;
      return prompt + completion;
    },
    contextWindow() {
      var u = this.tokenUsage;
      if (!u || !u.contextWindow) {
        return DEFAULT_LLM_MAX_INPUT_CONTEXT_TOKENS;
      }
      return u.contextWindow;
    },
    usedPercent() {
      var percent = (this.usedTokens / this.contextWindow) * 100;
      if (percent < 0) {
        percent = 0;
      }
      if (percent > 100) {
        percent = 100;
      }
      return percent.toFixed(1);
    },
    ringColor() {
      var percent = parseFloat(this.usedPercent);
      if (percent >= 90) {
        return "var(--color-error)";
      }
      if (percent >= 70) {
        return "var(--color-primary-fixed-dim)";
      }
      return "var(--color-primary)";
    },
    usedTokensLabel() {
      return this.formatNumber(this.usedTokens);
    },
    windowLabel() {
      return this.formatNumber(this.contextWindow);
    },
    cacheHitPercentLabel() {
      var u = this.tokenUsage;
      if (!u || u.cacheHitTokens == null || u.cacheHitTokens === 0) {
        return "";
      }
      var prompt = u.promptTokens || 0;
      if (prompt === 0) {
        return "";
      }
      var percent = (u.cacheHitTokens / prompt) * 100;
      return percent.toFixed(0) + "%";
    },
  },
  methods: {
    formatNumber(n) {
      if (n == null) {
        return "0";
      }
      if (n >= 1000) {
        return (n / 1000).toFixed(1) + "k";
      }
      return String(n);
    },
  },
};
</script>

<style scoped>
.token-usage-indicator {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-on-surface-variant);
  opacity: 0.8;
}

.token-ring-wrap {
  position: relative;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
}

/* 像素进度条：粗边框 + 平色填充，无圆角无渐变 */
.token-bar {
  width: 44px;
  height: 10px;
  border: 2px solid var(--color-outline);
  border-radius: 0;
  background: var(--color-surface);
  cursor: default;
  overflow: hidden;
}

.token-bar-fill {
  height: 100%;
  transition: width 0.15s steps(3);
}

.token-tooltip {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
  min-width: 180px;
  padding: 8px 10px;
  background: var(--color-surface-container-high);
  border: 2px solid var(--color-outline);
  border-radius: 0;
  box-shadow: 4px 4px 0 0 #101010;
  font-size: 13px;
  color: var(--color-on-surface);
  white-space: nowrap;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.15s steps(3), visibility 0.15s;
  pointer-events: none;
  z-index: 100;
}

.token-ring-wrap:hover .token-tooltip {
  opacity: 1;
  visibility: visible;
}

.token-tooltip-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  line-height: 1.6;
}

.token-tooltip-key {
  color: var(--color-on-surface-variant);
}

.token-tooltip-val {
  color: var(--color-on-surface);
}

.token-label {
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.token-cache {
  opacity: 0.7;
  color: var(--color-primary);
}
</style>
