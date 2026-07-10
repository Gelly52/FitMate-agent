<!-- FitMate-frontend/src/pages/settings/components/LlmConfigSection.vue -->
<template>
  <section class="llm-config-section">
    <div class="llm-config-card">
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
        <p class="llm-hint">留空表示不修改原 Key（显示为脱敏值）</p>
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
      form: { ...DEFAULT_LLM_CONFIG } as LlmConfig,
      models: [] as LlmModelOption[],
      fetchingModels: false,
      testing: false,
      saving: false,
      testResult: null as LlmTestResult | null,
      apiKeyEdited: false,
    };
  },
  computed: {
    apiKeyPlaceholder() {
      const current = llmConfig.getConfig().apiKey;
      if (current && !this.apiKeyEdited) {
        return current;
      }
      return "输入 API Key（留空不修改）";
    },
  },
  mounted() {
    this.syncFromStore();
  },
  methods: {
    syncFromStore() {
      const cfg = llmConfig.getConfig();
      this.form = { ...cfg, apiKey: "" };
      this.models = llmConfig.getModels();
      this.apiKeyEdited = false;
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
      if (!this.form.apiKey && !stored.apiKey) {
        this.$message && this.$message.error && this.$message.error("API Key 不能为空");
        return;
      }
      this.saving = true;
      try {
        await llmConfig.save(this.form);
        this.syncFromStore();
        this.$message && this.$message.success && this.$message.success("LLM 配置已保存");
      } catch (e) {
        this.$message && this.$message.error && this.$message.error("保存失败");
      } finally {
        this.saving = false;
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
  border: 1px solid var(--color-outline-variant);
  border-radius: 12px;
  background: var(--color-surface-container-lowest, var(--color-background));
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
  font-size: 13px;
  color: var(--color-on-surface-variant);
  font-weight: 600;
}
.llm-required {
  color: var(--color-error, #ef4444);
}
.llm-hint {
  font-size: 11px;
  color: var(--color-on-surface-variant);
  opacity: 0.7;
  margin: 0;
}
.llm-input,
.llm-select {
  padding: 8px 12px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  background: var(--color-background);
  color: var(--color-on-surface);
  font-size: 14px;
  font-family: "Inter", sans-serif;
  outline: none;
  transition: border-color 0.2s ease;
}
.llm-input:focus,
.llm-select:focus {
  border-color: var(--color-primary);
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
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  background: transparent;
  color: var(--color-on-surface-variant);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease;
}
.llm-btn-secondary:hover:not(:disabled) {
  color: var(--color-primary);
  border-color: var(--color-primary);
}
.llm-btn-secondary:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.llm-test-result {
  font-size: 12px;
  margin: 0;
}
.llm-test-ok {
  color: var(--color-primary);
}
.llm-test-fail {
  color: var(--color-error, #ef4444);
}
.llm-toggle {
  width: 44px;
  height: 24px;
  border-radius: 999px;
  border: none;
  background: var(--color-outline-variant);
  position: relative;
  cursor: pointer;
  transition: background 0.2s ease;
}
.llm-toggle-on {
  background: var(--color-primary);
}
.llm-toggle-knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.2s ease;
}
.llm-toggle-on .llm-toggle-knob {
  transform: translateX(20px);
}
.llm-radio-group {
  display: flex;
  gap: 16px;
}
.llm-radio {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-on-surface);
  cursor: pointer;
}
.llm-radio input {
  cursor: pointer;
}
.llm-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
.llm-btn-primary {
  padding: 9px 24px;
  border: none;
  border-radius: 8px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s ease;
}
.llm-btn-primary:hover:not(:disabled) {
  opacity: 0.85;
}
.llm-btn-primary:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
