<template>
  <div
    class="source-list"
    v-if="normalizedSources.length"
    role="list"
    aria-label="消息来源列表"
  >
    <div
      class="source-card"
      v-for="(source, index) in normalizedSources"
      :key="source.id || index"
      role="listitem"
    >
      <div class="source-card-header">
        <span class="source-card-index">来源 {{ index + 1 }}</span>
        <a
          v-if="source.url"
          class="source-card-link"
          :href="source.url"
          target="_blank"
          rel="noopener noreferrer"
        >
          打开
        </a>
      </div>
      <p class="source-card-title">{{ source.title }}</p>
      <p class="source-card-snippet" v-if="source.snippet">{{ source.snippet }}</p>
      <p class="source-card-extra" v-if="source.extra">{{ source.extra }}</p>
    </div>
  </div>
</template>

<script lang="ts">
import { normalizeSources } from "../../../utils/sourceNormalizer";

export default {
  name: "SourceCardList",
  props: {
    sources: {
      type: [Array, Object, String],
      default: function () {
        return [];
      },
    },
  },
  computed: {
    normalizedSources() {
      return normalizeSources(this.sources);
    },
  },
};
</script>

<style scoped>
.source-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.source-card {
  border: 1px solid var(--color-surface-container);
  border-radius: 8px;
  background: var(--color-surface-container-low);
  padding: 10px 12px;
}

.source-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.source-card-index {
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
}

.source-card-link {
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-primary-fixed-dim);
  text-decoration: none;
}

.source-card-link:hover {
  color: var(--color-primary-fixed);
}

.source-card-title {
  font-size: 13px;
  color: var(--color-on-surface);
  margin: 0 0 4px;
  line-height: 1.4;
  word-break: break-word;
}

.source-card-snippet {
  font-size: 12px;
  color: var(--color-on-surface-variant);
  margin: 0;
  line-height: 1.5;
  word-break: break-word;
}

.source-card-extra {
  font-size: 11px;
  color: var(--color-on-surface-variant);
  margin: 4px 0 0;
}
</style>
