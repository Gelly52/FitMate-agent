<template>
  <div class="kb-page">
    <header class="kb-header">
      <h1 class="font-inter text-display-lg text-on-surface tracking-tight">
        Knowledge Base
      </h1>
      <p class="font-inter text-body-base text-on-surface-variant">
        上传 .txt 文档，切换聊天的 RAG 模式即可基于知识库问答。
      </p>
    </header>

    <div class="kb-columns">
      <!-- 左侧：上传与文档管理 -->
      <div class="kb-col">
        <!-- Stats -->
        <section class="kb-stats">
          <div class="kb-stat">
            <span class="kb-stat-value">{{ docCount }}</span>
            <span class="kb-stat-label">文档总数</span>
          </div>
          <div class="kb-stat">
            <span class="kb-stat-value">{{ uploadSynced ? "已同步" : "未同步" }}</span>
            <span class="kb-stat-label">同步状态</span>
          </div>
        </section>

        <!-- Dropzone -->
        <label class="kb-dropzone" :class="{ 'kb-dropzone-active': isDragging }"
          @dragover.prevent="isDragging = true"
          @dragleave.prevent="isDragging = false"
          @drop.prevent="handleDrop"
        >
          <input
            ref="fileInput"
            type="file"
            accept=".txt"
            class="kb-file-input"
            @change="handleFileChange"
          />
          <span class="material-symbols-outlined kb-dropzone-icon">cloud_upload</span>
          <span class="kb-dropzone-text">拖拽 .txt 文档到此 或 <em>点击上传</em></span>
          <span class="kb-dropzone-hint">仅支持 .txt 文本文件，大小不超过 10MB</span>
        </label>

        <!-- Doc list -->
        <section class="kb-section">
          <div class="kb-section-head">
            <h2 class="font-inter text-label-sm text-on-surface uppercase tracking-widest">
              Uploaded Documents
            </h2>
          </div>
          <div v-if="uploadedDocs.length > 0" class="kb-file-list">
            <div
              v-for="(doc, idx) in uploadedDocs"
              :key="idx"
              class="kb-file-item"
            >
              <span class="material-symbols-outlined kb-file-icon">description</span>
              <span class="kb-file-name">{{ doc.fileName || "未命名文档" }}</span>
              <span class="kb-file-meta">{{ formatChatSessionTime(doc.createdAt) || "--" }}</span>
            </div>
          </div>
          <div v-else class="kb-empty">暂无已上传文档</div>
        </section>
      </div>

      <!-- 右侧：Execution Log -->
      <div class="kb-col">
        <section class="kb-section">
          <div class="kb-section-head kb-section-head-row">
            <h2 class="font-inter text-label-sm text-on-surface uppercase tracking-widest">
              Execution Log
            </h2>
            <span class="kb-live">
              <span class="kb-live-dot"></span>
              LIVE
            </span>
          </div>
          <div v-if="executionLog.length > 0" class="kb-log-list">
            <div
              v-for="(run, idx) in executionLog"
              :key="idx"
              class="kb-log-item"
              :class="{ 'kb-log-failed': run.status === 'failed' }"
            >
              <span class="kb-log-time">{{ run.time }}</span>
              <span class="kb-log-text">{{ run.label }}</span>
              <span class="kb-log-status">{{ run.statusLabel }}</span>
            </div>
          </div>
          <div v-else class="kb-empty">暂无执行记录</div>
        </section>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import ChatLogicBase from "../chat/ChatLogicBase.vue";
import doctorApi from "../../services/doctorApi";

export default {
  name: "KnowledgePage",
  extends: ChatLogicBase,
  data() {
    return {
      isDragging: false,
      executionLog: [],
    };
  },
  mounted() {
    this.fetchUploadedDocs();
    this.fetchExecutionLog();
  },
  methods: {
    handleFileChange(event) {
      var files = event && event.target && event.target.files;
      if (!files || files.length === 0) {
        return;
      }
      this.processUpload(files[0]);
      event.target.value = "";
    },
    handleDrop(event) {
      this.isDragging = false;
      var files =
        event && event.dataTransfer && event.dataTransfer.files;
      if (!files || files.length === 0) {
        return;
      }
      this.processUpload(files[0]);
    },
    processUpload(file) {
      if (!file) {
        return;
      }
      if (!/\.txt$/i.test(file.name || "")) {
        this.showUiMessage("error", "仅支持 .txt 文本文件");
        return;
      }
      var me = this;
      this.uploadDoc({
        file: file,
        onSuccess: function () {
          me.fetchUploadedDocs();
        },
      });
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
  },
};
</script>

<style scoped>
.kb-page {
  display: flex;
  flex-direction: column;
  gap: 32px;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  padding: 32px 24px 48px;
  background: var(--color-background);
}

.kb-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 48px;
  align-items: start;
}

.kb-col {
  display: flex;
  flex-direction: column;
  gap: 32px;
  min-width: 0;
}

.kb-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-bottom: 1px solid var(--color-surface-container);
  padding-bottom: 24px;
}

.kb-stats {
  display: flex;
  gap: 48px;
}

.kb-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.kb-stat-value {
  font-size: 24px;
  font-weight: 500;
  color: var(--color-on-surface);
  font-variant-numeric: tabular-nums;
}

.kb-stat-label {
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

.kb-dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 48px 32px;
  border: 1px dashed var(--color-outline-variant);
  border-radius: 8px;
  background: var(--color-surface-container-low);
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.kb-dropzone:hover,
.kb-dropzone-active {
  border-color: var(--color-primary-fixed-dim);
  background: color-mix(in srgb, var(--color-primary) 4%, transparent);
}

.kb-file-input {
  display: none;
}

.kb-dropzone-icon {
  font-size: 32px;
  color: var(--color-on-surface-variant);
}

.kb-dropzone-text {
  font-size: 14px;
  color: var(--color-on-surface-variant);
}

.kb-dropzone-text em {
  color: var(--color-primary-fixed-dim);
  font-style: normal;
}

.kb-dropzone-hint {
  font-size: 11px;
  color: var(--color-on-surface-variant);
}

.kb-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.kb-section-head {
  display: flex;
  align-items: center;
}

.kb-file-list {
  display: flex;
  flex-direction: column;
}

.kb-file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-surface-container);
}

.kb-file-icon {
  font-size: 18px;
  color: var(--color-on-surface-variant);
}

.kb-file-name {
  flex: 1;
  font-size: 14px;
  color: var(--color-on-surface);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-file-meta {
  font-size: 9px;
  letter-spacing: 0.08em;
  color: var(--color-on-surface-variant);
  flex-shrink: 0;
}

.kb-empty {
  padding: 24px 0;
  font-size: 13px;
  color: var(--color-on-surface-variant);
}

.kb-section-head-row {
  justify-content: space-between;
}

.kb-log-list {
  display: flex;
  flex-direction: column;
}

.kb-log-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-surface-container);
}

.kb-log-time {
  font-size: 12px;
  color: var(--color-primary-fixed-dim);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
  font-family: ui-monospace, monospace;
}

.kb-log-text {
  flex: 1;
  font-size: 13px;
  color: var(--color-on-surface-variant);
  font-family: ui-monospace, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-log-status {
  font-size: 12px;
  color: var(--color-on-surface-variant);
  flex-shrink: 0;
}

.kb-log-failed .kb-log-time,
.kb-log-failed .kb-log-status {
  color: var(--color-error);
}

.kb-live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

.kb-live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 6px rgba(34, 197, 94, 0.5);
  animation: kb-pulse 1.6s ease-in-out infinite;
}

@keyframes kb-pulse {
  0%,
  100% {
    opacity: 0.4;
  }
  50% {
    opacity: 1;
  }
}

@media (max-width: 900px) {
  .kb-columns {
    grid-template-columns: 1fr;
    gap: 32px;
  }
}
</style>
