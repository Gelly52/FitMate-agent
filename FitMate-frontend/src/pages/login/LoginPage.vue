<template>
  <main
    class="w-full min-h-screen flex flex-col items-center justify-center bg-background text-on-surface antialiased selection:bg-inverse-primary selection:text-inverse-surface px-margin"
  >
    <div class="w-full max-w-sm flex flex-col gap-xl">
      <!-- Brand -->
      <header class="flex flex-col items-center gap-md">
        <div
          class="w-24 h-24 rounded-full bg-surface-container border border-outline-variant flex items-center justify-center"
        >
          <span
            class="material-symbols-outlined text-primary"
            style="font-size: 56px; font-variation-settings: 'FILL' 1;"
            >smart_toy</span
          >
        </div>
        <h1
          class="font-inter text-headline-md text-on-surface tracking-tight"
        >
          System Auth
        </h1>
        <p
          class="font-inter text-label-xs text-on-surface-variant uppercase tracking-widest opacity-70"
        >
          FitMate // Secure Access
        </p>
      </header>

      <!-- Form -->
      <form
        class="flex flex-col gap-lg w-full"
        @submit.prevent="submitLogin"
      >
        <!-- Email -->
        <div class="flex flex-col gap-xs group">
          <label
            class="font-inter text-label-xs text-on-surface-variant uppercase tracking-widest transition-colors group-focus-within:text-inverse-primary"
            for="email"
          >
            Email Address
          </label>
          <input
            id="email"
            v-model.trim="loginForm.email"
            autocomplete="email"
            class="login-underline font-inter text-body-base text-on-surface"
            maxlength="100"
            placeholder="you@example.com"
            type="email"
          />
        </div>

        <!-- Code -->
        <div class="flex items-end gap-md">
          <div class="flex-1 flex flex-col gap-xs group">
            <label
              class="font-inter text-label-xs text-on-surface-variant uppercase tracking-widest transition-colors group-focus-within:text-inverse-primary"
              for="code"
            >
              Verification Code
            </label>
            <input
              id="code"
              v-model.trim="loginForm.code"
              autocomplete="one-time-code"
              class="login-underline font-inter text-body-base text-on-surface tracking-[0.5em]"
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
            class="mb-[5px] pb-xs font-inter text-label-xs text-on-surface uppercase tracking-widest transition-colors border-b border-transparent hover:text-inverse-primary hover:border-inverse-primary disabled:opacity-50 disabled:cursor-not-allowed"
            :disabled="isCodeSending || codeCountdown > 0"
            @click="sendLoginCode"
          >
            {{ codeCountdown > 0 ? `${codeCountdown}s` : "Get Code" }}
          </button>
        </div>

        <!-- Password -->
        <div class="flex flex-col gap-xs group">
          <label
            class="font-inter text-label-xs text-on-surface-variant uppercase tracking-widest transition-colors group-focus-within:text-inverse-primary"
            for="password"
          >
            Password
          </label>
          <input
            id="password"
            v-model.trim="loginForm.password"
            autocomplete="current-password"
            class="login-underline font-inter text-body-base text-on-surface"
            maxlength="32"
            minlength="8"
            placeholder="8-32 chars, letters + digits"
            type="password"
            @keyup.enter="submitLogin"
          />
        </div>

        <!-- Divider + Submit -->
        <div
          class="pt-md mt-sm border-t border-outline-variant/30 flex flex-col gap-md"
        >
          <button
            type="submit"
            class="w-full py-md bg-inverse-primary text-inverse-surface font-inter text-label-sm uppercase tracking-widest rounded-lg transition-colors duration-300 flex items-center justify-center gap-sm hover:bg-primary-container hover:text-on-primary-container disabled:opacity-50 disabled:cursor-not-allowed"
            :disabled="isLoginSubmitting"
          >
            <span>{{ isLoginSubmitting ? "Authenticating" : "Authenticate" }}</span>
            <span
              v-if="!isLoginSubmitting"
              class="material-symbols-outlined"
              style="font-size: 16px;"
              >arrow_forward</span
            >
          </button>
          <p
            class="text-center font-inter text-label-xs text-on-surface-variant uppercase tracking-widest opacity-60"
          >
            Secure Sector Encryption
          </p>
        </div>
      </form>
    </div>

    <!-- Footer -->
    <footer
      class="fixed bottom-0 left-0 right-0 py-margin flex justify-center opacity-40 pointer-events-none"
    >
      <p
        class="font-inter text-label-xs text-on-surface-variant uppercase tracking-widest"
      >
        FitMate DS // Build 4.2.1
      </p>
    </footer>
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
    submitLogin() {
      var email = (this.loginForm.email || "").trim();
      var code = (this.loginForm.code || "").trim();
      var password = (this.loginForm.password || "").trim();
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        this.showUiMessage("error", "请输入正确的邮箱");
        return;
      }
      if (!/^\d{4,6}$/.test(code)) {
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
.login-underline {
  background-color: transparent;
  border: none;
  border-bottom: 1px solid #414755;
  border-radius: 0;
  padding: 4px 0;
  width: 100%;
  outline: none;
  transition: border-color 0.2s ease;
}

.login-underline:focus {
  border-bottom-color: #005bc1;
}

.login-underline::placeholder {
  color: #363942;
}

/* Minimalist Input Autofill Reset */
input:-webkit-autofill,
input:-webkit-autofill:hover,
input:-webkit-autofill:focus,
input:-webkit-autofill:active {
  -webkit-box-shadow: 0 0 0 30px #10131b inset !important;
  -webkit-text-fill-color: #e0e2ed !important;
  transition: background-color 5000s ease-in-out 0s;
}
</style>
