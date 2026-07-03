package com.itgeo.fitmate.api.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 手动 RAG 配置快照响应。
 *
 * 供前端配置展示、benchmark 结果快照与联调排查使用。
 */
public class RagConfigResponse {
    /** 默认检索返回条数。 */
    private Integer defaultTopK;

    /** 允许请求的最大 topK 上限。 */
    private Integer maxTopK;

    /**
     * 历史兼容字段。
     * 当前主检索隔离已改为 `filterExpression`，前端读取该值时不应再按“扫描后内存过滤”理解。
     */
    private Integer filterScanLimit;

    /** 是否启用用户隔离。 */
    private Boolean userIsolationEnabled;

    /**
     * 当前隔离策略说明。
     * 当前实现固定为基于 `RedisVectorStore filterExpression + userId` 的检索侧过滤。
     */
    private String isolationStrategy;

    private String retrievalMode;
    private Integer vectorRecallK;
    private Integer keywordRecallK;
    private Integer rrfK;
    private Double vectorWeight;
    private Double keywordWeight;
    private String keywordIndexName;

    private Boolean rerankEnabled;
    private Integer rerankCandidateK;
    private Double rerankFusionWeight;
    private Double rerankDualHitBoost;
    private Double rerankQueryCoverageWeight;
    private String rerankStrategy;

    /** 当前分块策略：`token` / `semantic`。 */
    private String chunkingStrategy;

    /** 相邻句向量相似度高于该阈值时，继续合并到同一个语义 chunk。 */
    private Double mergeThreshold;

    /** 相似度相对上一对句子突降超过该阈值时，视为新的语义断点。 */
    private Double breakpointDropThreshold;

    /** 单个语义 chunk 允许包含的最大句子数。 */
    private Integer maxChunkSentenceCount;

    /** 单个语义 chunk 允许包含的最大字符数。 */
    private Integer maxChunkChars;

    /** 是否把空行/段落边界视为硬切分边界。 */
    private Boolean paragraphBoundaryEnabled;

    /** 句向量批量计算时的单批大小。 */
    private Integer sentenceEmbeddingBatchSize;

    /** 是否输出语义分块过程中的调试日志。 */
    private Boolean debugLogEnabled;


}
