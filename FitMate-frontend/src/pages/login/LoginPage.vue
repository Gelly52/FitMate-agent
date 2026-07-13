<template>
  <main
    class="login-page w-full min-h-screen flex flex-col items-center justify-center bg-background text-on-surface antialiased selection:bg-inverse-primary selection:text-inverse-surface px-margin"
  >
    <div class="w-full max-w-sm flex flex-col gap-xl">
      <!-- Brand -->
      <header class="flex flex-col items-center gap-md">
        <div class="login-mark" aria-hidden="true">
          <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="4" y="4" width="10" height="10" rx="2.5" fill="currentColor" />
            <rect x="18" y="4" width="10" height="10" rx="2.5" fill="currentColor" opacity="0.35" />
            <rect x="4" y="18" width="10" height="10" rx="2.5" fill="currentColor" opacity="0.35" />
            <rect x="18" y="18" width="10" height="10" rx="2.5" fill="currentColor" />
          </svg>
        </div>
        <h1 class="login-title">FitMate Agent</h1>
      </header>

      <!-- Form -->
      <form
        class="flex flex-col gap-lg w-full"
        @submit.prevent="submitLogin"
      >
        <!-- Email -->
        <div class="flex flex-col gap-xs group">
          <label class="login-label" for="email">Email</label>
          <input
            id="email"
            v-model.trim="loginForm.email"
            autocomplete="email"
            class="login-underline"
            maxlength="100"
            placeholder="you@example.com"
            type="email"
            @blur="checkEmailExists"
          />
        </div>

        <!-- Code -->
        <div class="flex flex-col gap-xs">
          <div class="flex items-end gap-md">
            <div class="flex-1 flex flex-col gap-xs group">
              <label class="login-label" for="code">Verification code</label>
              <input
                id="code"
                v-model.trim="loginForm.code"
                autocomplete="one-time-code"
                class="login-underline login-code-input"
                inputmode="numeric"
                maxlength="6"
                pattern="[0-9]*"
                placeholder="000000"
                type="text"
                @keyup.enter="submitLogin"
              />
            </div>
            <button
              type="button"
              class="login-code-btn"
              :disabled="isCodeSending || codeCountdown > 0"
              @click="sendLoginCode"
            >
              {{ codeCountdown > 0 ? `${codeCountdown}s` : "Send code" }}
            </button>
          </div>
          <p v-if="accountExists" class="login-hint">
            账号已存在，可不填验证码，直接用密码登录。
          </p>
        </div>

        <!-- Password -->
        <div class="flex flex-col gap-xs group">
          <label class="login-label" for="password">Password</label>
          <input
            id="password"
            v-model.trim="loginForm.password"
            autocomplete="current-password"
            class="login-underline"
            maxlength="32"
            minlength="8"
            placeholder="8–32 位，字母与数字"
            type="password"
            @keyup.enter="submitLogin"
          />
        </div>

        <!-- Submit -->
        <div class="pt-md flex flex-col gap-sm">
          <button
            type="submit"
            class="login-submit"
            :disabled="isLoginSubmitting"
          >
            <span>{{ isLoginSubmitting ? "Signing in…" : "Sign in" }}</span>
          </button>
        </div>
      </form>
    </div>
  </main>
</template>

<script lang="ts">
import toast from "../../services/toast";
import doctorApi from "../../services/doctorApi";
import { setToken, setUserInfo } from "../../services/http";

export default {
  name: "LoginPage",
  emits: ["login-success"],
  data() {
    return {
      loginForm: {
        email: "",
        code: "",
        password: "",
      },
      isCodeSending: false,
      isLoginSubmitting: false,
      codeCountdown: 0,
      codeCountdownTimer: null,
      // 账号存在且已设置密码时为 true，此时可跳过验证码
      accountExists: false,
    };
  },
  beforeUnmount() {
    this.clearCodeCountdown();
  },
  methods: {
    showUiMessage(type, text) {
      if (toast && typeof toast[type] === "function") {
        toast[type](text);
      }
    },
    unwrapApiData(res, fallbackMsg) {
      if (!res) {
        throw new Error(fallbackMsg || "请求失败");
      }
      if (typeof res.status !== "undefined" && res.status !== 200) {
        throw new Error(res.msg || fallbackMsg || "请求失败");
      }
      return typeof res.data === "undefined" ? res : res.data;
    },
    persistLoginSession(loginData) {
      if (!loginData) {
        throw new Error("登录返回数据为空");
      }
      var token = loginData.token;
      var userInfo = loginData.userInfo || {};
      var stableUserKey = userInfo.userKey || userInfo.id;
      if (!token || !stableUserKey) {
        throw new Error("登录返回缺少 token 或 userKey");
      }
      userInfo.id = stableUserKey;
      userInfo.userKey = stableUserKey;
      setToken(token);
      setUserInfo(userInfo);
      return userInfo;
    },
    startCodeCountdown(seconds) {
      this.clearCodeCountdown();
      this.codeCountdown = seconds;
      this.codeCountdownTimer = window.setInterval(
        function () {
          if (this.codeCountdown <= 1) {
            this.clearCodeCountdown();
            return;
          }
          this.codeCountdown -= 1;
        }.bind(this),
        1000
      );
    },
    clearCodeCountdown() {
      if (this.codeCountdownTimer) {
        window.clearInterval(this.codeCountdownTimer);
        this.codeCountdownTimer = null;
      }
      this.codeCountdown = 0;
    },
    sendLoginCode() {
      var email = (this.loginForm.email || "").trim();
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        this.showUiMessage("error", "请输入正确的邮箱");
        return;
      }
      this.isCodeSending = true;
      doctorApi
        .sendUserCode({ email: email })
        .then(
          function (res) {
            this.unwrapApiData(res, "验证码发送失败");
            this.showUiMessage("success", "验证码已发送至邮箱");
            this.startCodeCountdown(60);
          }.bind(this)
        )
        .catch(
          function (error) {
            this.showUiMessage(
              "error",
              error && error.message
                ? error.message
                : "验证码发送失败，请稍后重试"
            );
          }.bind(this)
        )
        .finally(
          function () {
            this.isCodeSending = false;
          }.bind(this)
        );
    },
    checkEmailExists() {
      var email = (this.loginForm.email || "").trim();
      // 邮箱格式不正确直接重置状态，不弹错（避免与提交校验重复）
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        this.accountExists = false;
        return Promise.resolve(false);
      }
      return doctorApi
        .checkEmailRegistered(email)
        .then(
          function (res) {
            var data = res && typeof res.data !== "undefined" ? res.data : res;
            var exists = !!(data && data.exists && data.passwordSet);
            this.accountExists = exists;
            return exists;
          }.bind(this)
        )
        .catch(
          function () {
            // 静默失败：不影响登录主流程，按需要验证码处理
            this.accountExists = false;
            return false;
          }.bind(this)
        );
    },
    async submitLogin() {
      var email = (this.loginForm.email || "").trim();
      var code = (this.loginForm.code || "").trim();
      var password = (this.loginForm.password || "").trim();
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        this.showUiMessage("error", "请输入正确的邮箱");
        return;
      }
      // 提交前再确认一次账号状态，避免 blur 异步未完成导致误判
      if (!this.accountExists) {
        await this.checkEmailExists();
      }
      // 账号已存在且已设置密码时可跳过验证码；其他情况必须填写验证码
      if (!this.accountExists && !/^\d{4,6}$/.test(code)) {
        this.showUiMessage("error", "请输入正确的验证码");
        return;
      }
      if (!/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(password)) {
        this.showUiMessage("error", "密码需为 8-32 位且同时包含字母和数字");
        return;
      }
      this.isLoginSubmitting = true;
      doctorApi
        .userLogin({ email: email, code: code, password: password })
        .then(
          function (res) {
            var data = this.unwrapApiData(res, "登录失败");
            var userInfo = this.persistLoginSession(data);
            this.showUiMessage(
              "success",
              data && data.newUser ? "注册并登录成功" : "登录成功"
            );
            this.$emit("login-success", userInfo);
            if (this.$router) {
              this.$router.push("/chat");
            }
          }.bind(this)
        )
        .catch(
          function (error) {
            this.showUiMessage(
              "error",
              error && error.message
                ? error.message
                : "登录失败，请检查邮箱、验证码和密码"
            );
          }.bind(this)
        )
        .finally(
          function () {
            this.isLoginSubmitting = false;
          }.bind(this)
        );
    },
  },
};
</script>

<style scoped>
/* ===== 字体：Manrope，简约现代有特色 ===== */
@import url("https://fonts.loli.net/css2?family=Manrope:wght@400;500;600;700&display=swap");

.login-page {
  font-family: "Manrope", system-ui, -apple-system, sans-serif;
  --login-gap-2xl: 48px;
  --login-radius: 10px;
}

/* ===== Brand mark：2x2 几何方块网格，对角透明度形成视觉韵律 ===== */
.login-mark {
  width: 44px;
  height: 44px;
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}
.login-mark svg {
  width: 100%;
  height: 100%;
}

/* ===== 标题：轻字重 + 紧凑字距，编辑风 ===== */
.login-title {
  font-family: "Manrope", system-ui, sans-serif;
  font-weight: 600;
  font-size: 22px;
  letter-spacing: -0.01em;
  color: var(--color-on-surface);
  margin: 0;
}

/* ===== 标签：小写、正常字距，柔和而非科技感 ===== */
.login-label {
  font-family: "Manrope", system-ui, sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-on-surface-variant);
  letter-spacing: 0;
  transition: color 0.2s ease;
}
.group:focus-within .login-label {
  color: var(--color-primary);
}

/* ===== 输入框：精致下划线 ===== */
.login-underline {
  background-color: transparent;
  border: none;
  border-bottom: 1px solid var(--color-outline-variant);
  border-radius: 0;
  padding: 8px 0;
  width: 100%;
  outline: none;
  font-family: "Manrope", system-ui, sans-serif;
  font-size: 15px;
  font-weight: 400;
  color: var(--color-on-surface);
  transition: border-color 0.2s ease;
}
.login-underline:focus {
  border-bottom-color: var(--color-primary);
}
.login-underline::placeholder {
  color: var(--color-on-surface-variant);
  opacity: 0.45;
  font-weight: 400;
}

/* 验证码输入：等宽字距，但不过分 */
.login-code-input {
  letter-spacing: 0.3em;
  font-variant-numeric: tabular-nums;
}

/* ===== 发送验证码按钮：文字按钮，下划线 hover ===== */
.login-code-btn {
  margin-bottom: 6px;
  padding: 4px 2px;
  font-family: "Manrope", system-ui, sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-primary);
  background: transparent;
  border: none;
  border-bottom: 1px solid transparent;
  cursor: pointer;
  transition: border-color 0.2s ease, opacity 0.2s ease;
  white-space: nowrap;
}
.login-code-btn:hover:not(:disabled) {
  border-bottom-color: var(--color-primary);
}
.login-code-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* ===== 提示文案 ===== */
.login-hint {
  font-family: "Manrope", system-ui, sans-serif;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-on-surface-variant);
  opacity: 0.75;
  line-height: 1.5;
  margin: 0;
}

/* ===== 提交按钮：实色、柔和圆角，去掉赛博风 ===== */
.login-submit {
  width: 100%;
  padding: 13px 20px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-family: "Manrope", system-ui, sans-serif;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.01em;
  border: none;
  border-radius: var(--login-radius);
  cursor: pointer;
  transition: opacity 0.2s ease, transform 0.1s ease;
}
.login-submit:hover:not(:disabled) {
  opacity: 0.9;
}
.login-submit:active:not(:disabled) {
  transform: translateY(1px);
}
.login-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ===== Autofill：跟随主题，避免亮色下黑块 ===== */
input:-webkit-autofill,
input:-webkit-autofill:hover,
input:-webkit-autofill:focus,
input:-webkit-autofill:active {
  -webkit-box-shadow: 0 0 0 30px var(--color-background) inset !important;
  -webkit-text-fill-color: var(--color-on-surface) !important;
  transition: background-color 5000s ease-in-out 0s;
}
</style>

<!-- 亮色模式柔和化：纯白背景改为柔和浅灰，降低刺眼感 -->
<style>
[data-theme="light"] .login-page {
  background-color: #eef1f5;
}
</style>
