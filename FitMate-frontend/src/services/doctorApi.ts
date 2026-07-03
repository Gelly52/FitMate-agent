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

const doctorApi = {
  sendUserCode,
  userLogin,
  userLogout,
  createSseTicket,
  getRecords,
  uploadRagDoc,
  agentExecute,
  getAgentRuns,
  getAgentRunDetail,
  compressContext,
  cancelAgent,
  rollbackMessage,
  ragConfig,
  benchmarkEvaluate,
  logTraining,
  logBodyMetrics,
  getRecentTraining,
  getRecentMetrics,
  getUploadedDocs,
  getUserProfile,
  updateUserProfile,
  getUserPreferences,
  saveUserPreferences,
  getLlmConfig,
  saveLlmConfig,
  listLlmModels,
  testLlmConnection,
  getLlmBalance,
};

if (typeof window !== "undefined") {
  window.doctorApi = doctorApi;
}

export default doctorApi;
