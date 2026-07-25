<template>
  <div class="memory-section">
    <h2 class="font-headline-sm text-headline-sm text-on-surface">记忆管理</h2>
    <p class="section-desc">这些是 Agent 关于你的长期记忆，可查看和删除。</p>

    <div class="memory-filters">
      <button
        v-for="t in types"
        :key="t.value"
        type="button"
        :class="['filter-btn', { 'filter-btn-active': filterType === t.value }]"
        @click="setFilter(t.value)"
      >
        {{ t.label }}
      </button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="items.length === 0" class="empty">暂无记忆</div>
    <div v-else class="memory-list">
      <div v-for="item in items" :key="item.id" class="memory-item">
        <div class="memory-item-head">
          <span class="memory-type" :class="typeClass(item.memoryType)">{{ item.memoryType }}</span>
          <span class="memory-time">{{ formatTime(item.createdAt) }}</span>
          <button type="button" class="delete-btn" @click="deleteItem(item.id)">删除</button>
        </div>
        <div class="memory-content">{{ item.content }}</div>
      </div>
    </div>

    <div class="memory-actions" v-if="items.length > 0">
      <button type="button" class="danger-btn" @click="deleteAll">清空全部记忆</button>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from "vue";
import memoryApi from "../../../services/memoryApi";
import type { MemoryItem } from "../../../types/memory";

export default defineComponent({
  name: "MemorySection",
  data() {
    return {
      items: [] as MemoryItem[],
      loading: true,
      filterType: "" as string,
      types: [
        { value: "", label: "全部" },
        { value: "FACT", label: "事实" },
        { value: "EPISODIC", label: "事件" },
        { value: "SNAPSHOT", label: "快照" },
        { value: "INSIGHT", label: "洞察" },
      ],
    };
  },
  mounted() {
    this.load();
  },
  methods: {
    load() {
      var me = this;
      me.loading = true;
      memoryApi
        .list(me.filterType || undefined)
        .then(function (res) {
          var data = res && res.data;
          if (data && Array.isArray(data.items)) {
            me.items = data.items;
          } else {
            me.items = [];
          }
        })
        .catch(function () {
          me.items = [];
        })
        .finally(function () {
          me.loading = false;
        });
    },
    setFilter(type: string) {
      this.filterType = type;
      this.load();
    },
    deleteItem(id: number) {
      if (!confirm("确定删除这条记忆？")) return;
      var me = this;
      memoryApi
        .delete(id)
        .then(function () {
          me.load();
        })
        .catch(function () {
          // ignore
        });
    },
    deleteAll() {
      if (!confirm("确定清空全部记忆？此操作不可恢复。")) return;
      var me = this;
      memoryApi
        .deleteAll()
        .then(function () {
          me.load();
        })
        .catch(function () {
          // ignore
        });
    },
    typeClass(memoryType: string): string {
      return "type-" + memoryType.toLowerCase();
    },
    formatTime(t: string): string {
      if (!t) return "";
      try {
        return new Date(t).toLocaleString("zh-CN");
      } catch (e) {
        return t;
      }
    },
  },
});
</script>

<style scoped>
.memory-section h2 {
  margin-bottom: 8px;
}
.section-desc {
  color: var(--color-on-surface-variant);
  font-size: 13px;
  margin-bottom: 16px;
}
.memory-filters {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.filter-btn {
  padding: 4px 12px;
  border: 1px solid var(--color-outline-variant);
  background: transparent;
  color: var(--color-on-surface-variant);
  border-radius: 12px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s ease;
}
.filter-btn:hover {
  color: var(--color-on-surface);
  border-color: var(--color-on-surface-variant);
}
.filter-btn-active {
  background: var(--color-primary);
  color: var(--color-surface);
  border-color: var(--color-primary);
}
.memory-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.memory-item {
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  padding: 12px;
  background: var(--color-surface-container-low);
}
.memory-item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 12px;
}
.memory-type {
  padding: 2px 8px;
  border-radius: 4px;
  color: white;
  font-weight: 500;
  font-size: 11px;
  letter-spacing: 0.04em;
}
.type-fact {
  background: #5b8def;
}
.type-episodic {
  background: #f59e0b;
}
.type-snapshot {
  background: #8b5cf6;
}
.type-insight {
  background: #10b981;
}
.memory-time {
  color: var(--color-on-surface-variant);
  font-family: ui-monospace, monospace;
}
.delete-btn {
  margin-left: auto;
  background: transparent;
  border: none;
  color: #ef4444;
  cursor: pointer;
  font-size: 12px;
  padding: 2px 6px;
}
.delete-btn:hover {
  text-decoration: underline;
}
.memory-content {
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-on-surface);
}
.memory-actions {
  margin-top: 16px;
}
.danger-btn {
  padding: 6px 16px;
  background: #ef4444;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.danger-btn:hover {
  background: #dc2626;
}
.loading,
.empty {
  padding: 40px;
  text-align: center;
  color: var(--color-on-surface-variant);
}
</style>
