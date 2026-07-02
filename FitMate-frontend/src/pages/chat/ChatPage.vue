<template>
  <div class="chat-page">
    <div class="chat-history-sticky">
      <div class="chat-history-bar">
        <button
          type="button"
          class="chat-history-action"
          :disabled="isSending || isStreaming || hasPendingAgentRun()"
          @click="handleCreateChat"
        >
          <span class="material-symbols-outlined">add</span>
          新建聊天
        </button>
        <button
          type="button"
          class="chat-history-action chat-history-toggle"
          @click="handleToggleChatExpand"
        >
          <span class="material-symbols-outlined">history</span>
          会话记录
          <span class="material-symbols-outlined chat-history-chevron">
            {{ chatExpanded ? "expand_less" : "expand_more" }}
          </span>
        </button>
      </div>

      <div v-if="chatExpanded" class="chat-history-panel">
        <div v-if="chatRecordsLoading" class="chat-history-empty">
          正在加载会话记录...
        </div>
        <div v-else-if="chatSessionList.length === 0" class="chat-history-empty">
          暂无会话记录
        </div>
        <template v-else>
          <button
            v-for="session in chatSessionList"
            :key="session.sessionId"
            type="button"
            class="chat-history-item"
            :class="{ 'chat-history-item-active': session.sessionId === activeChatSessionId }"
            @click="handleSelectChatSession(session.sessionId)"
          >
            <span class="chat-history-title">{{ resolveSessionTitle(session) }}</span>
            <span class="chat-history-meta">{{ formatSessionMeta(session) }}</span>
          </button>
        </template>
      </div>
    </div>

    <ChatMessageList
      :chat-list="chatList"
      :is-sending="isSending"
      :is-streaming="isStreaming"
      :show-back-to-bottom="showBackToBottom"
      :agent-steps="agentSteps"
      :active-agent-run="activeAgentRun"
      :thinking-content="thinkingContent"
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

    <div class="chat-page-input">
      <ChatInput
        ref="chatInputPanel"
        v-model="draftMessage"
        :internet-search-selected="internetSearchSelected"
        :knowledge-base-selected="knowledgeBaseSelected"
        :rag-selected="ragSelected"
        :agent-mode-selected="agentModeSelected"
        :is-sending="isSending"
        :is-streaming="isStreaming"
        @send="doChat"
        @toggle-internet-search="doInternetSearch"
        @toggle-knowledge-base="doKnowledgeBase"
        @toggle-rag="doRag"
        @toggle-agent-mode="doAgentMode"
      />
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
    if (!draft) {
      return;
    }
    window.sessionStorage.removeItem(PENDING_DRAFT_KEY);
    this.draftMessage = draft;
    var me = this;
    this.$nextTick(function () {
      me.doChat();
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

.chat-history-toggle {
  margin-left: auto;
}

.chat-history-chevron {
  margin-left: 2px;
}

.chat-history-panel {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 8px;
  max-height: 180px;
  overflow-y: auto;
  padding: 10px 18px 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--color-on-surface) 8%, transparent);
  background: color-mix(in srgb, var(--color-surface-container-lowest) 98%, transparent);
}

.chat-history-empty {
  grid-column: 1 / -1;
  padding: 10px 0;
  color: color-mix(in srgb, var(--color-on-surface) 52%, transparent);
  font-size: 13px;
}

.chat-history-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
  border: 1px solid color-mix(in srgb, var(--color-on-surface) 8%, transparent);
  border-radius: 12px;
  padding: 10px 12px;
  background: color-mix(in srgb, var(--color-on-surface) 4%, transparent);
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
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
}

.chat-history-meta {
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: color-mix(in srgb, var(--color-on-surface) 52%, transparent);
  font-size: 12px;
}

.chat-page-input {
  flex-shrink: 0;
  border-top: 1px solid color-mix(in srgb, var(--color-surface-container) 60%, transparent);
}
</style>
