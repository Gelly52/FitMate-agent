package com.itgeo.fitmate.api.wiki.application;

import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import java.util.List;

public interface WikiKeywordSearchService {

    /**
     * 对 wiki 页面做关键词召回。
     *
     * @param query  用户问题
     * @param userId 当前用户 ID（用于检索 GLOBAL + 该用户 USER space）
     * @param topK   返回数量
     * @return 命中的 wiki 页面列表
     */
    List<WikiPage> search(String query, Long userId, int topK);

    /**
     * 将一个 wiki 页面索引到 Redis Hash（关键词索引）。
     */
    void indexPage(WikiPage page, String scope, Long ownerUserId);
}
