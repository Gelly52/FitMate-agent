package com.itgeo.fitmate.api.config;

import com.itgeo.fitmate.api.rag.config.RagEmbeddingProperties;
import com.itgeo.fitmate.api.wiki.config.WikiProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;

/**
 * Redis 关键字检索索引启动期自动初始化。
 *
 * Spring AI 的 RedisVectorStore.initializeSchema(true) 只创建向量索引，
 * 不创建关键字检索所需的 RediSearch TEXT/TAG 索引。
 * 此组件在应用启动时确保 fitmate-keyword-index（RAG）和
 * fitmate-wiki-keyword-index（Wiki）存在；已存在则跳过。
 */
@Slf4j
@Configuration
public class RedisKeywordIndexBootstrap implements ApplicationRunner {

    @Resource
    private JedisPooled jedisPooled;

    @Resource
    private RagEmbeddingProperties ragEmbeddingProperties;

    @Resource
    private WikiProperties wikiProperties;

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        ensureRagKeywordIndex();
        ensureWikiKeywordIndex();
    }

    /** 创建 RAG 关键字索引（fitmate-keyword-index） */
    private void ensureRagKeywordIndex() {
        String indexName = ragEmbeddingProperties.getRetrieval().getKeywordIndexName();
        String keyPrefix = ragEmbeddingProperties.getRetrieval().getKeywordKeyPrefix();
        try {
            jedisPooled.ftCreate(
                    indexName,
                    FTCreateParams.createParams()
                            .on(IndexDataType.HASH)
                            .prefix(keyPrefix),
                    ragKeywordSchema()
            );
            log.info("RAG 关键字索引创建成功: index={} prefix={}", indexName, keyPrefix);
        } catch (Exception e) {
            if (isIndexExistsError(e)) {
                log.info("RAG 关键字索引已存在，跳过创建: {}", indexName);
            } else {
                log.warn("RAG 关键字索引创建失败: {} - {}", indexName, e.getMessage());
            }
        }
    }

    /** 创建 Wiki 关键字索引（fitmate-wiki-keyword-index） */
    private void ensureWikiKeywordIndex() {
        String indexName = wikiProperties.getKeyword().getIndexName();
        String keyPrefix = wikiProperties.getKeyword().getKeyPrefix();
        try {
            jedisPooled.ftCreate(
                    indexName,
                    FTCreateParams.createParams()
                            .on(IndexDataType.HASH)
                            .prefix(keyPrefix),
                    wikiKeywordSchema()
            );
            log.info("Wiki 关键字索引创建成功: index={} prefix={}", indexName, keyPrefix);
        } catch (Exception e) {
            if (isIndexExistsError(e)) {
                log.info("Wiki 关键字索引已存在，跳过创建: {}", indexName);
            } else {
                log.warn("Wiki 关键字索引创建失败: {} - {}", indexName, e.getMessage());
            }
        }
    }

    /** RAG 关键字索引 schema：与 RedisKeywordSearchServiceImpl.indexChunks 写入字段对齐 */
    private SchemaField[] ragKeywordSchema() {
        return new SchemaField[]{
                new TagField("userId"),
                new TagField("chunkId"),
                new TagField("documentId"),
                new NumericField("chunkSeq"),
                new TextField("fileName"),
                new TextField("source"),
                new TextField("content")
        };
    }

    /** Wiki 关键字索引 schema：与 WikiKeywordSearchServiceImpl.indexPage 写入字段对齐 */
    private SchemaField[] wikiKeywordSchema() {
        return new SchemaField[]{
                new TagField("pageId"),
                new TagField("spaceId"),
                new TagField("pageType"),
                new TagField("scope"),
                new TagField("ownerUserId"),
                new TextField("title"),
                new TextField("content")
        };
    }

    /** RediSearch 索引已存在时的错误识别 */
    private boolean isIndexExistsError(Exception e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("already exists") || msg.contains("Index already exists"));
    }
}
