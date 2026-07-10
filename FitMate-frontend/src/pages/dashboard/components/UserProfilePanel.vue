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
   Holographic Cyber-Passport
   美学定位：未来感全息身份证 / 赛博护照
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
  height: 2px;
  background: var(--color-primary);
  box-shadow: 0 0 8px var(--color-primary);
  animation: scan-pulse 1.4s ease-in-out infinite;
  margin-bottom: 8px;
}
.state-scan-idle {
  animation: none;
  opacity: 0.3;
  background: var(--color-outline-variant);
  box-shadow: none;
}
@keyframes scan-pulse {
  0%, 100% { opacity: 0.4; transform: scaleX(0.6); }
  50% { opacity: 1; transform: scaleX(1); }
}
.state-text {
  font-size: 12px;
  color: var(--color-on-surface);
  font-weight: 600;
  letter-spacing: 0.18em;
  font-family: ui-monospace, monospace;
}
.state-hint {
  font-size: 11px;
  color: var(--color-on-surface-variant);
  opacity: 0.7;
  font-family: ui-monospace, monospace;
  letter-spacing: 0.04em;
}

/* ============================================
   全息卡视觉锚点
   ============================================ */
.holo-wrap {
  position: relative;
  height: 160px;
  border-radius: 12px;
  margin-bottom: 18px;
  perspective: 900px;
  animation: holo-in 0.7s cubic-bezier(0.16, 1, 0.3, 1) backwards;
}
@keyframes holo-in {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.holo-card {
  position: absolute;
  inset: 0;
  border-radius: 12px;
  overflow: hidden;
  transform-style: preserve-3d;
  transition: transform 0.18s cubic-bezier(0.16, 1, 0.3, 1);
  background: linear-gradient(135deg, rgba(10, 8, 20, 0.95), rgba(18, 12, 32, 0.92));
  border: 1px solid color-mix(in srgb, var(--color-primary) 35%, rgba(255,255,255,0.1));
  box-shadow:
    0 12px 32px rgba(0, 0, 0, 0.55),
    0 3px 10px color-mix(in srgb, var(--color-primary) 18%, transparent),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

/* 全息彩虹层 - conic-gradient 旋转 */
.holo-iridescent {
  position: absolute;
  inset: -30%;
  background: conic-gradient(
    from 0deg,
    rgba(255, 0, 128, 0.35) 0%,
    rgba(255, 200, 0, 0.32) 14%,
    rgba(0, 255, 180, 0.32) 28%,
    rgba(0, 180, 255, 0.38) 42%,
    rgba(120, 80, 255, 0.35) 56%,
    rgba(255, 0, 200, 0.32) 70%,
    rgba(0, 255, 220, 0.3) 84%,
    rgba(255, 0, 128, 0.35) 100%
  );
  filter: blur(28px);
  opacity: 0.55;
  mix-blend-mode: screen;
  animation: holo-spin 12s linear infinite;
  pointer-events: none;
}
@keyframes holo-spin {
  to { transform: rotate(360deg); }
}

/* 主题色叠加，让全息与项目配色融合 */
.holo-card::before {
  content: "";
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 30% 20%, color-mix(in srgb, var(--color-primary) 28%, transparent) 0%, transparent 60%),
    radial-gradient(ellipse at 70% 80%, color-mix(in srgb, var(--color-primary) 18%, transparent) 0%, transparent 55%);
  mix-blend-mode: screen;
  opacity: 0.7;
  pointer-events: none;
}

/* 网格纹理 */
.holo-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.05) 1px, transparent 1px);
  background-size: 28px 28px;
  mask-image: radial-gradient(ellipse at center, black 40%, transparent 90%);
  -webkit-mask-image: radial-gradient(ellipse at center, black 40%, transparent 90%);
  pointer-events: none;
}

/* 扫描线 - 垂直 sweep */
.holo-scan {
  position: absolute;
  left: 0; right: 0;
  top: 0;
  height: 60%;
  background: linear-gradient(
    180deg,
    transparent 0%,
    color-mix(in srgb, var(--color-primary) 14%, transparent) 45%,
    color-mix(in srgb, var(--color-primary) 30%, transparent) 50%,
    color-mix(in srgb, var(--color-primary) 14%, transparent) 55%,
    transparent 100%
  );
  animation: holo-scan-move 3.2s cubic-bezier(0.45, 0, 0.55, 1) infinite;
  mix-blend-mode: screen;
  pointer-events: none;
}
@keyframes holo-scan-move {
  0% { transform: translateY(-100%); opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { transform: translateY(220%); opacity: 0; }
}

/* 反光斜切 sheen */
.holo-sheen {
  position: absolute;
  top: -50%; bottom: -50%;
  left: 30%;
  width: 12%;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(255, 255, 255, 0.18) 45%,
    rgba(255, 255, 255, 0.3) 50%,
    rgba(255, 255, 255, 0.18) 55%,
    transparent 100%
  );
  transform: rotate(18deg);
  animation: holo-sheen-move 6s ease-in-out infinite;
  mix-blend-mode: screen;
  pointer-events: none;
}
@keyframes holo-sheen-move {
  0%, 100% { transform: translateX(-200%) rotate(18deg); opacity: 0; }
  50% { transform: translateX(700%) rotate(18deg); opacity: 1; }
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
  font-size: 9px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.9);
  font-family: ui-monospace, monospace;
  text-shadow:
    0 0 4px rgba(255, 0, 128, 0.6),
    0 0 8px rgba(0, 200, 255, 0.5);
}
.holo-id {
  font-size: 9px;
  letter-spacing: 0.16em;
  color: rgba(255, 255, 255, 0.7);
  font-family: ui-monospace, monospace;
  text-shadow: 0 0 6px color-mix(in srgb, var(--color-primary) 80%, transparent);
}

.holo-headline {
  display: flex;
  flex-wrap: wrap;
  font-family: "Manrope", sans-serif;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: -0.01em;
  color: white;
  text-shadow:
    0.5px 0 0 rgba(255, 0, 128, 0.55),
    -0.5px 0 0 rgba(0, 220, 255, 0.55),
    0 1px 6px rgba(0, 0, 0, 0.6);
}
.holo-char {
  display: inline-block;
  opacity: 0;
  animation: holo-char-in 0.5s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}
@keyframes holo-char-in {
  from { opacity: 0; transform: translateY(4px); filter: blur(4px); }
  to { opacity: 1; transform: translateY(0); filter: blur(0); }
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
  padding: 6px 10px 6px 12px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.25);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}
.stamp-label {
  font-size: 8px;
  letter-spacing: 0.18em;
  color: rgba(255, 255, 255, 0.7);
  font-family: ui-monospace, monospace;
}
.stamp-value {
  font-size: 18px;
  font-weight: 700;
  color: white;
  font-family: "Manrope", sans-serif;
  letter-spacing: -0.01em;
  text-shadow:
    0.5px 0 0 rgba(255, 0, 128, 0.5),
    -0.5px 0 0 rgba(0, 220, 255, 0.5);
}
.stamp-ring {
  position: absolute;
  top: -2px; right: -2px;
  width: 8px; height: 8px;
  border-radius: 50%;
  border: 1px solid var(--color-primary);
  box-shadow: 0 0 6px var(--color-primary);
}

.holo-auth {
  display: flex;
  align-items: center;
  gap: 5px;
}
.auth-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: #7ee787;
  box-shadow: 0 0 8px #7ee787;
  animation: dot-pulse 2s ease-in-out infinite;
}
@keyframes dot-pulse {
  0%, 100% { opacity: 0.6; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.25); }
}
.auth-text {
  font-size: 9px;
  letter-spacing: 0.18em;
  color: rgba(255, 255, 255, 0.8);
  font-family: ui-monospace, monospace;
}

/* ============================================
   标签全息印章区
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
  border-bottom: 1px solid var(--color-outline-variant);
  opacity: 0;
  transform: translateY(6px);
  animation: row-in 0.5s cubic-bezier(0.16, 1, 0.3, 1) forwards;
  transition: transform 0.2s ease;
}
@keyframes row-in {
  to { opacity: 1; transform: translateY(0); }
}
.stamp-row:hover {
  transform: translateY(-2px);
}

.stamp-idx {
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--color-primary);
  font-family: ui-monospace, monospace;
  padding-top: 2px;
  text-shadow: 0 0 6px color-mix(in srgb, var(--color-primary) 60%, transparent);
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
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-on-surface);
  font-family: ui-monospace, monospace;
}
.stamp-count {
  font-size: 9px;
  letter-spacing: 0.1em;
  color: var(--color-on-surface-variant);
  font-family: ui-monospace, monospace;
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
  padding: 3px 8px;
  border-radius: 3px;
  font-size: 11px;
  color: var(--color-on-surface);
  background: color-mix(in srgb, var(--color-primary) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--color-primary) 28%, transparent);
  font-family: "Inter", sans-serif;
  font-weight: 500;
  letter-spacing: 0.01em;
  position: relative;
  overflow: hidden;
}
.tag-chip::before {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    transparent 0%,
    color-mix(in srgb, var(--color-primary) 18%, transparent) 50%,
    transparent 100%
  );
  opacity: 0.6;
  pointer-events: none;
}
.tag-empty {
  font-size: 12px;
  color: var(--color-on-surface-variant);
  opacity: 0.5;
  font-family: ui-monospace, monospace;
}

/* weight 横向条 - 全息填充 */
.stamp-bar {
  height: 2px;
  background: var(--color-surface-container);
  border-radius: 2px;
  overflow: hidden;
  position: relative;
}
.stamp-bar-fill {
  height: 100%;
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--color-primary) 60%, transparent) 0%,
    var(--color-primary) 50%,
    color-mix(in srgb, var(--color-primary) 80%, white 20%) 100%
  );
  box-shadow: 0 0 6px color-mix(in srgb, var(--color-primary) 70%, transparent);
  border-radius: 2px;
  transition: width 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

/* ============================================
   FIELD LOG 摘要
   ============================================ */
.field-log {
  position: relative;
  margin-top: 18px;
  padding: 12px 12px 12px 16px;
  border-radius: 8px;
  background: var(--color-surface-container-low);
  border: 1px solid var(--color-outline-variant);
  overflow: hidden;
  opacity: 0;
  animation: row-in 0.6s ease-out 1s forwards;
  box-shadow:
    0 3px 12px rgba(0, 0, 0, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

/* 顶部一次扫描线 */
.log-scan {
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    var(--color-primary) 50%,
    transparent 100%
  );
  box-shadow: 0 0 8px var(--color-primary);
  animation: log-scan-once 1.2s ease-out 1.1s backwards;
}
@keyframes log-scan-once {
  0% { transform: translateX(-100%); opacity: 0; }
  20% { opacity: 1; }
  100% { transform: translateX(100%); opacity: 0; }
}

/* 左侧全息色条 */
.field-log::before {
  content: "";
  position: absolute;
  left: 0; top: 14px; bottom: 14px;
  width: 3px;
  background: linear-gradient(
    180deg,
    #ff0080 0%,
    var(--color-primary) 50%,
    #00d4ff 100%
  );
  border-radius: 3px;
  box-shadow:
    0 0 8px color-mix(in srgb, var(--color-primary) 80%, transparent),
    0 0 12px rgba(255, 0, 128, 0.4);
}

.log-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.log-label {
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.22em;
  color: var(--color-primary);
  font-family: ui-monospace, monospace;
  text-shadow: 0 0 4px color-mix(in srgb, var(--color-primary) 60%, transparent);
}
.log-divider {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, var(--color-outline-variant), transparent);
}
.log-time {
  font-size: 9px;
  letter-spacing: 0.1em;
  color: var(--color-on-surface-variant);
  font-family: ui-monospace, monospace;
}

.log-text {
  font-size: 11px;
  line-height: 1.55;
  color: var(--color-on-surface);
  letter-spacing: 0.005em;
  margin: 0;
  font-family: "Inter", sans-serif;
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
  border-top: 1px solid var(--color-outline-variant);
  font-size: 9px;
  font-family: ui-monospace, monospace;
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
  width: 5px; height: 5px;
  border-radius: 50%;
  background: #7ee787;
  box-shadow: 0 0 6px #7ee787;
  animation: dot-pulse 2.4s ease-in-out infinite;
}
.footer-serial {
  opacity: 0.6;
}

/* ============================================
   LIGHT THEME 适配
   浅色页面背景上收敛全息强度，避免过度刺眼
   卡片本身保持深色赛博朋克质感（holo-card深色渐变不变）
   ============================================ */
[data-theme="light"] .holo-iridescent {
  opacity: 0.35;
  filter: blur(36px);
}
[data-theme="light"] .holo-card::before {
  opacity: 0.5;
}
[data-theme="light"] .holo-grid {
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
}
[data-theme="light"] .field-log {
  background: var(--color-surface-container);
  border-color: color-mix(in srgb, var(--color-primary) 20%, transparent);
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
    font-size: 15px;
  }
}

/* ============================================
   reduced-motion 适配
   ============================================ */
@media (prefers-reduced-motion: reduce) {
  .holo-iridescent,
  .holo-scan,
  .holo-sheen,
  .auth-dot,
  .signal-dot,
  .state-scan {
    animation: none;
  }
  .holo-char {
    animation: none;
    opacity: 1;
  }
}
</style>
