/**
 * 思考内容 sessionStorage 缓存。
 *
 * 设计要点：
 * - Key: `fitmate:thinking-cache:{userKey}:{sessionId}:{botMsgId}`
 *   - userKey 隔离用户
 *   - sessionId 便于按会话前缀失效
 *   - botMsgId 唯一标识一条 assistant 消息（流式与历史记录都持有）
 * - TTL: 1 小时；读取时若超时则视为未命中并清理对应 key
 * - schema 版本字段 v：未来字段升级时整体失效
 *
 * 该模块无 Vue 依赖，纯函数 + sessionStorage。
 */

export interface ThinkingCacheEntry {
  v: 1;
  cachedAt: number;
  thinkingContent: string;
  thinkingSegments: any[];
  agentSteps: any[];
}

const TTL_MS = 60 * 60 * 1000; // 1 小时
const KEY_PREFIX = "fitmate:thinking-cache:";

function buildKey(userKey: string, sessionId: string | number, botMsgId: string): string {
  return KEY_PREFIX + String(userKey) + ":" + String(sessionId) + ":" + String(botMsgId);
}

function buildUserPrefix(userKey: string): string {
  return KEY_PREFIX + String(userKey) + ":";
}

function buildSessionPrefix(userKey: string, sessionId: string | number): string {
  return KEY_PREFIX + String(userKey) + ":" + String(sessionId) + ":";
}

function safeParse(raw: string | null): ThinkingCacheEntry | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object") return null;
    if (parsed.v !== 1) return null; // schema 不匹配，视为未命中
    if (typeof parsed.cachedAt !== "number") return null;
    return parsed as ThinkingCacheEntry;
  } catch (e) {
    return null;
  }
}

function isExpired(entry: ThinkingCacheEntry, now: number = Date.now()): boolean {
  return now - entry.cachedAt > TTL_MS;
}

/**
 * 读取缓存。命中且未过期则返回 entry；过期或不存在返回 null（并清理过期项）。
 */
export function getThinking(
  userKey: string,
  sessionId: string | number,
  botMsgId: string
): ThinkingCacheEntry | null {
  if (!userKey || sessionId == null || !botMsgId) return null;
  const key = buildKey(userKey, sessionId, botMsgId);
  let raw: string | null = null;
  try {
    raw = sessionStorage.getItem(key);
  } catch (e) {
    return null;
  }
  const entry = safeParse(raw);
  if (!entry) return null;
  if (isExpired(entry)) {
    try {
      sessionStorage.removeItem(key);
    } catch (e) {
      /* ignore */
    }
    return null;
  }
  return entry;
}

/**
 * 写入缓存。任意字段缺失则跳过。
 */
export function setThinking(
  userKey: string,
  sessionId: string | number,
  botMsgId: string,
  data: {
    thinkingContent: string;
    thinkingSegments: any[];
    agentSteps: any[];
  }
): void {
  if (!userKey || sessionId == null || !botMsgId) return;
  if (!data) return;
  const entry: ThinkingCacheEntry = {
    v: 1,
    cachedAt: Date.now(),
    thinkingContent: String(data.thinkingContent || ""),
    thinkingSegments: Array.isArray(data.thinkingSegments) ? data.thinkingSegments : [],
    agentSteps: Array.isArray(data.agentSteps) ? data.agentSteps : [],
  };
  const key = buildKey(userKey, sessionId, botMsgId);
  try {
    sessionStorage.setItem(key, JSON.stringify(entry));
  } catch (e) {
    // sessionStorage 满 or 不可用：静默降级
    console.warn("thinkingCache.setThinking 写入失败:", e);
  }
}

/**
 * 失效单条消息缓存。
 */
export function invalidateMessage(
  userKey: string,
  sessionId: string | number,
  botMsgId: string
): void {
  if (!userKey || sessionId == null || !botMsgId) return;
  try {
    sessionStorage.removeItem(buildKey(userKey, sessionId, botMsgId));
  } catch (e) {
    /* ignore */
  }
}

/**
 * 失效某会话下所有消息缓存。遍历该用户前缀下所有 key，删除匹配 session 前缀的项。
 */
export function invalidateSession(
  userKey: string,
  sessionId: string | number
): void {
  if (!userKey || sessionId == null) return;
  const prefix = buildSessionPrefix(userKey, sessionId);
  try {
    const keysToRemove: string[] = [];
    for (let i = 0; i < sessionStorage.length; i++) {
      const k = sessionStorage.key(i);
      if (k && k.indexOf(prefix) === 0) {
        keysToRemove.push(k);
      }
    }
    for (const k of keysToRemove) {
      sessionStorage.removeItem(k);
    }
  } catch (e) {
    /* ignore */
  }
}

/**
 * 失效当前用户的所有思考缓存（登出/换号时调用）。
 */
export function invalidateUser(userKey: string): void {
  if (!userKey) return;
  const prefix = buildUserPrefix(userKey);
  try {
    const keysToRemove: string[] = [];
    for (let i = 0; i < sessionStorage.length; i++) {
      const k = sessionStorage.key(i);
      if (k && k.indexOf(prefix) === 0) {
        keysToRemove.push(k);
      }
    }
    for (const k of keysToRemove) {
      sessionStorage.removeItem(k);
    }
  } catch (e) {
    /* ignore */
  }
}

/**
 * 清空所有用户的思考缓存（仅用于调试/强制清理）。
 */
export function clearAllThinking(): void {
  try {
    const keysToRemove: string[] = [];
    for (let i = 0; i < sessionStorage.length; i++) {
      const k = sessionStorage.key(i);
      if (k && k.indexOf(KEY_PREFIX) === 0) {
        keysToRemove.push(k);
      }
    }
    for (const k of keysToRemove) {
      sessionStorage.removeItem(k);
    }
  } catch (e) {
    /* ignore */
  }
}
