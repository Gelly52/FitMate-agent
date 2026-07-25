<!-- FitMate-frontend/src/pages/settings/components/LlmConfigSection.vue -->
<template>
  <section class="llm-config-section">
    <div class="llm-config-card">
      <!-- 使用系统默认提示 -->
      <div v-if="config.usingDefault" class="llm-default-banner">
        <span class="llm-default-icon">✓</span>
        <span>当前使用系统默认配置，修改任意字段即切换为自定义配置</span>
      </div>

      <!-- API 地址 -->
      <div class="llm-field">
        <label class="llm-label">API 地址 <span class="llm-required">*</span></label>
        <input
          v-model="form.baseUrl"
          type="text"
          class="llm-input"
          placeholder="https://api.deepseek.com"
        />
      </div>

      <!-- API Key -->
      <div class="llm-field">
        <label class="llm-label">API Key <span class="llm-required">*</span></label>
        <input
          v-model="form.apiKey"
          type="password"
          class="llm-input"
          :placeholder="apiKeyPlaceholder"
          @focus="onApiKeyFocus"
        />
        <p class="llm-hint">{{ apiKeyHint }}</p>
      </div>

      <!-- 模型 -->
      <div class="llm-field">
        <label class="llm-label">模型</label>
        <div class="llm-model-row">
          <select v-model="form.model" class="llm-select">
            <option value="" disabled>请选择模型</option>
            <option v-for="m in models" :key="m.id" :value="m.id">{{ m.id }}</option>
          </select>
          <button type="button" class="llm-btn-secondary" :disabled="fetchingModels" @click="onFetchModels">
            {{ fetchingModels ? "拉取中..." : "拉取列表" }}
          </button>
          <button type="button" class="llm-btn-secondary" :disabled="testing || !form.model" @click="onTest">
            {{ testing ? "测活中..." : "测活" }}
          </button>
        </div>
        <p v-if="testResult" class="llm-test-result" :class="testResult.ok ? 'llm-test-ok' : 'llm-test-fail'">
          {{ testResult.ok ? '✓ 连通 (' + testResult.latencyMs + 'ms)' : '✗ ' + (testResult.error || '失败') }}
        </p>
      </div>

      <!-- 输入上下文最大值 -->
      <div class="llm-field">
        <label class="llm-label">输入上下文最大值（token）</label>
        <input v-model.number="form.maxInputContextTokens" type="number" min="1" class="llm-input" />
      </div>

      <!-- 输出上下文最大值 -->
      <div class="llm-field">
        <label class="llm-label">输出上下文最大值（token）</label>
        <input v-model.number="form.maxOutputContextTokens" type="number" min="1" class="llm-input" />
      </div>

      <!-- 思考模式 -->
      <div class="llm-field llm-field-inline">
        <label class="llm-label">思考模式</label>
        <button
          type="button"
          class="llm-toggle"
          :class="{ 'llm-toggle-on': form.thinkingEnabled }"
          @click="form.thinkingEnabled = !form.thinkingEnabled"
        >
          <span class="llm-toggle-knob"></span>
        </button>
      </div>

      <!-- 推理强度 -->
      <div class="llm-field">
        <label class="llm-label">推理强度</label>
        <div class="llm-radio-group">
          <label class="llm-radio">
            <input v-model="form.reasoningEffort" type="radio" value="high" />
            <span>high</span>
          </label>
          <label class="llm-radio">
            <input v-model="form.reasoningEffort" type="radio" value="max" />
            <span>max</span>
          </label>
        </div>
      </div>

      <div class="llm-actions">
        <button
          v-if="!config.usingDefault"
          type="button"
          class="llm-btn-danger"
          :disabled="resetting"
          @click="onReset"
        >
          {{ resetting ? "重置中..." : "重置为系统默认" }}
        </button>
        <button type="button" class="llm-btn-primary" :disabled="saving" @click="onSave">
          {{ saving ? "保存中..." : "保存" }}
        </button>
      </div>
    </div>
  </section>
</template>

<script lang="ts">
import { llmConfig } from "../../../services/llmConfig";
import type { LlmConfig, LlmModelOption, LlmTestResult } from "../../../types/settings";
import { DEFAULT_LLM_CONFIG } from "../../../types/settings";

export default {
  name: "LlmConfigSection",
  data() {
    return {
      form: { ...DEFAULT_LLM_CONFIG, apiKey: "" } as LlmConfig,
      models: [] as LlmModelOption[],
      fetchingModels: false,
      testing: false,
      saving: false,
      resetting: false,
      testResult: null as LlmTestResult | null,
      apiKeyEdited: false,
    };
  },
  computed: {
    config(): LlmConfig {
      return llmConfig.getConfig();
    },
    apiKeyPlaceholder() {
      const current = llmConfig.getConfig().apiKey;
      if (current && !this.apiKeyEdited) {
        return current;
      }
      return llmConfig.getConfig().usingDefault
        ? "输入自定义 API Key 覆盖系统默认（留空使用系统默认）"
        : "输入 API Key（留空不修改）";
    },
    apiKeyHint() {
      if (llmConfig.getConfig().usingDefault) {
        return "当前使用系统默认 Key，留空将继续使用系统默认 Key";
      }
      return "留空表示不修改原 Key（显示为脱敏值）";
    },
  },
  mounted() {
    this.syncFromStore();
    this.unsubscribe = llmConfig.subscribe(() => {
      this.syncFromStore();
    });
  },
  beforeUnmount() {
    if (this.unsubscribe) {
      this.unsubscribe();
    }
  },
  methods: {
    syncFromStore() {
      const cfg = llmConfig.getConfig();
      this.form = { ...cfg, apiKey: "" };
      this.models = llmConfig.getModels();
      this.apiKeyEdited = false;
      this.testResult = null;
    },
    onApiKeyFocus() {
      this.apiKeyEdited = true;
    },
    async onFetchModels() {
      this.fetchingModels = true;
      try {
        const override = this.form.apiKey ? this.form : undefined;
        this.models = await llmConfig.fetchModels(override);
        if (this.models.length === 0) {
          this.$message && this.$message.warning && this.$message.warning("未拉取到模型");
        }
      } catch (e) {
        this.$message && this.$message.error && this.$message.error("拉取模型列表失败");
      } finally {
        this.fetchingModels = false;
      }
    },
    async onTest() {
      if (!this.form.model) return;
      this.testing = true;
      this.testResult = null;
      try {
        const override = this.form.apiKey ? this.form : { ...this.form, apiKey: undefined };
        this.testResult = await llmConfig.testConnection(override);
      } catch (e) {
        this.testResult = { ok: false, error: "测活请求失败" };
      } finally {
        this.testing = false;
      }
    },
    async onSave() {
      if (!this.form.baseUrl || !this.form.baseUrl.trim()) {
        this.$message && this.$message.error && this.$message.error("API 地址不能为空");
        return;
      }
      const stored = llmConfig.getConfig();
      if (!this.form.apiKey && !stored.apiKey && !stored.usingDefault) {
        this.$message && this.$message.error && this.$message.error("API Key 不能为空");
        return;
      }
      this.saving = true;
      try {
        await llmConfig.save(this.form);
        this.syncFromStore();
        this.$message && this.$message.success && this.$message.success("LLM 配置已保存");
      } catch (e) {
        this.$message && this.$message.error && this.$message.error((e as Error).message || "保存失败");
      } finally {
        this.saving = false;
      }
    },
    async onReset() {
      if (!window.confirm("确定要重置为系统默认配置吗？这将清除你的自定义 API Key。")) {
        return;
      }
      this.resetting = true;
      try {
        await llmConfig.reset();
        this.syncFromStore();
        this.$message && this.$message.success && this.$message.success("已重置为系统默认配置");
      } catch (e) {
        this.$message && this.$message.error && this.$message.error((e as Error).message || "重置失败");
      } finally {
        this.resetting = false;
      }
    },
  },
};
</script>

<style scoped>
.llm-config-section {
  max-width: 640px;
}
.llm-config-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  border: 4px solid var(--color-outline);
  border-radius: 0;
  background: var(--color-surface-container);
  box-shadow: 6px 6px 0 0 #101010;
}
.llm-default-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 0;
  border: 2px solid var(--color-outline);
  background: var(--pixel-green);
  color: var(--pixel-white);
  font-size: 15px;
  font-weight: 500;
  box-shadow: 2px 2px 0 0 #101010;
}
.llm-default-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 0;
  border: 2px solid var(--pixel-black);
  background: var(--pixel-white);
  color: var(--pixel-green);
  font-size: 13px;
  flex-shrink: 0;
}
.llm-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.llm-field-inline {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.llm-label {
  font-size: 15px;
  color: var(--color-on-surface-variant);
  font-weight: 600;
}
.llm-required {
  color: var(--pixel-red);
}
.llm-hint {
  font-size: 13px;
  color: var(--color-on-surface-variant);
  opacity: 0.7;
  margin: 0;
}
.llm-input,
.llm-select {
  padding: 8px 12px;
  border: 3px solid var(--color-outline);
  border-radius: 0;
  background: var(--color-surface);
  color: var(--color-on-surface);
  font-size: 16px;
  font-family: var(--font-main);
  outline: none;
  box-shadow: inset 2px 2px 0 0 rgba(16, 16, 16, 0.35);
  transition: border-color 0.1s;
}
.llm-input:focus,
.llm-select:focus {
  border-color: var(--pixel-blue);
}
.llm-model-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.llm-model-row .llm-select {
  flex: 1;
}
.llm-btn-secondary {
  padding: 8px 14px;
  border: 2px solid var(--color-outline);
  border-radius: 0;
  background: var(--color-surface);
  color: var(--color-on-surface);
  font-size: 14px;
  cursor: pointer;
  box-shadow: 2px 2px 0 0 #101010;
  transition: background-color 0.1s, color 0.1s, transform 0.1s;
}
.llm-btn-secondary:hover:not(:disabled) {
  color: var(--color-on-primary);
  background: var(--color-primary);
}
.llm-btn-secondary:active:not(:disabled) {
  transform: translate(2px, 2px);
  box-shadow: none;
}
.llm-btn-secondary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: 2px 2px 0 0 #666666;
}
.llm-test-result {
  font-size: 14px;
  margin: 0;
}
.llm-test-ok {
  color: var(--pixel-green);
}
.llm-test-fail {
  color: var(--pixel-red);
}
.llm-toggle {
  width: 52px;
  height: 26px;
  border-radius: 0;
  border: 3px solid var(--color-outline);
  background: var(--color-surface-container-high);
  position: relative;
  cursor: pointer;
  padding: 0;
  transition: background 0.1s;
}
.llm-toggle-on {
  background: var(--color-primary);
}
.llm-toggle-knob {
  position: absolute;
  top: 1px;
  left: 1px;
  width: 18px;
  height: 18px;
  border-radius: 0;
  border: 2px solid var(--pixel-black);
  background: var(--pixel-white);
  transition: transform 0.1s steps(2);
}
.llm-toggle-on .llm-toggle-knob {
  transform: translateX(26px);
}
.llm-radio-group {
  display: flex;
  gap: 16px;
}
.llm-radio {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  color: var(--color-on-surface);
  cursor: pointer;
}
.llm-radio input {
  cursor: pointer;
  accent-color: var(--color-primary);
}
.llm-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  gap: 12px;
}
.llm-btn-danger {
  padding: 9px 18px;
  border: 3px solid var(--color-outline);
  border-radius: 0;
  background: var(--pixel-red);
  color: var(--pixel-white);
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 4px 4px 0 0 #101010;
  transition: background-color 0.1s, transform 0.1s;
}
.llm-btn-danger:hover:not(:disabled) {
  transform: scale(1.05);
}
.llm-btn-danger:active:not(:disabled) {
  transform: translate(2px, 2px);
  box-shadow: 2px 2px 0 0 #101010;
}
.llm-btn-danger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: 2px 2px 0 0 #666666;
}
.llm-btn-primary {
  padding: 9px 24px;
  border: 3px solid var(--color-outline);
  border-radius: 0;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 4px 4px 0 0 #101010;
  transition: background-color 0.1s, transform 0.1s;
  margin-left: auto;
}
.llm-btn-primary:hover:not(:disabled) {
  transform: scale(1.05);
}
.llm-btn-primary:active:not(:disabled) {
  transform: translate(2px, 2px);
  box-shadow: 2px 2px 0 0 #101010;
}
.llm-btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: 2px 2px 0 0 #666666;
}
</style>
