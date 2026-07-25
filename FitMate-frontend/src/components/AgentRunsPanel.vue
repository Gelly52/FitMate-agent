<!-- FitMate-frontend/src/components/AgentRunsPanel.vue -->
<template>
  <div class="agent-runs-root" ref="root">
    <button
      type="button"
      class="agent-runs-trigger"
      :class="{ 'agent-runs-trigger-active': open }"
      title="Agent 任务进度"
      @click="toggleOpen"
    >
      <span class="material-symbols-outlined">notifications</span>
      <span v-if="runningCount > 0" class="agent-runs-badge">
        {{ runningCount > 9 ? "9+" : runningCount }}
      </span>
    </button>

    <transition name="agent-runs-fade">
      <div v-if="open" class="agent-runs-dropdown" @click.stop>
        <div class="agent-runs-header">
          <span class="agent-runs-header-title">Agent 任务进度</span>
          <button
            type="button"
            class="agent-runs-refresh"
            :disabled="loading"
            title="刷新"
            @click="fetchRuns"
          >
            <span class="material-symbols-outlined" :class="{ 'agent-runs-spin': loading }">refresh</span>
          </button>
        </div>

        <div v-if="error" class="agent-runs-state agent-runs-state-error">
          <span class="material-symbols-outlined">error</span>
          <span>{{ error }}</span>
        </div>

        <div v-else-if="!loading && runs.length === 0" class="agent-runs-state agent-runs-state-empty">
          <span class="material-symbols-outlined">check_circle</span>
          <span>暂无运行中的任务</span>
        </div>

        <div v-else class="agent-runs-list">
          <button
            v-for="run in runs"
            :key="run.runId"
            type="button"
            class="agent-runs-item"
            @click="onRunClick(run)"
          >
            <span class="agent-runs-dot" :class="dotClass(run.status)"></span>
            <div class="agent-runs-item-body">
              <div class="agent-runs-item-text">{{ summarize(run.requestText) }}</div>
              <div class="agent-runs-item-meta">
                <span class="agent-runs-item-session">{{ run.sessionCode || "会话" }}</span>
                <span class="agent-runs-item-time">{{ formatTime(run.startedAt || run.createdAt) }}</span>
              </div>
            </div>
            <span class="material-symbols-outlined agent-runs-item-arrow">chevron_right</span>
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script lang="ts">
import { getAgentRuns } from "../services/doctorApi";

type RunItem = {
  runId?: number | string;
  chatSessionId?: number | string | null;
  sessionCode?: string | null;
  botMsgId?: string | null;
  requestText?: string;
  status?: string;
  createdAt?: string;
  startedAt?: string;
  finishedAt?: string;
};

const POLL_INTERVAL = 8000;

export default {
  name: "AgentRunsPanel",
  data() {
    return {
      open: false,
      loading: false,
      error: "" as string,
      runs: [] as RunItem[],
      pollTimer: null as ReturnType<typeof setInterval> | null,
    };
  },
  computed: {
    runningCount(): number {
      return this.runs.filter((r) => r.status === "running" || r.status === "pending").length;
    },
  },
  mounted() {
    document.addEventListener("click", this.onDocClick);
    document.addEventListener("keydown", this.onKeydown);
    // 初始拉取一次，用于角标展示
    this.fetchRuns();
    this.startPolling();
  },
  beforeUnmount() {
    document.removeEventListener("click", this.onDocClick);
    document.removeEventListener("keydown", this.onKeydown);
    this.stopPolling();
  },
  watch: {
    $route() {
      this.open = false;
    },
  },
  methods: {
    toggleOpen() {
      this.open = !this.open;
      if (this.open) {
        // 打开时立即刷新一次
        this.fetchRuns();
      }
    },
    onDocClick(e: MouseEvent) {
      const root = this.$refs.root as HTMLElement | undefined;
      if (root && !root.contains(e.target as Node)) {
        this.open = false;
      }
    },
    onKeydown(e: KeyboardEvent) {
      if (e.key === "Escape") this.open = false;
    },
    startPolling() {
      this.stopPolling();
      this.pollTimer = setInterval(() => {
        this.fetchRuns(true);
      }, POLL_INTERVAL);
    },
    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer);
        this.pollTimer = null;
      }
    },
    async fetchRuns(silent = false) {
      if (!silent) this.loading = true;
      this.error = "";
      try {
        const res = await getAgentRuns({ status: "running", limit: 20 });
        const list = (res as { data?: unknown })?.data;
        if (Array.isArray(list)) {
          this.runs = list as RunItem[];
        } else if (Array.isArray(res)) {
          this.runs = res as RunItem[];
        } else {
          this.runs = [];
        }
      } catch (e) {
        if (!silent) {
          this.error = "加载失败";
        }
      } finally {
        if (!silent) this.loading = false;
      }
    },
    summarize(text?: string): string {
      if (!text) return "(无请求文本)";
      const t = text.trim();
      if (t.length <= 48) return t;
      return t.slice(0, 48) + "…";
    },
    dotClass(status?: string): string {
      if (status === "running") return "agent-runs-dot-running";
      if (status === "pending") return "agent-runs-dot-pending";
      if (status === "failed" || status === "interrupted") return "agent-runs-dot-error";
      if (status === "success") return "agent-runs-dot-success";
      return "agent-runs-dot-running";
    },
    formatTime(time?: string): string {
      if (!time) return "";
      const d = new Date(time);
      if (isNaN(d.getTime())) return "";
      const now = new Date();
      const diffMs = now.getTime() - d.getTime();
      const diffSec = Math.floor(diffMs / 1000);
      if (diffSec < 60) return `${diffSec} 秒前`;
      const diffMin = Math.floor(diffSec / 60);
      if (diffMin < 60) return `${diffMin} 分钟前`;
      const diffHour = Math.floor(diffMin / 60);
      if (diffHour < 24) return `${diffHour} 小时前`;
      const mm = String(d.getMonth() + 1).padStart(2, "0");
      const dd = String(d.getDate()).padStart(2, "0");
      const hh = String(d.getHours()).padStart(2, "0");
      const mi = String(d.getMinutes()).padStart(2, "0");
      return `${mm}-${dd} ${hh}:${mi}`;
    },
    onRunClick(run: RunItem) {
      const sessionId = run.chatSessionId;
      this.open = false;
      if (sessionId != null) {
        this.$router.push(`/chat/${sessionId}`);
      } else {
        this.$router.push("/chat");
      }
    },
  },
};
</script>

<style scoped>
.agent-runs-root {
  position: relative;
  display: inline-block;
}

.agent-runs-trigger {
  width: 32px;
  height: 32px;
  border-radius: 0;
  background: transparent;
  border: 2px solid transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
  position: relative;
  color: var(--color-on-surface-variant);
  transition: color 0.1s, background-color 0.1s, border-color 0.1s;
}

.agent-runs-trigger:hover,
.agent-runs-trigger-active {
  color: var(--color-primary);
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
}

.agent-runs-trigger:active {
  transform: translate(2px, 2px);
}

.agent-runs-trigger .material-symbols-outlined {
  font-size: 22px;
}

.agent-runs-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 0;
  background: var(--color-error);
  color: var(--color-on-error);
  font-size: 12px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
  border: 2px solid var(--color-outline);
  box-sizing: content-box;
}

.agent-runs-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 320px;
  max-height: 420px;
  background: var(--color-surface-container);
  border: 3px solid var(--color-outline);
  border-radius: 0;
  box-shadow: 4px 4px 0 0 #101010;
  z-index: 50;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.agent-runs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 2px solid var(--color-outline);
}

.agent-runs-header-title {
  color: var(--color-on-surface);
  font-size: 15px;
  font-weight: 600;
}

.agent-runs-refresh {
  width: 24px;
  height: 24px;
  border: 2px solid transparent;
  background: transparent;
  cursor: pointer;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-on-surface-variant);
  border-radius: 0;
  transition: color 0.1s, background-color 0.1s, border-color 0.1s;
}

.agent-runs-refresh:hover:not(:disabled) {
  color: var(--color-primary);
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
}

.agent-runs-refresh:active:not(:disabled) {
  transform: translate(2px, 2px);
}

.agent-runs-refresh:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.agent-runs-refresh .material-symbols-outlined {
  font-size: 16px;
}

.agent-runs-spin {
  animation: agent-runs-spin 0.9s steps(8) infinite;
}

@keyframes agent-runs-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.agent-runs-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 14px;
  color: var(--color-on-surface-variant);
  font-size: 14px;
}

.agent-runs-state .material-symbols-outlined {
  font-size: 28px;
  opacity: 0.6;
}

.agent-runs-state-error {
  color: var(--color-error);
}

.agent-runs-list {
  overflow-y: auto;
  padding: 4px;
}

.agent-runs-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  padding: 10px 10px;
  background: transparent;
  border: 2px solid transparent;
  border-radius: 0;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  transition: background-color 0.1s, border-color 0.1s;
}

.agent-runs-item:hover {
  background: var(--color-surface-container-high);
  border-color: var(--color-outline);
}

.agent-runs-item:active {
  transform: translate(2px, 2px);
}

.agent-runs-dot {
  width: 8px;
  height: 8px;
  border-radius: 0;
  margin-top: 6px;
  flex-shrink: 0;
}

.agent-runs-dot-running {
  background: var(--color-primary);
  animation: agent-runs-blink 1.2s steps(2, jump-none) infinite;
}

.agent-runs-dot-pending {
  background: var(--color-on-surface-variant);
}

.agent-runs-dot-error {
  background: var(--color-error);
}

.agent-runs-dot-success {
  background: var(--pixel-green);
}

@keyframes agent-runs-blink {
  0% { opacity: 1; }
  50% { opacity: 0.3; }
  100% { opacity: 1; }
}

.agent-runs-item-body {
  flex: 1;
  min-width: 0;
}

.agent-runs-item-text {
  color: var(--color-on-surface);
  font-size: 15px;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-runs-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.agent-runs-item-session {
  color: var(--color-on-surface-variant);
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-runs-item-time {
  color: var(--color-on-surface-variant);
  font-size: 13px;
  white-space: nowrap;
  flex-shrink: 0;
}

.agent-runs-item-arrow {
  color: var(--color-on-surface-variant);
  font-size: 18px;
  margin-top: 2px;
  flex-shrink: 0;
}

.agent-runs-fade-enter-active,
.agent-runs-fade-leave-active {
  transition: opacity 0.1s steps(2), transform 0.1s steps(2);
}

.agent-runs-fade-enter-from,
.agent-runs-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
