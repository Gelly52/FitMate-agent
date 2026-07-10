<template>
  <div class="wiki-page">
    <header class="wiki-header">
      <h1 class="font-inter text-display-lg text-on-surface tracking-tight">
        Wiki
      </h1>
      <p class="font-inter text-body-base text-on-surface-variant">
        浏览知识库编译后的结构化 Wiki 页面。
      </p>
    </header>

    <!-- 空间切换器 -->
    <div class="wiki-spaces">
      <div class="wiki-spaces-label">SPACES</div>
      <div class="wiki-spaces-list">
        <button
          v-for="space in spaces"
          :key="space.id"
          type="button"
          class="wiki-space-chip"
          :class="{
            'wiki-space-chip-active': space.id === currentSpaceId,
          }"
          :disabled="loadingPages"
          @click="selectSpace(space.id)"
        >
          <span class="wiki-space-scope">{{ space.scopeType }}</span>
          <span class="wiki-space-title">{{ space.title }}</span>
        </button>
        <div v-if="spaces.length === 0 && !loadingSpaces" class="wiki-spaces-empty">
          暂无可见的 Wiki 空间
        </div>
      </div>
    </div>

    <div class="wiki-columns">
      <!-- 左栏：页面列表（按 pageType 分组） -->
      <div class="wiki-col">
        <div class="wiki-section-head">
          <h2 class="font-inter text-label-sm text-on-surface uppercase tracking-widest">
            Pages
          </h2>
          <span v-if="loadingPages" class="wiki-loading-hint">加载中...</span>
          <span v-else class="wiki-count-hint">{{ pages.length }} 页</span>
        </div>

        <div v-if="pages.length > 0" class="wiki-groups">
          <section
            v-for="group in groupedPages"
            :key="group.type"
            class="wiki-group"
          >
            <div class="wiki-group-head">
              <span class="wiki-group-type">{{ group.type }}</span>
              <span class="wiki-group-count">{{ group.items.length }}</span>
            </div>
            <ul class="wiki-page-list">
              <li
                v-for="page in group.items"
                :key="page.id"
                class="wiki-page-item"
                :class="{
                  'wiki-page-item-active':
                    selectedPage && selectedPage.id === page.id,
                }"
                @click="selectPage(page)"
              >
                <span class="wiki-page-title">{{ page.title }}</span>
                <span class="wiki-page-slug">{{ page.slug }}</span>
                <button
                  type="button"
                  class="wiki-page-delete"
                  title="删除页面"
                  @click.stop="handleDeletePage(page)"
                >
                  <span class="material-symbols-outlined">delete</span>
                </button>
              </li>
            </ul>
          </section>
        </div>

        <div v-else-if="!loadingPages && currentSpaceId" class="wiki-empty">
          该空间暂无 Wiki 页面
        </div>
        <div v-else-if="!loadingPages && !currentSpaceId" class="wiki-empty">
          请选择一个 Wiki 空间
        </div>
      </div>

      <!-- 右栏：页面详情 + Markdown 渲染 -->
      <div class="wiki-col">
        <div class="wiki-section-head">
          <h2 class="font-inter text-label-sm text-on-surface uppercase tracking-widest">
            Detail
          </h2>
        </div>

        <div v-if="selectedPage" class="wiki-detail">
          <!-- 页面元信息 -->
          <div class="wiki-detail-meta">
            <h3 class="wiki-detail-title">{{ selectedPage.title }}</h3>
            <div class="wiki-detail-tags">
              <span class="wiki-tag wiki-tag-type">{{ selectedPage.pageType }}</span>
              <span class="wiki-tag wiki-tag-slug">{{ selectedPage.slug }}</span>
              <span v-if="selectedPage.charCount" class="wiki-tag wiki-tag-count">
                {{ selectedPage.charCount }} 字
              </span>
              <span v-if="selectedPage.compiledAt" class="wiki-tag wiki-tag-time">
                编译于 {{ formatTime(selectedPage.compiledAt) }}
              </span>
            </div>
          </div>

          <!-- Markdown 渲染内容 -->
          <div
            v-if="renderedContent"
            class="wiki-markdown"
            v-html="renderedContent"
            @click="handleMarkdownClick"
          ></div>
          <div v-else class="wiki-empty">该页面无内容</div>
        </div>

        <div v-else class="wiki-empty">
          从左侧选择一个页面查看详情
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { marked } from "marked";
import toast from "../../services/toast";
import wikiApi from "../../services/wikiApi";
import type { WikiSpaceItem, WikiPageItem } from "../../services/wikiApi";

// marked 全局配置：GitHub Flavored Markdown + 换行转 <br>
marked.setOptions({ gfm: true, breaks: true });

/**
 * 将 wiki wikilink 语法转换为可点击的 <a> 标签。
 * 支持两种格式：
 *   [[目标]]          —— 目标既作为查询键，也作为显示文本
 *   [[目标|显示文本]]  —— 目标作为查询键，显示文本自定义
 * 实际数据中目标多为中文页面标题（如 [[胸肌训练]]），点击时在前端 pages 中按 title 匹配。
 */
function preprocessWikilinks(md: string): string {
  if (!md) return "";
  return md.replace(/\[\[([^\]]+)\]\]/g, function (_match, inner: string) {
    var parts = inner.split("|");
    var target = (parts[0] || "").trim();
    var label = (parts[1] || parts[0] || "").trim();
    if (!target) return inner;
    // 转义双引号，防止 data 属性注入
    var safeTarget = target.replace(/"/g, "&quot;");
    var safeLabel = label.replace(/</g, "&lt;").replace(/>/g, "&gt;");
    return (
      '<a class="wiki-wikilink" data-wiki-target="' +
      safeTarget +
      '">' +
      safeLabel +
      "</a>"
    );
  });
}

// pageType 分组顺序与中文标签
const PAGE_TYPE_ORDER: Array<{ type: string; label: string }> = [
  { type: "INDEX", label: "INDEX 目录" },
  { type: "ENTITY", label: "ENTITY 实体" },
  { type: "CONCEPT", label: "CONCEPT 概念" },
  { type: "SYNTHESIS", label: "SYNTHESIS 综合" },
  { type: "SOURCE_SUMMARY", label: "SOURCE_SUMMARY 源摘要" },
  { type: "LOG", label: "LOG 日志" },
];

export default {
  name: "WikiPage",
  data() {
    return {
      spaces: [] as WikiSpaceItem[],
      currentSpaceId: null as string | null,
      pages: [] as WikiPageItem[],
      selectedPage: null as WikiPageItem | null,
      loadingSpaces: false,
      loadingPages: false,
    };
  },
  computed: {
    /**
     * 按 pageType 分组的页面列表，遵循 PAGE_TYPE_ORDER 顺序，
     * 未识别类型追加到末尾。
     */
    groupedPages(): Array<{ type: string; label: string; items: WikiPageItem[] }> {
      const groups: Array<{ type: string; label: string; items: WikiPageItem[] }> = [];
      const bucket: Record<string, WikiPageItem[]> = {};

      for (const page of this.pages) {
        const t = page.pageType || "OTHER";
        if (!bucket[t]) bucket[t] = [];
        bucket[t].push(page);
      }

      for (const def of PAGE_TYPE_ORDER) {
        const items = bucket[def.type];
        if (items && items.length > 0) {
          groups.push({ type: def.type, label: def.label, items });
        }
      }

      // 未识别类型追加到末尾
      for (const t of Object.keys(bucket)) {
        if (PAGE_TYPE_ORDER.some((d) => d.type === t)) continue;
        groups.push({ type: t, label: t, items: bucket[t] });
      }

      return groups;
    },
    /**
     * 选中页面的 Markdown 渲染结果（HTML 字符串）。
     * 渲染前先用 preprocessWikilinks 把 [[xxx]] 转为可点击链接。
     */
    renderedContent(): string {
      if (!this.selectedPage || !this.selectedPage.contentMd) return "";
      try {
        var preprocessed = preprocessWikilinks(this.selectedPage.contentMd);
        return marked.parse(preprocessed) as string;
      } catch (e) {
        return "";
      }
    },
  },
  mounted() {
    this.loadSpaces();
  },
  methods: {
    loadSpaces() {
      var me = this;
      this.loadingSpaces = true;
      wikiApi
        .getWikiSpaces()
        .then(function (res: any) {
          // WikiController 直接返回 List，拦截器已解包 response.data，res 即数组
          var list = Array.isArray(res) ? res : [];
          me.spaces = list.map(function (s: any) {
            return {
              id: s.id,
              scopeType: s.scopeType,
              ownerUserId: s.ownerUserId,
              title: s.title,
              description: s.description,
              status: s.status,
            } as WikiSpaceItem;
          });
          // 默认选中第一个空间
          if (me.spaces.length > 0) {
            me.selectSpace(me.spaces[0].id);
          }
        })
        .catch(function () {
          me.spaces = [];
        })
        .finally(function () {
          me.loadingSpaces = false;
        });
    },
    selectSpace(spaceId: string) {
      if (this.currentSpaceId === spaceId) return;
      this.currentSpaceId = spaceId;
      this.selectedPage = null;
      this.loadPages(spaceId);
    },
    loadPages(spaceId: string) {
      var me = this;
      this.loadingPages = true;
      wikiApi
        .getWikiPages(spaceId)
        .then(function (res: any) {
          var list = Array.isArray(res) ? res : [];
          me.pages = list.map(function (p: any) {
            return {
              id: p.id,
              spaceId: p.spaceId,
              pageType: p.pageType,
              title: p.title,
              slug: p.slug,
              contentMd: p.contentMd,
              charCount: p.charCount,
              status: p.status,
              compiledAt: p.compiledAt,
              updatedAt: p.updatedAt,
            } as WikiPageItem;
          });
        })
        .catch(function () {
          me.pages = [];
        })
        .finally(function () {
          me.loadingPages = false;
        });
    },
    selectPage(page: WikiPageItem) {
      this.selectedPage = page;
    },
    async handleDeletePage(page: WikiPageItem) {
      if (!page || !page.id) return;
      var confirmed = window.confirm(
        '确定要删除 Wiki 页面"' + (page.title || page.slug) +
          '"吗？该页面的向量与关键词索引将一并清除，不可恢复。'
      );
      if (!confirmed) return;
      try {
        await wikiApi.deleteWikiPage(String(page.id));
      } catch (e) {
        console.error("删除 Wiki 页面失败:", e);
        toast.error("删除 Wiki 页面失败，请稍后重试");
        return;
      }
      toast.success("Wiki 页面已删除");
      // 若删除的是当前选中页，清空详情
      if (this.selectedPage && this.selectedPage.id === page.id) {
        this.selectedPage = null;
      }
      // 刷新当前空间页面列表
      if (this.currentSpaceId) {
        this.loadPages(this.currentSpaceId);
      }
    },
    /**
     * Markdown 渲染区点击事件委托：处理 wikilink 跳转。
     * wikilink 的 data-wiki-target 存储目标页面标题，在 pages 中按 title 匹配。
     */
    handleMarkdownClick(event: MouseEvent) {
      var node: any = event.target;
      while (node && node.nodeType === 1) {
        if (node.classList && node.classList.contains("wiki-wikilink")) break;
        node = node.parentNode;
      }
      if (!node || !node.classList || !node.classList.contains("wiki-wikilink")) {
        return;
      }
      var target = node.getAttribute("data-wiki-target");
      if (!target) return;

      // 在当前空间 pages 中按 title 精确匹配
      var found: WikiPageItem | null = null;
      for (var i = 0; i < this.pages.length; i++) {
        if (this.pages[i].title === target) {
          found = this.pages[i];
          break;
        }
      }
      // 精确匹配失败时尝试 trim 匹配
      if (!found) {
        for (var j = 0; j < this.pages.length; j++) {
          if (
            this.pages[j].title &&
            this.pages[j].title.trim() === target.trim()
          ) {
            found = this.pages[j];
            break;
          }
        }
      }

      if (found) {
        this.selectPage(found);
      } else {
        toast.warning("未找到 Wiki 页面：" + target);
      }
    },
    formatTime(raw: string | undefined): string {
      if (!raw) return "--";
      var d = new Date(raw);
      if (isNaN(d.getTime())) return "--";
      var pad = function (n: number) {
        return String(n).padStart(2, "0");
      };
      return (
        d.getFullYear() +
        "-" +
        pad(d.getMonth() + 1) +
        "-" +
        pad(d.getDate()) +
        " " +
        pad(d.getHours()) +
        ":" +
        pad(d.getMinutes())
      );
    },
  },
};
</script>

<style scoped>
.wiki-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  padding: 32px 24px 48px;
  background: var(--color-background);
}

.wiki-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-bottom: 1px solid var(--color-surface-container);
  padding-bottom: 24px;
  position: relative;
}
.wiki-header::after {
  content: "";
  position: absolute;
  bottom: -1px;
  left: 0;
  width: 60px;
  height: 2px;
  background: linear-gradient(90deg, var(--color-primary), transparent);
  box-shadow: 0 0 8px color-mix(in srgb, var(--color-primary) 70%, transparent);
}

/* 空间切换器 */
.wiki-spaces {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.wiki-spaces-label {
  font-size: 9px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-on-surface-variant);
  font-family: ui-monospace, "Inter", sans-serif;
}

.wiki-spaces-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.wiki-space-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 6px;
  background: var(--color-surface-container-low);
  color: var(--color-on-surface-variant);
  cursor: pointer;
  font-family: inherit;
  font-size: 13px;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;
}

.wiki-space-chip:hover:not(:disabled) {
  border-color: var(--color-primary-fixed-dim);
  color: var(--color-on-surface);
}

.wiki-space-chip-active {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
}

.wiki-space-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.wiki-space-scope {
  font-size: 9px;
  letter-spacing: 0.08em;
  padding: 2px 6px;
  border-radius: 3px;
  background: color-mix(in srgb, var(--color-primary) 20%, transparent);
  color: var(--color-primary-fixed-dim);
  font-family: ui-monospace, monospace;
}

.wiki-space-title {
  font-weight: 500;
}

.wiki-spaces-empty {
  font-size: 13px;
  color: var(--color-on-surface-variant);
  padding: 8px 0;
}

/* 双栏布局 */
.wiki-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 32px;
  align-items: start;
}

.wiki-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.wiki-section-head {
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
  padding-left: 10px;
}
.wiki-section-head::before {
  content: "";
  position: absolute;
  left: 0;
  top: 2px;
  bottom: 2px;
  width: 2px;
  background: var(--color-primary);
  border-radius: 2px;
  box-shadow: 0 0 6px color-mix(in srgb, var(--color-primary) 70%, transparent);
}
.wiki-section-head h2 {
  font-family: ui-monospace, "Inter", sans-serif !important;
  letter-spacing: 0.14em !important;
  text-shadow: 0 0 8px color-mix(in srgb, var(--color-primary) 25%, transparent);
}

.wiki-loading-hint,
.wiki-count-hint {
  font-size: 11px;
  color: var(--color-on-surface-variant);
  font-family: ui-monospace, monospace;
}

/* 分组列表 */
.wiki-groups {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.wiki-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.wiki-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 6px 0;
  border-bottom: 1px dashed var(--color-outline-variant);
}

.wiki-group-type {
  font-size: 10px;
  letter-spacing: 0.1em;
  color: var(--color-primary-fixed-dim);
  font-family: ui-monospace, monospace;
  text-transform: uppercase;
}

.wiki-group-count {
  font-size: 10px;
  color: var(--color-on-surface-variant);
  font-family: ui-monospace, monospace;
}

.wiki-page-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.wiki-page-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.2s ease;
  position: relative;
}
.wiki-page-item::before {
  content: "";
  position: absolute;
  left: 0;
  top: 50%;
  width: 3px;
  height: 0;
  background: var(--color-primary);
  border-radius: 2px;
  box-shadow: 0 0 6px color-mix(in srgb, var(--color-primary) 70%, transparent);
  transition: height 0.2s ease, top 0.2s ease;
}
.wiki-page-item:hover::before {
  height: 60%;
  top: 20%;
}
.wiki-page-item:hover {
  background: color-mix(in srgb, var(--color-primary) 3%, transparent);
}
.wiki-page-item-active {
  background: color-mix(in srgb, var(--color-primary) 6%, transparent);
}
.wiki-page-item-active::before {
  height: 70%;
  top: 15%;
}

.wiki-page-title {
  flex: 1;
  font-size: 14px;
  color: var(--color-on-surface);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wiki-page-slug {
  font-size: 10px;
  color: var(--color-on-surface-variant);
  font-family: ui-monospace, monospace;
  flex-shrink: 0;
}

.wiki-page-delete {
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
  opacity: 0;
  transition: opacity 0.2s ease, color 0.2s ease, background 0.2s ease;
  flex-shrink: 0;
}
.wiki-page-delete .material-symbols-outlined {
  font-size: 15px;
}
.wiki-page-item:hover .wiki-page-delete {
  opacity: 1;
}
.wiki-page-delete:hover {
  color: var(--color-error);
  background: color-mix(in srgb, var(--color-error) 10%, transparent);
}

.wiki-empty {
  padding: 24px 16px;
  font-size: 13px;
  color: var(--color-on-surface-variant);
  text-align: center;
  border: 1px dashed var(--color-outline-variant);
  border-radius: 8px;
}

/* 页面详情 */
.wiki-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.wiki-detail-meta {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-surface-container);
}

.wiki-detail-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--color-on-surface);
  letter-spacing: -0.01em;
  font-family: "Manrope", system-ui, sans-serif;
}

.wiki-detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.wiki-tag {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 3px;
  font-size: 11px;
  font-family: ui-monospace, monospace;
  letter-spacing: 0.04em;
}

.wiki-tag-type {
  background: color-mix(in srgb, var(--color-primary) 20%, transparent);
  color: var(--color-primary-fixed-dim);
}

.wiki-tag-slug {
  background: var(--color-surface-container);
  color: var(--color-on-surface-variant);
}

.wiki-tag-count,
.wiki-tag-time {
  background: transparent;
  color: var(--color-on-surface-variant);
  border: 1px solid var(--color-outline-variant);
}

/* Markdown 渲染区 */
.wiki-markdown {
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-on-surface);
  word-break: break-word;
}

.wiki-markdown :deep(h1),
.wiki-markdown :deep(h2),
.wiki-markdown :deep(h3),
.wiki-markdown :deep(h4),
.wiki-markdown :deep(h5),
.wiki-markdown :deep(h6) {
  margin: 1.4em 0 0.6em;
  font-weight: 600;
  line-height: 1.3;
  color: var(--color-on-surface);
}
.wiki-markdown :deep(h1) { font-size: 1.6em; }
.wiki-markdown :deep(h2) { font-size: 1.4em; }
.wiki-markdown :deep(h3) { font-size: 1.2em; }
.wiki-markdown :deep(h4) { font-size: 1.05em; }

.wiki-markdown :deep(p) {
  margin: 0.6em 0;
}

.wiki-markdown :deep(ul),
.wiki-markdown :deep(ol) {
  margin: 0.6em 0;
  padding-left: 1.6em;
}

.wiki-markdown :deep(li) {
  margin: 0.2em 0;
}

.wiki-markdown :deep(code) {
  padding: 2px 6px;
  border-radius: 3px;
  background: var(--color-surface-container);
  color: var(--color-primary-fixed-dim);
  font-family: ui-monospace, monospace;
  font-size: 0.9em;
}

.wiki-markdown :deep(pre) {
  margin: 0.8em 0;
  padding: 12px 14px;
  border-radius: 6px;
  background: var(--color-surface-container);
  overflow-x: auto;
}

.wiki-markdown :deep(pre code) {
  padding: 0;
  background: transparent;
  color: var(--color-on-surface);
}

.wiki-markdown :deep(blockquote) {
  margin: 0.8em 0;
  padding: 0.4em 1em;
  border-left: 3px solid var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 5%, transparent);
  color: var(--color-on-surface-variant);
}

.wiki-markdown :deep(a) {
  color: var(--color-primary-fixed-dim);
  text-decoration: none;
  border-bottom: 1px dashed var(--color-primary-fixed-dim);
}
.wiki-markdown :deep(a:hover) {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
}

/* wikilink 特殊样式（[[xxx]] 生成的内部链接） */
.wiki-markdown :deep(.wiki-wikilink) {
  color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 10%, transparent);
  padding: 1px 6px;
  border-radius: 3px;
  border-bottom: none;
  cursor: pointer;
  font-weight: 500;
  transition: background 0.2s ease, color 0.2s ease;
}
.wiki-markdown :deep(.wiki-wikilink:hover) {
  background: color-mix(in srgb, var(--color-primary) 20%, transparent);
  color: var(--color-on-surface);
}

.wiki-markdown :deep(table) {
  width: 100%;
  margin: 0.8em 0;
  border-collapse: collapse;
  font-size: 0.95em;
}

.wiki-markdown :deep(th),
.wiki-markdown :deep(td) {
  padding: 6px 10px;
  border: 1px solid var(--color-outline-variant);
  text-align: left;
}

.wiki-markdown :deep(th) {
  background: var(--color-surface-container);
  font-weight: 600;
}

.wiki-markdown :deep(hr) {
  margin: 1.2em 0;
  border: none;
  border-top: 1px solid var(--color-outline-variant);
}

@media (max-width: 900px) {
  .wiki-columns {
    grid-template-columns: 1fr;
    gap: 24px;
  }
}
</style>
