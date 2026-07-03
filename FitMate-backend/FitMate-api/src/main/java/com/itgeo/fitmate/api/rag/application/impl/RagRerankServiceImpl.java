package com.itgeo.fitmate.api.rag.application.impl;

import com.itgeo.fitmate.api.rag.application.RerankService;
import com.itgeo.fitmate.api.rag.config.RagEmbeddingProperties;
import com.itgeo.fitmate.api.rag.dto.RagRetrievedChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RagRerankServiceImpl implements RerankService {

    private static final Pattern LATIN_OR_DIGIT_TOKEN_PATTERN = Pattern.compile("[a-z0-9]{2,}");
    private static final Pattern CHINESE_BLOCK_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,}");
    private static final double PHRASE_MATCH_BOOST = 0.15D;

    private final RagEmbeddingProperties ragEmbeddingProperties;

    @Override
    public List<RagRetrievedChunk> rerank(String query, List<RagRetrievedChunk> candidates, int finalTopK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        int safeTopK = finalTopK <= 0 ? candidates.size() : finalTopK;
        List<RagRetrievedChunk> reranked = new ArrayList<>();

        for (RagRetrievedChunk item : candidates) {
            if (item == null) {
                continue;
            }

            boolean dualHit = isDualHit(item);
            double queryCoverageScore = calculateQueryCoverageScore(query, extractChunkText(item));
            double rerankScore = calculateRerankScore(query, item);

            item.setDualHit(dualHit);
            item.setQueryCoverageScore(queryCoverageScore);
            item.setRerankScore(rerankScore);
            reranked.add(item);
        }

        reranked.sort(
                Comparator.comparingDouble((RagRetrievedChunk item) -> safeScore(item.getRerankScore())).reversed()
                        .thenComparing(Comparator.comparingDouble(
                                (RagRetrievedChunk item) -> safeScore(item.getFusionScore())
                        ).reversed())
                        .thenComparingInt(item -> Boolean.TRUE.equals(item.getDualHit()) ? 0 : 1)
                        .thenComparingInt(this::bestOriginalRank)
                        .thenComparingInt(item -> item.getChunkSeq() == null ? Integer.MAX_VALUE : item.getChunkSeq())
        );

        List<RagRetrievedChunk> finalHits = reranked.stream()
                .limit(safeTopK)
                .collect(Collectors.toList());

        for (int i = 0; i < finalHits.size(); i++) {
            finalHits.get(i).setRerankRank(i + 1);
        }

        log.info("RAG rerank完成, candidateCount={}, finalTopK={}, resultCount={}, query={}",
                candidates.size(), safeTopK, finalHits.size(), query);

        return finalHits;
    }

    // 1. 主打分方法
    private double calculateRerankScore(String query, RagRetrievedChunk item){
        double fusionScore = safeScore(item.getFusionScore());
        boolean dualHit = isDualHit(item);
        double queryCoverageScore = calculateQueryCoverageScore(query, extractChunkText(item));

        Double fusionWeightConfig = ragEmbeddingProperties.getRetrieval().getRerankFusionWeight();
        Double dualHitBoostConfig = ragEmbeddingProperties.getRetrieval().getRerankDualHitBoost();
        Double queryCoverageWeightConfig = ragEmbeddingProperties.getRetrieval().getRerankQueryCoverageWeight();

        double fusionWeight = fusionWeightConfig == null ? 1.0D : fusionWeightConfig;
        double dualHitBoost = dualHitBoostConfig == null ? 0.15D : dualHitBoostConfig;
        double queryCoverageWeight = queryCoverageWeightConfig == null ? 0.35D : queryCoverageWeightConfig;

        double score = 0D;
        score += fusionScore * fusionWeight;
        score += queryCoverageScore * queryCoverageWeight;
        if (dualHit) {
            score += dualHitBoost;
        }
        return score;
    }

    // 2. 判断是否双路命中
    private boolean isDualHit(RagRetrievedChunk item){
        return item != null
                && item.getVectorRank() != null
                && item.getKeywordRank() != null;
    }

    // 3. 计算 query 覆盖率
    private double calculateQueryCoverageScore(String query, String content){
        if (query == null || query.isBlank() || content == null || content.isBlank()) {
            return 0D;
        }

        List<String> queryTokens = extractQueryTokens(query);
        if (queryTokens.isEmpty()) {
            return 0D;
        }

        String normalizedContent = normalizeText(content);
        long matchedCount = queryTokens.stream()
                .filter(normalizedContent::contains)
                .count();

        double tokenCoverage = (double) matchedCount / queryTokens.size();

        String normalizedQuery = normalizeText(query);
        double phraseBoost = normalizedQuery.isBlank() || !normalizedContent.contains(normalizedQuery)
                ? 0D
                : PHRASE_MATCH_BOOST;

        return Math.min(1.0D, tokenCoverage + phraseBoost);
    }

    // 4. query 归一化
    private String normalizeText(String text){
        if (text == null) {
            return "";
        }

        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}，。！？；：、“”‘’（）【】《》、·…—]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // 5. query token 提取
    private List<String> extractQueryTokens(String query){
        String normalizedQuery = normalizeText(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> tokens = new LinkedHashSet<>();

        Matcher latinMatcher = LATIN_OR_DIGIT_TOKEN_PATTERN.matcher(normalizedQuery);
        while (latinMatcher.find()) {
            tokens.add(latinMatcher.group());
        }

        Matcher chineseBlockMatcher = CHINESE_BLOCK_PATTERN.matcher(normalizedQuery);
        while (chineseBlockMatcher.find()) {
            String block = chineseBlockMatcher.group();
            if (block.length() == 2) {
                tokens.add(block);
                continue;
            }
            for (int i = 0; i < block.length() - 1; i++) {
                tokens.add(block.substring(i, i + 2));
            }
        }

        if (tokens.isEmpty()) {
            tokens.add(normalizedQuery);
        }

        return new ArrayList<>(tokens);
    }

    private String extractChunkText(RagRetrievedChunk item) {
        if (item == null || item.getDocument() == null || item.getDocument().getText() == null) {
            return "";
        }
        return item.getDocument().getText();
    }

    private double safeScore(Double value) {
        return value == null ? 0D : value;
    }

    private int bestOriginalRank(RagRetrievedChunk item) {
        int vectorRank = item.getVectorRank() == null ? Integer.MAX_VALUE : item.getVectorRank();
        int keywordRank = item.getKeywordRank() == null ? Integer.MAX_VALUE : item.getKeywordRank();
        return Math.min(vectorRank, keywordRank);
    }
}
