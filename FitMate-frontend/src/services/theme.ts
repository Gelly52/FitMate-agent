// FitMate-frontend/src/services/theme.ts
import type { ThemeMode, AccentColor, UserSettings } from "../types/settings";
import { DEFAULT_USER_SETTINGS } from "../types/settings";

const STORAGE_KEY_MODE = "fitmate_theme_mode";
const STORAGE_KEY_ACCENT = "fitmate_accent_color";

let mediaQuery: MediaQueryList | null = null;
let mediaListener: ((e: MediaQueryListEvent) => void) | null = null;
let syncCallback: ((settings: UserSettings) => void) | null = null;
let syncTimer: ReturnType<typeof setTimeout> | null = null;

/** 读取 localStorage 的 theme mode，缺省 dark */
export function getStoredMode(): ThemeMode {
  const v = localStorage.getItem(STORAGE_KEY_MODE);
  if (v === "light" || v === "dark" || v === "auto") return v;
  return DEFAULT_USER_SETTINGS.themeMode;
}

/** 读取 localStorage 的 accent color，缺省 blue */
export function getStoredAccent(): AccentColor {
  const v = localStorage.getItem(STORAGE_KEY_ACCENT);
  if (v === "blue" || v === "green" || v === "orange" || v === "purple" || v === "light" || v === "dark") return v;
  return DEFAULT_USER_SETTINGS.accentColor;
}

/** 把 mode 解析为实际 light/dark（auto 跟随系统） */
function resolveMode(mode: ThemeMode): "light" | "dark" {
  if (mode === "auto") {
    return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }
  return mode;
}

/** 把 mode + accent 应用到 <html> data 属性 */
function applyToDom(mode: ThemeMode, accent: AccentColor): void {
  const resolved = resolveMode(mode);
  document.documentElement.dataset.theme = resolved;
  document.documentElement.dataset.accent = accent;
  updateFavicon(resolved, accent);
}

/** accent + theme → favicon 背景色映射（取 --color-primary 值，像素调色板） */
const FAVICON_COLORS: Record<string, string> = {
  "dark:blue": "#6B8FD8",
  "light:blue": "#3A5BA0",
  "dark:green": "#4FA96B",
  "light:green": "#2D7D46",
  "dark:orange": "#D4A533",
  "light:orange": "#D4A533",
  "dark:purple": "#C75B5B",
  "light:purple": "#A83232",
  "dark:light": "#F8F8F8",
  "light:light": "#6B6B6B",
  "dark:dark": "#6B6B6B",
  "light:dark": "#101010",
};

/** 根据当前主题+强调色动态更新 favicon（像素 F + AI 火花徽标） */
function updateFavicon(theme: "light" | "dark", accent: AccentColor): void {
  const color = FAVICON_COLORS[`${theme}:${accent}`] || FAVICON_COLORS["dark:blue"];
  const fg = color === "#F8F8F8" || color === "#D4A533" ? "#101010" : "#F8F8F8";
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" fill="${color}"/><rect x="2" y="2" width="60" height="60" fill="none" stroke="#101010" stroke-width="4"/><rect x="10" y="10" width="14" height="44" fill="${fg}"/><rect x="24" y="10" width="18" height="14" fill="${fg}"/><rect x="24" y="32" width="14" height="14" fill="${fg}"/><rect x="48" y="10" width="10" height="10" fill="#D4A533"/></svg>`;
  const href = "data:image/svg+xml," + encodeURIComponent(svg);
  let link = document.querySelector("link[rel='icon']") as HTMLLinkElement | null;
  if (!link) {
    link = document.createElement("link");
    link.rel = "icon";
    document.head.appendChild(link);
  }
  link.href = href;
  link.type = "image/svg+xml";
}

/** 监听系统主题变化（仅 mode=auto 时生效） */
function bindMediaListener(mode: ThemeMode): void {
  if (mediaQuery && mediaListener) {
    mediaQuery.removeEventListener("change", mediaListener);
  }
  if (mode !== "auto") {
    mediaQuery = null;
    mediaListener = null;
    return;
  }
  mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
  mediaListener = (e: MediaQueryListEvent) => {
    const resolved = e.matches ? "dark" : "light";
    document.documentElement.dataset.theme = resolved;
    updateFavicon(resolved, getStoredAccent());
  };
  mediaQuery.addEventListener("change", mediaListener);
}

/** 初始化主题（在 Vue 挂载前调用，配合 fitmate-vite.html 防闪烁脚本） */
export function initTheme(): void {
  const mode = getStoredMode();
  const accent = getStoredAccent();
  applyToDom(mode, accent);
  bindMediaListener(mode);
}

/** 设置主题模式，即时生效 + 持久化 + 防抖同步 */
export function setMode(mode: ThemeMode): void {
  localStorage.setItem(STORAGE_KEY_MODE, mode);
  applyToDom(mode, getStoredAccent());
  bindMediaListener(mode);
  scheduleSync();
}

/** 设置强调色，即时生效 + 持久化 + 防抖同步 */
export function setAccent(accent: AccentColor): void {
  localStorage.setItem(STORAGE_KEY_ACCENT, accent);
  applyToDom(getStoredMode(), accent);
  scheduleSync();
}

/** 从后端加载设置覆盖本地（登录后调用） */
export function applyRemoteSettings(settings: UserSettings): void {
  localStorage.setItem(STORAGE_KEY_MODE, settings.themeMode);
  localStorage.setItem(STORAGE_KEY_ACCENT, settings.accentColor);
  applyToDom(settings.themeMode, settings.accentColor);
  bindMediaListener(settings.themeMode);
}

/** 注册同步回调（设置页或 AppLayout 注入后端保存函数） */
export function onSettingsChange(cb: (settings: UserSettings) => void): void {
  syncCallback = cb;
}

/** 防抖同步：800ms 内多次变更只触发一次后端保存 */
function scheduleSync(): void {
  if (syncTimer) clearTimeout(syncTimer);
  syncTimer = setTimeout(() => {
    if (syncCallback) {
      syncCallback({
        themeMode: getStoredMode(),
        accentColor: getStoredAccent(),
      });
    }
  }, 800);
}

export default {
  initTheme,
  getStoredMode,
  getStoredAccent,
  setMode,
  setAccent,
  applyRemoteSettings,
  onSettingsChange,
};
