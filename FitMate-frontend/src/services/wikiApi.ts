import instance from "./http";

/**
 * Wiki API 服务。
 *
 * 对应后端 WikiController，提供空间列表、页面列表、页面详情、
 * 按 slug 查单页、编译任务查询与手动重新编译能力。
 */

export interface WikiSpaceItem {
  id: string;
  scopeType: string; // GLOBAL / USER
  ownerUserId?: string;
  title: string;
  description?: string;
  status: string;
}

export interface WikiPageItem {
  id: string;
  spaceId: string;
  pageType: string; // INDEX/ENTITY/CONCEPT/SYNTHESIS/SOURCE_SUMMARY/LOG
  title: string;
  slug: string;
  contentMd?: string;
  charCount?: number;
  status?: string;
  compiledAt?: string;
  updatedAt?: string;
}

export interface WikiCompileJobItem {
  id: string;
  spaceId: string;
  triggerType: string;
  sourceDocId?: string;
  status: string; // PENDING/RUNNING/SUCCESS/FAILED
  errorMessage?: string;
  startedAt?: string;
  finishedAt?: string;
}

/** 查询可见的 Wiki 空间列表（GLOBAL + 当前用户 USER）。 */
export function getWikiSpaces(): Promise<WikiSpaceItem[] | unknown> {
  return instance({
    url: "/wiki/spaces",
    method: "get",
  });
}

/** 查询某空间下的页面列表（可选 pageType 过滤）。 */
export function getWikiPages(
  spaceId: number,
  pageType?: string
): Promise<WikiPageItem[] | unknown> {
  return instance({
    url: "/wiki/spaces/" + encodeURIComponent(spaceId) + "/pages",
    method: "get",
    params: pageType ? { pageType: pageType } : {},
  });
}

/** 按 pageId 查询单页详情。 */
export function getWikiPage(pageId: number): Promise<WikiPageItem | unknown> {
  return instance({
    url: "/wiki/pages/" + encodeURIComponent(pageId),
    method: "get",
  });
}

/** 按 slug 查询单页详情（供 wikilink 跳转使用）。 */
export function getWikiPageBySlug(
  spaceId: number,
  slug: string
): Promise<WikiPageItem | unknown> {
  return instance({
    url:
      "/wiki/spaces/" +
      encodeURIComponent(spaceId) +
      "/pages/" +
      encodeURIComponent(slug),
    method: "get",
  });
}

/** 查询编译任务状态。 */
export function getWikiCompileJob(
  jobId: number
): Promise<WikiCompileJobItem | unknown> {
  return instance({
    url: "/wiki/compile/" + encodeURIComponent(jobId),
    method: "get",
  });
}

/** 手动触发重新编译（同步执行）。 */
export function recompileWiki(jobId: number): Promise<unknown> {
  return instance({
    url: "/wiki/rebuild/" + encodeURIComponent(jobId),
    method: "post",
  });
}

/** 删除指定 Wiki 页面（校验空间归属后清理索引）。 */
export function deleteWikiPage(pageId: string): Promise<unknown> {
  return instance({
    url: "/wiki/pages/" + encodeURIComponent(pageId),
    method: "delete",
  });
}

const wikiApi = {
  getWikiSpaces,
  getWikiPages,
  getWikiPage,
  getWikiPageBySlug,
  getWikiCompileJob,
  recompileWiki,
  deleteWikiPage,
};

export default wikiApi;
