<template>
  <div class="profile-panel">
    <div v-if="loading" class="profile-loading">
      <span class="material-symbols-outlined spin">progress_activity</span>
      <span>正在生成用户画像...</span>
    </div>
    <div v-else-if="!profile || !profile.profileText" class="profile-empty">
      <span class="material-symbols-outlined">account_circle</span>
      <span>暂无画像</span>
      <span class="hint">开始对话或上传文档后自动生成</span>
    </div>
    <div v-else class="profile-content">
      <!-- 标签可视化区域 -->
      <div class="profile-tags" v-if="tags.length">
        <div
          v-for="tag in tags"
          :key="tag.label"
          class="profile-tag"
          :style="tagStyle(tag)"
          :title="tag.label"
        >
          {{ tag.label }}
        </div>
      </div>
      <!-- 画像文本 -->
      <div class="profile-text">
        {{ profile.profileText }}
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from "vue";
import memoryApi from "../../../services/memoryApi";
import type { ProfileResponse, ProfileTag } from "../../../types/memory";

export default defineComponent({
  name: "UserProfilePanel",
  data() {
    return {
      profile: null as ProfileResponse | null,
      tags: [] as ProfileTag[],
      loading: true,
    };
  },
  mounted() {
    this.loadProfile();
  },
  methods: {
    loadProfile() {
      var me = this;
      me.loading = true;
      memoryApi
        .getProfile()
        .then(function (res) {
          var data = res && res.data;
          me.profile = (data || null) as ProfileResponse | null;
          if (me.profile && me.profile.profileTagsJson) {
            try {
              me.tags = JSON.parse(me.profile.profileTagsJson);
            } catch (e) {
              me.tags = [];
            }
          } else {
            me.tags = [];
          }
        })
        .catch(function () {
          me.profile = null;
          me.tags = [];
        })
        .finally(function () {
          me.loading = false;
        });
    },
    tagStyle(tag: ProfileTag) {
      var size = 12 + tag.weight * 16; // 12px - 28px
      var colors: Record<string, string> = {
        identity: "#5b8def",
        goal: "#f59e0b",
        condition: "#ef4444",
        preference: "#10b981",
        status: "#8b5cf6",
      };
      return {
        fontSize: size + "px",
        backgroundColor: colors[tag.category] || "#6b7280",
        opacity: 0.6 + tag.weight * 0.4,
      };
    },
  },
});
</script>

<style scoped>
.profile-panel {
  min-height: 200px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}
.profile-loading,
.profile-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--color-on-surface-variant);
  padding: 40px 20px;
  border: 1px dashed var(--color-outline-variant);
  border-radius: 8px;
  background: var(--color-surface-container-low);
  flex: 1;
}
.profile-empty .hint {
  font-size: 12px;
  opacity: 0.7;
}
.profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 0;
}
.profile-tag {
  padding: 4px 10px;
  border-radius: 12px;
  color: white;
  font-weight: 500;
  cursor: default;
  transition: transform 0.2s ease;
}
.profile-tag:hover {
  transform: scale(1.1);
}
.profile-text {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-on-surface);
}
.spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
