package com.itgeo.fitmate.api.rag.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.rag.application.DocumentService;
import com.itgeo.fitmate.api.rag.application.KeywordSearchService;
import com.itgeo.fitmate.api.rag.application.RagFusionService;
import com.itgeo.fitmate.api.rag.application.RerankService;
import com.itgeo.fitmate.api.rag.config.RagEmbeddingProperties;
import com.itgeo.fitmate.api.rag.dto.RagConfigResponse;
import com.itgeo.fitmate.api.rag.dto.RagDocumentItem;
import com.itgeo.fitmate.api.rag.dto.RagRetrievedChunk;
import com.itgeo.fitmate.api.rag.dto.RagSearchResult;
import com.itgeo.fitmate.api.rag.infrastructure.chunking.SemanticDocumentChunker;
import com.itgeo.fitmate.api.rag.infrastructure.entity.RagDocument;
import com.itgeo.fitmate.api.rag.infrastructure.mapper.RagDocumentMapper;
import com.itgeo.fitmate.api.rag.infrastructure.parser.DocumentParser;
import com.itgeo.fitmate.api.rag.infrastructure.parser.DocumentParserFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * 文档向量化与检索服务实现。
 *
 * 说明：
 * 1. 上传时会把 fileName、userId、source 写入 metadata；
 * 2. 检索时通过 RedisVectorStore 的 filterExpression 基于 userId 做用户隔离；
 * 3. 当前仅服务手动 RAG 接口，不自动接入普通聊天与 Agent 执行链路。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final int DEFAULT_TOP_K = 4;
    private static final int MAX_TOP_K = 10;
    private static final int FILTER_SCAN_LIMIT = 50;

    private final RedisVectorStore redisVectorStore;

    private final RagDocumentMapper ragDocumentMapper;

    private final SemanticDocumentChunker semanticDocumentChunker;

    private final DocumentParserFactory documentParserFactory;

    private final RagEmbeddingProperties ragEmbeddingProperties;

    private final KeywordSearchService keywordSearchService;

    private final RagFusionService ragFusionService;

    private final RerankService rerankService;

    /**
     * 读取文本、切分语义 chunk 并写入向量库。
     */
    @Override
    public List<Document> loadText(Resource resource, String fileName, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (resource == null) {
            throw new IllegalArgumentException("上传资源不能为空");
        }

        String safeFileName = (fileName == null || fileName.isBlank()) ? "unknown" : fileName;
        if (!documentParserFactory.isSupported(safeFileName)) {
            throw new IllegalArgumentException("不支持的文件格式: " + safeFileName);
        }

        String metadataUserId = String.valueOf(userId);
        String fileType = extractExtension(safeFileName);
        String parsedText = parseToText(resource, safeFileName);

        if (parsedText == null || parsedText.isBlank()) {
            throw new IllegalArgumentException("文档解析结果为空，无法入库: " + safeFileName);
        }

        String normalizedText = normalizeText(parsedText);
        if (normalizedText.isBlank()) {
            throw new IllegalArgumentException("文档内容为空，无法入库: " + safeFileName);
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileName", safeFileName);
        metadata.put("userId", metadataUserId);
        metadata.put("source", safeFileName);
        metadata.put("fileType", fileType);

        List<Document> documentList = List.of(new Document(normalizedText, metadata));
        List<Document> splitDocuments = semanticDocumentChunker.splitDocuments(documentList);

        if (splitDocuments == null || splitDocuments.isEmpty()) {
            throw new IllegalArgumentException("文档切分后结果为空，无法入库: " + safeFileName);
        }

        RagDocument ragDocument = new RagDocument();
        ragDocument.setUserId(userId);
        ragDocument.setFileName(safeFileName);
        ragDocument.setSourceCount(documentList.size());
        ragDocument.setChunkCount(splitDocuments.size());
        ragDocument.setStatus("READY");
        ragDocumentMapper.insert(ragDocument);

        String documentId = String.valueOf(ragDocument.getId());

        for (int i = 0; i < splitDocuments.size(); i++) {
            Document document = splitDocuments.get(i);
            String chunkId = documentId + ":" + i;

            document.getMetadata().put("fileName", safeFileName);
            document.getMetadata().put("userId", metadataUserId);
            document.getMetadata().put("source", safeFileName);
            document.getMetadata().put("fileType", fileType);
            document.getMetadata().put("documentId", documentId);
            document.getMetadata().put("chunkId", chunkId);
            document.getMetadata().put("chunkSeq", i);

            // 用 chunkId 作为向量库文档 ID，使删除时可按 chunkId 精确清理
            splitDocuments.set(i, new Document(chunkId, document.getText(), document.getMetadata()));
        }

        redisVectorStore.add(splitDocuments);
        keywordSearchService.indexChunks(splitDocuments);

        log.info("RAG文档入库完成, userId={}, fileName={}, fileType={}, sourceCount={}, chunkCount={}",
                userId,
                safeFileName,
                fileType,
                documentList.size(),
                splitDocuments.size());

        return documentList;
    }

    /**
     * 删除指定 RAG 文档及其全部向量/关键词索引。
     * <p>
     * 流程：校验 userId 归属 → 拼 chunkId 列表删向量索引 → SCAN 删关键词索引 → 删 MySQL 记录。
     */
    @Override
    public void deleteDocument(Long userId, Long docId) {
        if (userId == null || docId == null) {
            throw new IllegalArgumentException("userId/docId 不能为空");
        }

        RagDocument doc = ragDocumentMapper.selectOne(
                new LambdaQueryWrapper<RagDocument>()
                        .eq(RagDocument::getId, docId)
                        .eq(RagDocument::getUserId, userId));
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在或无权操作");
        }

        int chunkCount = doc.getChunkCount() != null ? doc.getChunkCount() : 0;
        List<String> chunkIds = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            chunkIds.add(docId + ":" + i);
        }
        if (!chunkIds.isEmpty()) {
            redisVectorStore.delete(chunkIds);
        }

        keywordSearchService.deleteByDocument(docId);

        ragDocumentMapper.deleteById(docId);

        log.info("RAG 文档删除完成 userId={} docId={} fileName={} chunks={}",
                userId, docId, doc.getFileName(), chunkCount);
    }

    /**
     * 基于问题检索当前用户可见的 RAG 文档片段。
     */
    @Override
    public List<Document> doSearch(String question, Long userId, Integer topK) {
        return doSearchWithTrace(question, userId, topK).getFinalDocuments();
    }


    @Override
    public RagSearchResult doSearchWithTrace(String question, Long userId, Integer topK) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question不能为空");
        }
        int finalTopK = normalizeTopK(topK);
        int vectorRecallK = ragEmbeddingProperties.getRetrieval().getVectorRecallK();
        int keywordRecallK = ragEmbeddingProperties.getRetrieval().getKeywordRecallK();

        /*
         * 检索隔离说明：
         * 1. 这里通过 `SearchRequest.filterExpression("userId == '...'")` 把 userId 条件下推到 RedisVectorStore；
         * 2. 用户隔离发生在向量检索侧，只有满足 metadata.userId 的文档才会参与返回；
         * 3. 当前实现不是“先查全量结果，再在 Java 内存中过滤”。
         */
        boolean rerankEnabled = Boolean.TRUE.equals(ragEmbeddingProperties.getRetrieval().getRerankEnabled());
        int rerankCandidateK = normalizeRerankCandidateK(finalTopK);
        int fusionCandidateK = rerankEnabled ? rerankCandidateK : finalTopK;

        List<RagRetrievedChunk> vectorHits = vectorRecall(question, userId, vectorRecallK);
        List<RagRetrievedChunk> keywordHits = keywordSearchService.search(question, userId, keywordRecallK);
//        List<RagRetrievedChunk> fusedHits = ragFusionService.fuse(vectorHits, keywordHits, finalTopK);
        List<RagRetrievedChunk> fusedCandidates = ragFusionService.fuse(
                vectorHits,
                keywordHits,
                fusionCandidateK
        );

        List<RagRetrievedChunk> finalHits = rerankEnabled
                ? rerankService.rerank(question, fusedCandidates, finalTopK)
                : fusedCandidates.stream().limit(finalTopK).collect(Collectors.toList());


        RagSearchResult result = new RagSearchResult();
        result.setQuestion(question);
        result.setFinalTopK(finalTopK);
        result.setVectorHits(vectorHits);
        result.setKeywordHits(keywordHits);
        result.setFusedHits(fusedCandidates);
        result.setFinalDocuments(finalHits.stream().map(RagRetrievedChunk::getDocument).collect(Collectors.toList()));
        result.setRerankEnabled(rerankEnabled);
        result.setFusionCandidateK(fusionCandidateK);
        result.setFinalHits(finalHits);

        log.info("RAG混合检索完成, userId={}, finalTopK={}, fusionCandidateK={}, rerankEnabled={}, vectorRecallK={}, keywordRecallK={}, vectorHits={}, keywordHits={}, fusedCandidates={}, finalHits={}",
                userId,
                finalTopK,
                fusionCandidateK,
                rerankEnabled,
                vectorRecallK,
                keywordRecallK,
                vectorHits.size(),
                keywordHits.size(),
                fusedCandidates.size(),
                finalHits.size());
        return result;
    }

    /**
     * 查询当前用户已上传的 RAG 文档列表。
     */
    @Override
    public List<RagDocumentItem> listUserDocuments(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }

        List<RagDocument> ragDocuments = ragDocumentMapper.selectList(new LambdaQueryWrapper<RagDocument>()
                .eq(RagDocument::getUserId, userId)
                .orderByDesc(RagDocument::getCreatedAt));

        return ragDocuments.stream().map(ragDocument -> new RagDocumentItem(
                        ragDocument.getId(),
                        ragDocument.getFileName(),
                        ragDocument.getSourceCount(),
                        ragDocument.getChunkCount(),
                        ragDocument.getStatus(),
                        ragDocument.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /**
     * 返回当前代码中生效的手动 RAG 配置快照。
     *
     * 其中 `isolationStrategy` 明确表示当前采用 RedisVectorStore `filterExpression` 的检索侧过滤。
     */
    @Override
    public RagConfigResponse getRagConfig() {
        RagConfigResponse response = new RagConfigResponse();

        response.setDefaultTopK(ragEmbeddingProperties.getRetrieval().getDefaultTopK());
        response.setMaxTopK(ragEmbeddingProperties.getRetrieval().getMaxTopK());
        response.setFilterScanLimit(FILTER_SCAN_LIMIT);
        response.setUserIsolationEnabled(true);
        response.setIsolationStrategy("vectorstore_filterExpression_userId");

        response.setRetrievalMode(ragEmbeddingProperties.getRetrieval().getMode());
        response.setVectorRecallK(ragEmbeddingProperties.getRetrieval().getVectorRecallK());
        response.setKeywordRecallK(ragEmbeddingProperties.getRetrieval().getKeywordRecallK());
        response.setRrfK(ragEmbeddingProperties.getRetrieval().getRrfK());
        response.setVectorWeight(ragEmbeddingProperties.getRetrieval().getVectorWeight());
        response.setKeywordWeight(ragEmbeddingProperties.getRetrieval().getKeywordWeight());
        response.setKeywordIndexName(ragEmbeddingProperties.getRetrieval().getKeywordIndexName());

        response.setRerankEnabled(ragEmbeddingProperties.getRetrieval().getRerankEnabled());
        response.setRerankCandidateK(ragEmbeddingProperties.getRetrieval().getRerankCandidateK());
        response.setRerankFusionWeight(ragEmbeddingProperties.getRetrieval().getRerankFusionWeight());
        response.setRerankDualHitBoost(ragEmbeddingProperties.getRetrieval().getRerankDualHitBoost());
        response.setRerankQueryCoverageWeight(ragEmbeddingProperties.getRetrieval().getRerankQueryCoverageWeight());
        response.setRerankStrategy("heuristic");

        if(ragEmbeddingProperties.getChunking() != null){
            response.setChunkingStrategy(ragEmbeddingProperties.getChunking().getStrategy());
            response.setMergeThreshold(ragEmbeddingProperties.getChunking().getMergeThreshold());
            response.setBreakpointDropThreshold(ragEmbeddingProperties.getChunking().getBreakpointDropThreshold());
            response.setMaxChunkSentenceCount(ragEmbeddingProperties.getChunking().getMaxChunkSentenceCount());
            response.setMaxChunkChars(ragEmbeddingProperties.getChunking().getMaxChunkChars());
            response.setParagraphBoundaryEnabled(ragEmbeddingProperties.getChunking().getParagraphBoundaryEnabled());
            response.setSentenceEmbeddingBatchSize(ragEmbeddingProperties.getChunking().getSentenceEmbeddingBatchSize());
            response.setDebugLogEnabled(ragEmbeddingProperties.getChunking().getDebugLogEnabled());
        }
        return response;
    }

    /**
     * 规范化 topK，避免一次检索请求过大。
     */
    private int normalizeTopK(Integer topK) {
        Integer configuredDefaultTopK = ragEmbeddingProperties.getRetrieval().getDefaultTopK();
        Integer configuredMaxTopK = ragEmbeddingProperties.getRetrieval().getMaxTopK();
        int defaultTopK = (configuredDefaultTopK == null || configuredDefaultTopK <= 0)
                ? DEFAULT_TOP_K
                : configuredDefaultTopK;

        int maxTopK = (configuredMaxTopK == null || configuredMaxTopK <= 0)
                ? MAX_TOP_K
                : Math.max(configuredMaxTopK, defaultTopK);
        if (topK == null || topK <= 0) {
            return defaultTopK;
        }
        return Math.min(topK, maxTopK);
    }

    private List<RagRetrievedChunk> vectorRecall(String question, Long userId, int topK) {
        String filterExpression = "userId == '" + userId + "'";
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .filterExpression(filterExpression)
                .build();

        List<Document> results = redisVectorStore.similaritySearch(request);
        int resultSize = results == null ? 0 : results.size();
        log.info("vectorRecall完成, userId={}, topK={}, resultSize={}, question={}",
                userId, topK, resultSize, question);

        if (resultSize == 0) {
            // 用户过滤结果为空时直接返回，不执行无过滤对照检索，避免其他用户文档信息泄露到日志
            return List.of();
        }

        List<RagRetrievedChunk> hits = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            Document doc = results.get(i);
            log.info("vectorRecall hit idx={}, fileName={}, chunkId={}, preview={}",
                    i + 1,
                    doc.getMetadata().get("fileName"),
                    doc.getMetadata().get("chunkId"),
                    doc.getText() == null ? "" : doc.getText().substring(0, Math.min(80, doc.getText().length())));
            RagRetrievedChunk item = new RagRetrievedChunk();
            item.setChunkId(String.valueOf(doc.getMetadata().get("chunkId")));
            item.setDocumentId(String.valueOf(doc.getMetadata().get("documentId")));
            item.setChunkSeq(parseInteger(doc.getMetadata().get("chunkSeq")));
            item.setFileName(String.valueOf(doc.getMetadata().get("fileName")));
            item.setUserId(String.valueOf(doc.getMetadata().get("userId")));
            item.setDocument(doc);
            item.setVectorRank(i + 1);
            hits.add(item);
        }
        return hits;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private int normalizeRerankCandidateK(int finalTopK){
        Integer configured = ragEmbeddingProperties.getRetrieval().getRerankCandidateK();
        if (configured == null || configured <= 0) {
            return finalTopK;
        }
        return Math.max(configured, finalTopK);
    }

    private String parseToText(Resource resource, String fileName) {
        Path tempFile = null;
        try (InputStream inputStream = resource.getInputStream()) {
            String extension = extractExtension(fileName);
            tempFile = Files.createTempFile("rag-upload-", "." + extension);
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);

            DocumentParser parser = documentParserFactory.getParser(fileName);
            return parser.parse(tempFile.toAbsolutePath().toString());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("文档解析失败, fileName={}", fileName, e);
            throw new IllegalArgumentException("文档解析失败: " + fileName);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    log.warn("临时文件删除失败, path={}", tempFile, e);
                }
            }
        }
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            throw new IllegalArgumentException("文件名缺少扩展名: " + fileName);
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    private String normalizeText(String text) {
        return text.replace("\r\n", "\n").trim();
    }
}
