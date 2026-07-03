package com.itgeo.fitmate.api.rag.application;

import com.itgeo.fitmate.api.rag.dto.RagRetrievedChunk;
import java.util.List;
import org.springframework.ai.document.Document;

public interface KeywordSearchService {
    void indexChunks(List<Document> splitDocuments);

    List<RagRetrievedChunk> search(String question, Long userId, Integer topK);
}
