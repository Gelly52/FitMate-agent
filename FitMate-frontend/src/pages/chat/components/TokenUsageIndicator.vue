<template>
  <div class="token-usage-indicator">
    <span class="token-label">
      <span v-if="cacheHitPercentLabel" class="token-cache">缓存率 {{ cacheHitPercentLabel }} · </span>{{ usedTokensLabel }} / {{ windowLabel }}
    </span>

    <div class="token-ring-wrap">
      <svg class="token-ring" width="14" height="14" viewBox="0 0 36 36">
        <!-- 浅色背景圆（剩余上下文） -->
        <circle
          cx="18"
          cy="18"
          r="15.9"
          fill="none"
          stroke="var(--color-outline-variant)"
          stroke-width="4"
          opacity="0.4"
        />
        <!-- 深色前景圆（已用）按比例绘制 -->
        <circle
          cx="18"
          cy="18"
          r="15.9"
          fill="none"
          :stroke="ringColor"
          stroke-width="4"
          :stroke-dasharray="usedPercent + ', 100'"
          stroke-linecap="round"
          transform="rotate(-90 18 18)"
        />
      </svg>

      <!-- 悬停弹出详情 -->
      <div v-if="tokenUsage" class="token-tooltip">
        <div class="token-tooltip-row">
          <span class="token-tooltip-key">上下文占用</span>
          <span class="token-tooltip-val">{{ usedTokens }} / {{ contextWindow }}</span>
        </div>
        <div class="token-tooltip-row">
          <span class="token-tooltip-key">Prompt</span>
          <span class="token-tooltip-val">{{ tokenUsage.promptTokens || 0 }}</span>
        </div>
        <div class="token-tooltip-row">
          <span class="token-tooltip-key">Completion</span>
          <span class="token-tooltip-val">{{ tokenUsage.completionTokens || 0 }}</span>
        </div>
        <div v-if="tokenUsage.reasoningTokens" class="token-tooltip-row">
          <span class="token-tooltip-key">Thinking</span>
          <span class="token-tooltip-val">{{ tokenUsage.reasoningTokens }}</span>
        </div>
        <div v-if="tokenUsage.cacheHitTokens != null && tokenUsage.cacheHitTokens !== 0" class="token-tooltip-row">
          <span class="token-tooltip-key">缓存命中</span>
          <span class="token-tooltip-val">{{ tokenUsage.cacheHitTokens }} / {{ tokenUsage.promptTokens || 0 }}</span>
        </div>
        <div v-if="tokenUsage.cacheMissTokens != null && tokenUsage.cacheMissTokens !== 0" class="token-tooltip-row">
          <span class="token-tooltip-key">缓存未命中</span>
          <span class="token-tooltip-val">{{ tokenUsage.cacheMissTokens }}</span>
        </div>
        <div v-if="tokenUsage.cumulativeTotalTokens != null" class="token-tooltip-row">
          <span class="token-tooltip-key">本轮累计</span>
          <span class="token-tooltip-val">{{ tokenUsage.cumulativeTotalTokens }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
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
        return 65536;
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
  gap: 4px;
  font-size: 11px;
  color: var(--color-on-surface-variant);
  opacity: 0.7;
}

.token-ring-wrap {
  position: relative;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
}

.token-ring {
  cursor: default;
}

.token-tooltip {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 50%;
  transform: translateX(-50%);
  min-width: 180px;
  padding: 8px 10px;
  background: var(--color-surface-3, var(--color-surface-container-high, #2b2b2b));
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  font-size: 11px;
  color: var(--color-on-surface);
  white-space: nowrap;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.15s ease, visibility 0.15s ease;
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
  font-family: "Inter", sans-serif;
  font-weight: 500;
}

.token-label {
  font-family: "Inter", sans-serif;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.token-cache {
  opacity: 0.7;
  color: var(--color-primary);
}
</style>
