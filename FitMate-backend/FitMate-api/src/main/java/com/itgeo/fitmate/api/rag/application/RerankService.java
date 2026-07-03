package com.itgeo.fitmate.api.rag.application;

import com.itgeo.fitmate.api.rag.dto.RagRetrievedChunk;
import java.util.List;

public interface RerankService {
    List<RagRetrievedChunk> rerank(String query, List<RagRetrievedChunk> candidates, int finalTopK);
}
