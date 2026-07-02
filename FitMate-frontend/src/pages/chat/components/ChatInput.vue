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
      <button
        type="button"
        class="chat-send-btn"
        :class="{ 'is-loading': isSending || isStreaming }"
        :disabled="isSending || isStreaming"
        :aria-label="isSending || isStreaming ? '任务执行中，请稍候' : '发送任务'"
        @click="$emit('send')"
      >
        <span class="material-symbols-outlined">{{
          isSending || isStreaming ? "hourglass_empty" : "arrow_upward"
        }}</span>
      </button>
    </div>

    <!-- Footer: model selector 左对齐 + token 用量右对齐 -->
    <div class="chat-input-footer">
      <ModelSelector
        :model="currentModel"
        :models="availableModels"
        @select="$emit('select-model', $event)"
      />
      <TokenUsageIndicator :token-usage="tokenUsage" />
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
    "select-model",
    "toggle-internet-search",
    "toggle-knowledge-base",
    "toggle-rag",
  ],
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
  },
  computed: {
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
  border: 1px solid color-mix(in srgb, var(--color-on-surface) 10%, transparent);
  border-radius: 16px;
  background: var(--color-surface-container-lowest, var(--color-background));
  box-shadow: 0 1px 3px color-mix(in srgb, var(--color-on-surface) 8%, transparent);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.chat-input-card:focus-within {
  border-color: color-mix(in srgb, var(--color-primary) 50%, transparent);
  box-shadow: 0 2px 8px color-mix(in srgb, var(--color-primary) 12%, transparent);
}

.mode-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mode-pill {
  padding: 4px 14px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 9999px;
  background: transparent;
  color: var(--color-on-surface-variant);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.05em;
  font-family: "Inter", sans-serif;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.mode-pill:hover {
  color: var(--color-on-surface-variant);
  border-color: var(--color-on-surface-variant);
}

.mode-pill-active {
  color: var(--color-on-primary);
  background: var(--color-primary-fixed-dim);
  border-color: var(--color-primary-fixed-dim);
}

.mode-pill:disabled {
  cursor: not-allowed;
  opacity: 0.4;
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
  font-size: 15px;
  line-height: 1.5;
  font-family: "Inter", sans-serif;
  max-height: 160px;
  padding: 4px 0;
}

.chat-input-field::placeholder {
  color: color-mix(in srgb, var(--color-on-surface-variant) 50%, transparent);
}

.chat-send-btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: var(--color-primary);
  color: var(--color-on-primary);
  cursor: pointer;
  transition: color 0.2s ease, background 0.2s ease, opacity 0.2s ease;
}

.chat-send-btn:hover:not(:disabled) {
  opacity: 0.85;
}

.chat-send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.4;
  background: var(--color-on-surface-variant);
}

.chat-send-btn .material-symbols-outlined {
  font-size: 20px;
}

.chat-input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 14px;
}
</style>
