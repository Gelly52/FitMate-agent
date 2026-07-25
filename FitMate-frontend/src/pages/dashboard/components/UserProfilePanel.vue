<template>
  <div class="profile-panel">
    <!-- 加载态 -->
    <div v-if="loading" class="profile-state">
      <div class="state-scan"></div>
      <span class="state-text">DECRYPTING PROFILE</span>
      <span class="state-hint">生物特征解密中…</span>
    </div>
    <!-- 空态 -->
    <div v-else-if="!profile || !profile.profileText" class="profile-state">
      <div class="state-scan state-scan-idle"></div>
      <span class="state-text">NO SIGNAL</span>
      <span class="state-hint">开始对话或上传文档后自动生成</span>
    </div>
    <!-- 正常态 -->
    <template v-else>
      <!-- 全息卡视觉锚点 -->
      <div
        class="holo-wrap"
        @mousemove="onHoloMove"
        @mouseleave="resetHolo"
      >
        <div
          class="holo-card"
          :style="{ transform: holoTransform }"
        >
          <!-- 全息彩虹层 -->
          <div class="holo-iridescent"></div>
          <!-- 扫描线 -->
          <div class="holo-scan"></div>
          <!-- 反光斜切 -->
          <div class="holo-sheen"></div>
          <!-- 网格纹理 -->
          <div class="holo-grid"></div>

          <!-- 内容层 -->
          <div class="holo-content">
            <div class="holo-top">
              <span class="holo-eyebrow">BIOMETRIC PROFILE</span>
              <span class="holo-id">ID·{{ idSerial }}</span>
            </div>

            <div class="holo-headline">
              <span
                v-for="(ch, i) in headlineChars"
                :key="i"
                class="holo-char"
                :style="{ animationDelay: 0.3 + i * 0.03 + 's' }"
              >{{ ch === ' ' ? '\u00A0' : ch }}</span>
            </div>

            <div class="holo-meta">
              <div class="holo-stamp">
                <span class="stamp-label">COMPLETENESS</span>
                <span class="stamp-value">{{ completeness }}%</span>
                <span class="stamp-ring"></span>
              </div>
              <div class="holo-auth">
                <span class="auth-dot"></span>
                <span class="auth-text">AUTH·LIVE</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 标签全息印章区 -->
      <div class="stamp-list">
        <div
          v-for="(cat, idx) in categoryOrder"
          :key="cat"
          class="stamp-row"
          :style="{ animationDelay: 0.5 + idx * 0.08 + 's' }"
        >
          <div class="stamp-idx">{{ formatIdx(idx) }}</div>
          <div class="stamp-body">
            <div class="stamp-head">
              <span class="stamp-cat">{{ catLabel(cat) }}</span>
              <span class="stamp-count">{{ (grouped[cat] || []).length }} TAGS</span>
            </div>
            <div class="stamp-tags">
              <template v-if="grouped[cat] && grouped[cat].length">
                <span
                  v-for="(tag, ti) in grouped[cat]"
                  :key="ti"
                  class="tag-chip"
                >{{ tag }}</span>
              </template>
              <span v-else class="tag-empty">—</span>
            </div>
            <div class="stamp-bar">
              <div class="stamp-bar-fill" :style="{ width: catWeight(cat) + '%' }"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- FIELD LOG 摘要 -->
      <div class="field-log">
        <div class="log-scan"></div>
        <div class="log-head">
          <span class="log-label">FIELD LOG</span>
          <span class="log-divider"></span>
          <span class="log-time">{{ relativeTime || '—' }}</span>
        </div>
        <p class="log-text">{{ profile.profileText }}</p>
      </div>

      <!-- 终端 footer -->
      <div class="holo-footer">
        <span class="footer-signal">
          <span class="signal-dot"></span>ISSUED·v{{ profile.memoryVersion || '—' }}
        </span>
        <span class="footer-serial">CYBER·PASSPORT/{{ idSerial }}</span>
      </div>
    </template>
  </div>
</template>

<script lang="ts">
import { defineComponent } from "vue";
import memoryApi from "../../../services/memoryApi";
import type { ProfileResponse, ProfileTag } from "../../../types/memory";

type Category = ProfileTag["category"];

const CATEGORY_ORDER: Category[] = [
  "identity",
  "goal",
  "preference",
  "condition",
  "status",
];

const CATEGORY_LABELS: Record<Category, string> = {
  identity: "IDENTITY",
  goal: "GOAL",
  preference: "PREFERENCE",
  condition: "CONDITION",
  status: "STATUS",
};

function formatRelativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  if (diff < 0) return "just now";
  const min = Math.floor(diff / 60000);
  if (min < 1) return "just now";
  if (min < 60) return min + "m ago";
  const hr = Math.floor(min / 60);
  if (hr < 24) return hr + "h ago";
  return Math.floor(hr / 24) + "d ago";
}

export default defineComponent({
  name: "UserProfilePanel",
  data() {
    return {
      profile: null as ProfileResponse | null,
      tags: [] as ProfileTag[],
      loading: true,
      categoryOrder: CATEGORY_ORDER,
      holoRx: 0,
      holoRy: 0,
    };
  },
  computed: {
    grouped(): Record<Category, string[]> {
      const g: Record<Category, string[]> = {
        identity: [],
        goal: [],
        preference: [],
        condition: [],
        status: [],
      };
      for (const t of this.tags) {
        if (g[t.category]) g[t.category].push(t.label);
      }
      return g;
    },
    headline(): string {
      const identity = this.grouped.identity[0] || "";
      const goal = this.grouped.goal[0] || "";
      const parts = [identity, goal].filter(Boolean);
      return parts.length ? parts.join(" · ") : "你的画像";
    },
    headlineChars(): string[] {
      return this.headline.split("");
    },
    completeness(): number {
      const filled = CATEGORY_ORDER.filter(
        (cat) => this.grouped[cat] && this.grouped[cat].length > 0
      ).length;
      return Math.round((filled / CATEGORY_ORDER.length) * 100);
    },
    relativeTime(): string {
      if (!this.profile || !this.profile.generatedAt) return "";
      return formatRelativeTime(this.profile.generatedAt);
    },
    idSerial(): string {
      const v = (this.profile && this.profile.memoryVersion) || 1;
      return String(v).padStart(4, "0") + "·X";
    },
    holoTransform(): string {
      return `perspective(900px) rotateX(${this.holoRx}deg) rotateY(${this.holoRy}deg)`;
    },
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
    catLabel(cat: string): string {
      return (CATEGORY_LABELS as Record<string, string>)[cat] || cat;
    },
    formatIdx(idx: number): string {
      return String(idx + 1).padStart(2, "0");
    },
    catWeight(cat: Category): number {
      const items = (this.tags || []).filter((t) => t.category === cat);
      if (!items.length) return 0;
      const sum = items.reduce((s, t) => s + (t.weight || 0), 0);
      const avg = sum / items.length;
      return Math.max(8, Math.min(100, Math.round(avg * 100)));
    },
    onHoloMove(e: MouseEvent) {
      const el = e.currentTarget as HTMLElement;
      const rect = el.getBoundingClientRect();
      const x = (e.clientX - rect.left) / rect.width - 0.5;
      const y = (e.clientY - rect.top) / rect.height - 0.5;
      this.holoRy = x * 10;
      this.holoRx = -y * 10;
    },
    resetHolo() {
      this.holoRx = 0;
      this.holoRy = 0;
    },
  },
});
</script>

<style scoped>
/* ============================================
   Pixel ID Card (retro 8-bit passport)
   ============================================ */

.profile-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
  position: relative;
}

/* ============================================
   加载 / 空态
   ============================================ */
.profile-state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 40px 4px;
  color: var(--color-on-surface-variant);
}
.state-scan {
  width: 64px;
  height: 6px;
  background: var(--color-primary);
  border: 2px solid var(--color-outline);
  animation: scan-pulse 1.4s steps(3) infinite;
  margin-bottom: 8px;
}
.state-scan-idle {
  animation: none;
  opacity: 0.3;
  background: var(--color-outline-variant);
}
@keyframes scan-pulse {
  0%, 100% { opacity: 0.4; transform: scaleX(0.6); }
  50% { opacity: 1; transform: scaleX(1); }
}
.state-text {
  font-size: 15px;
  color: var(--color-on-surface);
  letter-spacing: 0.18em;
}
.state-hint {
  font-size: 14px;
  color: var(--color-on-surface-variant);
  opacity: 0.7;
  letter-spacing: 0.04em;
}

/* ============================================
   像素身份卡视觉锚点
   ============================================ */
.holo-wrap {
  position: relative;
  height: 160px;
  margin-bottom: 18px;
  perspective: 900px;
}

.holo-card {
  position: absolute;
  inset: 0;
  overflow: hidden;
  transform-style: preserve-3d;
  transition: transform 0.1s steps(2);
  background: var(--pixel-black);
  border: 3px solid var(--color-outline);
  box-shadow: 4px 4px 0 0 #101010;
}

/* 全息层收敛为纯色隐藏（保留 DOM 结构） */
.holo-iridescent {
  display: none;
}

/* 网格纹理：flat 像素网格 */
.holo-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(248, 248, 248, 0.06) 2px, transparent 2px),
    linear-gradient(90deg, rgba(248, 248, 248, 0.06) 2px, transparent 2px);
  background-size: 16px 16px;
  pointer-events: none;
}

/* 扫描线：flat 色块，步进移动 */
.holo-scan {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 8px;
  background: rgba(45, 125, 70, 0.4);
  animation: holo-scan-move 3.2s steps(12) infinite;
  pointer-events: none;
}
@keyframes holo-scan-move {
  0% { transform: translateY(-8px); }
  100% { transform: translateY(168px); }
}

/* 反光层隐藏（保留 DOM 结构） */
.holo-sheen {
  display: none;
}

/* 内容层 */
.holo-content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 100%;
  padding: 12px 14px;
}

.holo-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.holo-eyebrow {
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--pixel-white);
}
.holo-id {
  font-size: 12px;
  letter-spacing: 0.16em;
  color: rgba(248, 248, 248, 0.7);
}

.holo-headline {
  display: flex;
  flex-wrap: wrap;
  font-size: 20px;
  line-height: 1.25;
  color: var(--pixel-white);
}
.holo-char {
  display: inline-block;
  opacity: 0;
  animation: holo-char-in 0.15s steps(2) forwards;
}
@keyframes holo-char-in {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.holo-meta {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}
.holo-stamp {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 10px;
  border: 2px solid var(--pixel-white);
  background: var(--pixel-blue);
}
.stamp-label {
  font-size: 11px;
  letter-spacing: 0.18em;
  color: rgba(248, 248, 248, 0.7);
}
.stamp-value {
  font-size: 20px;
  color: var(--pixel-white);
}
.stamp-ring {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 8px;
  height: 8px;
  background: var(--pixel-yellow);
  border: 2px solid var(--pixel-black);
}

.holo-auth {
  display: flex;
  align-items: center;
  gap: 5px;
}
.auth-dot {
  width: 8px;
  height: 8px;
  background: var(--pixel-green);
  border: 1px solid var(--pixel-black);
  animation: dot-pulse 2s steps(2) infinite;
}
@keyframes dot-pulse {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}
.auth-text {
  font-size: 12px;
  letter-spacing: 0.18em;
  color: rgba(248, 248, 248, 0.8);
}

/* ============================================
   标签印章区
   ============================================ */
.stamp-list {
  display: flex;
  flex-direction: column;
  padding: 0 2px;
}

.stamp-row {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 2px solid var(--color-outline-variant);
  opacity: 0;
  transform: translateY(6px);
  animation: row-in 0.15s steps(3) forwards;
  transition: background 0.1s;
}
@keyframes row-in {
  to { opacity: 1; transform: translateY(0); }
}
.stamp-row:hover {
  background: var(--color-surface-container-low);
}

.stamp-idx {
  font-size: 12px;
  letter-spacing: 0.08em;
  color: var(--color-primary);
  padding-top: 2px;
}

.stamp-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.stamp-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.stamp-cat {
  font-size: 13px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-on-surface);
}
.stamp-count {
  font-size: 12px;
  letter-spacing: 0.1em;
  color: var(--color-on-surface-variant);
  opacity: 0.7;
}

.stamp-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 22px;
  align-items: center;
}
.tag-chip {
  display: inline-block;
  padding: 2px 8px;
  font-size: 14px;
  color: var(--color-on-surface);
  background: var(--color-surface-container);
  border: 2px solid var(--color-outline);
  box-shadow: 2px 2px 0 0 #101010;
}
.tag-empty {
  font-size: 14px;
  color: var(--color-on-surface-variant);
  opacity: 0.5;
}

/* weight 横向条：chunky flat 像素条 */
.stamp-bar {
  height: 10px;
  background: var(--color-surface-container);
  border: 2px solid var(--color-outline);
  overflow: hidden;
  position: relative;
}
.stamp-bar-fill {
  height: 100%;
  background: var(--color-primary);
  transition: width 0.15s steps(4);
}

/* ============================================
   FIELD LOG 摘要
   ============================================ */
.field-log {
  position: relative;
  margin-top: 18px;
  padding: 12px 12px 12px 18px;
  background: var(--color-surface-container-low);
  border: 3px solid var(--color-outline);
  box-shadow: 3px 3px 0 0 #101010;
  overflow: hidden;
  opacity: 0;
  animation: row-in 0.15s steps(3) 0.1s forwards;
}

/* 顶部扫描线：flat 色块 */
.log-scan {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--color-primary);
}

/* 左侧色条：flat 像素条 */
.field-log::before {
  content: "";
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6px;
  background: var(--color-primary);
}

.log-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.log-label {
  font-size: 12px;
  letter-spacing: 0.22em;
  color: var(--color-primary);
}
.log-divider {
  flex: 1;
  height: 2px;
  background: var(--color-outline-variant);
}
.log-time {
  font-size: 12px;
  letter-spacing: 0.1em;
  color: var(--color-on-surface-variant);
}

.log-text {
  font-size: 14px;
  line-height: 1.55;
  color: var(--color-on-surface);
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ============================================
   终端 footer
   ============================================ */
.holo-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 2px solid var(--color-outline-variant);
  font-size: 12px;
  color: var(--color-on-surface-variant);
  letter-spacing: 0.12em;
  opacity: 0.7;
}
.footer-signal {
  display: flex;
  align-items: center;
  gap: 6px;
}
.signal-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  background: var(--pixel-green);
  animation: dot-pulse 2.4s steps(2) infinite;
}
.footer-serial {
  opacity: 0.6;
}

/* ============================================
   移动端适配（< 900px）
   ============================================ */
@media (max-width: 900px) {
  .holo-wrap {
    height: 140px;
  }
  .stamp-row {
    grid-template-columns: 24px 1fr;
    gap: 8px;
    padding: 7px 0;
  }
  .holo-headline {
    font-size: 17px;
  }
}

/* ============================================
   reduced-motion 适配
   ============================================ */
@media (prefers-reduced-motion: reduce) {
  .holo-scan,
  .auth-dot,
  .signal-dot,
  .state-scan {
    animation: none;
  }
  .holo-char,
  .stamp-row,
  .field-log {
    animation: none;
    opacity: 1;
    transform: none;
  }
}
</style>
