package com.itgeo.fitmate.api.rag.config;

import com.itgeo.fitmate.api.rag.infrastructure.embedding.BgeM3HttpEmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

/**
 * RAG Redis VectorStore 装配配置。
 *
 * 负责根据 `rag.embedding.provider` 选择具体的 EmbeddingModel，
 * 并创建带有显式 metadata schema 的 RedisVectorStore。
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class RagRedisVectorStoreConfig {

    private final RagEmbeddingProperties ragEmbeddingProperties;

    /**
     * 显式声明 JedisPooled 连接 Bean。
     *
     * 说明：
     * 1. 当前 VectorStore 采用自定义 `@Bean` 装配，而不是完全依赖自动配置；
     * 2. 在这种场景下，需要把 `spring.data.redis.*` 转成可注入的 `JedisPooled` Bean；
     * 3. 这样自定义的 RedisVectorStore 才能复用项目统一的 Redis 连接参数，避免启动期缺少 `JedisPooled` 依赖。
     */
    @Bean(destroyMethod = "close")
    public JedisPooled jedisPooled(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.password:}") String password) {

        HostAndPort hostAndPort = new HostAndPort(host, port);

        DefaultJedisClientConfig.Builder configBuilder = DefaultJedisClientConfig.builder();
        if (password != null && !password.isBlank()) {
            configBuilder.password(password);
        }

        return new JedisPooled(hostAndPort, configBuilder.build());
    }

    /**
     * 当使用框架默认 EmbeddingModel 时，创建对应的 RedisVectorStore。
     */

    @Bean
    @Primary
    @ConditionalOnProperty(
            prefix = "rag.embedding",
            name = "provider",
            havingValue = "transformers-default",
            matchIfMissing = true
    )
    public RedisVectorStore ragRedisVectorStoreDefault(
            JedisPooled jedisPooled,
            EmbeddingModel embeddingModel
    ) {
        log.info("使用默认 EmbeddingModel: {}", embeddingModel.getClass().getName());
        return buildRedisVectorStore(jedisPooled, embeddingModel);
    }

    /**
     * 创建 bge-m3 的 HTTP 协议适配器。
     *
     * 说明：
     * 1. 当前实现只负责通过 HTTP 调用外部 embedding 服务；
     * 2. 不在当前 JVM 进程内执行本地模型推理；
     * 3. 返回结果会被包装为 Spring AI 可识别的 EmbeddingModel。
     */
    @Bean
    @Primary
    @ConditionalOnProperty(
            prefix = "rag.embedding",
            name = "provider",
            havingValue = "bge-m3-http"
    )
    public BgeM3HttpEmbeddingModel bgeM3HttpEmbeddingModel(OkHttpClient okHttpClient) {
        return new BgeM3HttpEmbeddingModel(
                okHttpClient,
                ragEmbeddingProperties.getEmbedding().getServiceUrl(),
                ragEmbeddingProperties.getEmbedding().getModelName(),
                ragEmbeddingProperties.getEmbedding().getDimension()
        );
    }


    /**
     * 当 `provider=bge-m3-http` 时，创建基于 HTTP embedding 适配器的 RedisVectorStore。
     */
    @Bean
    @Primary
    @ConditionalOnProperty(
            prefix = "rag.embedding",
            name = "provider",
            havingValue = "bge-m3-http"
    )
    public RedisVectorStore ragRedisVectorStoreBgeM3Http(
            JedisPooled jedisPooled,
            BgeM3HttpEmbeddingModel embeddingModel
    ) {
        log.info("使用 bge-m3 HTTP embedding 服务: {}", ragEmbeddingProperties.getEmbedding().getServiceUrl());
        return buildRedisVectorStore(jedisPooled, embeddingModel);
    }

    private RedisVectorStore buildRedisVectorStore(
            JedisPooled jedisPooled,
            EmbeddingModel embeddingModel
    ) {
        log.info("RAG Redis indexName={}", ragEmbeddingProperties.getVectorstore().getIndexName());
        log.info("RAG Redis prefix={}", ragEmbeddingProperties.getVectorstore().getPrefix());
        log.info("最终使用 EmbeddingModel: {}", embeddingModel.getClass().getName());

        /*
         * VectorStore 初始化要点：
         * 1. `indexName` 与 `prefix` 决定当前 embedding 方案写入哪一套 Redis 索引与键空间；
         * 2. `metadataFields` 必须显式声明，Redis schema 才知道这些 metadata 可被过滤或读取；
         * 3. `userId` 用于 `filterExpression` 的检索侧过滤，确保相似度检索只返回当前用户自己的文档；
         * 4. `fileName` 用于 benchmark 文件级命中判定、检索结果展示与来源追踪；
         * 5. `source` 用于保留来源标识，方便后续来源展示、排障与追溯；
         * 6. `initializeSchema(true)` 只负责在索引不存在时初始化 schema，不会给旧索引自动补齐历史字段。
         */

        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(ragEmbeddingProperties.getVectorstore().getIndexName())
                .prefix(ragEmbeddingProperties.getVectorstore().getPrefix())
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("userId"),
                        RedisVectorStore.MetadataField.tag("fileName"),
                        RedisVectorStore.MetadataField.tag("source"),
                        RedisVectorStore.MetadataField.tag("documentId"),
                        RedisVectorStore.MetadataField.tag("chunkId"),
                        RedisVectorStore.MetadataField.numeric("chunkSeq")
                )
                .initializeSchema(true)
                .build();
    }


//    @Bean
//    @Primary
//    public RedisVectorStore ragRedisVectorStore(
//            JedisPooled jedisPooled,
//            @Qualifier("ragEmbeddingModel") EmbeddingModel embeddingModel) {
//        log.info("RAG Redis indexName={}", ragEmbeddingProperties.getVectorstore().getIndexName());
//        log.info("RAG Redis prefix={}", ragEmbeddingProperties.getVectorstore().getPrefix());
//        log.info("最终使用 EmbeddingModel: {}", embeddingModel.getClass().getName());
//        return RedisVectorStore.builder(jedisPooled, embeddingModel)
//                .indexName(ragEmbeddingProperties.getVectorstore().getIndexName())
//                .prefix(ragEmbeddingProperties.getVectorstore().getPrefix())
//                .metadataFields(
//                        RedisVectorStore.MetadataField.tag("userId"),
//                        RedisVectorStore.MetadataField.tag("fileName"),
//                        RedisVectorStore.MetadataField.tag("source")
//                )
//                .initializeSchema(true)
//                .build();
//    }
}
