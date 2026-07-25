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
  font-size: 15px;
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
  border: 2px solid var(--color-outline);
  background: var(--color-surface-container);
  color: var(--color-on-surface-variant);
  border-radius: 0;
  cursor: pointer;
  font-size: 15px;
  box-shadow: 2px 2px 0 0 #101010;
  transition: color 0.1s, background-color 0.1s, transform 0.1s;
}
.filter-btn:hover {
  color: var(--color-on-surface);
  background: var(--color-surface-container-high);
}
.filter-btn:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}
.filter-btn-active {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border-color: var(--color-outline);
}
.filter-btn-active:hover {
  background: var(--color-primary);
  color: var(--color-on-primary);
}
.memory-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.memory-item {
  border: 3px solid var(--color-outline);
  border-radius: 0;
  padding: 12px;
  background: var(--color-surface-container);
  box-shadow: 4px 4px 0 0 #101010;
}
.memory-item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 14px;
}
.memory-type {
  padding: 2px 8px;
  border-radius: 0;
  border: 2px solid var(--pixel-black);
  color: var(--pixel-white);
  font-weight: 500;
  font-size: 13px;
  letter-spacing: 0.04em;
}
.type-fact {
  background: var(--pixel-blue);
}
.type-episodic {
  background: var(--pixel-yellow);
  color: var(--pixel-black);
}
.type-snapshot {
  background: var(--pixel-gray);
}
.type-insight {
  background: var(--pixel-green);
}
.memory-time {
  color: var(--color-on-surface-variant);
  font-family: var(--font-main);
}
.delete-btn {
  margin-left: auto;
  background: var(--pixel-red);
  border: 2px solid var(--color-outline);
  color: var(--pixel-white);
  cursor: pointer;
  font-size: 13px;
  padding: 2px 6px;
  border-radius: 0;
  box-shadow: 2px 2px 0 0 #101010;
  transition: transform 0.1s;
}
.delete-btn:hover {
  transform: scale(1.05);
}
.delete-btn:active {
  transform: translate(2px, 2px);
  box-shadow: none;
}
.memory-content {
  font-size: 15px;
  line-height: 1.5;
  color: var(--color-on-surface);
}
.memory-actions {
  margin-top: 16px;
}
.danger-btn {
  padding: 6px 16px;
  background: var(--pixel-red);
  color: var(--pixel-white);
  border: 3px solid var(--color-outline);
  border-radius: 0;
  cursor: pointer;
  font-size: 15px;
  box-shadow: 4px 4px 0 0 #101010;
  transition: transform 0.1s;
}
.danger-btn:hover {
  transform: scale(1.05);
}
.danger-btn:active {
  transform: translate(2px, 2px);
  box-shadow: 2px 2px 0 0 #101010;
}
.loading,
.empty {
  padding: 40px;
  text-align: center;
  color: var(--color-on-surface-variant);
}
</style>
