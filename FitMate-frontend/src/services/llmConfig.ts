// FitMate-frontend/src/services/llmConfig.ts
import doctorApi from "./doctorApi";
import type {
  LlmConfig,
  LlmModelOption,
  LlmTestResult,
} from "../types/settings";
import { DEFAULT_LLM_CONFIG } from "../types/settings";

const STORAGE_KEY = "fitmate_llm_config";

interface LlmConfigState {
  config: LlmConfig;
  models: LlmModelOption[];
}

const state: LlmConfigState = {
  config: { ...DEFAULT_LLM_CONFIG },
  models: [],
};

const subscribers: Array<() => void> = [];

function notify(): void {
  for (let i = 0; i < subscribers.length; i++) {
    try {
      subscribers[i]();
    } catch (e) {
      console.error("llmConfig subscriber error", e);
    }
  }
}

function persistLocal(): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state.config));
  } catch (e) {
    // ignore
  }
}

function restoreLocal(): void {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === "object") {
      state.config = Object.assign({}, DEFAULT_LLM_CONFIG, parsed);
    }
  } catch (e) {
    // ignore
  }
}

/** 从后端加载 LLM 配置（登录后调用） */
async function load(): Promise<void> {
  try {
    const res = await doctorApi.getLlmConfig();
    if (res && res.status === 200 && res.data) {
      state.config = Object.assign({}, DEFAULT_LLM_CONFIG, res.data);
      persistLocal();
      notify();
    }
  } catch (e) {
    console.error("加载 LLM 配置失败", e);
    restoreLocal();
  }
}

/** 获取当前配置 */
function getConfig(): LlmConfig {
  return state.config;
}

/** 获取当前模型列表 */
function getModels(): LlmModelOption[] {
  return state.models;
}

/** 保存配置（PUT 后端，apiKey 为空表示不修改） */
async function save(patch: Partial<LlmConfig>): Promise<void> {
  const merged = Object.assign({}, state.config, patch);
  // 调用方未显式提供 apiKey 时，置空以触发后端"保留原密文"分支；
  // 否则会把 state.config 里的脱敏值（如 sk-****e05f）当真实 key 加密落库，污染密文。
  if (patch.apiKey === undefined) {
    merged.apiKey = "";
  }
  const saveRes = await doctorApi.saveLlmConfig(merged);
  // 后端异常时 HTTP 仍为 200，需检查 LeeResult.status（200=成功，500=业务错误）
  if (saveRes && saveRes.status && saveRes.status !== 200) {
    throw new Error(saveRes.msg || "保存 LLM 配置失败");
  }
  // 保存成功后重新拉取脱敏配置
  try {
    const res = await doctorApi.getLlmConfig();
    if (res && res.status === 200 && res.data) {
      state.config = Object.assign({}, DEFAULT_LLM_CONFIG, res.data);
    } else {
      state.config = merged;
    }
  } catch (e) {
    state.config = merged;
  }
  persistLocal();
  notify();
}

/** 拉取模型列表（用当前配置或传入的临时配置） */
async function fetchModels(
  override?: Partial<LlmConfig>
): Promise<LlmModelOption[]> {
  const payload = override
    ? Object.assign({}, state.config, override)
    : {};
  const res = await doctorApi.listLlmModels(payload);
  if (res && res.status === 200 && Array.isArray(res.data)) {
    state.models = res.data;
    notify();
    return res.data;
  }
  return [];
}

/** 测活（用当前配置或传入的临时配置） */
async function testConnection(
  override?: Partial<LlmConfig>
): Promise<LlmTestResult> {
  const payload = override
    ? Object.assign({}, state.config, override)
    : {};
  const res = await doctorApi.testLlmConnection(payload);
  if (res && res.status === 200 && res.data) {
    return res.data as LlmTestResult;
  }
  const msg = (res as any) && (res as any).msg ? (res as any).msg : "";
  return { ok: false, error: msg || "测活失败" };
}

/** 订阅配置变更 */
function subscribe(cb: () => void): () => void {
  subscribers.push(cb);
  return function () {
    const idx = subscribers.indexOf(cb);
    if (idx >= 0) subscribers.splice(idx, 1);
  };
}

restoreLocal();

export const llmConfig = {
  load,
  getConfig,
  getModels,
  save,
  fetchModels,
  testConnection,
  subscribe,
};

export default llmConfig;
