<template>
  <main
    class="login-page w-full min-h-screen flex flex-col items-center justify-center bg-background text-on-surface antialiased selection:bg-inverse-primary selection:text-inverse-surface px-margin"
  >
    <div class="login-card w-full max-w-sm flex flex-col gap-lg">
      <!-- Brand -->
      <header class="flex flex-col items-center gap-md">
        <div class="login-mark animate-float" aria-hidden="true">
          <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="4" y="4" width="8" height="24" fill="currentColor" />
            <rect x="12" y="4" width="10" height="8" fill="currentColor" />
            <rect x="12" y="16" width="8" height="8" fill="currentColor" />
            <rect x="26" y="4" width="6" height="6" fill="#D4A533" />
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
/* ===== 像素风登录页：硬边框 + 硬阴影 + 像素网格背景 ===== */
.login-page {
  position: relative;
  background-color: var(--color-background);
}

/* 低透明度像素网格背景（纯装饰，不响应指针） */
.login-page::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(var(--color-outline) 1px, transparent 1px),
    linear-gradient(90deg, var(--color-outline) 1px, transparent 1px);
  background-size: 24px 24px;
  opacity: 0.06;
}

/* ===== 登录卡片：hero 级 4px 边框 + 8px 硬阴影 ===== */
.login-card {
  position: relative;
  background: var(--color-surface);
  border: 4px solid var(--color-outline);
  box-shadow: 8px 8px 0 0 #101010;
  padding: 40px 32px;
}

/* ===== Brand mark：像素方块图标，悬浮动画 ===== */
.login-mark {
  width: 56px;
  height: 56px;
  padding: 8px;
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3px solid var(--color-outline);
  background: var(--color-surface);
  box-shadow: 3px 3px 0 0 #101010;
}
.login-mark svg {
  width: 100%;
  height: 100%;
}

/* ===== 标题：继承像素字体 ===== */
.login-title {
  font-size: 28px;
  font-weight: 400;
  letter-spacing: 0.02em;
  color: var(--color-on-surface);
  margin: 0;
}

/* ===== 标签 ===== */
.login-label {
  font-size: 15px;
  color: var(--color-on-surface-variant);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  transition: color 0.1s;
}
.group:focus-within .login-label {
  color: var(--color-primary);
}

/* ===== 输入框：像素 Input 规范（3px 边框 + 内嵌硬阴影，聚焦变蓝） ===== */
.login-underline {
  background-color: var(--color-surface);
  border: 3px solid var(--color-outline);
  border-radius: 0;
  padding: 8px 12px;
  width: 100%;
  outline: none;
  font-size: 17px;
  color: var(--color-on-surface);
  box-shadow: inset 2px 2px 0 0 rgba(16, 16, 16, 0.35);
  transition: border-color 0.1s;
}
.login-underline:focus {
  border-color: var(--pixel-blue);
  outline: none;
}
.login-underline::placeholder {
  color: var(--color-on-surface-variant);
  opacity: 0.5;
}

/* 验证码输入：等宽字距 */
.login-code-input {
  letter-spacing: 0.3em;
  font-variant-numeric: tabular-nums;
}

/* ===== 发送验证码按钮：黄色像素小按钮 ===== */
.login-code-btn {
  padding: 8px 12px;
  font-size: 15px;
  color: var(--pixel-black);
  background: var(--pixel-yellow);
  border: 3px solid var(--color-outline);
  box-shadow: 2px 2px 0 0 #101010;
  cursor: pointer;
  white-space: nowrap;
  transition: background-color 0.1s, transform 0.1s, box-shadow 0.1s;
}
.login-code-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}
.login-code-btn:active:not(:disabled) {
  transform: translate(2px, 2px);
  box-shadow: 0 0 0 0 #101010;
}
.login-code-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: 2px 2px 0 0 #666666;
}

/* ===== 提示文案 ===== */
.login-hint {
  font-size: 14px;
  color: var(--color-on-surface-variant);
  line-height: 1.5;
  margin: 0;
}

/* ===== 提交按钮：像素 Button 规范（主色 + 硬阴影 + 按压下沉） ===== */
.login-submit {
  width: 100%;
  padding: 12px 20px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: 18px;
  letter-spacing: 0.05em;
  border: 3px solid var(--color-outline);
  border-radius: 0;
  box-shadow: 4px 4px 0 0 #101010;
  cursor: pointer;
  transition: transform 0.1s, box-shadow 0.1s, background-color 0.1s;
}
.login-submit:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 5px 5px 0 0 #101010;
}
.login-submit:active:not(:disabled) {
  transform: translate(2px, 2px);
  box-shadow: 2px 2px 0 0 #101010;
}
.login-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: 2px 2px 0 0 #666666;
}

/* ===== Autofill：跟随主题，避免亮色下黑块 ===== */
input:-webkit-autofill,
input:-webkit-autofill:hover,
input:-webkit-autofill:focus,
input:-webkit-autofill:active {
  -webkit-box-shadow: 0 0 0 30px var(--color-surface) inset !important;
  -webkit-text-fill-color: var(--color-on-surface) !important;
  transition: background-color 5000s ease-in-out 0s;
}
</style>
