// FitMate-frontend/src/types/settings.ts

/** 主题模式：亮色 / 暗色 / 跟随系统 */
export type ThemeMode = "light" | "dark" | "auto";

/** 强调色预设 */
export type AccentColor = "blue" | "green" | "orange" | "purple" | "light" | "dark";

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
  { value: "orange", label: "黄" },
  { value: "purple", label: "红" },
  { value: "light", label: "亮色" },
  { value: "dark", label: "暗色" },
];

/** 主题模式选项（供 UI 渲染按钮组） */
export const THEME_MODE_OPTIONS: {
  value: ThemeMode;
  label: string;
  icon: string;
}[] = [
  { value: "light", label: "亮色", icon: "light_mode" },
  { value: "dark", label: "暗色", icon: "dark_mode" },
  { value: "auto", label: "跟随系统", icon: "desktop_windows" },
];

export const DEFAULT_LLM_MAX_INPUT_CONTEXT_TOKENS = 512000;
export const DEFAULT_LLM_MAX_OUTPUT_CONTEXT_TOKENS = 384000;

/** LLM 配置（前后端共享结构，GET 接口返回脱敏 apiKey） */
export interface LlmConfig {
  /** API 地址，必填，默认 https://api.deepseek.com */
  baseUrl: string;
  /** API Key，必填。GET 返回脱敏值（如 sk-****e05f），PUT 接收明文 */
  apiKey: string;
  /** 模型 ID，默认 deepseek-v4-flash */
  model: string;
  /** 输入上下文最大值（token），默认 512000（500K）。用作截断阈值与窗口展示 */
  maxInputContextTokens: number;
  /** 输出上下文最大值（token，对应 API max_tokens），默认 384000（375K） */
  maxOutputContextTokens: number;
  /** 是否启用思考模式，默认 true */
  thinkingEnabled: boolean;
  /** 推理强度，默认 high */
  reasoningEffort: "high" | "max";
  /** 是否使用系统默认配置（DB无自定义配置时为true） */
  usingDefault?: boolean;
}

/** 默认 LLM 配置（DB 无值时回退） */
export const DEFAULT_LLM_CONFIG: LlmConfig = {
  baseUrl: "https://api.deepseek.com",
  apiKey: "",
  model: "deepseek-v4-flash",
  maxInputContextTokens: DEFAULT_LLM_MAX_INPUT_CONTEXT_TOKENS,
  maxOutputContextTokens: DEFAULT_LLM_MAX_OUTPUT_CONTEXT_TOKENS,
  thinkingEnabled: true,
  reasoningEffort: "high",
  usingDefault: true,
};

/** DeepSeek 模型列表项（GET /models 返回） */
export interface LlmModelOption {
  id: string;
  ownedBy: string;
}

/** 测活结果 */
export interface LlmTestResult {
  ok: boolean;
  model?: string;
  latencyMs?: number;
  error?: string;
}

/** 单币种余额明细（DeepSeek /user/balance 返回项） */
export interface LlmBalanceInfo {
  currency: string;
  totalBalance: string;
  grantedBalance: string;
  toppedUpBalance: string;
}

/** 余额查询结果 */
export interface LlmBalanceResult {
  isAvailable: boolean;
  balanceInfos: LlmBalanceInfo[];
}

/** 单个 MCP server 配置 */
export interface McpServerConfig {
  name: string;
  url: string;
  sseEndpoint: string;
  enabled: boolean;
}

/** MCP 配置（GET/PUT /user/mcp-config 的整体结构） */
export interface McpConfigItem {
  servers: McpServerConfig[];
}

/** MCP 连接测试结果（POST /user/mcp/test 返回） */
export interface McpTestResult {
  ok: boolean;
  latencyMs: number;
  error: string;
  tools: string[];
}

/** 技能元信息（GET /skill/list 返回项） */
export interface SkillInfo {
  name: string;
  description: string;
  trigger: string;
}
