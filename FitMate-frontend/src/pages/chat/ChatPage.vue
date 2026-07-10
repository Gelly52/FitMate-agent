<template>
  <div class="chat-page">
    <div class="chat-history-sticky">
      <div class="chat-history-bar">
        <button
          v-if="activeView === 'chat-history'"
          type="button"
          class="chat-history-action"
          @click="handleBackToChat"
        >
          <span class="material-symbols-outlined">arrow_back</span>
          返回聊天
        </button>
        <template v-else>
          <button
            type="button"
            class="chat-history-action"
            :disabled="isSending || isStreaming"
            @click="handleCreateChat"
          >
            <span class="material-symbols-outlined">add</span>
            新建聊天
          </button>
          <div
            class="chat-history-toggle-wrap"
            @mouseenter="handleChatHistoryHover(true)"
            @mouseleave="handleChatHistoryHover(false)"
          >
            <button
              type="button"
              class="chat-history-action chat-history-toggle"
            >
              <span class="material-symbols-outlined">history</span>
              会话记录
              <span class="material-symbols-outlined chat-history-chevron">
                {{ chatExpanded ? "expand_less" : "expand_more" }}
              </span>
            </button>
            <div
              v-if="chatExpanded && activeView === 'chat'"
              class="chat-history-panel"
            >
              <div v-if="chatRecordsLoading" class="chat-history-empty">
                正在加载会话记录...
              </div>
              <div v-else-if="chatSessionList.length === 0" class="chat-history-empty">
                暂无会话记录
              </div>
              <template v-else>
                <div
                  v-for="session in recentChatSessions"
                  :key="session.sessionId"
                  class="chat-history-item"
                  :class="{ 'chat-history-item-active': session.sessionId === activeChatSessionId }"
                  @click="handleSelectChatSession(session.sessionId)"
                >
                  <span class="chat-history-title">{{ resolveSessionTitle(session) }}</span>
                  <span class="chat-history-meta">{{ formatSessionMeta(session) }}</span>
                  <span class="chat-history-actions" @click.stop>
                    <button
                      type="button"
                      class="chat-history-action-btn"
                      title="重命名"
                      @click="handleRenameSession(session)"
                    >
                      <span class="material-symbols-outlined">edit</span>
                    </button>
                    <button
                      type="button"
                      class="chat-history-action-btn chat-history-action-danger"
                      title="删除"
                      @click="handleDeleteSession(session.sessionId)"
                    >
                      <span class="material-symbols-outlined">delete</span>
                    </button>
                  </span>
                </div>
                <button
                  v-if="chatSessionList.length > recentChatSessions.length"
                  type="button"
                  class="chat-history-more"
                  @click="handleShowAllChatSessions"
                >
                  ...
                </button>
              </template>
            </div>
          </div>
        </template>
      </div>
    </div>

    <template v-if="activeView === 'chat'">
      <ChatMessageList
        :chat-list="chatList"
        :is-sending="isSending"
        :is-streaming="isStreaming"
        :show-back-to-bottom="showBackToBottom"
        :active-agent-run="currentRunView"
        :active-agent-runs="activeAgentRuns"
        :is-thinking="isThinking"
        :thinking-expanded="thinkingExpanded"
        @toggle-thinking="toggleThinkingExpanded"
        @chat-scroll="handleChatScroll"
        @copy-message="copyMessageContent"
        @quote-message="quoteMessageToInput"
        @retry-message="retryUserMessage"
        @execute-direct="handleDirectTask"
        @scroll-to-bottom="scrollToBottom(true)"
      />

      <div v-if="confirmBar.visible" class="chat-confirm-bar">
        <div class="chat-confirm-card">
          <span class="material-symbols-outlined chat-confirm-icon">help</span>
          <span class="chat-confirm-text">{{ confirmBar.text }}</span>
          <div class="chat-confirm-actions">
            <button
              type="button"
              class="chat-confirm-btn chat-confirm-cancel"
              @click="confirmBarCancel"
            >
              {{ confirmBar.cancelText }}
            </button>
            <button
              type="button"
              class="chat-confirm-btn chat-confirm-accept"
              @click="confirmBarAccept"
            >
              {{ confirmBar.confirmText }}
            </button>
          </div>
        </div>
      </div>

      <div class="chat-page-input">
        <ChatInput
          ref="chatInputPanel"
          v-model="draftMessage"
          :internet-search-selected="internetSearchSelected"
          :knowledge-base-selected="knowledgeBaseSelected"
          :rag-selected="ragSelected"
          :is-sending="isSending"
          :is-streaming="isStreaming"
          :token-usage="tokenUsage"
          :current-model="currentModel"
          :available-models="availableModels"
          :thinking-enabled="thinkingEnabled"
          :reasoning-effort="reasoningEffort"
          :is-compressing="isCompressing"
          :can-compress="canCompressContext"
          @send="doChat"
          @stop="stopGeneration"
          @select-model="onModelSelect"
          @toggle-thinking="onToggleThinking"
          @select-reasoning-effort="onSelectReasoningEffort"
          @toggle-internet-search="doInternetSearch"
          @toggle-knowledge-base="doKnowledgeBase"
          @toggle-rag="doRag"
          @trigger-compress="triggerManualCompress"
        />
      </div>
    </template>

    <div v-else-if="activeView === 'chat-history'" class="chat-history-page">
      <div class="chat-history-page-header">
        <span class="chat-history-page-title">全部会话记录</span>
        <span class="chat-history-page-count">
          共 {{ chatSessionList.length }} 条
        </span>
      </div>
      <div v-if="chatRecordsLoading" class="chat-history-page-empty">
        正在加载会话记录...
      </div>
      <div v-else-if="chatSessionList.length === 0" class="chat-history-page-empty">
        暂无会话记录
      </div>
      <div v-else class="chat-history-page-list">
        <div
          v-for="session in chatSessionList"
          :key="session.sessionId"
          class="chat-history-page-item"
          :class="{ 'chat-history-page-item-active': session.sessionId === activeChatSessionId }"
          @click="handleSelectChatSession(session.sessionId)"
        >
          <span class="chat-history-page-item-title">{{ resolveSessionTitle(session) }}</span>
          <span class="chat-history-page-item-meta">{{ formatSessionMeta(session) }}</span>
          <span class="chat-history-actions" @click.stop>
            <button
              type="button"
              class="chat-history-action-btn"
              title="重命名"
              @click="handleRenameSession(session)"
            >
              <span class="material-symbols-outlined">edit</span>
            </button>
            <button
              type="button"
              class="chat-history-action-btn chat-history-action-danger"
              title="删除"
              @click="handleDeleteSession(session.sessionId)"
            >
              <span class="material-symbols-outlined">delete</span>
            </button>
          </span>
        </div>
      </div>
    </div>

    <div v-if="confirmDialog.visible" class="chat-confirm-dialog-mask">
      <div
        class="chat-confirm-dialog"
        :class="{ 'chat-confirm-dialog-danger': confirmDialog.danger }"
        role="dialog"
        aria-modal="true"
      >
        <div class="chat-confirm-dialog-header">
          <span class="material-symbols-outlined chat-confirm-dialog-icon">
            {{ confirmDialog.danger ? "warning" : "help" }}
          </span>
          <span class="chat-confirm-dialog-title">
            {{ confirmDialog.title || "确认" }}
          </span>
        </div>
        <div class="chat-confirm-dialog-body">
          {{ confirmDialog.text }}
        </div>
        <div class="chat-confirm-dialog-actions">
          <button
            type="button"
            class="chat-confirm-dialog-btn chat-confirm-dialog-cancel"
            @click="confirmDialogCancel"
          >
            {{ confirmDialog.cancelText }}
          </button>
          <button
            type="button"
            class="chat-confirm-dialog-btn chat-confirm-dialog-accept"
            @click="confirmDialogAccept"
          >
            {{ confirmDialog.confirmText }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import ChatLogicBase from "./ChatLogicBase.vue";
import ChatMessageList from "./components/ChatMessageList.vue";
import ChatInput from "./components/ChatInput.vue";

const PENDING_DRAFT_KEY = "fitmate:pending-draft";

export default {
  name: "ChatPage",
  extends: ChatLogicBase,
  components: {
    ChatMessageList,
    ChatInput,
  },
  methods: {
    resolveSessionTitle(session) {
      if (!session) {
        return "未命名会话";
      }
      return (
        session.title ||
        session.sessionTitle ||
        session.name ||
        session.summary ||
        "未命名会话"
      );
    },
    formatSessionMeta(session) {
      if (!session) {
        return "历史会话";
      }

      var rawTime =
        session.updatedAt ||
        session.updateTime ||
        session.createdAt ||
        session.createTime;
      var timeText = this.formatSessionTime(rawTime);
      var messageCount = Array.isArray(session.messages)
        ? session.messages.length
        : null;
      var countText = messageCount != null ? messageCount + " 条消息" : "";

      if (timeText && countText) {
        return timeText + " · " + countText;
      }
      return timeText || countText || session.sceneType || "历史会话";
    },
    formatSessionTime(rawTime) {
      if (!rawTime) {
        return "";
      }
      var date = rawTime instanceof Date ? rawTime : new Date(rawTime);
      if (isNaN(date.getTime())) {
        return String(rawTime);
      }

      var month = String(date.getMonth() + 1).padStart(2, "0");
      var day = String(date.getDate()).padStart(2, "0");
      var hour = String(date.getHours()).padStart(2, "0");
      var minute = String(date.getMinutes()).padStart(2, "0");
      return month + "-" + day + " " + hour + ":" + minute;
    },
  },
  mounted() {
    if (typeof window === "undefined") {
      return;
    }
    var draft = window.sessionStorage.getItem(PENDING_DRAFT_KEY);
    if (draft) {
      // 从 training/metrics 等页面跳转过来自动发送消息的场景：
      // 走 doChat 创建/继续会话，跳过会话恢复
      window.sessionStorage.removeItem(PENDING_DRAFT_KEY);
      this.draftMessage = draft;
      var me = this;
      this.$nextTick(function () {
        me.doChat();
      });
      return;
    }
    // 无 pending draft：从 URL/sessionStorage 恢复上次会话
    // 这样切换到其他页面再回来时，能恢复到原先的会话状态
    var self = this;
    this.$nextTick(function () {
      self.restoreChatSessionFromRoute();
    });
  },
};
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  flex: 1;
  min-height: 0;
  background: var(--color-background);
}

.chat-history-sticky {
  position: sticky;
  top: 0;
  z-index: 20;
  flex-shrink: 0;
  background: var(--color-background);
}

.chat-history-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  border-bottom: 1px solid color-mix(in srgb, var(--color-on-surface) 8%, transparent);
  background: color-mix(in srgb, var(--color-background) 96%, transparent);
}

.chat-history-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid color-mix(in srgb, var(--color-on-surface) 12%, transparent);
  border-radius: 999px;
  padding: 7px 12px;
  background: color-mix(in srgb, var(--color-on-surface) 6%, transparent);
  color: color-mix(in srgb, var(--color-on-surface) 88%, transparent);
  cursor: pointer;
  font-size: 13px;
}

.chat-history-action:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--color-primary) 55%, transparent);
  background: color-mix(in srgb, var(--color-primary) 16%, transparent);
}

.chat-history-action:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.chat-history-action .material-symbols-outlined {
  font-size: 18px;
}

.chat-history-toggle-wrap {
  position: relative;
  margin-left: auto;
}

.chat-history-toggle {
  /* hover 触发，无需点击 */
}

.chat-history-chevron {
  margin-left: 2px;
}

.chat-history-panel {
  position: absolute;
  top: 100%;
  right: 0;
  z-index: 30;
  width: 380px;
  max-width: calc(100vw - 36px);
  max-height: 400px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  margin-top: 4px;
  border: 1px solid color-mix(in srgb, var(--color-on-surface) 10%, transparent);
  border-radius: 12px;
  background: color-mix(in srgb, var(--color-surface-container-lowest) 98%, transparent);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18);
}

.chat-history-empty {
  padding: 10px 0;
  color: color-mix(in srgb, var(--color-on-surface) 52%, transparent);
  font-size: 13px;
  text-align: center;
}

.chat-history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  border: 1px solid transparent;
  border-radius: 8px;
  padding: 8px 10px;
  background: transparent;
  color: color-mix(in srgb, var(--color-on-surface) 88%, transparent);
  cursor: pointer;
  text-align: left;
}

.chat-history-item:hover {
  border-color: color-mix(in srgb, var(--color-primary) 48%, transparent);
  background: color-mix(in srgb, var(--color-primary) 14%, transparent);
}

.chat-history-item-active {
  border-color: color-mix(in srgb, var(--color-primary) 82%, transparent);
  background: color-mix(in srgb, var(--color-primary) 20%, transparent);
}

.chat-history-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
}

.chat-history-meta {
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: color-mix(in srgb, var(--color-on-surface) 52%, transparent);
  font-size: 12px;
}

/* 会话记录行的重命名 / 删除操作按钮 */
.chat-history-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.18s ease;
}

.chat-history-item:hover .chat-history-actions,
.chat-history-page-item:hover .chat-history-actions {
  opacity: 1;
}

.chat-history-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: color-mix(in srgb, var(--color-on-surface) 60%, transparent);
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease, border-color 0.18s ease;
}

.chat-history-action-btn .material-symbols-outlined {
  font-size: 16px;
}

.chat-history-action-btn:hover {
  background: color-mix(in srgb, var(--color-on-surface) 10%, transparent);
  color: var(--color-on-surface);
  border-color: color-mix(in srgb, var(--color-on-surface) 12%, transparent);
}

.chat-history-action-danger:hover {
  background: color-mix(in srgb, #e53935 16%, transparent);
  color: #e53935;
  border-color: color-mix(in srgb, #e53935 45%, transparent);
}

.chat-history-more {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px transparent;
  border-radius: 8px;
  padding: 6px 10px;
  background: transparent;
  color: color-mix(in srgb, var(--color-primary) 78%, transparent);
  cursor: pointer;
  text-align: center;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 2px;
}

.chat-history-more:hover {
  background: color-mix(in srgb, var(--color-primary) 14%, transparent);
}

.chat-history-page {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 16px 24px 24px;
  overflow: hidden;
}

.chat-history-page-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--color-on-surface) 8%, transparent);
  margin-bottom: 12px;
}

.chat-history-page-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.chat-history-page-count {
  font-size: 13px;
  color: color-mix(in srgb, var(--color-on-surface) 52%, transparent);
}

.chat-history-page-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: color-mix(in srgb, var(--color-on-surface) 52%, transparent);
  font-size: 14px;
}

.chat-history-page-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-right: 4px;
}

.chat-history-page-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  border: 1px solid color-mix(in srgb, var(--color-on-surface) 8%, transparent);
  border-radius: 10px;
  padding: 10px 14px;
  background: color-mix(in srgb, var(--color-on-surface) 3%, transparent);
  color: color-mix(in srgb, var(--color-on-surface) 88%, transparent);
  cursor: pointer;
  text-align: left;
}

.chat-history-page-item:hover {
  border-color: color-mix(in srgb, var(--color-primary) 48%, transparent);
  background: color-mix(in srgb, var(--color-primary) 14%, transparent);
}

.chat-history-page-item-active {
  border-color: color-mix(in srgb, var(--color-primary) 82%, transparent);
  background: color-mix(in srgb, var(--color-primary) 20%, transparent);
}

.chat-history-page-item-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
}

.chat-history-page-item-meta {
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: color-mix(in srgb, var(--color-on-surface) 52%, transparent);
  font-size: 13px;
}

.chat-page-input {
  flex-shrink: 0;
  padding: 8px 24px 16px;
  background: var(--color-background);
}

.chat-confirm-bar {
  flex-shrink: 0;
  padding: 8px 24px 0;
  background: var(--color-background);
}

.chat-confirm-card {
  max-width: 768px;
  margin: 0 auto;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border: 1px solid color-mix(in srgb, var(--color-on-surface) 10%, transparent);
  border-radius: 16px;
  background: var(--color-surface-container-lowest, var(--color-background));
  color: color-mix(in srgb, var(--color-on-surface) 88%, transparent);
  font-size: 13px;
}

.chat-confirm-icon {
  font-size: 20px;
  flex-shrink: 0;
  color: color-mix(in srgb, var(--color-on-surface) 55%, transparent);
}

.chat-confirm-text {
  flex: 1;
  min-width: 0;
  line-height: 1.4;
}

.chat-confirm-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.chat-confirm-btn {
  border: 1px solid color-mix(in srgb, var(--color-on-surface) 12%, transparent);
  border-radius: 9999px;
  padding: 6px 14px;
  font-size: 13px;
  cursor: pointer;
  background: transparent;
  color: color-mix(in srgb, var(--color-on-surface) 75%, transparent);
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.chat-confirm-cancel:hover {
  border-color: var(--color-on-surface-variant);
  color: var(--color-on-surface);
}

.chat-confirm-accept {
  border-color: color-mix(in srgb, var(--color-primary) 55%, transparent);
  background: color-mix(in srgb, var(--color-primary) 16%, transparent);
  color: var(--color-primary);
}

.chat-confirm-accept:hover {
  background: color-mix(in srgb, var(--color-primary) 24%, transparent);
  border-color: var(--color-primary);
}

/* 居中模态确认弹窗（用于删除会话等需要强提示的二次确认） */
.chat-confirm-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: color-mix(in srgb, rgba(0, 0, 0, 0.45) 100%, transparent);
  animation: chat-confirm-dialog-fade 0.18s ease;
}

@keyframes chat-confirm-dialog-fade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.chat-confirm-dialog {
  width: 100%;
  max-width: 420px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 22px 22px 18px;
  border: 1px solid color-mix(in srgb, var(--color-on-surface) 10%, transparent);
  border-radius: 18px;
  background: var(--color-surface-container-lowest, var(--color-background));
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.32);
  animation: chat-confirm-dialog-pop 0.2s ease;
}

@keyframes chat-confirm-dialog-pop {
  from {
    transform: translateY(8px) scale(0.98);
    opacity: 0;
  }
  to {
    transform: translateY(0) scale(1);
    opacity: 1;
  }
}

.chat-confirm-dialog-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-confirm-dialog-icon {
  font-size: 24px;
  flex-shrink: 0;
  color: color-mix(in srgb, var(--color-on-surface) 55%, transparent);
}

.chat-confirm-dialog-danger .chat-confirm-dialog-icon {
  color: #e53935;
}

.chat-confirm-dialog-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-on-surface);
}

.chat-confirm-dialog-body {
  font-size: 14px;
  line-height: 1.55;
  color: color-mix(in srgb, var(--color-on-surface) 78%, transparent);
  word-break: break-word;
}

.chat-confirm-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}

.chat-confirm-dialog-btn {
  min-width: 84px;
  border: 1px solid color-mix(in srgb, var(--color-on-surface) 14%, transparent);
  border-radius: 9999px;
  padding: 8px 18px;
  font-size: 13px;
  cursor: pointer;
  background: transparent;
  color: color-mix(in srgb, var(--color-on-surface) 80%, transparent);
  transition: background 0.18s ease, color 0.18s ease, border-color 0.18s ease;
}

.chat-confirm-dialog-cancel:hover {
  background: color-mix(in srgb, var(--color-on-surface) 8%, transparent);
  color: var(--color-on-surface);
}

.chat-confirm-dialog-accept {
  border-color: color-mix(in srgb, var(--color-primary) 55%, transparent);
  background: color-mix(in srgb, var(--color-primary) 18%, transparent);
  color: var(--color-primary);
  font-weight: 600;
}

.chat-confirm-dialog-accept:hover {
  background: color-mix(in srgb, var(--color-primary) 28%, transparent);
  border-color: var(--color-primary);
}

.chat-confirm-dialog-danger .chat-confirm-dialog-accept {
  border-color: color-mix(in srgb, #e53935 55%, transparent);
  background: color-mix(in srgb, #e53935 18%, transparent);
  color: #e53935;
}

.chat-confirm-dialog-danger .chat-confirm-dialog-accept:hover {
  background: color-mix(in srgb, #e53935 30%, transparent);
  border-color: #e53935;
}
</style>
