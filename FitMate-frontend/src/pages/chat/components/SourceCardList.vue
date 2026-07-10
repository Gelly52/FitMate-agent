<template>
  <div
    v-if="normalizedSources.length"
    class="source-list"
    role="list"
    aria-label="消息来源列表"
    ref="listRoot"
  >
    <span class="source-list-label">来源</span>
    <div class="source-list-items">
      <div
        v-for="(source, index) in normalizedSources"
        :key="source.id || index"
        class="source-item"
        :class="{ 'is-open': openIdx === index }"
        ref="itemRefs"
        role="listitem"
      >
        <button
          type="button"
          class="source-item-btn"
          :aria-expanded="openIdx === index ? 'true' : 'false'"
          :aria-label="'查看来源 ' + (index + 1) + ' 详情'"
          @click="togglePopover(index, $event)"
        >
          <span class="source-item-index">[{{ index + 1 }}]</span>
          <span class="source-item-title">{{ resolveTitle(source) }}</span>
          <span
            v-if="source.url"
            class="material-symbols-outlined source-item-link-icon"
            aria-hidden="true"
          >open_in_new</span>
        </button>

        <transition name="source-popover">
          <div
            v-if="openIdx === index"
            class="source-popover"
            role="dialog"
            :aria-label="'来源 ' + (index + 1) + ' 详情'"
            :style="popoverStyle"
            @click.stop
          >
            <button
              type="button"
              class="source-popover-close"
              aria-label="关闭"
              @click="closePopover"
            >
              <span class="material-symbols-outlined">close</span>
            </button>
            <p
              v-if="shouldShowTitle(source)"
              class="source-popover-title"
            >{{ source.title }}</p>
            <p v-if="source.snippet" class="source-popover-snippet">{{ source.snippet }}</p>
            <p v-if="source.extra" class="source-popover-extra">{{ source.extra }}</p>
            <a
              v-if="source.url"
              class="source-popover-link"
              :href="source.url"
              target="_blank"
              rel="noopener noreferrer"
            >
              打开链接
              <span class="material-symbols-outlined source-popover-link-icon">open_in_new</span>
            </a>
          </div>
        </transition>
      </div>
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
  data() {
    return {
      openIdx: -1,
      popoverStyle: {} as Record<string, string>,
    };
  },
  computed: {
    normalizedSources() {
      return normalizeSources(this.sources);
    },
  },
  beforeUnmount() {
    this.removeOutsideClickListener();
  },
  watch: {
    normalizedSources() {
      // 来源列表变化时（如新消息到达）重置打开状态
      this.openIdx = -1;
    },
  },
  methods: {
    resolveTitle(source) {
      if (!source) {
        return "未命名";
      }
      var title = source.title || "未命名来源";
      // 标题过长时由 CSS ellipsis 截断，这里不额外处理
      return title;
    },
    // 判断是否在 Popover 中显示标题行：
    // 若 title 为空、或只是"来源 N"这类占位（与 chip 编号重复），则不显示
    shouldShowTitle(source) {
      if (!source) {
        return false;
      }
      var title = (source.title || "").trim();
      if (!title) {
        return false;
      }
      // 占位模式：来源 1 / 来源1 / source 1 等
      var placeholderMatch = title.match(/^(来源|source|引用|reference)\s*\d+$/i);
      if (placeholderMatch) {
        return false;
      }
      return true;
    },
    togglePopover(index, event) {
      if (event) {
        event.stopPropagation();
      }
      if (this.openIdx === index) {
        this.closePopover();
        return;
      }
      this.openIdx = index;
      // 计算锚点 chip 的视口位置，用 fixed 定位避免被 overflow 裁剪
      var btn = event && event.currentTarget;
      if (btn && btn.getBoundingClientRect) {
        var rect = btn.getBoundingClientRect();
        var popoverMaxWidth = 300;
        var viewportWidth = window.innerWidth || document.documentElement.clientWidth;
        var left = rect.left;
        // 靠右边缘时右对齐，避免溢出
        if (left + popoverMaxWidth > viewportWidth - 8) {
          left = Math.max(8, rect.right - popoverMaxWidth);
        }
        this.popoverStyle = {
          position: "fixed",
          top: (rect.bottom + 6) + "px",
          left: left + "px",
        };
      }
      // 下一帧再绑定 listener，避免当前 click 立即触发关闭
      this.$nextTick(
        function () {
          this.addOutsideClickListener();
        }.bind(this)
      );
    },
    closePopover() {
      this.openIdx = -1;
      this.removeOutsideClickListener();
    },
    handleOutsideClick(event) {
      var root = this.$refs.listRoot;
      if (root && root.contains(event.target)) {
        return;
      }
      this.closePopover();
    },
    addOutsideClickListener() {
      if (typeof document === "undefined") {
        return;
      }
      this.removeOutsideClickListener();
      // 用 capture:true 确保比按钮本身的 click 先收到
      document.addEventListener("click", this.handleOutsideClick, true);
    },
    removeOutsideClickListener() {
      if (typeof document === "undefined") {
        return;
      }
      document.removeEventListener("click", this.handleOutsideClick, true);
    },
  },
};
</script>

<style scoped>
.source-list {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  position: relative;
}

.source-list-label {
  font-size: 10px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
  flex-shrink: 0;
  opacity: 0.7;
}

.source-list-items {
  display: flex;
  flex-wrap: nowrap;
  gap: 4px;
  min-width: 0;
  flex: 1;
  overflow-x: auto;
  /* 隐藏滚动条但保留横向滚动 */
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.source-list-items::-webkit-scrollbar {
  display: none;
}

.source-item {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
}

.source-item-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  border: 1px solid var(--color-surface-container);
  border-radius: 9999px;
  background: var(--color-surface-container-low);
  color: var(--color-on-surface-variant);
  font-size: 11px;
  line-height: 1.4;
  cursor: pointer;
  max-width: 160px;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;
  white-space: nowrap;
}

.source-item-btn:hover {
  border-color: color-mix(in srgb, var(--color-primary) 40%, transparent);
  color: var(--color-on-surface);
}

.source-item.is-open .source-item-btn {
  border-color: color-mix(in srgb, var(--color-primary) 60%, transparent);
  background: color-mix(in srgb, var(--color-primary) 8%, var(--color-surface-container-low));
  color: var(--color-on-surface);
}

.source-item-index {
  flex-shrink: 0;
  font-weight: 600;
  color: var(--color-primary-fixed-dim);
}

.source-item.is-open .source-item-index {
  color: var(--color-primary-fixed);
}

.source-item-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.source-item-link-icon {
  flex-shrink: 0;
  font-size: 12px;
  opacity: 0.6;
}

/* ===== Popover（fixed 定位，位置由 JS 计算） ===== */
.source-popover {
  position: fixed;
  z-index: 1000;
  width: 300px;
  max-width: 80vw;
  padding: 10px 28px 10px 12px;
  border: 1px solid var(--color-surface-container-high);
  border-radius: 8px;
  background: var(--color-surface-container-lowest);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.18), 0 2px 6px rgba(0, 0, 0, 0.08);
}

.source-popover-close {
  position: absolute;
  top: 6px;
  right: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-on-surface-variant);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
  padding: 0;
}

.source-popover-close:hover {
  background: var(--color-surface-container);
  color: var(--color-on-surface);
}

.source-popover-close .material-symbols-outlined {
  font-size: 14px;
}

.source-popover-title {
  font-size: 12px;
  color: var(--color-on-surface);
  margin: 0 0 4px;
  line-height: 1.4;
  word-break: break-word;
  font-weight: 500;
}

.source-popover-snippet {
  font-size: 12px;
  color: var(--color-on-surface-variant);
  margin: 0;
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
  max-height: 200px;
  overflow-y: auto;
  /* Firefox */
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--color-on-surface) 15%, transparent) transparent;
}

.source-popover-snippet::-webkit-scrollbar {
  width: 5px;
}

.source-popover-snippet::-webkit-scrollbar-track {
  background: transparent;
}

.source-popover-snippet::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--color-on-surface) 15%, transparent);
  border-radius: 3px;
}

.source-popover-extra {
  font-size: 11px;
  color: var(--color-on-surface-variant);
  margin: 6px 0 0;
  opacity: 0.8;
}

.source-popover-link {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 10px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-primary-fixed-dim);
  text-decoration: none;
  padding: 4px 6px;
  margin-top: 6px;
  border-radius: 4px;
  transition: background 0.2s ease, color 0.2s ease;
}

.source-popover-link:hover {
  color: var(--color-primary-fixed);
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
}

.source-popover-link-icon {
  font-size: 12px;
}

/* transition */
.source-popover-enter-active,
.source-popover-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.source-popover-enter-from,
.source-popover-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
