package com.itgeo.fitmate.api.wiki.application;

import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import java.util.List;

public interface WikiSearchService {

    /**
     * Wiki 检索（向量 + 关键词 + RRF + 可选 rerank）。
     *
     * @param question 用户问题
     * @param userId   当前用户 ID
     * @param topK     返回数量
     * @return 命中的 wiki 页面列表
     */
    List<WikiPage> search(String question, Long userId, int topK);
}
