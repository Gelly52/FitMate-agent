package com.itgeo.fitmate.api.rag.application;

import com.itgeo.fitmate.api.rag.dto.RagRetrievedChunk;
import java.util.List;

/*
 * 融合算法
 */
public interface RagFusionService {
    List<RagRetrievedChunk> fuse(List<RagRetrievedChunk> vectorHits,
                                 List<RagRetrievedChunk> keywordHits,
                                 int fusionCandidateK);
}
