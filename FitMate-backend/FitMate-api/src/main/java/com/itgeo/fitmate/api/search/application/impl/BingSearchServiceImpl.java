package com.itgeo.fitmate.api.search.application.impl;

import com.itgeo.fitmate.api.search.application.WebSearchService;
import com.itgeo.fitmate.api.search.dto.SearchResult;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Bing 中国联网搜索服务实现。
 * <p>
 * 直接抓取 https://cn.bing.com/search?q=xxx 结果页，用 Jsoup 解析 li.b_algo 节点。
 * 不依赖 SearXng，国内访问稳定。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BingSearchServiceImpl implements WebSearchService {

    private static final String BING_SEARCH_URL = "https://cn.bing.com/search";

    private final OkHttpClient okHttpClient;

    @Value("${internet.websearch.bing.counts:10}")
    private int resultCounts;

    @Override
    public List<SearchResult> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = BING_SEARCH_URL + "?q=" + encodedQuery + "&count=" + resultCounts + "&setlang=zh-CN";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Bing 搜索请求失败, HTTP {}", response.code());
                return List.of();
            }
            if (response.body() == null) {
                return List.of();
            }
            String html = response.body().string();
            List<SearchResult> results = parseBingResults(html);
            log.info("Bing 搜索 query={}, 命中 {} 条结果", query, results.size());
            return results;
        } catch (IOException e) {
            log.warn("Bing 搜索异常, query={}", query, e);
            return List.of();
        }
    }

    /**
     * 解析 Bing 搜索结果页 HTML。
     * 策略：选取 li.b_algo 节点，提取 h2>a（标题+链接）与摘要文本。
     * 摘要优先取 .b_caption p，其次 .b_lineclamp*，最后取整个 li 纯文本。
     */
    List<SearchResult> parseBingResults(String html) {
        List<SearchResult> results = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements items = doc.select("li.b_algo");
        for (Element item : items) {
            if (results.size() >= resultCounts) {
                break;
            }
            Element titleLink = item.selectFirst("h2 > a");
            if (titleLink == null) {
                continue;
            }
            String title = titleLink.text();
            String link = titleLink.absUrl("href");
            if (link == null || link.isEmpty()) {
                link = titleLink.attr("href");
            }
            String snippet = extractSnippet(item);
            if (title.isBlank() || link.isBlank()) {
                continue;
            }
            // score 用序号倒序，越靠前分越高
            double score = 1.0 - (results.size() * 0.05);
            results.add(new SearchResult(title, link, snippet, Math.max(score, 0.0)));
        }
        return results;
    }

    private String extractSnippet(Element item) {
        // 优先 .b_caption p
        Element caption = item.selectFirst(".b_caption p");
        if (caption != null && !caption.text().isBlank()) {
            return caption.text();
        }
        // 其次 b_lineclamp 系列
        Element lineclamp = item.selectFirst("[class^=b_lineclamp]");
        if (lineclamp != null && !lineclamp.text().isBlank()) {
            return lineclamp.text();
        }
        // 兜底：移除标题后取剩余文本
        String fullText = item.text();
        String title = item.selectFirst("h2") != null ? item.selectFirst("h2").text() : "";
        if (!title.isBlank() && fullText.startsWith(title)) {
            fullText = fullText.substring(title.length()).trim();
        }
        return fullText;
    }
}
