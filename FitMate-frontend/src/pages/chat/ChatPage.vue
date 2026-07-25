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
  border-bottom: 3px solid var(--color-outline);
  background: var(--color-background);
}

.chat-history-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 2px solid var(--color-outline);
  border-radius: 0;
  padding: 6px 12px;
  background: var(--color-surface);
  color: var(--color-on-surface);
  cursor: pointer;
  font-size: 15px;
  box-shadow: 2px 2px 0 0 #101010;
  transition: background-color 0.1s, color 0.1s, border-color 0.1s;
}

.chat-history-action:hover:not(:disabled) {
  border-color: var(--pixel-blue);
  color: var(--pixel-blue);
}

.chat-history-action:active:not(:disabled) {
  transform: translate(2px, 2px);
  box-shadow: 0 0 0 0 #101010;
}

.chat-history-action:disabled {
  cursor: not-allowed;
  opacity: 0.5;
  box-shadow: 2px 2px 0 0 #666666;
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
  gap: 4px;
  padding: 8px;
  margin-top: 6px;
  border: 3px solid var(--color-outline);
  border-radius: 0;
  background: var(--color-surface);
  box-shadow: 6px 6px 0 0 #101010;
}

.chat-history-empty {
  padding: 10px 0;
  color: var(--color-on-surface-variant);
  font-size: 15px;
  text-align: center;
}

.chat-history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  border: 2px solid transparent;
  border-radius: 0;
  padding: 8px 10px;
  background: transparent;
  color: var(--color-on-surface);
  cursor: pointer;
  text-align: left;
  transition: background-color 0.1s, border-color 0.1s;
}

.chat-history-item:hover {
  border-color: var(--pixel-blue);
  background: var(--color-surface-container);
}

.chat-history-item-active {
  border-color: var(--pixel-blue);
  background: var(--color-surface-container-high);
}

.chat-history-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  font-weight: 600;
}

.chat-history-meta {
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-on-surface-variant);
  font-size: 14px;
}

/* 会话记录行的重命名 / 删除操作按钮 */
.chat-history-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.1s steps(2);
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
  border: 2px solid transparent;
  border-radius: 0;
  background: transparent;
  color: var(--color-on-surface-variant);
  cursor: pointer;
  transition: background-color 0.1s, color 0.1s, border-color 0.1s;
}

.chat-history-action-btn .material-symbols-outlined {
  font-size: 16px;
}

.chat-history-action-btn:hover {
  background: var(--color-surface-container-high);
  color: var(--color-on-surface);
  border-color: var(--color-outline);
}

.chat-history-action-btn:active {
  transform: translate(1px, 1px);
}

.chat-history-action-danger:hover {
  background: var(--pixel-red);
  color: var(--pixel-white);
  border-color: var(--color-outline);
}

.chat-history-more {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid transparent;
  border-radius: 0;
  padding: 6px 10px;
  background: transparent;
  color: var(--color-primary);
  cursor: pointer;
  text-align: center;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0;
}

.chat-history-more:hover {
  border-color: var(--color-outline);
  background: var(--color-surface-container);
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
  border-bottom: 3px solid var(--color-outline);
  margin-bottom: 12px;
}

.chat-history-page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-on-surface);
}

.chat-history-page-count {
  font-size: 15px;
  color: var(--color-on-surface-variant);
}

.chat-history-page-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-on-surface-variant);
  font-size: 16px;
}

.chat-history-page-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 4px;
}

.chat-history-page-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  border: 2px solid var(--color-outline);
  border-radius: 0;
  padding: 10px 14px;
  background: var(--color-surface);
  color: var(--color-on-surface);
  box-shadow: 2px 2px 0 0 #101010;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.1s, border-color 0.1s;
}

.chat-history-page-item:hover {
  border-color: var(--pixel-blue);
  background: var(--color-surface-container);
}

.chat-history-page-item-active {
  border-color: var(--pixel-blue);
  background: var(--color-surface-container-high);
}

.chat-history-page-item-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 16px;
  font-weight: 600;
}

.chat-history-page-item-meta {
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-on-surface-variant);
  font-size: 15px;
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
  border: 3px solid var(--color-outline);
  border-radius: 0;
  background: var(--color-surface);
  color: var(--color-on-surface);
  font-size: 15px;
  box-shadow: 4px 4px 0 0 #101010;
}

.chat-confirm-icon {
  font-size: 20px;
  flex-shrink: 0;
  color: var(--pixel-yellow);
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
  border: 2px solid var(--color-outline);
  border-radius: 0;
  padding: 5px 14px;
  font-size: 15px;
  cursor: pointer;
  background: var(--color-surface);
  color: var(--color-on-surface);
  box-shadow: 2px 2px 0 0 #101010;
  transition: color 0.1s, border-color 0.1s, background-color 0.1s;
}

.chat-confirm-btn:active {
  transform: translate(2px, 2px);
  box-shadow: 0 0 0 0 #101010;
}

.chat-confirm-cancel:hover {
  background: var(--color-surface-container);
}

.chat-confirm-accept {
  border-color: var(--color-outline);
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.chat-confirm-accept:hover {
  border-color: var(--pixel-blue);
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
  background: rgba(16, 16, 16, 0.7);
  animation: chat-confirm-dialog-fade 0.15s steps(3);
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
  border: 4px solid var(--color-outline);
  border-radius: 0;
  background: var(--color-surface);
  box-shadow: 8px 8px 0 0 #101010;
  animation: chat-confirm-dialog-pop 0.15s steps(3);
}

@keyframes chat-confirm-dialog-pop {
  from {
    transform: translateY(8px) scale(0.95);
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
  color: var(--pixel-yellow);
}

.chat-confirm-dialog-danger .chat-confirm-dialog-icon {
  color: var(--pixel-red);
}

.chat-confirm-dialog-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-on-surface);
}

.chat-confirm-dialog-body {
  font-size: 16px;
  line-height: 1.5;
  color: var(--color-on-surface);
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
  border: 2px solid var(--color-outline);
  border-radius: 0;
  padding: 7px 18px;
  font-size: 15px;
  cursor: pointer;
  background: var(--color-surface);
  color: var(--color-on-surface);
  box-shadow: 2px 2px 0 0 #101010;
  transition: background-color 0.1s, color 0.1s, border-color 0.1s;
}

.chat-confirm-dialog-btn:active {
  transform: translate(2px, 2px);
  box-shadow: 0 0 0 0 #101010;
}

.chat-confirm-dialog-cancel:hover {
  background: var(--color-surface-container);
}

.chat-confirm-dialog-accept {
  border-color: var(--color-outline);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-weight: 600;
}

.chat-confirm-dialog-accept:hover {
  border-color: var(--pixel-blue);
}

.chat-confirm-dialog-danger .chat-confirm-dialog-accept {
  border-color: var(--color-outline);
  background: var(--pixel-red);
  color: var(--pixel-white);
}

.chat-confirm-dialog-danger .chat-confirm-dialog-accept:hover {
  border-color: var(--pixel-yellow);
}
</style>
