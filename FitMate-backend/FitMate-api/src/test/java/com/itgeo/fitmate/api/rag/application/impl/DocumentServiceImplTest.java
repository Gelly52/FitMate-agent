package com.itgeo.fitmate.api.rag.application.impl;

import com.itgeo.fitmate.api.rag.application.KeywordSearchService;
import com.itgeo.fitmate.api.rag.application.RagFusionService;
import com.itgeo.fitmate.api.rag.application.RerankService;
import com.itgeo.fitmate.api.rag.config.RagEmbeddingProperties;
import com.itgeo.fitmate.api.rag.dto.RagRetrievedChunk;
import com.itgeo.fitmate.api.rag.dto.RagSearchResult;
import com.itgeo.fitmate.api.rag.infrastructure.chunking.SemanticDocumentChunker;
import com.itgeo.fitmate.api.rag.infrastructure.mapper.RagDocumentMapper;
import com.itgeo.fitmate.api.rag.infrastructure.parser.DocumentParserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DocumentServiceImpl 多用户隔离测试。
 * <p>
 * 重点覆盖 vectorRecall 当用户过滤结果为空时，不应执行无过滤对照检索，
 * 防止其他用户的文档信息被写入日志造成信息泄露。
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private RedisVectorStore redisVectorStore;
    @Mock
    private RagDocumentMapper ragDocumentMapper;
    @Mock
    private SemanticDocumentChunker semanticDocumentChunker;
    @Mock
    private DocumentParserFactory documentParserFactory;
    @Mock
    private RagEmbeddingProperties ragEmbeddingProperties;
    @Mock
    private KeywordSearchService keywordSearchService;
    @Mock
    private RagFusionService ragFusionService;
    @Mock
    private RerankService rerankService;

    @InjectMocks
    private DocumentServiceImpl documentService;

    private static final Long USER_A_ID = 1001L;

    @BeforeEach
    void setUp() {
        RagEmbeddingProperties.Retrieval retrieval = new RagEmbeddingProperties.Retrieval();
        retrieval.setVectorRecallK(16);
        retrieval.setKeywordRecallK(16);
        retrieval.setRerankEnabled(false);
        retrieval.setDefaultTopK(4);
        retrieval.setMaxTopK(10);
        when(ragEmbeddingProperties.getRetrieval()).thenReturn(retrieval);
    }

    /**
     * 核心安全场景：当用户过滤的向量检索结果为空时，
     * 不应执行无过滤对照检索（避免其他用户文档信息写入日志）。
     * <p>
     * 修复前：similaritySearch 会被调用 2 次（带 filter + 无 filter 对照）
     * 修复后：similaritySearch 只被调用 1 次（带 filter）
     */
    @Test
    void doSearchWithTrace_emptyVectorResult_shouldNotExecuteUnfilteredLookup() {
        // given: 带过滤的向量检索返回空
        when(redisVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(Collections.emptyList());
        // 关键词检索也返回空
        when(keywordSearchService.search(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Collections.emptyList());
        // fusion 返回空
        when(ragFusionService.fuse(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Collections.emptyList());

        // when: 用户 A 执行检索
        RagSearchResult result = documentService.doSearchWithTrace("test question", USER_A_ID, 4);

        // then: similaritySearch 只被调用一次（带 filter 的那次），不应有第二次无 filter 调用
        verify(redisVectorStore, times(1)).similaritySearch(any(SearchRequest.class));
        // 结果应为空
        assertTrue(result.getVectorHits().isEmpty());
        assertTrue(result.getFinalDocuments().isEmpty());
    }

    /**
     * 正常场景：当用户过滤的向量检索有结果时，应返回命中结果。
     */
    @Test
    void doSearchWithTrace_withVectorResult_shouldReturnHits() {
        // given: 构造一条属于 USER_A 的向量检索命中
        Document doc = new Document("content of user A doc", Map.of(
                "userId", String.valueOf(USER_A_ID),
                "fileName", "user_a_doc.pdf",
                "chunkId", "1",
                "documentId", "100",
                "chunkSeq", 1
        ));
        when(redisVectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc));
        when(keywordSearchService.search(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Collections.emptyList());
        when(ragFusionService.fuse(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());

        // when
        RagSearchResult result = documentService.doSearchWithTrace("test question", USER_A_ID, 4);

        // then: 只调用一次 similaritySearch
        verify(redisVectorStore, times(1)).similaritySearch(any(SearchRequest.class));
        assertEquals(1, result.getVectorHits().size());
    }
}
