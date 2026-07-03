package com.itgeo.fitmate.api.agent.tool;

import cn.hutool.core.util.StrUtil;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.rag.application.DocumentService;
import com.itgeo.fitmate.api.wiki.application.QueryRewriteService;
import com.itgeo.fitmate.api.wiki.application.WikiSearchService;
import com.itgeo.fitmate.api.wiki.config.WikiProperties;
import com.itgeo.fitmate.api.wiki.dto.KbSearchObservation;
import com.itgeo.fitmate.api.wiki.dto.KbSearchObservation.RawChunkItem;
import com.itgeo.fitmate.api.wiki.dto.KbSearchObservation.WikiPageItem;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

/**
 * 知识库复合检索工具。
 *
 * 串行两阶段：
 *   1. Wiki 检索（默认）
 *   2. if (ragEnabled): rewrite query -> RAG 检索
 *
 * 对 Agent 透明：LLM 只看到 kb.search 一个工具，内部按开关跑不同子流程。
 *
 * 注意：ragEnabled 不从 call.arguments 获取（LLM 不会传该参数），
 * 而是通过 KbSearchContextHolder 由 AgentLoopExecutor 在调用前注入。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KbSearchToolExecutor implements ToolExecutor {

    private final WikiSearchService wikiSearchService;
    private final DocumentService documentService;
    private final QueryRewriteService queryRewriteService;
    private final WikiProperties wikiProperties;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "kb.search",
                "检索用户知识库（Wiki 页面，包含已上传文档的编译后知识）。当用户询问文档内容、Wiki、知识库、已上传资料相关问题时必须调用此工具；若已启用 RAG，会基于 Wiki 结果改写 query 后检索原始文档。参数: {\"query\": \"问题\", \"topK\": 1-10}",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"topK\":{\"type\":\"integer\"}},\"required\":[\"query\"]}",
                true
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        String query = argumentText(call, "query");
        if (StrUtil.isBlank(query)) {
            return ToolResult.error("query不能为空");
        }
        int topK = normalizeNumber(argument(call, "topK"),
                wikiProperties.getRetrieval().getDefaultTopK(),
                wikiProperties.getRetrieval().getMaxTopK());
        Long userId = authenticatedUser.getUserId();

        // ragEnabled 从 chatEntity 通过 ThreadLocal 传入（LLM 不会传该参数）
        Boolean ragEnabled = KbSearchContextHolder.getRagEnabled();

        // 1. Wiki 检索
        List<WikiPage> wikiPages = wikiSearchService.search(query, userId, topK);
        List<WikiPageItem> wikiItems = wikiPages == null ? List.of()
                : wikiPages.stream().map(this::toWikiItem).collect(Collectors.toList());

        KbSearchObservation observation = new KbSearchObservation();
        observation.setWiki(wikiItems);

        // 2. if ragEnabled: rewrite + RAG 检索
        if (Boolean.TRUE.equals(ragEnabled)) {
            String wikiContent = wikiItems.stream()
                    .map(WikiPageItem::getContent)
                    .collect(Collectors.joining("\n\n"));
            String rewrittenQuery = queryRewriteService.rewrite(query, wikiContent);
            observation.setRewrittenQuery(rewrittenQuery);

            List<Document> ragDocs = documentService.doSearch(rewrittenQuery, userId, topK);
            List<RawChunkItem> ragItems = new ArrayList<>();
            if (ragDocs != null) {
                ragItems = ragDocs.stream().map(this::toRagItem).collect(Collectors.toList());
            }
            observation.setRag(ragItems);
        } else {
            observation.setRag(List.of());
        }

        String summary = String.format("Wiki 命中 %d 条%s",
                wikiItems.size(),
                Boolean.TRUE.equals(ragEnabled)
                        ? "；RAG 命中 " + (observation.getRag() == null ? 0 : observation.getRag().size()) + " 条"
                        : "");
        return ToolResult.ok(summary, observation);
    }

    private WikiPageItem toWikiItem(WikiPage page) {
        WikiPageItem item = new WikiPageItem();
        item.setPageId(page.getId());
        item.setTitle(page.getTitle());
        item.setPageType(page.getPageType());
        item.setContent(page.getContentMd());
        return item;
    }

    private RawChunkItem toRagItem(Document doc) {
        RawChunkItem item = new RawChunkItem();
        item.setText(doc.getText());
        Object fileName = doc.getMetadata().get("fileName");
        item.setFileName(fileName == null ? "" : String.valueOf(fileName));
        return item;
    }

    private String argumentText(ToolCall call, String key) {
        Object value = argument(call, key);
        return value == null ? null : String.valueOf(value);
    }

    private Object argument(ToolCall call, String key) {
        return call == null || call.getArguments() == null ? null : call.getArguments().get(key);
    }

    private int normalizeNumber(Object raw, int defaultValue, int max) {
        if (raw instanceof Number number) {
            return Math.max(1, Math.min(number.intValue(), max));
        }
        if (raw instanceof String text) {
            try {
                return Math.max(1, Math.min(Integer.parseInt(text), max));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
