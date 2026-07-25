<template>
  <div class="chat-input-card">
    <!-- Mode toggle pills -->
    <div class="mode-toggle" role="group" aria-label="任务模式切换">
      <button
        type="button"
        class="mode-pill"
        :class="{ 'mode-pill-active': knowledgeBaseSelected }"
        :aria-pressed="knowledgeBaseSelected ? 'true' : 'false'"
        @click="$emit('toggle-knowledge-base', knowledgeBaseSelected)"
      >
        Wiki
      </button>
      <button
        type="button"
        class="mode-pill"
        :class="{ 'mode-pill-active': ragSelected }"
        :disabled="!knowledgeBaseSelected"
        :aria-pressed="ragSelected ? 'true' : 'false'"
        @click="$emit('toggle-rag', ragSelected)"
      >
        RAG
      </button>
      <button
        type="button"
        class="mode-pill"
        :class="{ 'mode-pill-active': internetSearchSelected }"
        :aria-pressed="internetSearchSelected ? 'true' : 'false'"
        @click="$emit('toggle-internet-search', internetSearchSelected)"
      >
        WebSearch
      </button>
    </div>

    <!-- Input -->
    <div class="chat-input-bar">
      <textarea
        ref="userInput"
        class="chat-input-field"
        :value="modelValue"
        :placeholder="inputPlaceholderText"
        aria-label="任务输入框"
        autocomplete="off"
        spellcheck="false"
        rows="1"
        :aria-busy="isSending || isStreaming ? 'true' : 'false'"
        @input="handleInput"
        @keydown="handleKeyDown"
      ></textarea>
    </div>

    <!-- Footer: model selector 左对齐 + token 用量 + 发送按钮 右对齐 -->
    <div class="chat-input-footer">
      <div class="footer-left">
        <ModelSelector
          :model="currentModel"
          :models="availableModels"
          @select="$emit('select-model', $event)"
        />
        <button
          type="button"
          class="thinking-toggle"
          :class="{ 'thinking-toggle-on': thinkingEnabled }"
          :title="thinkingEnabled ? '关闭思考模式' : '开启思考模式'"
          @click="$emit('toggle-thinking')"
        >
          <span class="material-symbols-outlined thinking-toggle-icon">psychology</span>
          <span class="thinking-toggle-label">思考</span>
        </button>
        <div class="effort-selector" ref="effortRoot">
          <button
            type="button"
            class="effort-trigger"
            :disabled="!thinkingEnabled"
            @click="toggleEffortOpen"
          >
            <span class="effort-label">{{ reasoningEffort || 'high' }}</span>
            <span class="material-symbols-outlined effort-chevron">
              {{ effortOpen ? 'expand_less' : 'expand_more' }}
            </span>
          </button>
          <div v-if="effortOpen" class="effort-dropdown">
            <button
              v-for="opt in effortOptions"
              :key="opt"
              type="button"
              class="effort-option"
              :class="{ 'effort-option-active': opt === reasoningEffort }"
              @click="selectEffort(opt)"
            >
              {{ opt }}
            </button>
          </div>
        </div>
        <button
          v-if="showCompressBtn"
          type="button"
          class="compress-trigger"
          :disabled="isCompressing || isSending || isStreaming || !canCompress"
          :title="compressBtnTitle"
          @click="$emit('trigger-compress')"
        >
          <span class="material-symbols-outlined" :class="{ 'spin': isCompressing }">
            {{ isCompressing ? 'sync' : 'compress' }}
          </span>
        </button>
      </div>
      <div class="footer-right">
        <span v-if="isCompressing" class="compress-status">
          <span class="material-symbols-outlined spin">sync</span>
          <span>正在压缩上下文</span>
        </span>
        <TokenUsageIndicator :token-usage="tokenUsage" />
        <button
          v-if="!(isSending || isStreaming)"
          type="button"
          class="chat-send-btn"
          :aria-label="'发送任务'"
          @click="$emit('send')"
        >
          <span class="material-symbols-outlined">arrow_upward</span>
        </button>
        <button
          v-else
          type="button"
          class="chat-stop-btn"
          :aria-label="'停止生成'"
          @click="$emit('stop')"
        >
          <span class="material-symbols-outlined">stop_circle</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import TokenUsageIndicator from "./TokenUsageIndicator.vue";
import ModelSelector from "./ModelSelector.vue";

export default {
  name: "ChatInput",
  components: {
    TokenUsageIndicator,
    ModelSelector,
  },
  emits: [
    "update:modelValue",
    "send",
    "stop",
    "select-model",
    "toggle-thinking",
    "select-reasoning-effort",
    "toggle-internet-search",
    "toggle-knowledge-base",
    "toggle-rag",
    "trigger-compress",
  ],
  data() {
    return {
      effortOpen: false,
      effortOptions: ["high", "max"],
    };
  },
  mounted() {
    document.addEventListener("click", this.onDocumentClickEffort);
  },
  beforeUnmount() {
    document.removeEventListener("click", this.onDocumentClickEffort);
  },
  props: {
    modelValue: {
      type: String,
      default: "",
    },
    internetSearchSelected: {
      type: Boolean,
      default: false,
    },
    knowledgeBaseSelected: {
      type: Boolean,
      default: true,
    },
    ragSelected: {
      type: Boolean,
      default: false,
    },
    isSending: {
      type: Boolean,
      default: false,
    },
    isStreaming: {
      type: Boolean,
      default: false,
    },
    tokenUsage: {
      type: Object,
      default: null,
    },
    currentModel: {
      type: String,
      default: "",
    },
    availableModels: {
      type: Array,
      default: () => [],
    },
    thinkingEnabled: {
      type: Boolean,
      default: true,
    },
    reasoningEffort: {
      type: String,
      default: "high",
    },
    isCompressing: {
      type: Boolean,
      default: false,
    },
    showCompressBtn: {
      type: Boolean,
      default: true,
    },
    canCompress: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    compressBtnTitle() {
      if (this.isCompressing) return "正在压缩上下文...";
      if (!this.canCompress) return "历史消息不足，无法压缩";
      return "压缩对话上下文";
    },
    inputPlaceholderText() {
      if (this.isStreaming) {
        return "任务执行中，可先输入下一条指令...";
      }
      if (this.isSending) {
        return "任务已提交，等待执行中...";
      }
      if (this.knowledgeBaseSelected && this.ragSelected) {
        return "输入问题，将从知识库 Wiki 与原始文档中检索...";
      }
      if (this.knowledgeBaseSelected) {
        return "输入问题，将从知识库 Wiki 中检索相关信息...";
      }
      if (this.internetSearchSelected) {
        return "输入问题，将联网搜索获取最新信息...";
      }
      return "请输入任务，例如：分析我这周训练并生成周报";
    },
  },
  methods: {
    handleInput(event) {
      this.$emit("update:modelValue", event.target.value);
      this.autoResize(event.target);
    },
    toggleEffortOpen() {
      if (!this.thinkingEnabled) return;
      this.effortOpen = !this.effortOpen;
    },
    selectEffort(opt) {
      this.effortOpen = false;
      this.$emit("select-reasoning-effort", opt);
    },
    onDocumentClickEffort(e) {
      var root = this.$refs.effortRoot;
      if (root && !root.contains(e.target)) {
        this.effortOpen = false;
      }
    },
    autoResize(el) {
      if (!el) {
        return;
      }
      el.style.height = "auto";
      el.style.height = Math.min(el.scrollHeight, 160) + "px";
    },
    handleKeyDown(event) {
      if (!event) {
        return;
      }
      var isEnter = event.key === "Enter" || event.keyCode === 13;
      if (!isEnter) {
        return;
      }
      var isComposing = event.isComposing || event.keyCode === 229;
      if (isComposing) {
        return;
      }
      if (event.ctrlKey || event.altKey || event.metaKey || event.shiftKey) {
        return;
      }
      event.preventDefault();
      this.$emit("send");
    },
    focusInput() {
      var userInput = this.$refs.userInput;
      if (!userInput) {
        return;
      }
      userInput.focus();
    },
    setCursorToEnd() {
      var me = this;
      this.$nextTick(function () {
        var userInput = me.$refs.userInput;
        if (!userInput) {
          return;
        }
        userInput.focus();
        var valueLength = userInput.value.length;
        if (typeof userInput.setSelectionRange === "function") {
          userInput.setSelectionRange(valueLength, valueLength);
        }
        me.autoResize(userInput);
      });
    },
  },
};
</script>

<style scoped>
.chat-input-card {
  max-width: 768px;
  margin: 0 auto;
  width: 100%;
  padding: 14px 18px 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: 3px solid var(--color-outline);
  border-radius: 0;
  background: var(--color-surface);
  box-shadow: 4px 4px 0 0 #101010, inset 2px 2px 0 0 rgba(16, 16, 16, 0.15);
  transition: border-color 0.1s;
}

.chat-input-card:focus-within {
  border-color: var(--pixel-blue);
}

.mode-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mode-pill {
  padding: 3px 14px;
  border: 2px solid var(--color-outline);
  border-radius: 0;
  background: var(--color-surface);
  color: var(--color-on-surface-variant);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0;
  cursor: pointer;
  box-shadow: 2px 2px 0 0 #101010;
  transition: color 0.1s, border-color 0.1s, background-color 0.1s;
}

.mode-pill:hover {
  color: var(--color-on-surface);
  border-color: var(--pixel-blue);
}

.mode-pill:active:not(:disabled) {
  transform: translate(2px, 2px);
  box-shadow: 0 0 0 0 #101010;
}

.mode-pill-active {
  color: var(--color-on-primary);
  background: var(--color-primary);
  border-color: var(--color-outline);
}

.mode-pill:disabled {
  cursor: not-allowed;
  opacity: 0.5;
  box-shadow: 2px 2px 0 0 #666666;
}

.chat-input-bar {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding-bottom: 4px;
}

.chat-input-field {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  resize: none;
  color: var(--color-on-surface);
  font-size: 17px;
  line-height: 1.5;
  letter-spacing: 0;
  max-height: 160px;
  padding: 4px 0;
}

.chat-input-field::placeholder {
  color: var(--color-on-surface-variant);
  opacity: 0.7;
}

.chat-send-btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 2px solid var(--color-outline);
  border-radius: 0;
  background: var(--color-primary);
  color: var(--color-on-primary);
  cursor: pointer;
  box-shadow: 2px 2px 0 0 #101010;
  transition: background-color 0.1s, color 0.1s;
}

.chat-send-btn:hover:not(:disabled) {
  background: var(--pixel-green);
  color: var(--pixel-white);
}

.chat-send-btn:active:not(:disabled) {
  transform: translate(2px, 2px);
  box-shadow: 0 0 0 0 #101010;
}

.chat-send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
  background: var(--pixel-gray);
  box-shadow: 2px 2px 0 0 #666666;
}

.chat-send-btn .material-symbols-outlined {
  font-size: 20px;
}

.chat-stop-btn {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  border: 2px solid var(--color-outline);
  border-radius: 0;
  background: var(--pixel-red);
  color: var(--pixel-white);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 2px 2px 0 0 #101010;
  transition: background-color 0.1s;
}
.chat-stop-btn:hover {
  background: var(--pixel-yellow);
  color: var(--pixel-black);
}
.chat-stop-btn:active {
  transform: translate(2px, 2px);
  box-shadow: 0 0 0 0 #101010;
}
.chat-stop-btn .material-symbols-outlined {
  font-size: 20px;
}

.chat-input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 14px;
}

.footer-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.footer-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.compress-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: 2px solid var(--color-outline);
  border-radius: 0;
  background: var(--color-surface);
  color: var(--color-on-surface-variant);
  cursor: pointer;
  transition: color 0.1s, border-color 0.1s, background-color 0.1s;
}

.compress-trigger:hover:not(:disabled) {
  color: var(--color-primary);
  border-color: var(--pixel-blue);
}

.compress-trigger:active:not(:disabled) {
  transform: translate(1px, 1px);
}

.compress-trigger:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.compress-trigger .material-symbols-outlined {
  font-size: 15px;
}

/* Thinking toggle */
.thinking-toggle {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  border: 2px solid transparent;
  border-radius: 0;
  background: transparent;
  color: var(--color-on-surface-variant);
  font-size: 13px;
  letter-spacing: 0;
  cursor: pointer;
  transition: color 0.1s, background-color 0.1s, border-color 0.1s;
}

.thinking-toggle:hover {
  color: var(--color-on-surface);
  background: var(--color-surface-container);
  border-color: var(--color-outline);
}

.thinking-toggle:active {
  transform: translate(1px, 1px);
}

.thinking-toggle-on {
  color: var(--color-primary);
  border-color: var(--color-outline);
}

.thinking-toggle-icon {
  font-size: 14px !important;
}

.thinking-toggle-label {
  font-weight: 600;
}

/* Effort selector */
.effort-selector {
  position: relative;
  display: inline-flex;
}

.effort-trigger {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 6px;
  border: 2px solid transparent;
  border-radius: 0;
  background: transparent;
  color: var(--color-on-surface-variant);
  font-size: 13px;
  letter-spacing: 0;
  cursor: pointer;
  transition: color 0.1s, background-color 0.1s, border-color 0.1s;
}

.effort-trigger:hover:not(:disabled) {
  color: var(--color-on-surface);
  background: var(--color-surface-container);
  border-color: var(--color-outline);
}

.effort-trigger:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.effort-chevron {
  font-size: 14px !important;
}

.effort-dropdown {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 0;
  min-width: 80px;
  padding: 4px;
  border: 3px solid var(--color-outline);
  border-radius: 0;
  background: var(--color-surface);
  box-shadow: 4px 4px 0 0 #101010;
  z-index: 100;
}

.effort-option {
  display: block;
  width: 100%;
  text-align: left;
  padding: 4px 8px;
  border: none;
  border-radius: 0;
  background: transparent;
  color: var(--color-on-surface);
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.1s;
}

.effort-option:hover {
  background: var(--color-surface-container);
}

.effort-option-active {
  color: var(--color-on-primary);
  background: var(--color-primary);
  font-weight: 600;
}

.compress-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-primary);
}

.compress-status .material-symbols-outlined {
  font-size: 13px;
}

.spin {
  animation: spin 1.2s steps(8) infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
