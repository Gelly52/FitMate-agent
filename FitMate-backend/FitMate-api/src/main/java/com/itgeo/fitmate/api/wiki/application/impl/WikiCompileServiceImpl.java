package com.itgeo.fitmate.api.wiki.application.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriteRequest;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriter;
import com.itgeo.fitmate.api.agent.llm.LlmJsonSanitizer;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.chat.infrastructure.ReasoningChatClient;
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
import com.itgeo.fitmate.api.auth.infrastructure.mapper.UserMapper;
import com.itgeo.fitmate.api.auth.infrastructure.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
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
    private final ReasoningChatClient reasoningChatClient;
    private final PromptTemplateManager promptTemplateManager;
    private final WikiProperties wikiProperties;
    private final WikiKeywordSearchService wikiKeywordSearchService;
    private final RedisVectorStore wikiRedisVectorStore;
    private final MemoryWriter memoryWriter;
    private final MemoryProperties memoryProperties;
    private final UserMapper userMapper;

    public WikiCompileServiceImpl(
            WikiCompileJobMapper compileJobMapper,
            WikiSpaceMapper spaceMapper,
            WikiPageMapper pageMapper,
            WikiLogMapper logMapper,
            RagDocumentMapper ragDocumentMapper,
            DocumentService documentService,
            ReasoningChatClient reasoningChatClient,
            PromptTemplateManager promptTemplateManager,
            WikiProperties wikiProperties,
            WikiKeywordSearchService wikiKeywordSearchService,
            @Qualifier("wikiRedisVectorStore") RedisVectorStore wikiRedisVectorStore,
            MemoryWriter memoryWriter,
            MemoryProperties memoryProperties,
            UserMapper userMapper) {
        this.compileJobMapper = compileJobMapper;
        this.spaceMapper = spaceMapper;
        this.pageMapper = pageMapper;
        this.logMapper = logMapper;
        this.ragDocumentMapper = ragDocumentMapper;
        this.documentService = documentService;
        this.reasoningChatClient = reasoningChatClient;
        this.promptTemplateManager = promptTemplateManager;
        this.wikiProperties = wikiProperties;
        this.wikiKeywordSearchService = wikiKeywordSearchService;
        this.wikiRedisVectorStore = wikiRedisVectorStore;
        this.memoryWriter = memoryWriter;
        this.memoryProperties = memoryProperties;
        this.userMapper = userMapper;
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
            log.info("Wiki 编译开始 job={} spaceId={} docId={} fileName={}",
                    jobId, job.getSpaceId(), ragDoc.getId(), ragDoc.getFileName());

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
            log.info("Wiki 编译调用 LLM job={} fileName={}", jobId, ragDoc.getFileName());
            String llmOutput = reasoningChatClient.call(promptText).getContent();

            // 6. 解析 JSON 指令
            JSONObject root = JSONUtil.parseObj(LlmJsonSanitizer.sanitize(llmOutput));
            JSONArray actions = root.getJSONArray("actions");
            if (actions == null) {
                throw new IllegalStateException("LLM 未返回 actions 数组");
            }
            log.info("Wiki 编译 LLM 返回 {} 条指令 job={}", actions.size(), jobId);

            // 7. 执行指令，收集触碰的页面 ID
            List<Long> touchedPageIds = new ArrayList<>();
            for (int i = 0; i < actions.size(); i++) {
                JSONObject action = actions.getJSONObject(i);
                Long touchedId = applyAction(job.getSpaceId(), action, space, ragDoc);
                if (touchedId != null) {
                    touchedPageIds.add(touchedId);
                }
            }

            // 记忆提取（仅 USER scope）
            try {
                if ("USER".equals(space.getScopeType()) && memoryProperties.isEnabled()) {
                    extractWikiMemories(llmOutput, space.getOwnerUserId(), job.getSourceDocId());
                }
            } catch (Exception e) {
                log.warn("Wiki 记忆提取失败 docId={} userId={}", job.getSourceDocId(), space.getOwnerUserId(), e);
            }

            // 8. 标记成功，记录触碰的页面
            job.setStatus("SUCCESS");
            job.setFinishedAt(LocalDateTime.now());
            job.setPagesTouchedJson(JSONUtil.toJsonStr(touchedPageIds));
            compileJobMapper.updateById(job);
            log.info("Wiki 编译完成 job={} 触碰 {} 页", jobId, touchedPageIds.size());

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
        // 查询用户昵称，用于空间命名
        User user = userMapper.selectById(userId);
        String displayName = (user != null && user.getNickname() != null && !user.getNickname().isEmpty())
                ? user.getNickname()
                : (user != null && user.getUsername() != null ? user.getUsername() : "用户" + userId);
        WikiSpace space = new WikiSpace();
        space.setScopeType("USER");
        space.setOwnerUserId(userId);
        space.setTitle(displayName + "的健身知识库");
        space.setStatus("ACTIVE");
        space.setCreatedAt(LocalDateTime.now());
        space.setUpdatedAt(LocalDateTime.now());
        spaceMapper.insert(space);
        return space.getId();
    }

    @Override
    public void deletePage(Long userId, Long pageId) {
        if (userId == null || pageId == null) {
            throw new IllegalArgumentException("userId/pageId 不能为空");
        }

        WikiPage page = pageMapper.selectById(pageId);
        if (page == null) {
            throw new IllegalArgumentException("Wiki 页面不存在");
        }

        // 校验空间归属：USER space 的 ownerUserId 必须匹配
        WikiSpace space = spaceMapper.selectById(page.getSpaceId());
        if (space == null) {
            throw new IllegalArgumentException("Wiki 空间不存在");
        }
        if ("USER".equals(space.getScopeType())
                && !userId.equals(space.getOwnerUserId())) {
            throw new IllegalArgumentException("无权删除该 Wiki 页面");
        }

        deletePageData(page);
        log.info("Wiki 页面删除完成 userId={} pageId={} title={}", userId, pageId, page.getTitle());
    }

    @Override
    public int cleanupExpiredPages() {
        int retentionMonths = wikiProperties.getRetentionMonths();
        LocalDateTime threshold = LocalDateTime.now().minusMonths(retentionMonths);

        // 1. 删除过期的非 LOG 页面（LOG 页面裁剪内容而非整体删除）
        List<WikiPage> expired = pageMapper.selectList(
                new LambdaQueryWrapper<WikiPage>()
                        .lt(WikiPage::getCompiledAt, threshold)
                        .ne(WikiPage::getPageType, "LOG"));
        for (WikiPage page : expired) {
            deletePageData(page);
        }

        // 2. 裁剪 LOG 页面，只保留最近 retentionMonths 的日志条目
        int trimmedCount = trimLogPages(threshold);

        // 3. 总量上限兜底
        int overLimitCount = enforceMaxPagesPerSpace();

        int total = expired.size() + trimmedCount + overLimitCount;
        log.info("Wiki 清理完成：过期删除 {} 页，LOG 裁剪 {} 页，超限删除 {} 页（保留 {} 月，上限 {} 页/space）",
                expired.size(), trimmedCount, overLimitCount, retentionMonths, wikiProperties.getMaxPagesPerSpace());
        return total;
    }

    /**
     * 裁剪所有 LOG 页面，只保留最近 retentionMonths 的 `## YYYY-MM-DD` 日志段。
     * 非标准日期格式的段（如旧 bug 产生的 `## [日期] type | title`）一并清理。
     */
    private int trimLogPages(LocalDateTime threshold) {
        List<WikiPage> logPages = pageMapper.selectList(
                new LambdaQueryWrapper<WikiPage>()
                        .eq(WikiPage::getPageType, "LOG"));
        LocalDate thresholdDate = threshold.toLocalDate();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        int trimmed = 0;
        for (WikiPage logPage : logPages) {
            String content = logPage.getContentMd();
            if (content == null || content.isBlank()) {
                continue;
            }
            String trimmedContent = trimLogContentByDate(content, thresholdDate, fmt);
            if (!trimmedContent.equals(content)) {
                logPage.setContentMd(trimmedContent);
                logPage.setContentHash(sha256(trimmedContent));
                logPage.setCharCount(trimmedContent.length());
                logPage.setUpdatedAt(LocalDateTime.now());
                pageMapper.updateById(logPage);
                // 重建索引
                WikiSpace space = spaceMapper.selectById(logPage.getSpaceId());
                String scope = space == null ? "GLOBAL" : space.getScopeType();
                Long ownerUserId = space == null ? null : space.getOwnerUserId();
                wikiKeywordSearchService.indexPage(logPage, scope, ownerUserId);
                indexPageToVectorStore(logPage, scope, ownerUserId);
                trimmed++;
            }
        }
        return trimmed;
    }

    /** LOG content_md 按日期段裁剪：保留 >= thresholdDate 的 `## YYYY-MM-DD` 段，其余删除。 */
    private String trimLogContentByDate(String content, LocalDate thresholdDate, DateTimeFormatter fmt) {
        Pattern dateHeading = Pattern.compile("^## (\\d{4}-\\d{2}-\\d{2})\\s*$");
        String[] lines = content.split("\n", -1);

        StringBuilder header = new StringBuilder();
        List<List<String>> sections = new ArrayList<>();
        List<LocalDate> sectionDates = new ArrayList<>();
        List<String> current = null;

        for (String line : lines) {
            Matcher m = dateHeading.matcher(line.trim());
            if (m.matches()) {
                // 新段开始
                current = new ArrayList<>();
                current.add(line);
                sections.add(current);
                sectionDates.add(LocalDate.parse(m.group(1), fmt));
            } else if (current != null) {
                current.add(line);
            } else {
                header.append(line).append("\n");
            }
        }

        // 重组：头部 + 保留的段
        StringBuilder result = new StringBuilder(header);
        for (int i = 0; i < sections.size(); i++) {
            if (!sectionDates.get(i).isBefore(thresholdDate)) {
                for (String line : sections.get(i)) {
                    result.append(line).append("\n");
                }
            }
        }
        return result.toString().stripTrailing() + "\n";
    }

    /**
     * 总量上限兜底：每个 space 页面数超过 maxPagesPerSpace 时，
     * 按 compiled_at 升序删除最旧的页面（排除 LOG，LOG 由裁剪逻辑管理）。
     */
    private int enforceMaxPagesPerSpace() {
        int maxPages = wikiProperties.getMaxPagesPerSpace();
        // 查询每个 space 的页面数（排除 LOG）
        List<WikiSpace> spaces = spaceMapper.selectList(null);
        int deleted = 0;
        for (WikiSpace space : spaces) {
            Long count = pageMapper.selectCount(
                    new LambdaQueryWrapper<WikiPage>()
                            .eq(WikiPage::getSpaceId, space.getId())
                            .ne(WikiPage::getPageType, "LOG"));
            if (count > maxPages) {
                int toDelete = count.intValue() - maxPages;
                List<WikiPage> oldest = pageMapper.selectList(
                        new LambdaQueryWrapper<WikiPage>()
                                .eq(WikiPage::getSpaceId, space.getId())
                                .ne(WikiPage::getPageType, "LOG")
                                .orderByAsc(WikiPage::getCompiledAt)
                                .last("LIMIT " + toDelete));
                for (WikiPage page : oldest) {
                    deletePageData(page);
                    deleted++;
                }
                log.warn("Wiki 页面超限 spaceId={} count={} 超限删除 {} 页", space.getId(), count, toDelete);
            }
        }
        return deleted;
    }

    /**
     * 实际执行 Wiki 页面的索引与记录清理（向量 + 关键词 + MySQL）。
     * 供 deletePage（用户删除）与 cleanupExpiredPages（定时清理）复用。
     */
    private void deletePageData(WikiPage page) {
        try {
            wikiRedisVectorStore.delete(List.of(String.valueOf(page.getId())));
        } catch (Exception e) {
            log.warn("Wiki 向量索引删除失败 pageId={}: {}", page.getId(), e.getMessage());
        }
        wikiKeywordSearchService.deleteByPage(page.getId());
        pageMapper.deleteById(page.getId());
    }

    private Long applyAction(Long spaceId, JSONObject action, WikiSpace space, RagDocument sourceDoc) {
        String type = action.getStr("action");
        if (type == null) {
            log.warn("action 字段缺失: {}", action);
            return null;
        }
        return switch (type) {
            case "create" -> upsertPage(spaceId, action, space, sourceDoc, false);
            case "update" -> upsertPage(spaceId, action, space, sourceDoc, true);
            case "update_index" -> upsertIndexPage(spaceId, action);
            case "append_log" -> {
                appendLog(spaceId, action.getStr("entry"));
                yield null;
            }
            default -> {
                log.warn("未知 action 类型: {}", type);
                yield null;
            }
        };
    }

    /**
     * 组装页面 frontmatter JSON。
     * <p>
     * 包含标准字段 type/title，以及 LLM action 中除控制字段外的额外元数据
     * （如 exercises、muscle_group、category 等），保留编译时的结构化信息。
     * action 为 null 时仅写入标准字段（用于 LOG 等内部生成的页面）。
     */
    private String buildFrontmatter(JSONObject action, WikiPage page) {
        JSONObject frontmatter = new JSONObject();
        frontmatter.set("type", page.getPageType());
        frontmatter.set("title", page.getTitle());
        if (action != null) {
            for (String key : action.keySet()) {
                if (key.equals("action") || key.equals("slug") || key.equals("page_type")
                        || key.equals("title") || key.equals("content_md")) {
                    continue;
                }
                frontmatter.set(key, action.get(key));
            }
        }
        return frontmatter.toString();
    }

    /**
     * 从 markdown 内容第一行 `# 标题` 提取标题文本。
     * 用于 LLM 未返回 title 但 content_md 有 H1 标题时的兜底。
     */
    private String extractTitleFromContent(String contentMd) {
        if (contentMd == null || contentMd.isBlank()) {
            return null;
        }
        for (String line : contentMd.split("\n", 3)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ") && trimmed.length() > 2) {
                String title = trimmed.substring(2).trim();
                if (!title.isEmpty()) {
                    return title;
                }
            }
            // 跳过开头的空行，遇到非空行非标题则停止
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                break;
            }
        }
        return null;
    }

    private Long upsertPage(Long spaceId, JSONObject action, WikiSpace space, RagDocument sourceDoc,
                            boolean isUpdate) {
        String slug = action.getStr("slug");
        String pageType = action.getStr("page_type");
        String title = action.getStr("title");
        String contentMd = action.getStr("content_md");

        if (slug == null || slug.isBlank()) {
            log.warn("upsertPage 缺少 slug，跳过: {}", action);
            return null;
        }

        WikiPage existing = pageMapper.selectOne(
                new LambdaQueryWrapper<WikiPage>()
                        .eq(WikiPage::getSpaceId, spaceId)
                        .eq(WikiPage::getSlug, slug));

        WikiPage page = existing != null ? existing : new WikiPage();
        page.setSpaceId(spaceId);
        // page_type / title 兜底：LLM 可能未返回必填字段，insert 时不能为空
        if (pageType != null && !pageType.isBlank()) {
            page.setPageType(pageType);
        } else if (existing == null) {
            log.warn("LLM 未返回 page_type，使用默认 ENTITY。action: {}", action);
            page.setPageType("ENTITY");
        }
        if (title != null && !title.isBlank()) {
            page.setTitle(title);
        } else if (existing == null) {
            // 兜底：从 content_md 第一行 # 标题提取，比用英文 slug 更适合中文 Wiki
            String extractedTitle = extractTitleFromContent(contentMd);
            if (extractedTitle != null) {
                page.setTitle(extractedTitle);
            } else {
                log.warn("LLM 未返回 title 且 content_md 无标题，使用 slug 替代。action: {}", action);
                page.setTitle(slug);
            }
        }
        page.setSlug(slug);
        page.setContentMd(contentMd);
        page.setContentHash(sha256(contentMd));
        page.setCharCount(contentMd == null ? 0 : contentMd.length());
        page.setStatus("PUBLISHED");
        page.setSourceDocId(sourceDoc.getId());
        page.setCompiledAt(LocalDateTime.now());
        page.setFrontmatterJson(buildFrontmatter(action, page));

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
        return page.getId();
    }

    private Long upsertIndexPage(Long spaceId, JSONObject action) {
        String contentMd = action.getStr("content_md");
        if (contentMd == null) {
            log.warn("update_index 缺少 content_md，跳过");
            return null;
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
        index.setFrontmatterJson(buildFrontmatter(action, index));
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
        return index.getId();
    }

    private void appendLog(Long spaceId, String entry) {
        if (entry == null || entry.isBlank()) return;
        // 1. 写入 t_wiki_log 表（结构化日志记录）
        WikiLog wikiLog = new WikiLog();
        wikiLog.setSpaceId(spaceId);
        wikiLog.setEntryType("INGEST");
        wikiLog.setEntrySummary(entry);
        wikiLog.setCreatedAt(LocalDateTime.now());
        logMapper.insert(wikiLog);
        // 2. 同步更新 LOG 类型 Wiki 页面的 content_md，使前端 Wiki 浏览页可见
        upsertLogPage(spaceId, entry);
    }

    /**
     * 同步更新 LOG 类型 Wiki 页面：将新条目追加到 content_md 末尾。
     *
     * LOG 页用于在前端 Wiki 浏览页展示“知识库更新日志”，
     * 若空间内不存在则按约定 slug=update-log 创建。
     * entry 由 LLM 输出，本身为 markdown 片段，直接追加并刷新 updated_at/compiled_at。
     */
    private void upsertLogPage(Long spaceId, String entry) {
        WikiPage logPage = pageMapper.selectOne(
                new LambdaQueryWrapper<WikiPage>()
                        .eq(WikiPage::getSpaceId, spaceId)
                        .eq(WikiPage::getPageType, "LOG"));
        boolean isNew = logPage == null;
        if (isNew) {
            logPage = new WikiPage();
            logPage.setSpaceId(spaceId);
            logPage.setPageType("LOG");
            logPage.setSlug("update-log");
            logPage.setTitle("知识库更新日志");
            logPage.setStatus("PUBLISHED");
            logPage.setContentMd("# 知识库更新日志\n\n记录知识库的变更历史。");
        }

        String existing = logPage.getContentMd() == null ? "" : logPage.getContentMd();
        String strippedEntry = entry.strip();
        // 保证与前文以空行分隔，markdown 渲染时分节清晰
        String newContent = existing.endsWith("\n")
                ? existing + "\n" + strippedEntry + "\n"
                : existing + "\n\n" + strippedEntry + "\n";

        logPage.setContentMd(newContent);
        logPage.setContentHash(sha256(newContent));
        logPage.setCharCount(newContent.length());
        logPage.setCompiledAt(LocalDateTime.now());
        logPage.setUpdatedAt(LocalDateTime.now());
        logPage.setFrontmatterJson(buildFrontmatter(null, logPage));

        if (isNew) {
            pageMapper.insert(logPage);
        } else {
            pageMapper.updateById(logPage);
        }

        // 同步到 Redis 关键词 + 向量索引
        WikiSpace space = spaceMapper.selectById(spaceId);
        String scope = space == null ? "GLOBAL" : space.getScopeType();
        Long ownerUserId = space == null ? null : space.getOwnerUserId();
        wikiKeywordSearchService.indexPage(logPage, scope, ownerUserId);
        indexPageToVectorStore(logPage, scope, ownerUserId);
    }

    /**
     * 从 Wiki 编译的 LLM 输出中提取长期记忆并写入。
     * 仅当 LLM 输出包含 memory_extraction 数组时生效。
     */
    private void extractWikiMemories(String llmOutput, Long userId, Long sourceDocId) {
        JSONObject json;
        try {
            json = JSONUtil.parseObj(LlmJsonSanitizer.sanitize(llmOutput));
        } catch (Exception e) {
            return;
        }
        JSONArray extractions = json.getJSONArray("memory_extraction");
        if (extractions == null || extractions.isEmpty()) {
            return;
        }
        String source = "wiki_compile:" + sourceDocId;
        for (Object item : extractions) {
            JSONObject m = (JSONObject) item;
            String content = m.getStr("content");
            if (content == null || content.isBlank()) continue;
            Object metadata = m.get("metadata");
            MemoryWriteRequest req = MemoryWriteRequest.builder()
                    .userId(userId)
                    .memoryType("FACT")
                    .content(content)
                    .metadataJson(metadata != null ? JSONUtil.toJsonStr(metadata) : null)
                    .source(source)
                    .build();
            memoryWriter.writeIfNotIgnored(req);
        }
        log.info("Wiki 记忆提取完成 docId={} userId={} 提取 {} 条", sourceDocId, userId, extractions.size());
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
            // 用 pageId 作为向量库文档 ID，使删除时可按 pageId 精确清理
            Document document = new Document(String.valueOf(page.getId()), text, metadata);
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
