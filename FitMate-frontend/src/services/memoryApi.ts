import instance from "./http";
import type { MemoryListResponse, ProfileResponse } from "../types/memory";

// 后端响应拦截器已返回 response.data（即 LeeResult body: {status, msg, data, ok}）
// 以下函数返回的是整个 LeeResult body，调用方需取 .data 字段获取业务数据

export function listMemories(type?: string, page = 1, size = 20) {
  return instance({
    url: "/memory/list",
    method: "get",
    params: { type, page, size },
  });
}

export function deleteMemory(id: number) {
  return instance({
    url: `/memory/${id}`,
    method: "delete",
  });
}

export function deleteAllMemories() {
  return instance({
    url: "/memory/all",
    method: "delete",
  });
}

export function getMemoryProfile() {
  return instance({
    url: "/memory/profile",
    method: "get",
  });
}

export function rebuildMemoryProfile() {
  return instance({
    url: "/memory/profile/rebuild",
    method: "post",
  });
}

const memoryApi = {
  list: listMemories,
  delete: deleteMemory,
  deleteAll: deleteAllMemories,
  getProfile: getMemoryProfile,
  rebuildProfile: rebuildMemoryProfile,
};

export default memoryApi;
