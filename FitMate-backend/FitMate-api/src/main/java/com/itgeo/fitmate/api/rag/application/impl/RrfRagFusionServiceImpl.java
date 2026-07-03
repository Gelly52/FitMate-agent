package com.itgeo.fitmate.api.rag.application.impl;

import com.itgeo.fitmate.api.rag.application.RagFusionService;
import com.itgeo.fitmate.api.rag.config.RagEmbeddingProperties;
import com.itgeo.fitmate.api.rag.dto.RagRetrievedChunk;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RrfRagFusionServiceImpl implements RagFusionService {

    private final RagEmbeddingProperties ragEmbeddingProperties;

    @Override
    public List<RagRetrievedChunk> fuse(List<RagRetrievedChunk> vectorHits, List<RagRetrievedChunk> keywordHits, int topK) {

        int rrfK = ragEmbeddingProperties.getRetrieval().getRrfK();
        double vectorWeight = ragEmbeddingProperties.getRetrieval().getVectorWeight();
        double keywordWeight = ragEmbeddingProperties.getRetrieval().getKeywordWeight();

        Map<String, RagRetrievedChunk> merged = new LinkedHashMap<>();

        if (vectorHits != null) {
            for (int i = 0; i < vectorHits.size(); i++) {
                RagRetrievedChunk item = vectorHits.get(i);
                item.setVectorRank(i + 1);

                double score = vectorWeight / (rrfK + i + 1);
                item.setFusionScore(score);

                merged.put(item.getChunkId(), item);
            }
        }

        if (keywordHits != null) {
            for (int i = 0; i < keywordHits.size(); i++) {
                RagRetrievedChunk item = keywordHits.get(i);
                item.setKeywordRank(i + 1);

                double score = keywordWeight / (rrfK + i + 1);

                RagRetrievedChunk existing = merged.get(item.getChunkId());
                if (existing == null) {
                    item.setFusionScore(score);
                    merged.put(item.getChunkId(), item);
                } else {
                    existing.setKeywordRank(i + 1);
                    existing.setFusionScore(
                            (existing.getFusionScore() == null ? 0D : existing.getFusionScore()) + score
                    );
                }
            }
        }

        return merged.values().stream()
                .sorted(Comparator.comparing(RagRetrievedChunk::getFusionScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }
}
