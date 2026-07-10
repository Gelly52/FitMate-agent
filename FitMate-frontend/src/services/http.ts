import axios from "axios";
import { API_BASE } from "../config/runtime";

const TOKEN_COOKIE_KEY = "user_token";
const USER_INFO_COOKIE_KEY = "user_info";
const DEFAULT_COOKIE_MAX_AGE = 7 * 24 * 60 * 60;

export function getCookieValue(name, cookieString) {
  const source =
    typeof cookieString === "string"
      ? cookieString
      : typeof document !== "undefined"
      ? document.cookie
      : "";

  if (!source) {
    return undefined;
  }

  const cookieEntries = source.split(";");
  for (let index = 0; index < cookieEntries.length; index += 1) {
    const cookieEntry = cookieEntries[index].trim();
    if (!cookieEntry) {
      continue;
    }

    const separatorIndex = cookieEntry.indexOf("=");
    const rawName =
      separatorIndex >= 0 ? cookieEntry.slice(0, separatorIndex) : cookieEntry;
    if (rawName !== name) {
      continue;
    }

    const rawValue =
      separatorIndex >= 0 ? cookieEntry.slice(separatorIndex + 1) : "";
    try {
      return decodeURIComponent(rawValue);
    } catch (error) {
      return rawValue;
    }
  }

  return undefined;
}

export function setCookieValue(
  name,
  value,
  maxAgeSeconds = DEFAULT_COOKIE_MAX_AGE
) {
  if (typeof document === "undefined") {
    return;
  }

  const encodedValue = encodeURIComponent(value == null ? "" : String(value));
  let cookie = `${name}=${encodedValue}; path=/; SameSite=Lax`;
  if (typeof maxAgeSeconds === "number") {
    cookie += `; max-age=${maxAgeSeconds}`;
  }
  document.cookie = cookie;
}

export function removeCookieValue(name) {
  if (typeof document === "undefined") {
    return;
  }
  document.cookie = `${name}=; path=/; max-age=0; SameSite=Lax`;
}

export function getToken() {
  return getCookieValue(TOKEN_COOKIE_KEY);
}

export function setToken(token, maxAgeSeconds = DEFAULT_COOKIE_MAX_AGE) {
  if (!token) {
    return;
  }
  setCookieValue(TOKEN_COOKIE_KEY, token, maxAgeSeconds);
}

export function getUserInfo() {
  const userJson = getCookieValue(USER_INFO_COOKIE_KEY);
  if (userJson === undefined || userJson === "") {
    return undefined;
  }

  try {
    return JSON.parse(userJson);
  } catch (error) {
    return undefined;
  }
}

export function setUserInfo(userInfo, maxAgeSeconds = DEFAULT_COOKIE_MAX_AGE) {
  if (!userInfo) {
    return;
  }
  setCookieValue(USER_INFO_COOKIE_KEY, JSON.stringify(userInfo), maxAgeSeconds);
  notifyUserInfoChanged();
}

/** 用户信息变更事件名，供组件监听以同步刷新（如右上角头像）。 */
export const USER_INFO_CHANGED_EVENT = "fitmate:user-info-changed";

function notifyUserInfoChanged() {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent(USER_INFO_CHANGED_EVENT));
}

/** 局部更新用户信息（同时写入 cookie 并通知监听组件）。 */
export function updateUserState(partial: Record<string, unknown>) {
  const current = getUserInfo() || {};
  const next = { ...current, ...partial };
  setUserInfo(next);
}

export function clearUserSession() {
  removeCookieValue(TOKEN_COOKIE_KEY);
  removeCookieValue(USER_INFO_COOKIE_KEY);
  // 清理前端残留的用户级缓存，防止同浏览器切换用户时新用户看到旧用户数据
  // - fitmate_llm_config: localStorage 中的 LLM 配置（含脱敏 apiKey、baseUrl、model）
  // - fitmate:pending-draft: sessionStorage 中的待发送草稿（可能含个人健康数据）
  try {
    localStorage.removeItem("fitmate_llm_config");
  } catch (e) {
    /* ignore */
  }
  try {
    sessionStorage.removeItem("fitmate:pending-draft");
  } catch (e) {
    /* ignore */
  }
  notifyUserInfoChanged();
}

export function createHttpInstance() {
  const httpInstance = axios.create({
    baseURL: API_BASE,
    withCredentials: true,
    // MCP 测试连接可能耗时较长（HTTP 预探测 + MCP initialize + listTools），
    // 给足 120s 避免被 axios 主动取消导致 status=canceled。
    timeout: 120000,
  });

  httpInstance.interceptors.request.use(
    (config) => {
      const nextConfig = config || {};
      nextConfig.headers = nextConfig.headers || {};

      const userInfo = getUserInfo();
      if (userInfo) {
        nextConfig.headers.headerUserId = userInfo.userKey || userInfo.id;
      }

      const userToken = getToken();
      if (userToken) {
        nextConfig.headers.headerUserToken = userToken;
      }

      return nextConfig;
    },
    (error) => {
      console.log(error);
      return Promise.reject(error);
    }
  );

  httpInstance.interceptors.response.use(
    (response) => response.data,
    (error) => {
      console.log("err: " + error);
      console.log("err: " + (error && error.data));
      return Promise.reject(error);
    }
  );

  return httpInstance;
}

export const instance = createHttpInstance();
export const http = instance;

if (typeof window !== "undefined") {
  window.instance = instance;
}

export default instance;
