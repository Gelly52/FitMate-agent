package com.itgeo.fitmate.api.rag.application.impl;

import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.rag.application.DocumentService;
import com.itgeo.fitmate.api.rag.application.RagBenchmarkService;
import com.itgeo.fitmate.api.rag.dto.RagBenchmarkEvaluateRequest;
import com.itgeo.fitmate.api.rag.dto.RagBenchmarkEvaluateResponse;
import com.itgeo.fitmate.api.rag.dto.RagBenchmarkQuestionRequest;
import com.itgeo.fitmate.api.rag.dto.RagBenchmarkQuestionResultResponse;
import com.itgeo.fitmate.api.rag.dto.RagConfigResponse;
import com.itgeo.fitmate.api.rag.dto.RagRetrievedChunk;
import com.itgeo.fitmate.api.rag.dto.RagSearchResult;
import com.itgeo.fitmate.api.rag.infrastructure.entity.RagBenchmarkRun;
import com.itgeo.fitmate.api.rag.infrastructure.mapper.RagBenchmarkRunMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

/**
 * RAG benchmark 服务实现。
 *
 * 当前评测聚焦“文件级检索命中”：判断目标文件是否出现在 topK 召回文件列表中，
 * 不评估 chunk recall、NDCG、答案质量或 LLM 裁判结果。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagBenchmarkServiceImpl implements RagBenchmarkService {

    private static final int MAX_QUESTION_COUNT = 20;
    private static final int TOP_HIT_PREVIEW_MAX_LENGTH = 120;

    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";

    private final DocumentService documentService;
    private final RagBenchmarkRunMapper ragBenchmarkRunMapper;

    /**
     * 执行当前用户的 RAG benchmark 评测。
     */
    @Override
    public RagBenchmarkEvaluateResponse evaluate(Long userId, RagBenchmarkEvaluateRequest request) {

        int top1HitCount = 0;
        double reciprocalRankSum = 0D;
        int firstHitFileRankSum = 0;
        int firstHitFileRankCount = 0;
        int uniqueRetrievedFileCountSum = 0;

        validateRequest(userId, request);

        String datasetName = normalizeNullableText(request.getDatasetName());
        RagConfigResponse ragConfig = requireRagConfig();
        int effectiveTopK = resolveEffectiveTopK(request.getTopK(), ragConfig);

        RagBenchmarkRun run = new RagBenchmarkRun();
        run.setUserId(userId);
        run.setDatasetName(datasetName);
        run.setQuestionCount(request.getQuestions().size());
        run.setStatus(STATUS_RUNNING);
        run.setConfigSnapshotJson(JSONUtil.toJsonStr(
                buildConfigSnapshot(request.getTopK(), effectiveTopK, ragConfig)
        ));
        ragBenchmarkRunMapper.insert(run);

        try {
            List<RagBenchmarkQuestionResultResponse> results = new ArrayList<>();
            int hitCount = 0;

            /*
             * 评测步骤：
             * 1. 逐题调用 `documentService.doSearch(...)`，拿到当前用户范围内的 topK 检索结果；
             * 2. 从结果中同时提取 chunk 级文件名列表与去重后的文件级列表；
             * 3. 用文件级列表判断目标文件是否命中，并计算首个命中文件排名等统计项；
             * 4. 汇总单题结果后，再生成整次 benchmark 的总体指标与持久化快照。
             */

            for (int i = 0; i < request.getQuestions().size(); i++) {
                RagBenchmarkQuestionRequest item = request.getQuestions().get(i);
                String expectedFileName = item.getExpectedFileName().trim();

                RagSearchResult searchResult = documentService.doSearchWithTrace(
                        item.getQuestion(),
                        userId,
                        effectiveTopK
                );
                List<Document> documents = searchResult.getFinalDocuments();
                List<RagRetrievedChunk> finalHits = searchResult.getFinalHits();
                RagRetrievedChunk top1Chunk = (finalHits == null || finalHits.isEmpty()) ? null : finalHits.get(0);

                List<String> chunkFileNames = extractChunkFileNames(documents);
                List<String> retrievedFileNames = extractRetrievedFileNames(documents);


                /*
                 * 命中语义说明：
                 * 1. `chunkFileNames` 保留原始返回顺序，主要用于观察 chunk 级重复情况；
                 * 2. `retrievedFileNames` 是去重后的文件列表，代表“本题实际召回了哪些文件”；
                 * 3. `hit`、`top1Hit`、`hitRate` 的核心判断都基于文件级列表，而不是 chunk recall、NDCG 或答案质量。
                 */
                Integer firstHitChunkRank = findRank(chunkFileNames, expectedFileName);
                Integer firstHitFileRank = findRank(retrievedFileNames, expectedFileName);

                boolean hit = firstHitFileRank != null;
                boolean top1Hit = Integer.valueOf(1).equals(firstHitFileRank);
                double reciprocalRank = firstHitFileRank == null ? 0D : 1D / firstHitFileRank;

                int retrievedChunkCount = chunkFileNames.size();
                int uniqueRetrievedFileCount = retrievedFileNames.size();
                int duplicateChunkCount = retrievedChunkCount - uniqueRetrievedFileCount;
                String top1FileName = retrievedFileNames.isEmpty() ? null : retrievedFileNames.get(0);

                if (hit) {
                    hitCount++;
                }
                if (top1Hit) {
                    top1HitCount++;
                }
                reciprocalRankSum += reciprocalRank;
                uniqueRetrievedFileCountSum += uniqueRetrievedFileCount;
                if (firstHitFileRank != null) {
                    firstHitFileRankSum += firstHitFileRank;
                    firstHitFileRankCount++;
                }

                RagBenchmarkQuestionResultResponse questionResult = new RagBenchmarkQuestionResultResponse();
                questionResult.setIndex(i + 1);
                questionResult.setQuestion(item.getQuestion());
                questionResult.setExpectedFileName(expectedFileName);
                questionResult.setHit(hit);
                questionResult.setRetrievedFileNames(retrievedFileNames);
                questionResult.setTopHitPreview(buildTopHitPreview(documents));
                questionResult.setFirstHitChunkRank(firstHitChunkRank);
                questionResult.setFirstHitFileRank(firstHitFileRank);
                questionResult.setTop1Hit(top1Hit);
                questionResult.setReciprocalRank(reciprocalRank);
                questionResult.setRetrievedChunkCount(retrievedChunkCount);
                questionResult.setUniqueRetrievedFileCount(uniqueRetrievedFileCount);
                questionResult.setDuplicateChunkCount(duplicateChunkCount);
                questionResult.setTop1FileName(top1FileName);

                questionResult.setTop1FusionScore(top1Chunk == null ? null : top1Chunk.getFusionScore());
                questionResult.setTop1RerankScore(top1Chunk == null ? null : top1Chunk.getRerankScore());
                questionResult.setTop1QueryCoverageScore(top1Chunk == null ? null : top1Chunk.getQueryCoverageScore());
                questionResult.setTop1DualHit(top1Chunk == null ? null : top1Chunk.getDualHit());

                results.add(questionResult);
            }

            /*
             * 总体指标说明：
             * 1. `hitCount` / `hitRate` 表示“题目对应目标文件是否被召回”的文件级命中统计；
             * 2. 它们不表示 chunk 级召回率，也不代表答案正确率；
             * 3. `top1HitRate`、`mrr`、`avgFirstHitFileRank` 是在文件级命中语义上的补充观测指标。
             */

            RagBenchmarkEvaluateResponse response = new RagBenchmarkEvaluateResponse();
            response.setRunId(run.getId());
            response.setDatasetName(datasetName);
            response.setQuestionCount(request.getQuestions().size());
            response.setTopK(effectiveTopK);
            response.setStatus(STATUS_SUCCESS);
            response.setHitCount(hitCount);
            response.setHitRate((double) hitCount / request.getQuestions().size());
            response.setUserIsolationEnabled(ragConfig.getUserIsolationEnabled());
            response.setIsolationStrategy(ragConfig.getIsolationStrategy());
            response.setResults(results);
            response.setTop1HitCount(top1HitCount);
            response.setTop1HitRate((double) top1HitCount / request.getQuestions().size());
            response.setMrr(reciprocalRankSum / request.getQuestions().size());
            response.setAvgUniqueRetrievedFileCount(
                    (double) uniqueRetrievedFileCountSum / request.getQuestions().size()
            );
            response.setAvgFirstHitFileRank(
                    firstHitFileRankCount == 0 ? null : (double) firstHitFileRankSum / firstHitFileRankCount
            );

            markRunSuccess(run.getId(), response);
            return response;
        } catch (Exception e) {
            markRunFailed(run.getId(), datasetName, effectiveTopK, e.getMessage());
            log.error("RAG benchmark 评测失败, userId={}, runId={}", userId, run.getId(), e);
            throw e;
        }
    }

    private void validateRequest(Long userId, RagBenchmarkEvaluateRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (request.getQuestions() == null || request.getQuestions().isEmpty()) {
            throw new IllegalArgumentException("questions不能为空");
        }
        if (request.getQuestions().size() > MAX_QUESTION_COUNT) {
            throw new IllegalArgumentException("questions最多20条");
        }

        for (int i = 0; i < request.getQuestions().size(); i++) {
            RagBenchmarkQuestionRequest item = request.getQuestions().get(i);
            int index = i + 1;

            if (item == null) {
                throw new IllegalArgumentException("questions[" + index + "]不能为空");
            }
            if (!hasText(item.getQuestion())) {
                throw new IllegalArgumentException("questions[" + index + "].question不能为空");
            }
            if (!hasText(item.getExpectedFileName())) {
                throw new IllegalArgumentException("questions[" + index + "].expectedFileName不能为空");
            }
        }
    }

    private RagConfigResponse requireRagConfig() {
        RagConfigResponse ragConfig = documentService.getRagConfig();
        if (ragConfig == null) {
            throw new IllegalArgumentException("RAG配置不存在");
        }
        return ragConfig;
    }

    private int resolveEffectiveTopK(Integer requestedTopK, RagConfigResponse ragConfig) {
        int safeDefaultTopK = (ragConfig.getDefaultTopK() == null || ragConfig.getDefaultTopK() <= 0)
                ? 4
                : ragConfig.getDefaultTopK();

        int safeMaxTopK = (ragConfig.getMaxTopK() == null || ragConfig.getMaxTopK() <= 0)
                ? safeDefaultTopK
                : Math.max(ragConfig.getMaxTopK(), safeDefaultTopK);

        if (requestedTopK == null || requestedTopK <= 0) {
            return safeDefaultTopK;
        }
        return Math.min(requestedTopK, safeMaxTopK);
    }

    private Map<String, Object> buildConfigSnapshot(Integer requestedTopK,
                                                    Integer effectiveTopK,
                                                    RagConfigResponse ragConfig) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("requestedTopK", requestedTopK);
        snapshot.put("effectiveTopK", effectiveTopK);
        snapshot.put("defaultTopK", ragConfig.getDefaultTopK());
        snapshot.put("maxTopK", ragConfig.getMaxTopK());
        snapshot.put("filterScanLimit", ragConfig.getFilterScanLimit());
        snapshot.put("userIsolationEnabled", ragConfig.getUserIsolationEnabled());
        snapshot.put("isolationStrategy", ragConfig.getIsolationStrategy());

        snapshot.put("retrievalMode", ragConfig.getRetrievalMode());
        snapshot.put("vectorRecallK", ragConfig.getVectorRecallK());
        snapshot.put("keywordRecallK", ragConfig.getKeywordRecallK());
        snapshot.put("rrfK", ragConfig.getRrfK());
        snapshot.put("vectorWeight", ragConfig.getVectorWeight());
        snapshot.put("keywordWeight", ragConfig.getKeywordWeight());
        snapshot.put("keywordIndexName", ragConfig.getKeywordIndexName());

        snapshot.put("rerankEnabled", ragConfig.getRerankEnabled());
        snapshot.put("rerankCandidateK", ragConfig.getRerankCandidateK());
        snapshot.put("rerankFusionWeight", ragConfig.getRerankFusionWeight());
        snapshot.put("rerankDualHitBoost", ragConfig.getRerankDualHitBoost());
        snapshot.put("rerankQueryCoverageWeight", ragConfig.getRerankQueryCoverageWeight());
        snapshot.put("rerankStrategy", ragConfig.getRerankStrategy());

        snapshot.put("chunkingStrategy", ragConfig.getChunkingStrategy());
        snapshot.put("mergeThreshold", ragConfig.getMergeThreshold());
        snapshot.put("breakpointDropThreshold", ragConfig.getBreakpointDropThreshold());
        snapshot.put("maxChunkSentenceCount", ragConfig.getMaxChunkSentenceCount());
        snapshot.put("maxChunkChars", ragConfig.getMaxChunkChars());
        snapshot.put("paragraphBoundaryEnabled", ragConfig.getParagraphBoundaryEnabled());
        snapshot.put("sentenceEmbeddingBatchSize", ragConfig.getSentenceEmbeddingBatchSize());
        snapshot.put("debugLogEnabled", ragConfig.getDebugLogEnabled());
        return snapshot;
    }

    private List<String> extractRetrievedFileNames(List<Document> documents) {
        LinkedHashSet<String> fileNames = new LinkedHashSet<>();
        if (documents == null) {
            return new ArrayList<>();
        }

        for (Document document : documents) {
            if (document == null || document.getMetadata() == null) {
                continue;
            }
            Object metadataFileName = document.getMetadata().get("fileName");
            if (metadataFileName == null) {
                continue;
            }
            String fileName = String.valueOf(metadataFileName);
            if (hasText(fileName)) {
                fileNames.add(fileName);
            }
        }

        return new ArrayList<>(fileNames);
    }

    private String buildTopHitPreview(List<Document> documents) {
        if (documents == null || documents.isEmpty() || documents.get(0) == null) {
            return null;
        }

        String text = documents.get(0).getText();
        if (!hasText(text)) {
            return null;
        }

        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= TOP_HIT_PREVIEW_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, TOP_HIT_PREVIEW_MAX_LENGTH) + "...";
    }

    private void markRunSuccess(Long runId, RagBenchmarkEvaluateResponse response) {
        RagBenchmarkRun update = new RagBenchmarkRun();
        update.setId(runId);
        update.setStatus(STATUS_SUCCESS);
        update.setResultJson(JSONUtil.toJsonStr(response));
        ragBenchmarkRunMapper.updateById(update);
    }

    private void markRunFailed(Long runId, String datasetName, Integer topK, String message) {
        try {
            Map<String, Object> failed = new LinkedHashMap<>();
            failed.put("status", STATUS_FAILED);
            failed.put("datasetName", datasetName);
            failed.put("topK", topK);
            failed.put("message", message);

            RagBenchmarkRun update = new RagBenchmarkRun();
            update.setId(runId);
            update.setStatus(STATUS_FAILED);
            update.setResultJson(JSONUtil.toJsonStr(failed));
            ragBenchmarkRunMapper.updateById(update);
        } catch (Exception ex) {
            log.error("更新RAG benchmark失败状态失败, runId={}", runId, ex);
        }
    }

    private boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }

    private String normalizeNullableText(String text) {
        return hasText(text) ? text.trim() : null;
    }

    private List<String> extractChunkFileNames(List<Document> documents) {
        List<String> fileNames = new ArrayList<>();
        if (documents == null) {
            return fileNames;
        }

        for (Document document : documents) {
            if (document == null || document.getMetadata() == null) {
                continue;
            }
            Object metadataFileName = document.getMetadata().get("fileName");
            if (metadataFileName == null) {
                continue;
            }
            String fileName = String.valueOf(metadataFileName).trim();
            if (!fileName.isEmpty()) {
                fileNames.add(fileName);
            }
        }
        return fileNames;
    }

    private Integer findRank(List<String> items, String target) {
        if (items == null || target == null) {
            return null;
        }
        for (int i = 0; i < items.size(); i++) {
            if (target.equals(items.get(i))) {
                return i + 1;
            }
        }
        return null;
    }
}
