// FitMate-frontend/src/types/settings.ts

/** 主题模式：亮色 / 暗色 / 跟随系统 */
export type ThemeMode = "light" | "dark" | "auto";

/** 强调色预设 */
export type AccentColor = "blue" | "green" | "orange" | "purple";

/** 用户偏好设置（前后端共享结构） */
export interface UserSettings {
  themeMode: ThemeMode;
  accentColor: AccentColor;
}

/** 用户资料（设置页展示 + 部分编辑） */
export interface UserProfile {
  nickname: string | null;
  phone: string | null;
  email: string | null;
  username: string | null;
  createdAt: string | null;
  lastLoginAt: string | null;
}

/** 用户资料更新请求（仅允许 nickname / phone） */
export interface UserProfileUpdate {
  nickname?: string;
  phone?: string;
}

/** 默认设置 */
export const DEFAULT_USER_SETTINGS: UserSettings = {
  themeMode: "dark",
  accentColor: "blue",
};

/** 可选强调色列表（供 UI 渲染色板） */
export const ACCENT_COLOR_OPTIONS: { value: AccentColor; label: string }[] = [
  { value: "blue", label: "蓝" },
  { value: "green", label: "绿" },
  { value: "orange", label: "橙" },
  { value: "purple", label: "紫" },
];

/** 主题模式选项（供 UI 渲染按钮组） */
export const THEME_MODE_OPTIONS: { value: ThemeMode; label: string; icon: string }[] = [
  { value: "light", label: "亮色", icon: "light_mode" },
  { value: "dark", label: "暗色", icon: "dark_mode" },
  { value: "auto", label: "跟随系统", icon: "desktop_windows" },
];
