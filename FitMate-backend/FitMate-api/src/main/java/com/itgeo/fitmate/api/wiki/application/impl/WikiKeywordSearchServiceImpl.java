package com.itgeo.fitmate.api.wiki.application.impl;

import com.itgeo.fitmate.api.wiki.application.WikiKeywordSearchService;
import com.itgeo.fitmate.api.wiki.config.WikiProperties;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiPageMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;

@Service
@Slf4j
@RequiredArgsConstructor
public class WikiKeywordSearchServiceImpl implements WikiKeywordSearchService {

    private final JedisPooled jedisPooled;
    private final WikiProperties wikiProperties;
    private final WikiPageMapper wikiPageMapper;

    @Override
    public List<WikiPage> search(String query, Long userId, int topK) {
        String indexName = wikiProperties.getKeyword().getIndexName();
        // 检索 GLOBAL + 当前用户 USER space
        // RediSearch 语法：(@scope:{GLOBAL} | @ownerUserId:{userId}) {query}
        String filterExpr = String.format("(@scope:{GLOBAL} | @ownerUserId:{%d})", userId);
        String fullQuery = filterExpr + " " + escapeQuery(query);

        Query q = new Query(fullQuery).limit(0, topK);
        try {
            SearchResult result = jedisPooled.ftSearch(indexName, q);
            List<WikiPage> pages = new ArrayList<>();
            for (Document doc : result.getDocuments()) {
                String pageIdStr = (String) doc.get("pageId");
                if (pageIdStr == null) continue;
                Long pageId = Long.valueOf(pageIdStr);
                WikiPage page = wikiPageMapper.selectById(pageId);
                if (page != null) pages.add(page);
            }
            return pages;
        } catch (Exception e) {
            log.warn("Wiki 关键词检索失败，降级返回空: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void indexPage(WikiPage page, String scope, Long ownerUserId) {
        String keyPrefix = wikiProperties.getKeyword().getKeyPrefix();
        String key = keyPrefix + page.getId();
        Map<String, String> fields = new HashMap<>();
        fields.put("pageId", String.valueOf(page.getId()));
        fields.put("spaceId", String.valueOf(page.getSpaceId()));
        fields.put("pageType", page.getPageType());
        fields.put("scope", scope);
        fields.put("ownerUserId", ownerUserId == null ? "" : String.valueOf(ownerUserId));
        fields.put("title", page.getTitle() == null ? "" : page.getTitle());
        fields.put("content", page.getContentMd() == null ? "" : page.getContentMd());
        jedisPooled.hset(key, fields);
    }

    @Override
    public void deleteByPage(Long pageId) {
        if (pageId == null) {
            return;
        }
        String keyPrefix = wikiProperties.getKeyword().getKeyPrefix();
        jedisPooled.del(keyPrefix + pageId);
        log.info("Wiki 关键词索引已清理 pageId={}", pageId);
    }

    private String escapeQuery(String query) {
        if (query == null) return "";
        return query.trim().replaceAll("[\"\\\\]", " ");
    }
}
