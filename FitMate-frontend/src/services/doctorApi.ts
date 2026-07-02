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

export function doChat(bo) {
  return instance({
    url: "/chat/doChat",
    method: "post",
    data: bo,
  });
}

export function ragSearch(bo) {
  return instance({
    url: "/rag/search",
    method: "post",
    data: bo,
  });
}

export function internetSearch(bo) {
  return instance({
    url: "/internet/search",
    method: "post",
    data: bo,
  });
}

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

const doctorApi = {
  doChat,
  ragSearch,
  internetSearch,
  sendUserCode,
  userLogin,
  userLogout,
  createSseTicket,
  getRecords,
  uploadRagDoc,
  agentExecute,
  getAgentRuns,
  getAgentRunDetail,
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
};

if (typeof window !== "undefined") {
  window.doctorApi = doctorApi;
}

export default doctorApi;
