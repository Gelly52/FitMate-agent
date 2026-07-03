package com.itgeo.fitmate.api.search.application.impl;

import com.itgeo.fitmate.api.search.dto.SearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BingSearchServiceImpl HTML 解析逻辑单元测试。
 * 通过构造模拟 Bing 搜索结果页 HTML，验证 parseBingResults 解析正确性。
 */
class BingSearchServiceImplTest {

    private BingSearchServiceImpl newService() {
        BingSearchServiceImpl service = new BingSearchServiceImpl(null);
        // 反射设置 resultCounts 默认值（@Value 未注入时的兜底）
        try {
            java.lang.reflect.Field f = BingSearchServiceImpl.class.getDeclaredField("resultCounts");
            f.setAccessible(true);
            f.setInt(service, 10);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return service;
    }

    @Test
    void parseBingResults_extractsTitleUrlAndSnippet() {
        String html = "<html><body><ol id=\"b_results\">"
                + "<li class=\"b_algo\">"
                + "<h2><a href=\"https://example.com/java\">Java 21 虚拟线程实战</a></h2>"
                + "<div class=\"b_caption\"><p>本文介绍 Java 21 虚拟线程的用法与性能提升。</p></div>"
                + "</li>"
                + "<li class=\"b_algo\">"
                + "<h2><a href=\"https://blog.example.com/vt\">虚拟线程源码解析</a></h2>"
                + "<div class=\"b_caption\"><p>深入虚拟线程实现原理。</p></div>"
                + "</li>"
                + "</ol></body></html>";

        List<SearchResult> results = newService().parseBingResults(html);

        assertEquals(2, results.size());
        assertEquals("Java 21 虚拟线程实战", results.get(0).getTitle());
        assertEquals("https://example.com/java", results.get(0).getUrl());
        assertTrue(results.get(0).getContent().contains("Java 21 虚拟线程"));
        assertEquals("虚拟线程源码解析", results.get(1).getTitle());
    }

    @Test
    void parseBingResults_respectsResultCountsLimit() {
        StringBuilder html = new StringBuilder("<html><body><ol id=\"b_results\">");
        for (int i = 0; i < 20; i++) {
            html.append("<li class=\"b_algo\"><h2><a href=\"https://x.com/")
                    .append(i).append("\">结果").append(i).append("</a></h2>")
                    .append("<div class=\"b_caption\"><p>摘要").append(i).append("</p></div></li>");
        }
        html.append("</ol></body></html>");

        BingSearchServiceImpl service = newService();
        List<SearchResult> results = service.parseBingResults(html.toString());

        assertEquals(10, results.size(), "应受 resultCounts 限制为 10 条");
    }

    @Test
    void parseBingResults_skipsItemsWithoutTitleLink() {
        String html = "<html><body><ol id=\"b_results\">"
                + "<li class=\"b_algo\"><div>无标题的项</div></li>"
                + "<li class=\"b_algo\"><h2><a href=\"https://ok.com\">有效结果</a></h2>"
                + "<div class=\"b_caption\"><p>摘要</p></div></li>"
                + "</ol></body></html>";

        List<SearchResult> results = newService().parseBingResults(html);

        assertEquals(1, results.size(), "应跳过无标题链接的项");
        assertEquals("有效结果", results.get(0).getTitle());
    }

    @Test
    void parseBingResults_emptyHtmlReturnsEmpty() {
        List<SearchResult> results = newService().parseBingResults("<html><body></body></html>");
        assertNotNull(results);
        assertTrue(results.isEmpty(), "空 HTML 应返回空列表");
    }

    @Test
    void parseBingResults_fallsBackToLineclampForSnippet() {
        String html = "<html><body><ol id=\"b_results\">"
                + "<li class=\"b_algo\">"
                + "<h2><a href=\"https://x.com\">标题</a></h2>"
                + "<p class=\"b_lineclamp4\">lineclamp 摘要内容</p>"
                + "</li>"
                + "</ol></body></html>";

        List<SearchResult> results = newService().parseBingResults(html);

        assertEquals(1, results.size());
        assertFalse(results.get(0).getContent().isBlank(), "应能从 b_lineclamp 提取摘要");
    }
}
