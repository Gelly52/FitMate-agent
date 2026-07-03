package com.itgeo.fitmate.api.search.application;

import com.itgeo.fitmate.api.search.dto.SearchResult;
import java.util.List;

/**
 * 联网搜索服务契约（供 Agent web.search 工具使用）。
 * <p>
 * 抽象具体搜索引擎（Bing/百度等），上层只关心搜索结果。
 */
public interface WebSearchService {

    /**
     * 执行一次联网搜索。
     *
     * @param query 搜索关键词
     * @return 搜索结果列表（标题/URL/摘要）
     */
    List<SearchResult> search(String query);
}
