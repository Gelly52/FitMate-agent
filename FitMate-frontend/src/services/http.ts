import axios from "axios";
import { reactive } from "vue";
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
  userState.data = userInfo;
}

/** 响应式用户状态，供组件监听用户信息变化（如昵称更新后右上角头像同步）。 */
export const userState = reactive<{ data: Record<string, unknown> | undefined }>({
  data: getUserInfo(),
});

/** 局部更新用户信息（同时写入 cookie 与响应式 state）。 */
export function updateUserState(partial: Record<string, unknown>) {
  const current = getUserInfo() || {};
  const next = { ...current, ...partial };
  setUserInfo(next);
}

export function clearUserSession() {
  removeCookieValue(TOKEN_COOKIE_KEY);
  removeCookieValue(USER_INFO_COOKIE_KEY);
  userState.data = undefined;
}

export function createHttpInstance() {
  const httpInstance = axios.create({
    baseURL: API_BASE,
    withCredentials: true,
    timeout: 60000,
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
