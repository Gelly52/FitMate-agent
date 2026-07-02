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
</template>

<script lang="ts">
import ChatLogicBase from "../chat/ChatLogicBase.vue";

export default {
  name: "KnowledgePage",
  extends: ChatLogicBase,
  data() {
    return {
      isDragging: false,
    };
  },
  mounted() {
    this.fetchUploadedDocs();
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
  },
};
</script>

<style scoped>
.kb-page {
  display: flex;
  flex-direction: column;
  gap: 32px;
  width: 100%;
  max-width: 800px;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  padding: 32px 24px 48px;
  background: var(--color-background);
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
</style>
