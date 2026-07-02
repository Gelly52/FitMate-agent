<template>
  <div class="dash-page">
    <!-- Metric overview -->
    <section class="dash-metrics">
      <div class="dash-metric">
        <span class="dash-metric-label">Training Days</span>
        <span class="dash-metric-value">{{ weekSummary.trainingDays || 0 }}</span>
        <div class="dash-metric-rule"></div>
      </div>
      <div class="dash-metric">
        <span class="dash-metric-label">Total Volume</span>
        <span class="dash-metric-value">{{ formatVolume(weekSummary.totalVolume) }}</span>
        <div class="dash-metric-rule"></div>
      </div>
      <div class="dash-metric">
        <span class="dash-metric-label">Docs</span>
        <span class="dash-metric-value">{{ docCount }}</span>
        <div class="dash-metric-rule"></div>
      </div>
      <div class="dash-metric">
        <span class="dash-metric-label">Last Task</span>
        <span class="dash-metric-value dash-metric-accent">{{ lastExecTime || "--" }}</span>
        <div class="dash-metric-rule"></div>
      </div>
    </section>

    <!-- Two columns: Knowledge Base + Execution Log -->
    <section class="dash-columns">
      <!-- Knowledge Base -->
      <div class="dash-col">
        <div class="dash-col-head">
          <h3 class="dash-col-title">Knowledge Base</h3>
          <router-link to="/upload" class="dash-col-action">MANAGE FILES</router-link>
        </div>

        <div v-if="uploadedDocs.length > 0" class="dash-file-list">
          <div
            v-for="(doc, idx) in uploadedDocs"
            :key="idx"
            class="dash-file-item"
          >
            <span class="material-symbols-outlined dash-file-icon">description</span>
            <span class="dash-file-name">{{ doc.fileName || "未命名文档" }}</span>
            <span class="dash-file-meta">{{ formatChatSessionTime(doc.createdAt) || "--" }}</span>
          </div>
        </div>
        <div v-else class="dash-empty">暂无知识库文档</div>

        <label class="dash-dropzone">
          <input
            ref="fileInput"
            type="file"
            accept=".txt"
            class="dash-file-input"
            @change="handleFileChange"
          />
          <span class="material-symbols-outlined dash-dropzone-icon">upload</span>
          <span class="dash-dropzone-text">点击上传 .txt 文档以更新语料库</span>
        </label>
      </div>

      <!-- Execution Log -->
      <div class="dash-col">
        <div class="dash-col-head">
          <h3 class="dash-col-title">Execution Log</h3>
          <span class="dash-live">
            <span class="dash-live-dot"></span>
            LIVE
          </span>
        </div>

        <div v-if="executionLog.length > 0" class="dash-log-list">
          <div
            v-for="(run, idx) in executionLog"
            :key="idx"
            class="dash-log-item"
            :class="{ 'dash-log-failed': run.status === 'failed' }"
          >
            <span class="dash-log-time">{{ run.time }}</span>
            <span class="dash-log-text">{{ run.label }}</span>
            <span class="dash-log-status">{{ run.statusLabel }}</span>
          </div>
        </div>
        <div v-else class="dash-empty">暂无执行记录</div>
      </div>
    </section>

    <!-- Quick actions -->
    <section class="dash-actions">
      <h3 class="dash-col-title">Quick Actions</h3>
      <div class="dash-actions-grid">
        <button
          v-for="action in quickActions"
          :key="action.text"
          type="button"
          class="dash-action-card"
          @click="handleDirectTask(action.text)"
        >
          <span class="material-symbols-outlined dash-action-icon">{{ action.icon }}</span>
          <span class="dash-action-label">{{ action.label }}</span>
          <span class="dash-action-desc">{{ action.desc }}</span>
        </button>
      </div>
    </section>

    <!-- Status bar -->
    <footer class="dash-status">
      <span>SYSTEM STATUS: {{ sseStatusLabel }}</span>
      <span>DOCS: {{ docCount }} // MODE: {{ activeModeLabel }}</span>
    </footer>
  </div>
</template>

<script lang="ts">
import ChatLogicBase from "../chat/ChatLogicBase.vue";
import doctorApi from "../../services/doctorApi";

export default {
  name: "DashboardPage",
  extends: ChatLogicBase,
  data() {
    return {
      executionLog: [],
      quickActions: [
        {
          label: "分析本周训练",
          desc: "查看训练数据与趋势分析",
          icon: "search",
          text: "分析我这周的训练情况",
        },
        {
          label: "恢复状态评估",
          desc: "评估疲劳与恢复建议",
          icon: "favorite",
          text: "帮我看下最近的恢复状态",
        },
        {
          label: "生成本周周报",
          desc: "分析并发送训练周报",
          icon: "description",
          text: "分析我这周的训练情况，生成周报发到我的邮箱",
        },
      ],
    };
  },
  computed: {
    sseStatusLabel() {
      if (this.sseState === "connected") {
        return "OPERATIONAL";
      }
      if (this.sseState === "connecting") {
        return "CONNECTING";
      }
      if (this.sseState === "disconnected" || this.sseState === "unsupported") {
        return "OFFLINE";
      }
      return "STANDBY";
    },
  },
  mounted() {
    this.fetchUploadedDocs();
    this.fetchExecutionLog();
  },
  methods: {
    formatVolume(volume) {
      var v = Number(volume) || 0;
      if (v >= 1000) {
        return (v / 1000).toFixed(1) + "k";
      }
      return String(v);
    },
    fetchExecutionLog() {
      var me = this;
      doctorApi
        .getAgentRuns({ limit: 8 })
        .then(function (res) {
          var data = me.unwrapApiData(res, "加载执行记录失败");
          if (Array.isArray(data)) {
            me.executionLog = data.map(function (run) {
              return me.mapAgentRunToLogItem(run);
            });
          }
        })
        .catch(function () {
          me.executionLog = [];
        });
    },
    mapAgentRunToLogItem(run) {
      var status = me_normalizeStatus(run && run.status);
      var rawTime = run && (run.finishedAt || run.startedAt || run.createdAt);
      var time = "--:--";
      if (rawTime) {
        var date = new Date(rawTime);
        if (!isNaN(date.getTime())) {
          time =
            String(date.getHours()).padStart(2, "0") +
            ":" +
            String(date.getMinutes()).padStart(2, "0");
        }
      }
      var label =
        (run && (run.requestText || run.botMsgId)) || "Agent Run";
      if (label.length > 40) {
        label = label.slice(0, 40) + "...";
      }
      return {
        time: time,
        label: label,
        status: status,
        statusLabel:
          status === "success"
            ? "成功"
            : status === "failed"
            ? "失败"
            : status === "running"
            ? "执行中"
            : "等待",
      };

      function me_normalizeStatus(s) {
        var v = s == null ? "pending" : String(s).toLowerCase();
        if (v === "completed" || v === "success") {
          return "success";
        }
        if (
          v === "error" ||
          v === "failed" ||
          v === "cancelled" ||
          v === "timeout"
        ) {
          return "failed";
        }
        if (v === "running") {
          return "running";
        }
        return "pending";
      }
    },
    handleFileChange(event) {
      var files = event && event.target && event.target.files;
      if (!files || files.length === 0) {
        return;
      }
      var file = files[0];
      var me = this;
      this.uploadDoc({
        file: file,
        onSuccess: function () {
          me.fetchUploadedDocs();
        },
      });
      event.target.value = "";
    },
  },
};
</script>

<style scoped>
.dash-page {
  display: flex;
  flex-direction: column;
  gap: 64px;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  padding: 32px 48px 48px;
  background: var(--color-background);
}

.dash-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 32px;
}

.dash-metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dash-metric-label {
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

.dash-metric-value {
  font-size: 40px;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: -0.02em;
  color: var(--color-on-surface);
  font-variant-numeric: tabular-nums;
}

.dash-metric-accent {
  color: var(--color-primary-fixed-dim);
}

.dash-metric-rule {
  height: 1px;
  background: var(--color-surface-container);
  margin-top: 8px;
}

.dash-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 64px;
}

.dash-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dash-col-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dash-col-title {
  font-size: 24px;
  font-weight: 500;
  letter-spacing: -0.01em;
  color: var(--color-on-surface);
  margin: 0;
}

.dash-col-action {
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
  text-decoration: none;
  transition: color 0.2s ease;
}

.dash-col-action:hover {
  color: var(--color-primary-fixed-dim);
}

.dash-file-list,
.dash-log-list {
  display: flex;
  flex-direction: column;
}

.dash-file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-surface-container);
}

.dash-file-icon {
  font-size: 18px;
  color: var(--color-on-surface-variant);
}

.dash-file-name {
  flex: 1;
  font-size: 14px;
  color: var(--color-on-surface);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dash-file-meta {
  font-size: 9px;
  letter-spacing: 0.08em;
  color: var(--color-on-surface-variant);
  flex-shrink: 0;
}

.dash-empty {
  padding: 24px 0;
  font-size: 13px;
  color: var(--color-on-surface-variant);
}

.dash-dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px;
  margin-top: 8px;
  border: 1px solid var(--color-surface-container);
  border-radius: 8px;
  background: var(--color-surface-container-low);
  cursor: pointer;
  transition: border-color 0.2s ease;
}

.dash-dropzone:hover {
  border-color: var(--color-outline-variant);
}

.dash-file-input {
  display: none;
}

.dash-dropzone-icon {
  font-size: 22px;
  color: var(--color-on-surface-variant);
}

.dash-dropzone-text {
  font-size: 12px;
  color: var(--color-on-surface-variant);
}

.dash-log-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-surface-container);
}

.dash-log-time {
  font-size: 12px;
  color: var(--color-primary-fixed-dim);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
  font-family: ui-monospace, monospace;
}

.dash-log-text {
  flex: 1;
  font-size: 13px;
  color: var(--color-on-surface-variant);
  font-family: ui-monospace, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dash-log-status {
  font-size: 12px;
  color: var(--color-on-surface-variant);
  flex-shrink: 0;
}

.dash-log-failed .dash-log-time,
.dash-log-failed .dash-log-status {
  color: var(--color-error);
}

.dash-live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

.dash-live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 6px rgba(34, 197, 94, 0.5);
  animation: dash-pulse 1.6s ease-in-out infinite;
}

@keyframes dash-pulse {
  0%,
  100% {
    opacity: 0.4;
  }
  50% {
    opacity: 1;
  }
}

.dash-actions {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dash-actions-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.dash-action-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 20px;
  border: 1px solid var(--color-surface-container);
  border-radius: 8px;
  background: var(--color-surface-container-low);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.dash-action-card:hover {
  border-color: var(--color-outline-variant);
  transform: translateY(-2px);
}

.dash-action-icon {
  font-size: 22px;
  color: var(--color-primary-fixed-dim);
}

.dash-action-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.dash-action-desc {
  font-size: 12px;
  color: var(--color-on-surface-variant);
}

.dash-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 16px;
  border-top: 1px solid var(--color-surface-container);
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

@media (max-width: 900px) {
  .dash-page {
    padding: 24px;
    gap: 40px;
  }
  .dash-metrics {
    grid-template-columns: repeat(2, 1fr);
    gap: 24px;
  }
  .dash-columns {
    grid-template-columns: 1fr;
    gap: 40px;
  }
  .dash-actions-grid {
    grid-template-columns: 1fr;
  }
}
</style>
