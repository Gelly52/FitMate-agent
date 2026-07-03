<!-- FitMate-frontend/src/pages/chat/components/ModelSelector.vue -->
<template>
  <div class="model-selector" ref="root">
    <button
      type="button"
      class="model-selector-trigger"
      @click="toggle"
    >
      <span class="material-symbols-outlined model-selector-icon">neurology</span>
      <span class="model-selector-name">{{ model || "未选择" }}</span>
      <span class="material-symbols-outlined model-selector-chevron">
        {{ open ? "expand_less" : "expand_more" }}
      </span>
    </button>
    <div v-if="open" class="model-selector-dropdown">
      <div v-if="!models || models.length === 0" class="model-selector-empty">
        暂无模型，请到设置页拉取
      </div>
      <button
        v-for="m in models"
        :key="m.id"
        type="button"
        class="model-option"
        :class="{ 'model-option-active': m.id === model }"
        @click="select(m.id)"
      >
        {{ m.id }}
      </button>
    </div>
  </div>
</template>

<script lang="ts">
import type { LlmModelOption } from "../../../types/settings";

export default {
  name: "ModelSelector",
  props: {
    model: {
      type: String,
      default: "",
    },
    models: {
      type: Array as () => LlmModelOption[],
      default: () => [],
    },
  },
  emits: ["select"],
  data() {
    return {
      open: false,
    };
  },
  mounted() {
    document.addEventListener("click", this.onDocumentClick);
  },
  beforeUnmount() {
    document.removeEventListener("click", this.onDocumentClick);
  },
  methods: {
    toggle() {
      this.open = !this.open;
    },
    select(modelId: string) {
      this.open = false;
      this.$emit("select", modelId);
    },
    onDocumentClick(e: MouseEvent) {
      const root = this.$refs.root as HTMLElement | null;
      if (root && !root.contains(e.target as Node)) {
        this.open = false;
      }
    },
  },
};
</script>

<style scoped>
.model-selector {
  position: relative;
  display: inline-flex;
}
.model-selector-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-on-surface-variant);
  font-size: 11px;
  font-family: "Inter", sans-serif;
  cursor: pointer;
  transition: color 0.2s ease, background 0.2s ease;
}
.model-selector-trigger:hover {
  color: var(--color-on-surface);
  background: var(--color-surface-container);
}
.model-selector-icon {
  font-size: 14px !important;
}
.model-selector-name {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.model-selector-chevron {
  font-size: 14px !important;
}
.model-selector-dropdown {
  position: absolute;
  bottom: calc(100% + 4px);
  left: 0;
  min-width: 180px;
  max-height: 240px;
  overflow-y: auto;
  padding: 4px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  background: var(--color-surface-container-lowest, var(--color-background));
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  z-index: 100;
}
.model-selector-empty {
  padding: 8px 12px;
  font-size: 11px;
  color: var(--color-on-surface-variant);
  opacity: 0.7;
}
.model-option {
  display: block;
  width: 100%;
  text-align: left;
  padding: 6px 10px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-on-surface);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.model-option:hover {
  background: var(--color-surface-container);
}
.model-option-active {
  color: var(--color-primary);
  font-weight: 600;
}
</style>
