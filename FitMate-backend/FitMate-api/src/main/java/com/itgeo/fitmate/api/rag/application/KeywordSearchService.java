package com.itgeo.fitmate.api.rag.application;

import com.itgeo.fitmate.api.rag.dto.RagRetrievedChunk;
import java.util.List;
import org.springframework.ai.document.Document;

public interface KeywordSearchService {
    void indexChunks(List<Document> splitDocuments);

    List<RagRetrievedChunk> search(String question, Long userId, Integer topK);

    /**
     * 删除指定 RAG 文档在关键词索引中的全部 chunk。
     *
     * @param docId RAG 文档主键
     */
    void deleteByDocument(Long docId);
}
