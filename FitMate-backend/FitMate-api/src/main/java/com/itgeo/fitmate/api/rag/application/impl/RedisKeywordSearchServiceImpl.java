package com.itgeo.fitmate.api.rag.application.impl;

import com.itgeo.fitmate.api.rag.application.KeywordSearchService;
import com.itgeo.fitmate.api.rag.config.RagEmbeddingProperties;
import com.itgeo.fitmate.api.rag.dto.RagRetrievedChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.SearchResult;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisKeywordSearchServiceImpl implements KeywordSearchService {

    private final JedisPooled jedisPooled;
    private final RagEmbeddingProperties ragEmbeddingProperties;

    @Override
    public void indexChunks(List<Document> splitDocuments) {
        if (splitDocuments == null || splitDocuments.isEmpty()) {
            return;
        }

        String keyPrefix = ragEmbeddingProperties.getRetrieval().getKeywordKeyPrefix();

        for (Document document : splitDocuments) {
            Map<String, Object> metadata = document.getMetadata();
            String chunkId = String.valueOf(metadata.get("chunkId"));
            String key = keyPrefix + chunkId;

            jedisPooled.hset(key, Map.of(
                    "chunkId", String.valueOf(metadata.get("chunkId")),
                    "documentId", String.valueOf(metadata.get("documentId")),
                    "chunkSeq", String.valueOf(metadata.get("chunkSeq")),
                    "userId", String.valueOf(metadata.get("userId")),
                    "fileName", String.valueOf(metadata.get("fileName")),
                    "source", String.valueOf(metadata.getOrDefault("source", "")),
                    "content", document.getText() == null ? "" : document.getText()
            ));
        }

    }

    @Override
    public List<RagRetrievedChunk> search(String question, Long userId, Integer topK) {
        if (topK <= 0) {
            return List.of();
        }

        if (question == null || question.isBlank() || userId == null) {
            return List.of();
        }

        String indexName = ragEmbeddingProperties.getRetrieval().getKeywordIndexName();
        int safeTopK = topK == null || topK <= 0 ? 10 : topK;

        String escapedQuestion = question.trim();
        String queryText = "(@userId:{" + userId + "}) " + escapedQuestion;

        Query query = new Query(queryText)
                .limit(0, safeTopK);

        SearchResult result = jedisPooled.ftSearch(indexName, query);

        List<RagRetrievedChunk> hits = new ArrayList<>();
        int rank = 1;

        for (redis.clients.jedis.search.Document redisDoc : result.getDocuments()) {
            RagRetrievedChunk item = new RagRetrievedChunk();
            item.setChunkId(String.valueOf(redisDoc.get("chunkId")));
            item.setDocumentId(String.valueOf(redisDoc.get("documentId")));
            item.setChunkSeq(parseInt(redisDoc.get("chunkSeq")));
            item.setFileName(String.valueOf(redisDoc.get("fileName")));
            item.setUserId(String.valueOf(redisDoc.get("userId")));
            item.setKeywordRank(rank++);
            item.setDocument(new Document(
                    String.valueOf(redisDoc.get("content")),
                    Map.of(
                            "chunkId", String.valueOf(redisDoc.get("chunkId")),
                            "documentId", String.valueOf(redisDoc.get("documentId")),
                            "chunkSeq", String.valueOf(redisDoc.get("chunkSeq")),
                            "fileName", String.valueOf(redisDoc.get("fileName")),
                            "userId", String.valueOf(redisDoc.get("userId")),
                            "source", String.valueOf(redisDoc.get("source"))
                    )
            ));
            hits.add(item);
        }

        return hits;
    }

    private Integer parseInt(Object value) {
        if (value == null) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
