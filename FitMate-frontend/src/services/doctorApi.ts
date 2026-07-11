import instance from "./http";
import type {
  AgentExecuteAck,
  AgentRunDetail,
} from "../types/agent";

export type AgentExecuteRequest = Record<string, unknown>;

export type AgentRunListParams = {
  status?: string;
  limit?: number;
  [key: string]: unknown;
};

export type AgentRunListItem = AgentRunDetail & {
  createdAt?: string;
  startedAt?: string;
  finishedAt?: string;
};

export function sendUserCode(bo) {
  return instance({
    url: "/user/code",
    method: "post",
    data: bo,
  });
}

export function userLogin(bo) {
  return instance({
    url: "/user/login",
    method: "post",
    data: bo,
  });
}

export function checkEmailRegistered(email) {
  return instance({
    url: "/user/check-email",
    method: "get",
    params: { email: email },
  });
}

export function userLogout() {
  return instance({
    url: "/user/logout",
    method: "post",
  });
}

export function createSseTicket() {
  return instance({
    url: "/user/sse-ticket",
    method: "post",
  });
}

export function getRecords(who, sessionId, limit) {
  return instance({
    url: "/chat/records",
    method: "get",
    params: {
      who,
      sessionId,
      limit,
    },
  });
}

export function getThinkingByMessageId(messageId) {
  return instance({
    url: "/chat/thinking/" + messageId,
    method: "get",
  });
}

export function uploadRagDoc(formData) {
  return instance({
    url: "/rag/uploadRagDoc",
    method: "post",
    data: formData,
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });
}

export function agentExecute(bo: AgentExecuteRequest): Promise<AgentExecuteAck | unknown> {
  return instance({
    url: "/agent/execute",
    method: "post",
    data: bo,
  });
}

export function getAgentRuns(params?: AgentRunListParams): Promise<AgentRunListItem[] | unknown> {
  return instance({
    url: "/agent/runs",
    method: "get",
    params: params || {},
  });
}

export function getAgentRunDetail(runId: number | string): Promise<AgentRunDetail | unknown> {
  return instance({
    url: "/agent/runs/" + encodeURIComponent(runId),
    method: "get",
  });
}

/**
 * 按 botMsgId 查询 Agent run 详情（含 step 列表）。
 * 用于历史会话消息二次加载执行轨迹：展开历史 bot 消息思考过程时，
 * 通过 botMsgId 反查关联的 AgentRun，补齐 step 列表以还原本轮执行链路。
 */
export function getAgentRunDetailByBotMsgId(botMsgId: string | number): Promise<AgentRunDetail | unknown> {
  return instance({
    url: "/agent/runs/by-bot-msg/" + encodeURIComponent(botMsgId),
    method: "get",
  });
}

export function compressContext(sessionId: number | string) {
  return instance({
    url: "/agent/sessions/" + encodeURIComponent(sessionId) + "/compress",
    method: "post",
  });
}

export function cancelAgent(runId: number | string) {
  return instance({
    url: "/agent/cancel",
    method: "post",
    params: { runId },
  });
}

export function rollbackMessage(sessionId: number | string, botMsgId: string) {
  return instance({
    url: "/chat/rollback",
    method: "post",
    data: { sessionId, botMsgId },
  });
}

/**
 * 删除指定会话及其全部关联数据（消息、思考内容、上下文压缩摘要、AgentRun/Step）。
 * 删除当前活动会话后，前端应清空聊天界面并回到"新建会话"状态。
 */
export function deleteChatSession(sessionId: number | string) {
  return instance({
    url: "/chat/sessions/" + encodeURIComponent(sessionId),
    method: "delete",
  });
}

/**
 * 重命名指定会话标题。
 */
export function renameChatSession(sessionId: number | string, title: string) {
  return instance({
    url: "/chat/sessions/" + encodeURIComponent(sessionId) + "/title",
    method: "put",
    data: { title },
  });
}

export function ragConfig(bo) {
  return instance({
    url: "/rag/config",
    method: "post",
    data: bo,
  });
}

export function benchmarkEvaluate(bo) {
  return instance({
    url: "/rag/benchmark/evaluate",
    method: "post",
    data: bo,
  });
}

export function logTraining(bo) {
  return instance({
    url: "/training/log",
    method: "post",
    data: bo,
  });
}

export function logBodyMetrics(bo) {
  return instance({
    url: "/body-metrics/log",
    method: "post",
    data: bo,
  });
}

export function getRecentTraining(limit) {
  return instance({
    url: "/training/recent?limit=" + (limit || 5),
    method: "get",
  });
}

export function getRecentMetrics(limit) {
  return instance({
    url: "/body-metrics/recent?limit=" + (limit || 5),
    method: "get",
  });
}

export function getUploadedDocs() {
  return instance({
    url: "/rag/docs",
    method: "get",
  });
}

export function deleteRagDoc(docId: string) {
  return instance({
    url: "/rag/docs/" + encodeURIComponent(docId),
    method: "delete",
  });
}

export function getUserProfile() {
  return instance({
    url: "/user/profile",
    method: "get",
  });
}

export function updateUserProfile(bo) {
  return instance({
    url: "/user/profile",
    method: "put",
    data: bo,
  });
}

export function getUserPreferences() {
  return instance({
    url: "/user/preferences",
    method: "get",
  });
}

export function saveUserPreferences(bo) {
  return instance({
    url: "/user/preferences",
    method: "put",
    data: bo,
  });
}

export function getLlmConfig() {
  return instance({
    url: "/user/llm-config",
    method: "get",
  });
}

export function saveLlmConfig(bo) {
  return instance({
    url: "/user/llm-config",
    method: "put",
    data: bo,
  });
}

export function resetLlmConfig() {
  return instance({
    url: "/user/llm-config",
    method: "delete",
  });
}

export function listLlmModels(bo) {
  return instance({
    url: "/user/llm/models",
    method: "post",
    data: bo || {},
  });
}

export function testLlmConnection(bo) {
  return instance({
    url: "/user/llm/test",
    method: "post",
    data: bo || {},
  });
}

export function getLlmBalance(bo) {
  return instance({
    url: "/user/llm/balance",
    method: "post",
    data: bo || {},
  });
}

export function getMcpConfig() {
  return instance({
    url: "/user/mcp-config",
    method: "get",
  });
}

export function saveMcpConfig(bo) {
  return instance({
    url: "/user/mcp-config",
    method: "put",
    data: bo,
  });
}

export function testMcpConnection(bo) {
  return instance({
    url: "/user/mcp/test",
    method: "post",
    data: bo,
  });
}

export function getSkillList() {
  return instance({
    url: "/skill/list",
    method: "get",
  });
}

// ========== 有氧训练 ==========
export function logCardio(bo) {
  return instance({ url: "/cardio/log", method: "post", data: bo });
}
export function getRecentCardio(limit) {
  return instance({ url: "/cardio/recent?limit=" + (limit || 10), method: "get" });
}

// ========== 心率 ==========
export function logHeartRate(bo) {
  return instance({ url: "/heart-rate/log", method: "post", data: bo });
}
export function getRecentHeartRate(limit) {
  return instance({ url: "/heart-rate/recent?limit=" + (limit || 10), method: "get" });
}

// ========== 饮食 ==========
export function logDiet(bo) {
  return instance({ url: "/diet/log", method: "post", data: bo });
}
export function getRecentDiet(limit) {
  return instance({ url: "/diet/recent?limit=" + (limit || 10), method: "get" });
}

// ========== 派生指标 ==========
export function getTrainingSummary() {
  return instance({ url: "/training/summary", method: "get" });
}
export function getBodyMetricsSummary() {
  return instance({ url: "/body-metrics/summary", method: "get" });
}

const doctorApi = {
  sendUserCode,
  userLogin,
  checkEmailRegistered,
  userLogout,
  createSseTicket,
  getRecords,
  getThinkingByMessageId,
  uploadRagDoc,
  agentExecute,
  getAgentRuns,
  getAgentRunDetail,
  getAgentRunDetailByBotMsgId,
  compressContext,
  cancelAgent,
  rollbackMessage,
  deleteChatSession,
  renameChatSession,
  ragConfig,
  benchmarkEvaluate,
  logTraining,
  logBodyMetrics,
  getRecentTraining,
  getRecentMetrics,
  getUploadedDocs,
  deleteRagDoc,
  getUserProfile,
  updateUserProfile,
  getUserPreferences,
  saveUserPreferences,
  getLlmConfig,
  saveLlmConfig,
  resetLlmConfig,
  listLlmModels,
  testLlmConnection,
  getLlmBalance,
  getMcpConfig,
  saveMcpConfig,
  testMcpConnection,
  getSkillList,
  logCardio,
  getRecentCardio,
  logHeartRate,
  getRecentHeartRate,
  logDiet,
  getRecentDiet,
  getTrainingSummary,
  getBodyMetricsSummary,
};

if (typeof window !== "undefined") {
  window.doctorApi = doctorApi;
}

export default doctorApi;
