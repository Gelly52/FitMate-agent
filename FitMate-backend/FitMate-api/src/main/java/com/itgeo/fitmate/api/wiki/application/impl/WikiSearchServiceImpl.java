package com.itgeo.fitmate.api.wiki.application.impl;

import com.itgeo.fitmate.api.rag.application.RagFusionService;
import com.itgeo.fitmate.api.rag.application.RerankService;
import com.itgeo.fitmate.api.rag.dto.RagRetrievedChunk;
import com.itgeo.fitmate.api.wiki.application.WikiKeywordSearchService;
import com.itgeo.fitmate.api.wiki.application.WikiSearchService;
import com.itgeo.fitmate.api.wiki.config.WikiProperties;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WikiSearchServiceImpl implements WikiSearchService {

    private final RedisVectorStore wikiRedisVectorStore;
    private final WikiKeywordSearchService wikiKeywordSearchService;
    private final WikiProperties wikiProperties;
    private final RagFusionService ragFusionService;
    private final RerankService rerankService;

    public WikiSearchServiceImpl(
            @Qualifier("wikiRedisVectorStore") RedisVectorStore wikiRedisVectorStore,
            WikiKeywordSearchService wikiKeywordSearchService,
            WikiProperties wikiProperties,
            RagFusionService ragFusionService,
            RerankService rerankService) {
        this.wikiRedisVectorStore = wikiRedisVectorStore;
        this.wikiKeywordSearchService = wikiKeywordSearchService;
        this.wikiProperties = wikiProperties;
        this.ragFusionService = ragFusionService;
        this.rerankService = rerankService;
    }

    @Override
    public List<WikiPage> search(String question, Long userId, int topK) {
        int vectorRecallK = wikiProperties.getRetrieval().getVectorRecallK();
        int keywordRecallK = wikiProperties.getRetrieval().getKeywordRecallK();

        // 1. 向量召回
        // filter 表达式：scope == 'GLOBAL' || ownerUserId == '{userId}'
        String filterExpr = String.format("scope == 'GLOBAL' || ownerUserId == '%s'", userId);
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(vectorRecallK)
                .filterExpression(filterExpr)
                .build();
        List<Document> vectorHits = wikiRedisVectorStore.similaritySearch(request);
        log.debug("Wiki 向量召回 {} 条", vectorHits == null ? 0 : vectorHits.size());

        // 2. 关键词召回
        List<WikiPage> keywordHits = wikiKeywordSearchService.search(question, userId, keywordRecallK);
        log.debug("Wiki 关键词召回 {} 条", keywordHits.size());

        // 3. 合并去重（按 pageId）并转为 RagRetrievedChunk（以 pageId 作为 chunkId 便于 RRF 去重）
        Map<Long, WikiPage> pageMap = new HashMap<>();
        List<RagRetrievedChunk> vectorChunks = new ArrayList<>();
        List<RagRetrievedChunk> keywordChunks = new ArrayList<>();

        if (vectorHits != null) {
            for (Document doc : vectorHits) {
                Long pageId = parseLong(doc.getMetadata().get("pageId"));
                if (pageId == null) continue;
                WikiPage page = new WikiPage();
                page.setId(pageId);
                page.setContentMd(doc.getText());
                Object titleObj = doc.getMetadata().getOrDefault("title", "");
                page.setTitle(titleObj == null ? "" : String.valueOf(titleObj));
                page.setSpaceId(parseLong(doc.getMetadata().get("spaceId")));
                pageMap.put(pageId, page);
                vectorChunks.add(toChunk(doc, pageId, "vector"));
            }
        }
        for (WikiPage page : keywordHits) {
            pageMap.putIfAbsent(page.getId(), page);
            keywordChunks.add(toChunkFromPage(page, "keyword"));
        }

        if (pageMap.isEmpty()) return List.of();

        // 4. RRF 融合（复用现有 RagFusionService，分别传向量/关键词命中）
        List<RagRetrievedChunk> fused = ragFusionService.fuse(
                vectorChunks,
                keywordChunks,
                wikiProperties.getRetrieval().getDefaultTopK()
        );

        // 5. 可选 rerank
        if (Boolean.TRUE.equals(wikiProperties.getRetrieval().getRerankEnabled()) && !fused.isEmpty()) {
            fused = rerankService.rerank(question, fused, topK);
        } else {
            fused = fused.stream().limit(topK).collect(Collectors.toList());
        }

        // 6. 转回 WikiPage（从 chunk.document.metadata 取 pageId 回查 pageMap）
        List<WikiPage> result = new ArrayList<>();
        for (RagRetrievedChunk chunk : fused) {
            Document doc = chunk.getDocument();
            if (doc == null) continue;
            Long pageId = parseLong(doc.getMetadata().get("pageId"));
            if (pageId != null && pageMap.containsKey(pageId)) {
                result.add(pageMap.get(pageId));
            }
        }
        return result;
    }

    private Long parseLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将向量召回的 Spring AI Document 包装为 RagRetrievedChunk。
     * 注意：RagRetrievedChunk 本身没有 text/metadata 字段，文本与元数据通过 document 字段携带。
     */
    private RagRetrievedChunk toChunk(Document doc, Long pageId, String source) {
        Map<String, Object> meta = new HashMap<>(doc.getMetadata());
        meta.put("recallSource", source);
        Document wrapped = new Document(doc.getText(), meta);
        RagRetrievedChunk chunk = new RagRetrievedChunk();
        chunk.setChunkId(String.valueOf(pageId));
        chunk.setDocument(wrapped);
        return chunk;
    }

    /**
     * 将关键词召回的 WikiPage 包装为 RagRetrievedChunk。
     */
    private RagRetrievedChunk toChunkFromPage(WikiPage page, String source) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("pageId", page.getId());
        meta.put("spaceId", page.getSpaceId());
        meta.put("title", page.getTitle());
        meta.put("recallSource", source);
        Document doc = new Document(page.getContentMd() == null ? "" : page.getContentMd(), meta);
        RagRetrievedChunk chunk = new RagRetrievedChunk();
        chunk.setChunkId(String.valueOf(page.getId()));
        chunk.setDocument(doc);
        return chunk;
    }
}
