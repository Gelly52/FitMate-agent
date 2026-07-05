<template>
  <div
    id="chat-messages"
    class="chat-scroll"
    role="log"
    aria-live="polite"
    aria-label="任务执行与对话消息"
    tabindex="0"
    @scroll="$emit('chat-scroll')"
  >
    <div class="chat-scroll-inner">
      <welcome-panel
        v-if="chatList.length === 0 && !isSending"
        @execute-direct="$emit('execute-direct', $event)"
      />

      <agent-step-card
        v-if="shouldShowTaskCard()"
        :steps="innerSteps"
        :thinking-content="innerThinkingContent"
        :thinking-segments="innerThinkingSegments"
        :is-thinking="isThinking"
        :thinking-expanded="thinkingExpanded"
        :is-sending="isSending"
        :is-streaming="isStreaming"
        @toggle-thinking="$emit('toggle-thinking')"
      />

      <template v-for="(item, index) in chatList">
        <div
          v-if="shouldShowDateSeparator(index)"
          :key="'date-' + (item.id || index)"
          class="chat-date-separator"
        >
          <span>{{ formatDateLabel(item.createdAt) }}</span>
        </div>

        <!-- User message -->
        <div
          v-if="item.chatType === 'user'"
          :key="'msg-user-' + (item.id || index)"
          class="msg-row msg-row-user"
        >
          <div class="msg-bubble msg-bubble-user">
            <div class="msg-text">{{ item.content }}</div>
            <div class="msg-meta">
              <span class="msg-time">{{ formatMessageTime(item) }}</span>
              <div class="msg-actions">
                <button type="button" class="msg-action-btn" title="复制" @click="$emit('copy-message', item)">
                  <span class="material-symbols-outlined">content_copy</span>
                </button>
                <button type="button" class="msg-action-btn" title="引用" @click="$emit('quote-message', item)">
                  <span class="material-symbols-outlined">format_quote</span>
                </button>
                <button type="button" class="msg-action-btn" title="重试" @click="$emit('retry-message', item)">
                  <span class="material-symbols-outlined">refresh</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Compress notice (system summary) -->
        <div
          v-else-if="item.chatType === 'system'"
          :key="'msg-system-' + (item.id || index)"
          class="msg-row msg-row-system"
        >
          <div class="compress-notice" :title="item.summaryContent || ''">
            <span class="material-symbols-outlined compress-icon">compress</span>
            <span>已压缩 {{ item.compressCount || 0 }} 条历史对话</span>
          </div>
        </div>

        <!-- Bot message -->
        <div
          v-else
          :key="'msg-bot-' + (item.id || index)"
          class="msg-row msg-row-bot"
        >
          <div class="msg-bubble msg-bubble-bot">
            <reasoning-trace-block
              v-if="item.thinkingContent || (item.agentSteps && item.agentSteps.length > 0) || (item.thinkingSegments && item.thinkingSegments.length > 0) || item.isThinking || item.messageId"
              :thinking-content="item.thinkingContent || ''"
              :thinking-segments="item.thinkingSegments || []"
              :steps="item.agentSteps || []"
              :is-thinking="!!item.isThinking"
              :expanded="item.thinkingExpanded !== false"
              :thinking-loaded="!!item.thinkingLoaded"
              :thinking-loading="!!item.thinkingLoading"
              :message-id="item.messageId"
              @toggle-thinking="$emit('toggle-thinking', item)"
            />
            <div class="msg-text markdown-body" v-html="item.content"></div>
            <div v-if="item.interrupted" class="msg-interrupted-badge">
              <span class="material-symbols-outlined">pause_circle</span>
              <span>已中断</span>
            </div>
            <source-card-list :sources="getMessageSources(item)" />
            <div class="msg-meta">
              <span class="msg-time">{{ formatMessageTime(item) }}</span>
              <div class="msg-actions">
                <button type="button" class="msg-action-btn" title="复制" @click="$emit('copy-message', item)">
                  <span class="material-symbols-outlined">content_copy</span>
                </button>
                <button type="button" class="msg-action-btn" title="引用" @click="$emit('quote-message', item)">
                  <span class="material-symbols-outlined">format_quote</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <button
      v-show="showBackToBottom"
      type="button"
      class="back-to-bottom"
      aria-label="回到底部"
      @click="$emit('scroll-to-bottom')"
    >
      <span class="material-symbols-outlined">arrow_downward</span>
    </button>
  </div>
</template>

<script lang="ts">
import SourceCardList from "./SourceCardList.vue";
import AgentStepCard from "./AgentStepCard.vue";
import WelcomePanel from "./WelcomePanel.vue";
import ReasoningTraceBlock from "./ReasoningTraceBlock.vue";

export default {
  name: "ChatMessageList",
  components: {
    SourceCardList,
    AgentStepCard,
    WelcomePanel,
    ReasoningTraceBlock,
  },
  emits: [
    "chat-scroll",
    "copy-message",
    "quote-message",
    "retry-message",
    "execute-direct",
    "scroll-to-bottom",
    "toggle-thinking",
  ],
  props: {
    chatList: {
      type: Array,
      default: function () {
        return [];
      },
    },
    isSending: {
      type: Boolean,
      default: false,
    },
    isStreaming: {
      type: Boolean,
      default: false,
    },
    showBackToBottom: {
      type: Boolean,
      default: false,
    },
    activeAgentRun: {
      type: Object,
      default: function () {
        return null;
      },
    },
    isThinking: {
      type: Boolean,
      default: false,
    },
    thinkingExpanded: {
      type: Boolean,
      default: true,
    },
  },
  computed: {
    innerSteps() {
      return this.activeAgentRun && this.activeAgentRun.steps
        ? this.activeAgentRun.steps
        : [];
    },
    innerThinkingContent() {
      return this.activeAgentRun && this.activeAgentRun.thinkingContent
        ? this.activeAgentRun.thinkingContent
        : "";
    },
    innerThinkingSegments() {
      return this.activeAgentRun && this.activeAgentRun.thinkingSegments
        ? this.activeAgentRun.thinkingSegments
        : [];
    },
  },
  methods: {
    shouldShowTaskCard() {
      if (this.chatList && this.chatList.length > 0) {
        return false;
      }
      var runStatus = this.activeAgentRun && this.activeAgentRun.status
        ? String(this.activeAgentRun.status).toLowerCase()
        : "";
      var hasRunningAgentRun = !!(
        this.activeAgentRun &&
        this.activeAgentRun.runId != null &&
        runStatus !== "success" &&
        runStatus !== "completed" &&
        runStatus !== "failed" &&
        runStatus !== "error"
      );
      return (
        hasRunningAgentRun ||
        (this.innerSteps && this.innerSteps.length > 0) ||
        (this.innerThinkingContent && this.innerThinkingContent.length > 0) ||
        (this.isSending && !this.isStreaming)
      );
    },
    resolveMessageDate(rawValue) {
      var input = rawValue;
      if (rawValue && typeof rawValue === "object" && rawValue.createdAt) {
        input = rawValue.createdAt;
      }
      if (!input) {
        return null;
      }
      var date = input instanceof Date ? input : new Date(input);
      if (isNaN(date.getTime())) {
        return null;
      }
      return date;
    },
    getDateKey(rawValue) {
      var date = this.resolveMessageDate(rawValue);
      if (!date) {
        return "unknown";
      }
      var year = date.getFullYear();
      var month = (date.getMonth() + 1).toString().padStart(2, "0");
      var day = date.getDate().toString().padStart(2, "0");
      return year + "-" + month + "-" + day;
    },
    shouldShowDateSeparator(index) {
      if (index === 0) {
        return true;
      }
      var current = this.chatList[index];
      var previous = this.chatList[index - 1];
      var currentDateKey = this.getDateKey(current && current.createdAt);
      var previousDateKey = this.getDateKey(previous && previous.createdAt);
      return currentDateKey !== previousDateKey;
    },
    formatDateLabel(createdAt) {
      var date = this.resolveMessageDate(createdAt);
      if (!date) {
        return "今天";
      }
      var now = new Date();
      var todayDate = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      var targetDate = new Date(
        date.getFullYear(),
        date.getMonth(),
        date.getDate()
      );
      var diffDays = Math.round((todayDate - targetDate) / 86400000);
      if (diffDays === 0) {
        return "今天";
      }
      if (diffDays === 1) {
        return "昨天";
      }
      return this.getDateKey(date);
    },
    formatMessageTime(item) {
      var date = this.resolveMessageDate(item && item.createdAt);
      if (!date) {
        return "--:--";
      }
      var hours = date.getHours().toString().padStart(2, "0");
      var minutes = date.getMinutes().toString().padStart(2, "0");
      // 跟随系统 12/24 小时制：用 toLocaleTimeString 检测系统偏好
      try {
        var sample = date.toLocaleTimeString(undefined, { hour: "numeric" });
        // 若系统输出含 AM/PM（或本地化的上/下午），则用 12 小时制
        if (/\d\s*[ap]\.?m\.?$/i.test(sample) || /[上下]午$/.test(sample)) {
          return date.toLocaleTimeString(undefined, {
            hour: "2-digit",
            minute: "2-digit",
            hour12: true,
          });
        }
      } catch (_e) {
        // 降级：保持 24 小时制
      }
      return hours + ":" + minutes;
    },
    getMessageSources(item) {
      return item && item.sources != null ? item.sources : [];
    },
  },
};
</script>

<style scoped>
.chat-scroll {
  position: relative;
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  /* Firefox */
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--color-on-surface) 15%, transparent) transparent;
}

/* Webkit (Chrome / Edge / Safari) */
.chat-scroll::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.chat-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.chat-scroll::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--color-on-surface) 15%, transparent);
  border-radius: 4px;
  border: 2px solid transparent;
  background-clip: padding-box;
}

.chat-scroll::-webkit-scrollbar-thumb:hover {
  background: color-mix(in srgb, var(--color-on-surface) 25%, transparent);
  background-clip: padding-box;
}

.chat-scroll-inner {
  max-width: 768px;
  margin: 0 auto;
  width: 100%;
  padding: 32px 24px 24px;
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.chat-date-separator {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 16px 0;
}

.chat-date-separator span {
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
  padding: 2px 10px;
  border: 1px solid var(--color-surface-container);
  border-radius: 9999px;
}

.msg-row-system {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 12px 0;
}

.compress-notice {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 14px;
  border-radius: 9999px;
  background: color-mix(in srgb, var(--color-primary) 8%, var(--color-surface));
  border: 1px solid color-mix(in srgb, var(--color-primary) 25%, transparent);
  color: var(--color-on-surface-variant);
  font-size: 12px;
  cursor: default;
  user-select: none;
}

.compress-icon {
  font-size: 14px;
  color: var(--color-primary);
}

.msg-row {
  display: flex;
  margin-bottom: 24px;
}

.msg-row-user {
  justify-content: flex-end;
}

.msg-row-bot {
  justify-content: flex-start;
}

.msg-bubble {
  max-width: 92%;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.msg-row-bot .msg-bubble {
  width: 92%;
}

/* 用户气泡：子元素右对齐，让短消息 .msg-text 按内容宽度收缩，避免尾部留空 */
.msg-row-user .msg-bubble {
  align-items: flex-end;
}

.msg-bubble-user .msg-text {
  background: var(--color-surface-container);
  border: 1px solid var(--color-surface-container-high);
  border-radius: 12px 12px 4px 12px;
  padding: 10px 14px;
  color: var(--color-on-surface);
  font-size: 15px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  max-width: 100%;
}

.msg-bubble-bot .msg-text {
  color: var(--color-on-surface);
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.msg-row-user .msg-meta {
  justify-content: flex-end;
}

.msg-row:hover .msg-meta {
  opacity: 1;
}

.msg-time {
  font-size: 9px;
  letter-spacing: 0.08em;
  color: var(--color-on-surface-variant);
}

.msg-actions {
  display: flex;
  gap: 4px;
}

.msg-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-on-surface-variant);
  cursor: pointer;
  transition: color 0.2s ease, background 0.2s ease;
}

.msg-action-btn:hover {
  color: var(--color-primary-fixed-dim);
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
}

.msg-action-btn .material-symbols-outlined {
  font-size: 16px;
}

.back-to-bottom {
  position: sticky;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--color-outline-variant);
  background: var(--color-surface-container);
  color: var(--color-on-surface-variant);
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease;
}

.back-to-bottom:hover {
  color: var(--color-primary-fixed-dim);
  border-color: var(--color-primary-fixed-dim);
}

.back-to-bottom .material-symbols-outlined {
  font-size: 20px;
}

.markdown-body :deep(p) {
  margin: 0 0 8px;
}

.markdown-body :deep(pre) {
  background: var(--color-surface-container-lowest);
  border: 1px solid var(--color-surface-container);
  border-radius: 8px;
  padding: 12px;
  overflow-x: auto;
  margin: 8px 0;
}

.markdown-body :deep(code) {
  font-family: ui-monospace, monospace;
  font-size: 13px;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}

.markdown-body :deep(a) {
  color: var(--color-primary-fixed-dim);
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--color-surface-container);
  padding: 6px 10px;
  text-align: left;
}

.msg-interrupted-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  font-size: 12px;
}
.msg-interrupted-badge .material-symbols-outlined {
  font-size: 14px;
}
</style>
