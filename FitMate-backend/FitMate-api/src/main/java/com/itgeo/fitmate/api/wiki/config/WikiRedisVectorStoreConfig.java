package com.itgeo.fitmate.api.wiki.config;

import com.itgeo.fitmate.api.rag.infrastructure.embedding.BgeM3HttpEmbeddingModel;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import redis.clients.jedis.JedisPooled;

/**
 * Wiki 模块装配。
 *
 * 1. wikiRedisVectorStore：独立 Redis VectorStore，与 RAG 隔离
 *    - 独立 indexName / prefix
 *    - 独立 metadata schema（spaceId/pageId/pageType/scope/ownerUserId/title）
 *    - 复用同一 BgeM3HttpEmbeddingModel（1024 维，与 RAG 同模型）
 *
 * 2. wikiCompileExecutor：Wiki 编译异步线程池
 *    - 供 @Async("wikiCompileExecutor") 使用
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class WikiRedisVectorStoreConfig {

    private final WikiProperties wikiProperties;

    /**
     * Wiki 专用 RedisVectorStore，通过 @Qualifier("wikiRedisVectorStore") 注入。
     *
     * 注意：现有 RAG 的 RedisVectorStore 标注了 @Primary，因此 Wiki 这里不使用 @Primary，
     * 通过显式 @Qualifier 注入避免冲突。
     */
    @Bean("wikiRedisVectorStore")
    public RedisVectorStore wikiRedisVectorStore(
            JedisPooled jedisPooled,
            BgeM3HttpEmbeddingModel embeddingModel) {

        log.info("Wiki Redis indexName={}", wikiProperties.getVectorstore().getIndexName());
        log.info("Wiki Redis prefix={}", wikiProperties.getVectorstore().getPrefix());

        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(wikiProperties.getVectorstore().getIndexName())
                .prefix(wikiProperties.getVectorstore().getPrefix())
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("spaceId"),
                        RedisVectorStore.MetadataField.tag("pageId"),
                        RedisVectorStore.MetadataField.tag("pageType"),
                        RedisVectorStore.MetadataField.tag("scope"),        // GLOBAL / USER
                        RedisVectorStore.MetadataField.tag("ownerUserId"),  // USER 用，GLOBAL 空
                        RedisVectorStore.MetadataField.tag("title")
                )
                .initializeSchema(true)
                .build();
    }

    /**
     * Wiki 编译异步线程池。
     *
     * corePoolSize 取自 fitmate.wiki.compile.async-pool-size（默认 3），
     * maxPoolSize = corePoolSize * 2，queueCapacity = 50。
     */
    @Bean("wikiCompileExecutor")
    public Executor wikiCompileExecutor() {
        int coreSize = wikiProperties.getCompile().getAsyncPoolSize();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(coreSize * 2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("wiki-compile-");
        executor.initialize();
        log.info("Wiki 编译线程池初始化: core={}, max={}, queue=50", coreSize, coreSize * 2);
        return executor;
    }
}
