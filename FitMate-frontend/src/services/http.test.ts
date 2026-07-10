// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { clearUserSession, setToken, setUserInfo } from "./http";

/**
 * 验证 clearUserSession() 登出时清理前端用户级缓存。
 * 重点覆盖 fitmate_llm_config (localStorage) 与 fitmate:pending-draft (sessionStorage)，
 * 防止同浏览器切换用户时新用户看到旧用户的 LLM 配置或待发送草稿。
 */
describe("clearUserSession 清理用户级缓存", () => {
  beforeEach(() => {
    // 重置存储状态，模拟登录用户 A 后的残留
    localStorage.clear();
    sessionStorage.clear();
    // 写入 token / userInfo（cookie）
    setToken("user-a-token");
    setUserInfo({ userKey: "userA", id: 1001 });
    // 写入 LLM 配置残留（localStorage）
    localStorage.setItem(
      "fitmate_llm_config",
      JSON.stringify({
        baseUrl: "https://api.deepseek.com",
        apiKey: "sk-****e05f",
        model: "deepseek-v4-flash",
      })
    );
    // 写入待发送草稿残留（sessionStorage，可能含个人健康数据）
    sessionStorage.setItem("fitmate:pending-draft", "我的体重 75kg，心率 120");
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it("登出后应清理 localStorage 中的 fitmate_llm_config", () => {
    // 修复前：残留；修复后：应被删除
    expect(localStorage.getItem("fitmate_llm_config")).not.toBeNull();
    clearUserSession();
    expect(localStorage.getItem("fitmate_llm_config")).toBeNull();
  });

  it("登出后应清理 sessionStorage 中的 fitmate:pending-draft", () => {
    expect(sessionStorage.getItem("fitmate:pending-draft")).not.toBeNull();
    clearUserSession();
    expect(sessionStorage.getItem("fitmate:pending-draft")).toBeNull();
  });

  it("登出后应同时清理 cookie 中的 token 与 userInfo", () => {
    clearUserSession();
    // cookie 通过 max-age=0 失效，再次读取应为 undefined
    expect(
      document.cookie
        .split(";")
        .map((c) => c.trim().split("=")[0])
        .includes("user_token")
    ).toBe(false);
    expect(
      document.cookie
        .split(";")
        .map((c) => c.trim().split("=")[0])
        .includes("user_info")
    ).toBe(false);
  });

  it("登出后应触发 user-info-changed 事件（通知组件刷新）", () => {
    let fired = false;
    const handler = () => {
      fired = true;
    };
    window.addEventListener("fitmate:user-info-changed", handler);
    clearUserSession();
    window.removeEventListener("fitmate:user-info-changed", handler);
    expect(fired).toBe(true);
  });

  it("切换用户场景：A 登出后 B 登入不会读到 A 的残留", () => {
    // 用户 A 登出
    clearUserSession();
    // 模拟用户 B 登入：此时 llmConfig 模块若 restoreLocal() 应读不到任何残留
    const restoredLlm = localStorage.getItem("fitmate_llm_config");
    const restoredDraft = sessionStorage.getItem("fitmate:pending-draft");
    expect(restoredLlm).toBeNull();
    expect(restoredDraft).toBeNull();
  });
});
