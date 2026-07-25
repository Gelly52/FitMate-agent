<!-- FitMate-frontend/src/pages/settings/components/ProfileSection.vue -->
<template>
  <div>
    <div class="settings-card">
      <!-- 头像 + 昵称 + 邮箱 -->
      <div class="flex items-center gap-md mb-lg">
        <div class="settings-avatar">
          <span v-if="avatarLetter">{{ avatarLetter }}</span>
          <span v-else class="material-symbols-outlined" style="font-size:28px;">person</span>
        </div>
        <div class="min-w-0">
          <div class="text-on-surface font-inter text-body-base font-semibold truncate">
            {{ displayNickname }}
          </div>
          <div class="text-on-surface-variant font-inter text-label-xs truncate">
            {{ profile && profile.email ? profile.email : "—" }}
          </div>
        </div>
      </div>

      <!-- 字段网格 -->
      <div class="settings-field-grid">
        <!-- 昵称（可编辑） -->
        <div class="settings-field">
          <div class="settings-field-label">昵称</div>
          <div v-if="!editing.nickname" class="settings-field-value">
            <span>{{ displayNickname }}</span>
            <button class="settings-edit-btn" @click="startEdit('nickname')" title="编辑">
              <span class="material-symbols-outlined" style="font-size:14px;">edit</span>
            </button>
          </div>
          <div v-else class="settings-field-edit">
            <input v-model="forms.nickname" class="settings-input" maxlength="100" />
            <button class="settings-save-btn" @click="saveField('nickname')" :disabled="saving">保存</button>
            <button class="settings-cancel-btn" @click="cancelEdit('nickname')">取消</button>
          </div>
        </div>

        <!-- 手机号（可编辑） -->
        <div class="settings-field">
          <div class="settings-field-label">手机号</div>
          <div v-if="!editing.phone" class="settings-field-value">
            <span>{{ maskedPhone }}</span>
            <button class="settings-edit-btn" @click="startEdit('phone')" title="编辑">
              <span class="material-symbols-outlined" style="font-size:14px;">edit</span>
            </button>
          </div>
          <div v-else class="settings-field-edit">
            <input v-model="forms.phone" class="settings-input" maxlength="11" placeholder="13800138000" />
            <button class="settings-save-btn" @click="saveField('phone')" :disabled="saving">保存</button>
            <button class="settings-cancel-btn" @click="cancelEdit('phone')">取消</button>
          </div>
        </div>

        <!-- 邮箱（只读） -->
        <div class="settings-field">
          <div class="settings-field-label">邮箱（登录账号）</div>
          <div class="settings-field-value settings-field-readonly">
            <span>{{ profile && profile.email ? profile.email : "—" }}</span>
            <span class="material-symbols-outlined settings-lock-icon" title="只读">lock</span>
          </div>
        </div>

        <!-- 用户名（只读） -->
        <div class="settings-field">
          <div class="settings-field-label">用户名</div>
          <div class="settings-field-value settings-field-readonly">
            <span>{{ profile && profile.username ? profile.username : "—" }}</span>
            <span class="material-symbols-outlined settings-lock-icon" title="只读">lock</span>
          </div>
        </div>

        <!-- 注册时间（只读） -->
        <div class="settings-field">
          <div class="settings-field-label">注册时间</div>
          <div class="settings-field-value settings-field-readonly">
            <span>{{ profile && profile.createdAt ? formatDate(profile.createdAt) : "—" }}</span>
            <span class="material-symbols-outlined settings-lock-icon" title="只读">lock</span>
          </div>
        </div>

        <!-- 最近登录（只读） -->
        <div class="settings-field">
          <div class="settings-field-label">最近登录</div>
          <div class="settings-field-value settings-field-readonly">
            <span>{{ profile && profile.lastLoginAt ? formatDate(profile.lastLoginAt) : "—" }}</span>
            <span class="material-symbols-outlined settings-lock-icon" title="只读">lock</span>
          </div>
        </div>
      </div>
    </div>

    <!-- DeepSeek 账户余额 -->
    <div class="settings-card balance-card">
      <div class="balance-header">
        <div class="balance-title">
          <span class="material-symbols-outlined balance-title-icon">account_balance_wallet</span>
          <div>
            <div class="balance-title-text">DeepSeek 账户余额</div>
            <div class="balance-title-sub">基于当前 LLM 配置查询</div>
          </div>
        </div>
        <button
          class="balance-refresh-btn"
          :disabled="balanceLoading"
          @click="loadBalance"
          title="刷新"
        >
          <span class="material-symbols-outlined" :class="{ 'spin': balanceLoading }">refresh</span>
        </button>
      </div>

      <!-- 加载中 -->
      <div v-if="balanceLoading && !balance" class="balance-state">
        <span class="material-symbols-outlined spin">progress_activity</span>
        <span>查询中…</span>
      </div>

      <!-- 错误态 -->
      <div v-else-if="balanceError" class="balance-state balance-state-error">
        <span class="material-symbols-outlined">error_outline</span>
        <span>{{ balanceError }}</span>
      </div>

      <!-- 余额展示 -->
      <div v-else-if="balance" class="balance-body">
        <div v-if="balance.isAvailable === false" class="balance-warn">
          <span class="material-symbols-outlined">warning</span>
          <span>当前账户余额不足，无法调用 API</span>
        </div>
        <div
          v-for="info in balance.balanceInfos"
          :key="info.currency"
          class="balance-item"
        >
          <div class="balance-item-main">
            <span class="balance-total">{{ info.totalBalance || "0.00" }}</span>
            <span class="balance-currency">{{ info.currency || "CNY" }}</span>
          </div>
          <div class="balance-item-sub">
            <span>赠金 {{ info.grantedBalance || "0.00" }}</span>
            <span class="balance-dot">·</span>
            <span>充值 {{ info.toppedUpBalance || "0.00" }}</span>
          </div>
        </div>
        <div v-if="balanceUpdatedAt" class="balance-updated">
          更新于 {{ balanceUpdatedAt }}
        </div>
      </div>

      <!-- 空态 -->
      <div v-else class="balance-state">
        <span class="material-symbols-outlined">wallet</span>
        <span>点击右上角刷新按钮查询余额</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import doctorApi from "../../../services/doctorApi";
import toast from "../../../services/toast";
import { updateUserState } from "../../../services/http";
import type { LlmBalanceResult, UserProfile } from "../../../types/settings";

export default {
  name: "ProfileSection",
  props: {
    profile: {
      type: Object as () => UserProfile | null,
      default: null,
    },
  },
  emits: ["updated"],
  data() {
    return {
      editing: { nickname: false, phone: false },
      forms: { nickname: "", phone: "" },
      saving: false,
      balance: null as LlmBalanceResult | null,
      balanceLoading: false,
      balanceError: "" as string,
      balanceUpdatedAt: "" as string,
    };
  },
  mounted() {
    this.loadBalance();
  },
  computed: {
    displayNickname(): string {
      const p = this.profile;
      if (!p) return "—";
      return p.nickname || p.username || "—";
    },
    avatarLetter(): string {
      const name = this.displayNickname;
      if (!name || name === "—") return "";
      return name.charAt(0).toUpperCase();
    },
    maskedPhone(): string {
      const phone = this.profile && this.profile.phone;
      if (!phone) return "—";
      if (phone.length < 7) return phone;
      return phone.substring(0, 3) + "****" + phone.substring(phone.length - 4);
    },
  },
  methods: {
    async loadBalance() {
      if (this.balanceLoading) return;
      this.balanceLoading = true;
      this.balanceError = "";
      try {
        const res = await doctorApi.getLlmBalance();
        if (res && res.status === 200 && res.data) {
          this.balance = res.data as LlmBalanceResult;
          this.balanceUpdatedAt = this.formatNow();
        } else {
          this.balanceError = (res && res.msg) || "查询余额失败";
        }
      } catch (e: any) {
        this.balanceError = (e && e.message) || "查询余额失败，请稍后重试";
      } finally {
        this.balanceLoading = false;
      }
    },
    formatNow(): string {
      try {
        const d = new Date();
        const pad = (n: number) => String(n).padStart(2, "0");
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
      } catch {
        return "";
      }
    },
    formatDate(iso: string): string {
      if (!iso) return "—";
      try {
        return new String(iso).replace("T", " ").substring(0, 19);
      } catch {
        return iso;
      }
    },
    startEdit(field: "nickname" | "phone") {
      if (field === "nickname") {
        this.forms.nickname = this.profile?.nickname || "";
        this.editing.nickname = true;
      } else {
        this.forms.phone = this.profile?.phone || "";
        this.editing.phone = true;
      }
    },
    cancelEdit(field: "nickname" | "phone") {
      if (field === "nickname") this.editing.nickname = false;
      else this.editing.phone = false;
    },
    async saveField(field: "nickname" | "phone") {
      if (this.saving) return;
      this.saving = true;
      try {
        const payload: Record<string, string> = {};
        if (field === "nickname") {
          const v = (this.forms.nickname || "").trim();
          if (!v || v.length > 100) {
            toast.error("昵称长度需为 1-100");
            return;
          }
          payload.nickname = v;
        } else {
          const v = (this.forms.phone || "").trim();
          if (v && !/^1[3-9]\d{9}$/.test(v)) {
            toast.error("手机号格式不正确");
            return;
          }
          payload.phone = v;
        }
        const res = await doctorApi.updateUserProfile(payload);
        if (res && res.status === 200) {
          toast.success("已保存");
          this.editing[field] = false;
          // 同步到全局响应式 user state，右上角头像/昵称即时刷新
          const updated = res.data as UserProfile;
          updateUserState({ nickname: updated.nickname, phone: updated.phone });
          this.$emit("updated", updated);
        } else {
          toast.error((res && res.msg) || "保存失败");
        }
      } catch (e) {
        toast.error("保存失败，请稍后重试");
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.settings-card {
  background: var(--color-surface-container);
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  padding: 16px;
}

.settings-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--color-secondary-container);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  font-weight: 600;
  font-size: 18px;
  flex-shrink: 0;
}

.settings-field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

@media (max-width: 640px) {
  .settings-field-grid {
    grid-template-columns: 1fr;
  }
}

.settings-field {
  background: var(--color-surface);
  border: 1px solid var(--color-outline-variant);
  border-radius: 4px;
  padding: 10px 12px;
}

.settings-field-label {
  color: var(--color-on-surface-variant);
  font-size: 11px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  margin-bottom: 4px;
}

.settings-field-value {
  color: var(--color-on-surface);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.settings-field-readonly {
  color: var(--color-on-surface-variant);
}

.settings-lock-icon {
  font-size: 13px;
  color: var(--color-outline);
  margin-left: auto;
}

.settings-edit-btn {
  margin-left: auto;
  background: transparent;
  border: none;
  color: var(--color-on-surface-variant);
  cursor: pointer;
  padding: 2px;
  border-radius: 2px;
  transition: color 0.2s ease;
}

.settings-edit-btn:hover {
  color: var(--color-primary);
}

.settings-field-edit {
  display: flex;
  align-items: center;
  gap: 6px;
}

.settings-input {
  flex: 1;
  background: var(--color-surface-container-low);
  border: 1px solid var(--color-outline-variant);
  border-radius: 2px;
  padding: 4px 8px;
  color: var(--color-on-surface);
  font-size: 13px;
  font-family: inherit;
  outline: none;
}

.settings-input:focus {
  border-color: var(--color-primary);
}

.settings-save-btn,
.settings-cancel-btn {
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 2px;
  cursor: pointer;
  border: 1px solid var(--color-outline-variant);
  background: transparent;
  color: var(--color-on-surface-variant);
}

.settings-save-btn {
  background: var(--color-primary);
  color: var(--color-on-primary);
  border-color: var(--color-primary);
}

.settings-save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 余额卡片 */
.balance-card {
  margin-top: 16px;
}

.balance-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.balance-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.balance-title-icon {
  font-size: 22px;
  color: var(--color-primary);
  flex-shrink: 0;
}

.balance-title-text {
  color: var(--color-on-surface);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
}

.balance-title-sub {
  color: var(--color-on-surface-variant);
  font-size: 11px;
  margin-top: 2px;
  opacity: 0.75;
}

.balance-refresh-btn {
  background: transparent;
  border: 1px solid var(--color-outline-variant);
  border-radius: 4px;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--color-on-surface-variant);
  transition: color 0.2s ease, border-color 0.2s ease;
  flex-shrink: 0;
}

.balance-refresh-btn:hover:not(:disabled) {
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.balance-refresh-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.balance-refresh-btn .material-symbols-outlined {
  font-size: 18px;
}

.balance-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.balance-item {
  background: var(--color-surface);
  border: 1px solid var(--color-outline-variant);
  border-radius: 6px;
  padding: 12px 14px;
}

.balance-item-main {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.balance-total {
  color: var(--color-on-surface);
  font-size: 22px;
  font-weight: 700;
  font-family: "Inter", sans-serif;
  letter-spacing: -0.01em;
}

.balance-currency {
  color: var(--color-on-surface-variant);
  font-size: 12px;
  font-weight: 600;
}

.balance-item-sub {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  color: var(--color-on-surface-variant);
  font-size: 12px;
}

.balance-dot {
  opacity: 0.6;
}

.balance-warn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--color-error-container, rgba(239, 68, 68, 0.1));
  color: var(--color-on-error-container, var(--color-error, #ef4444));
  font-size: 12px;
}

.balance-warn .material-symbols-outlined {
  font-size: 16px;
}

.balance-updated {
  color: var(--color-on-surface-variant);
  font-size: 11px;
  opacity: 0.7;
  text-align: right;
  margin-top: 2px;
}

.balance-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 20px 12px;
  color: var(--color-on-surface-variant);
  font-size: 13px;
}

.balance-state .material-symbols-outlined {
  font-size: 18px;
}

.balance-state-error {
  color: var(--color-error, #ef4444);
}

.spin {
  animation: balance-spin 0.9s linear infinite;
}

@keyframes balance-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
