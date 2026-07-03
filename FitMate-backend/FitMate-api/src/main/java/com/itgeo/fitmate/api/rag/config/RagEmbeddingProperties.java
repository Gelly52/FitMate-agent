package com.itgeo.fitmate.api.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
/**
 * RAG Embedding、向量索引与分块策略的统一配置入口。
 *
 * 约定：
 * 1. `embedding` 负责描述向量模型提供方、服务地址与维度；
 * 2. `vectorstore` 负责描述 Redis 向量索引名称与键前缀；
 * 3. `chunking` 负责描述语义分块策略及阈值参数。
 */
public class RagEmbeddingProperties {

    /** Embedding 模型相关配置。 */
    private Embedding embedding = new Embedding();

    /** Redis VectorStore 相关配置。 */
    private Vectorstore vectorstore = new Vectorstore();

    /** 文档分块相关配置。 */
    private Chunking chunking = new Chunking();

    private Retrieval retrieval = new Retrieval();

    @Data
    /**
     * Embedding 模型配置。
     */
    public static class Embedding {
        /**
         * Embedding 提供方标识。
         * 当前实现主要区分 Spring 默认模型与 `bge-m3-http` HTTP 适配器。
         */
        private String provider = "transformers-default";

        /**
         * 外部 embedding 服务地址。
         * 当 provider 为 `bge-m3-http` 时，请求会通过 HTTP 发送到该地址。
         */
        private String serviceUrl = "http://127.0.0.1:7086";

        /** 传递给 embedding 服务的模型名称。 */
        private String modelName = "bge-m3";

        /** 向量维度配置，用于声明当前 embedding 输出长度。 */
        private Integer dimension = 1024;
    }

    @Data
    /**
     * Redis 向量存储配置。
     */
    public static class Vectorstore {
        /** 用于区分不同 embedding 方案的 Redis 索引名称。 */
        private String indexName = "lee-vectorstore";

        /** 用于隔离不同向量命名空间的 Redis 键前缀。 */
        private String prefix = "embedding:";
    }

    @Data
    /**
     * 语义分块配置。
     */
    public static class Chunking {
        /**
         * 分块策略标识。
         * 当前支持 `token` 与 `semantic`，默认走语义分块。
         */
        private String strategy = "semantic";

        /** 相邻句向量相似度高于该阈值时，继续合并到同一个语义 chunk。 */
        private Double mergeThreshold = 0.82D;

        /** 相似度相对上一对句子突降超过该阈值时，视为新的语义断点。 */
        private Double breakpointDropThreshold = 0.12D;

        /** 单个语义 chunk 允许包含的最大句子数。 */
        private Integer maxChunkSentenceCount = 8;

        /** 单个语义 chunk 允许包含的最大字符数。 */
        private Integer maxChunkChars = 800;

        /** 句向量批量计算时的单批大小。 */
        private Integer sentenceEmbeddingBatchSize = 64;

        /** 是否把空行/段落边界视为硬切分边界。 */
        private Boolean paragraphBoundaryEnabled = true;

        /** 是否输出语义分块过程中的调试日志。 */
        private Boolean debugLogEnabled = false;
    }

    @Data
    public static class Retrieval {
        private String mode = "hybrid";
        private Integer defaultTopK = 4;
        private Integer maxTopK = 10;

        private Integer vectorRecallK = 16;
        private Integer keywordRecallK = 16;

        private Integer rrfK = 60;
        private Double vectorWeight = 1.0D;
        private Double keywordWeight = 1.0D;

        private String keywordIndexName = "rag_chunk_keyword_idx";
        private String keywordKeyPrefix = "rag:chunk:";

        private Boolean rerankEnabled = false;
        private Integer rerankCandidateK = 10;

        private Double rerankFusionWeight = 1.0D;
        private Double rerankDualHitBoost = 0.15D;
        private Double rerankQueryCoverageWeight = 0.35D;
    }

}
