package com.itgeo.fitmate.api.wiki.application.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import com.itgeo.fitmate.api.rag.application.DocumentService;
import com.itgeo.fitmate.api.rag.infrastructure.entity.RagDocument;
import com.itgeo.fitmate.api.rag.infrastructure.mapper.RagDocumentMapper;
import com.itgeo.fitmate.api.wiki.application.WikiCompileService;
import com.itgeo.fitmate.api.wiki.application.WikiKeywordSearchService;
import com.itgeo.fitmate.api.wiki.config.WikiProperties;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiCompileJob;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiLog;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiSpace;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiCompileJobMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiLogMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiPageMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiSpaceMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Wiki 编译服务实现。
 *
 * 调用 LLM 将 RAG 源文档编译为结构化 wiki 页面，并将结果写入：
 *  1. MySQL（t_wiki_page / t_wiki_log）
 *  2. Redis Hash（关键词索引，由 WikiKeywordSearchService 负责）
 *  3. Redis Vector Store（向量索引，由 wikiRedisVectorStore.add 负责）
 *
 * 编译动作（actions）由 LLM 输出 JSON 描述，本服务解析并执行。
 */
@Service
@Slf4j
public class WikiCompileServiceImpl implements WikiCompileService {

    private final WikiCompileJobMapper compileJobMapper;
    private final WikiSpaceMapper spaceMapper;
    private final WikiPageMapper pageMapper;
    private final WikiLogMapper logMapper;
    private final RagDocumentMapper ragDocumentMapper;
    private final DocumentService documentService;
    private final ChatModel chatModel;
    private final PromptTemplateManager promptTemplateManager;
    private final WikiProperties wikiProperties;
    private final WikiKeywordSearchService wikiKeywordSearchService;
    private final RedisVectorStore wikiRedisVectorStore;

    public WikiCompileServiceImpl(
            WikiCompileJobMapper compileJobMapper,
            WikiSpaceMapper spaceMapper,
            WikiPageMapper pageMapper,
            WikiLogMapper logMapper,
            RagDocumentMapper ragDocumentMapper,
            DocumentService documentService,
            ChatModel chatModel,
            PromptTemplateManager promptTemplateManager,
            WikiProperties wikiProperties,
            WikiKeywordSearchService wikiKeywordSearchService,
            @Qualifier("wikiRedisVectorStore") RedisVectorStore wikiRedisVectorStore) {
        this.compileJobMapper = compileJobMapper;
        this.spaceMapper = spaceMapper;
        this.pageMapper = pageMapper;
        this.logMapper = logMapper;
        this.ragDocumentMapper = ragDocumentMapper;
        this.documentService = documentService;
        this.chatModel = chatModel;
        this.promptTemplateManager = promptTemplateManager;
        this.wikiProperties = wikiProperties;
        this.wikiKeywordSearchService = wikiKeywordSearchService;
        this.wikiRedisVectorStore = wikiRedisVectorStore;
    }

    @Override
    public WikiCompileJob submitCompileJob(Long spaceId, Long sourceDocId, Long triggerBy) {
        WikiCompileJob job = new WikiCompileJob();
        job.setSpaceId(spaceId);
        job.setSourceDocId(sourceDocId);
        job.setTriggerType("DOC_UPLOAD");
        job.setStatus("PENDING");
        job.setCreatedByUserId(triggerBy);
        job.setCreatedAt(LocalDateTime.now());
        compileJobMapper.insert(job);
        return job;
    }

    @Override
    public void executeCompile(Long jobId) {
        WikiCompileJob job = compileJobMapper.selectById(jobId);
        if (job == null) {
            log.warn("Wiki 编译任务不存在: {}", jobId);
            return;
        }
        job.setStatus("RUNNING");
        job.setStartedAt(LocalDateTime.now());
        compileJobMapper.updateById(job);

        try {
            // 1. 读源文档
            RagDocument ragDoc = ragDocumentMapper.selectById(job.getSourceDocId());
            if (ragDoc == null) {
                throw new IllegalStateException("源文档不存在: " + job.getSourceDocId());
            }

            // 2. 拉取原始资料文本（简化实现：通过 DocumentService 检索该文档的 chunk 拼接）
            String rawContent = fetchRawText(ragDoc);

            // 3. 读 schema
            String schemaContent = loadSchema();

            // 4. 读当前 INDEX 页
            WikiSpace space = spaceMapper.selectById(job.getSpaceId());
            String indexContent = loadIndexContent(job.getSpaceId());

            // 5. 调 LLM 编译
            String promptText = promptTemplateManager.buildWikiCompilePrompt(
                    schemaContent, rawContent, indexContent);
            String llmOutput = chatModel.call(new Prompt(promptText))
                    .getResult().getOutput().getText();

            // 6. 解析 JSON 指令
            JSONObject root = JSONUtil.parseObj(llmOutput);
            JSONArray actions = root.getJSONArray("actions");
            if (actions == null) {
                throw new IllegalStateException("LLM 未返回 actions 数组");
            }

            // 7. 执行指令
            for (int i = 0; i < actions.size(); i++) {
                JSONObject action = actions.getJSONObject(i);
                applyAction(job.getSpaceId(), action, space, ragDoc);
            }

            // 8. 标记成功
            job.setStatus("SUCCESS");
            job.setFinishedAt(LocalDateTime.now());
            compileJobMapper.updateById(job);

        } catch (Exception e) {
            log.error("Wiki 编译失败 job={}", jobId, e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setFinishedAt(LocalDateTime.now());
            compileJobMapper.updateById(job);
        }
    }

    @Override
    public Optional<WikiCompileJob> getJob(Long jobId) {
        return Optional.ofNullable(compileJobMapper.selectById(jobId));
    }

    @Override
    public Long getOrCreateUserSpace(Long userId) {
        WikiSpace existing = spaceMapper.selectOne(
                new LambdaQueryWrapper<WikiSpace>()
                        .eq(WikiSpace::getScopeType, "USER")
                        .eq(WikiSpace::getOwnerUserId, userId));
        if (existing != null) {
            return existing.getId();
        }
        WikiSpace space = new WikiSpace();
        space.setScopeType("USER");
        space.setOwnerUserId(userId);
        space.setTitle("用户 " + userId + " Wiki");
        space.setStatus("ACTIVE");
        space.setCreatedAt(LocalDateTime.now());
        space.setUpdatedAt(LocalDateTime.now());
        spaceMapper.insert(space);
        return space.getId();
    }

    private void applyAction(Long spaceId, JSONObject action, WikiSpace space, RagDocument sourceDoc) {
        String type = action.getStr("action");
        if (type == null) {
            log.warn("action 字段缺失: {}", action);
            return;
        }
        switch (type) {
            case "create" -> upsertPage(spaceId, action, space, sourceDoc, false);
            case "update" -> upsertPage(spaceId, action, space, sourceDoc, true);
            case "update_index" -> upsertIndexPage(spaceId, action);
            case "append_log" -> appendLog(spaceId, action.getStr("entry"));
            default -> log.warn("未知 action 类型: {}", type);
        }
    }

    private void upsertPage(Long spaceId, JSONObject action, WikiSpace space, RagDocument sourceDoc,
                            boolean isUpdate) {
        String slug = action.getStr("slug");
        String pageType = action.getStr("page_type");
        String title = action.getStr("title");
        String contentMd = action.getStr("content_md");

        if (slug == null || slug.isBlank()) {
            log.warn("upsertPage 缺少 slug，跳过: {}", action);
            return;
        }

        WikiPage existing = pageMapper.selectOne(
                new LambdaQueryWrapper<WikiPage>()
                        .eq(WikiPage::getSpaceId, spaceId)
                        .eq(WikiPage::getSlug, slug));

        WikiPage page = existing != null ? existing : new WikiPage();
        page.setSpaceId(spaceId);
        if (pageType != null) page.setPageType(pageType);
        if (title != null) page.setTitle(title);
        page.setSlug(slug);
        page.setContentMd(contentMd);
        page.setContentHash(sha256(contentMd));
        page.setCharCount(contentMd == null ? 0 : contentMd.length());
        page.setStatus("PUBLISHED");
        page.setSourceDocId(sourceDoc.getId());
        page.setCompiledAt(LocalDateTime.now());

        if (existing == null) {
            pageMapper.insert(page);
        } else {
            pageMapper.updateById(page);
        }

        // 同步到 Redis 关键词 + 向量索引
        String scope = space == null ? "GLOBAL" : space.getScopeType();
        Long ownerUserId = space == null ? null : space.getOwnerUserId();
        wikiKeywordSearchService.indexPage(page, scope, ownerUserId);
        indexPageToVectorStore(page, scope, ownerUserId);
    }

    private void upsertIndexPage(Long spaceId, JSONObject action) {
        String contentMd = action.getStr("content_md");
        if (contentMd == null) {
            log.warn("update_index 缺少 content_md，跳过");
            return;
        }
        WikiPage index = pageMapper.selectOne(
                new LambdaQueryWrapper<WikiPage>()
                        .eq(WikiPage::getSpaceId, spaceId)
                        .eq(WikiPage::getSlug, "index"));
        boolean isNew = index == null;
        if (isNew) {
            index = new WikiPage();
            index.setSpaceId(spaceId);
            index.setPageType("INDEX");
            index.setSlug("index");
            index.setTitle("Index");
            index.setStatus("PUBLISHED");
        }
        index.setTitle("Index");
        index.setContentMd(contentMd);
        index.setContentHash(sha256(contentMd));
        index.setCharCount(contentMd.length());
        index.setCompiledAt(LocalDateTime.now());
        if (isNew) {
            pageMapper.insert(index);
        } else {
            pageMapper.updateById(index);
        }

        WikiSpace space = spaceMapper.selectById(spaceId);
        String scope = space == null ? "GLOBAL" : space.getScopeType();
        Long ownerUserId = space == null ? null : space.getOwnerUserId();
        wikiKeywordSearchService.indexPage(index, scope, ownerUserId);
        indexPageToVectorStore(index, scope, ownerUserId);
    }

    private void appendLog(Long spaceId, String entry) {
        if (entry == null || entry.isBlank()) return;
        WikiLog wikiLog = new WikiLog();
        wikiLog.setSpaceId(spaceId);
        wikiLog.setEntryType("INGEST");
        wikiLog.setEntrySummary(entry);
        wikiLog.setCreatedAt(LocalDateTime.now());
        logMapper.insert(wikiLog);
    }

    /**
     * 将页面写入 Redis 向量索引（复用 wikiRedisVectorStore.add）。
     *
     * metadata 包含 spaceId/pageId/pageType/scope/ownerUserId/title，与 WikiRedisVectorStoreConfig 的 schema 对齐。
     * 若向量索引写入失败仅记录日志，不影响编译主流程。
     */
    private void indexPageToVectorStore(WikiPage page, String scope, Long ownerUserId) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("spaceId", String.valueOf(page.getSpaceId()));
            metadata.put("pageId", String.valueOf(page.getId()));
            metadata.put("pageType", page.getPageType() == null ? "" : page.getPageType());
            metadata.put("scope", scope == null ? "" : scope);
            metadata.put("ownerUserId", ownerUserId == null ? "" : String.valueOf(ownerUserId));
            metadata.put("title", page.getTitle() == null ? "" : page.getTitle());

            String text = page.getContentMd() == null ? "" : page.getContentMd();
            Document document = new Document(text, metadata);
            wikiRedisVectorStore.add(List.of(document));
        } catch (Exception e) {
            log.warn("Wiki 向量索引写入失败 pageId={}: {}", page.getId(), e.getMessage());
        }
    }

    /**
     * 简化实现：通过 DocumentService.doSearch 取该文档的 chunk 拼接为原文。
     *
     * 后续可优化为新增 DocumentService.getRawTextByDocId(docId) 直接读取原始文本。
     */
    private String fetchRawText(RagDocument ragDoc) {
        List<Document> docs = documentService.doSearch(
                ragDoc.getFileName(), ragDoc.getUserId(), 10);
        if (docs == null || docs.isEmpty()) {
            return ragDoc.getFileName() == null ? "" : ragDoc.getFileName();
        }
        StringBuilder sb = new StringBuilder();
        for (Document d : docs) {
            sb.append(d.getText()).append("\n\n");
        }
        return sb.toString();
    }

    private String loadSchema() {
        try {
            ClassPathResource res = new ClassPathResource("prompts/wiki-schema.md");
            return new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("加载 wiki-schema.md 失败: {}", e.getMessage());
            return "";
        }
    }

    private String loadIndexContent(Long spaceId) {
        WikiPage index = pageMapper.selectOne(
                new LambdaQueryWrapper<WikiPage>()
                        .eq(WikiPage::getSpaceId, spaceId)
                        .eq(WikiPage::getSlug, "index"));
        return index == null ? "(空)" : index.getContentMd();
    }

    private String sha256(String text) {
        if (text == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "";
        }
    }
}
