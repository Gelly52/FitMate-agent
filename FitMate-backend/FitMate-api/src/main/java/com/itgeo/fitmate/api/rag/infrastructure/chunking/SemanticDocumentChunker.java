package com.itgeo.fitmate.api.rag.infrastructure.chunking;

import com.itgeo.fitmate.api.rag.config.RagEmbeddingProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

/**
 * 语义文档分块器。
 *
 * 当前实现围绕 RAG 检索链路做语义切分：先按段落、再按句子拆分，随后基于相邻句向量相似度合并，
 * 在命中阈值/突降/上限断点时结束当前 chunk，最后对超长结果执行 overflow fallback。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SemanticDocumentChunker {

    private final EmbeddingModel embeddingModel;
    private final RagEmbeddingProperties ragEmbeddingProperties;
    private final TokenTextSplitter overflowTokenTextSplitter = new TokenTextSplitter();

    /**
     * 按当前配置对文档集合执行分块。
     *
     * 当策略不是 `semantic` 时，直接回退到 `TokenTextSplitter`。
     */
    public List<Document> splitDocuments(List<Document> sourceDocuments){
        if (sourceDocuments == null || sourceDocuments.isEmpty()) {
            return List.of();
        }

        RagEmbeddingProperties.Chunking config = ragEmbeddingProperties.getChunking();
        if (config == null || !"semantic".equalsIgnoreCase(config.getStrategy())) {
            return overflowTokenTextSplitter.apply(sourceDocuments);
        }

        List<Document> result = new ArrayList<>();
        for (Document sourceDocument : sourceDocuments) {
            result.addAll(splitSingleDocument(sourceDocument));
        }
        return result;
    }

    private List<Document> splitSingleDocument(Document sourceDocument){
        if (sourceDocument == null || sourceDocument.getText() == null || sourceDocument.getText().isBlank()) {
            return List.of();
        }

        /*
         * 语义分块主流程：
         * 1. 先按空行/段落边界做段落切分，避免跨主题段落直接混合；
         * 2. 再对每个段落做句子切分，形成更细粒度的语义单元；
         * 3. 对相邻句子批量计算向量后，按相邻句向量相似度决定是否继续合并；
         * 4. 当出现“低于合并阈值 / 相似度突降 / 句子数上限 / 字符数上限”时，视为新的语义断点；
         * 5. 对最终得到的 chunk 再执行 overflow fallback，防止单块文本过长超出后续处理能力。
         */

        List<String> paragraphs = splitParagraphs(sourceDocument.getText());
        List<String> chunkTexts = new ArrayList<>();

        for (String paragraph : paragraphs) {
            List<String> sentences = splitSentences(paragraph);
            if (sentences.isEmpty()) {
                continue;
            }

            if (sentences.size() == 1) {
                chunkTexts.addAll(applyOverflowFallback(sentences.get(0)));
                continue;
            }

            List<float[]> vectors = embedSentencesInBatch(sentences);
            List<String> mergedChunks = mergeSentencesBySemanticSimilarity(sentences, vectors);

            for (String mergedChunk : mergedChunks) {
                chunkTexts.addAll(applyOverflowFallback(mergedChunk));
            }
        }

        return toChunkDocuments(sourceDocument, chunkTexts);
    }

    /**
     * 按段落边界切分文本；若关闭段落边界配置，则整个文本视为一个段落。
     */

    private List<String> splitParagraphs(String text){
        RagEmbeddingProperties.Chunking config = ragEmbeddingProperties.getChunking();
        if (text == null || text.isBlank()) {
            return List.of();
        }

        if (config == null || Boolean.FALSE.equals(config.getParagraphBoundaryEnabled())) {
            return List.of(text.trim());
        }

        String[] parts = text.split("\\R\\s*\\R+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                result.add(part.trim());
            }
        }

        if (result.isEmpty()) {
            result.add(text.trim());
        }
        return result;
    }

    /**
     * 按中英文常见句末标点切分句子。
     */

    private List<String> splitSentences(String paragraph){
        if (paragraph == null || paragraph.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < paragraph.length(); i++) {
            char c = paragraph.charAt(i);
            current.append(c);

            if (isSentenceBoundary(c)) {
                String sentence = current.toString().trim();
                if (!sentence.isBlank()) {
                    result.add(sentence);
                }
                current.setLength(0);
            }
        }

        if (!current.toString().isBlank()) {
            result.add(current.toString().trim());
        }

        return result;
    }

    private boolean isSentenceBoundary(char c) {
        return c == '。'
                || c == '！'
                || c == '？'
                || c == '；'
                || c == '.'
                || c == '!'
                || c == '?'
                || c == ';';
    }

    /**
     * 按批次调用 EmbeddingModel，为句子列表生成向量。
     */

    private List<float[]> embedSentencesInBatch(List<String> sentences){
        if (sentences == null || sentences.isEmpty()) {
            return List.of();
        }

        int batchSize = ragEmbeddingProperties.getChunking().getSentenceEmbeddingBatchSize() == null
                ? 64
                : Math.max(1, ragEmbeddingProperties.getChunking().getSentenceEmbeddingBatchSize());

        List<float[]> result = new ArrayList<>();
        for (int start = 0; start < sentences.size(); start += batchSize) {
            int end = Math.min(start + batchSize, sentences.size());
            List<String> batch = sentences.subList(start, end);
            List<float[]> vectors = embeddingModel.embed(batch);
            result.addAll(vectors);
        }

        return result;
    }

    /**
     * 基于相邻句向量相似度合并句子，生成语义 chunk。
     *
     * 这里不是简单按固定长度切分，而是按“相邻句是否仍属于同一语义片段”决定是否继续拼接。
     */
    private List<String> mergeSentencesBySemanticSimilarity(List<String> sentences, List<float[]> vectors){
        List<String> result = new ArrayList<>();
        if (sentences == null || sentences.isEmpty()) {
            return result;
        }

        if (sentences.size() == 1) {
            result.add(sentences.get(0));
            return result;
        }

        /*
         * 合并判定要点：
         * 1. 只比较相邻句子的向量相似度，适合识别局部语义连续性；
         * 2. 若当前相似度低于 mergeThreshold，则认为主题连接变弱，应立即断开；
         * 3. 若相似度相较上一对句子出现明显突降，也会视为语义 breakpoint；
         * 4. 即使语义仍连续，只要达到句子数或字符数上限，也必须截断，避免 chunk 过大。
         */

        StringBuilder currentChunk = new StringBuilder(sentences.get(0));
        int currentChunkSentenceCount = 1;
        Double previousSimilarity = null;

        for (int i = 1; i < sentences.size(); i++) {
            double currentSimilarity = cosineSimilarity(vectors.get(i - 1), vectors.get(i));
            String nextSentence = sentences.get(i);

            int projectedChunkChars = currentChunk.length() + 1 + nextSentence.length();

            boolean shouldBreak = shouldBreak(
                    currentSimilarity,
                    previousSimilarity,
                    currentChunkSentenceCount,
                    projectedChunkChars
            );

            if (ragEmbeddingProperties.getChunking().getDebugLogEnabled()) {
                log.info("语义分块相邻句相似度, index={}, similarity={}", i, currentSimilarity);
            }

            if (shouldBreak) {
                result.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder(nextSentence);
                currentChunkSentenceCount = 1;
            } else {
                currentChunk.append("\n").append(nextSentence);
                currentChunkSentenceCount++;
            }

            previousSimilarity = currentSimilarity;
        }

        if (!currentChunk.toString().isBlank()) {
            result.add(currentChunk.toString().trim());
        }

        return result;
    }

    /**
     * 根据阈值、突降情况与大小上限判断当前 chunk 是否需要断开。
     */

    private boolean shouldBreak(
            double currentSimilarity,
            Double previousSimilarity,
            int currentChunkSentenceCount,
            int projectedChunkChars
    ){
        RagEmbeddingProperties.Chunking config = ragEmbeddingProperties.getChunking();

        double mergeThreshold = config.getMergeThreshold() == null ? 0.82D : config.getMergeThreshold();
        double breakpointDropThreshold = config.getBreakpointDropThreshold() == null ? 0.12D : config.getBreakpointDropThreshold();
        int maxChunkSentenceCount = config.getMaxChunkSentenceCount() == null ? 8 : config.getMaxChunkSentenceCount();
        int maxChunkChars = config.getMaxChunkChars() == null ? 800 : config.getMaxChunkChars();

        if (currentSimilarity < mergeThreshold) {
            return true;
        }

        if (previousSimilarity != null && (previousSimilarity - currentSimilarity) >= breakpointDropThreshold) {
            return true;
        }

        if (currentChunkSentenceCount >= maxChunkSentenceCount) {
            return true;
        }

        return projectedChunkChars > maxChunkChars;
    }

    private double cosineSimilarity(float[] left, float[] right){
        if (left == null || right == null || left.length == 0 || right.length == 0 || left.length != right.length) {
            return 0D;
        }

        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;

        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }

        if (leftNorm == 0D || rightNorm == 0D) {
            return 0D;
        }

        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }


    /**
     * 对超长语义 chunk 执行 overflow fallback。
     *
     * 当语义合并后的文本仍超过字符上限时，这里回退到 `TokenTextSplitter` 继续拆分，
     * 以保证最终写入向量库的 chunk 大小可控。
     */
    private List<String> applyOverflowFallback(String chunkText){
        if (chunkText == null || chunkText.isBlank()) {
            return List.of();
        }

        int maxChunkChars = ragEmbeddingProperties.getChunking().getMaxChunkChars() == null
                ? 800
                : ragEmbeddingProperties.getChunking().getMaxChunkChars();

        if (chunkText.length() <= maxChunkChars) {
            return List.of(chunkText.trim());
        }

        Document tempDocument = new Document(chunkText);
        List<Document> fallbackDocuments = overflowTokenTextSplitter.apply(List.of(tempDocument));

        List<String> result = new ArrayList<>();
        for (Document fallbackDocument : fallbackDocuments) {
            if (fallbackDocument != null && fallbackDocument.getText() != null && !fallbackDocument.getText().isBlank()) {
                result.add(fallbackDocument.getText().trim());
            }
        }
        return result;
    }

    /**
     * 把 chunk 文本重新包装为 Document，并继承原始 metadata。
     */


    private List<Document> toChunkDocuments(Document sourceDocument, List<String> chunkTexts){
        if (chunkTexts == null || chunkTexts.isEmpty()) {
            return List.of();
        }

        List<Document> result = new ArrayList<>();
        Map<String, Object> sourceMetadata = new HashMap<>(sourceDocument.getMetadata());

        for (String chunkText : chunkTexts) {
            if (chunkText == null || chunkText.isBlank()) {
                continue;
            }
            result.add(new Document(chunkText.trim(), new HashMap<>(sourceMetadata)));
        }

        return result;
    }

}
