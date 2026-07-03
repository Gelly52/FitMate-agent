package com.itgeo.fitmate.api.search.application.impl;

import com.itgeo.fitmate.api.agent.config.AgentProperties;
import com.itgeo.fitmate.api.search.application.WebFetchService;
import com.itgeo.fitmate.api.search.dto.WebFetchResult;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

/**
 * 网页抓取服务实现。
 * 使用 OkHttp 抓取页面，Jsoup 解析并提取正文纯文本。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebFetchServiceImpl implements WebFetchService {

    /** 正文最大字符数，超出截断。 */
    private static final int MAX_CONTENT_LENGTH = 8000;

    /** 仅允许 http/https 协议。 */
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);

    private final OkHttpClient okHttpClient;
    private final AgentProperties agentProperties;

    @Override
    public WebFetchResult fetch(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url 不能为空");
        }
        if (!URL_PATTERN.matcher(url).find()) {
            throw new IllegalArgumentException("仅支持 http/https 协议: " + url);
        }

        // 派生带工具超时的 client（复用共享 client 的连接池/拦截器配置）
        int timeoutSeconds = agentProperties != null && agentProperties.getToolTimeoutSeconds() != null
                ? agentProperties.getToolTimeoutSeconds() : 30;
        OkHttpClient timedClient = okHttpClient.newBuilder()
                .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder().url(url).get()
                .header("User-Agent", "FitMate-Agent/1.0")
                .build();

        try (Response response = timedClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("抓取失败, HTTP " + response.code());
            }
            if (response.body() == null) {
                throw new RuntimeException("响应体为空");
            }
            String contentType = response.header("Content-Type", "");
            if (!contentType.toLowerCase().contains("text/html")) {
                throw new RuntimeException("仅支持 HTML 页面, Content-Type: " + contentType);
            }
            String html = response.body().string();
            return parseAndBuild(url, html);
        } catch (IOException e) {
            throw new RuntimeException("抓取异常: " + e.getMessage(), e);
        }
    }

    private WebFetchResult parseAndBuild(String url, String html) {
        Document doc = Jsoup.parse(html);
        String title = doc.title() == null ? "" : doc.title().trim();
        String mainText = extractMainText(html);
        int originalLength = mainText.length();
        boolean truncated = originalLength > MAX_CONTENT_LENGTH;
        String content = truncated
                ? mainText.substring(0, MAX_CONTENT_LENGTH) + "\n\n...[已截断, 原文长度 " + originalLength + " 字符]"
                : mainText;
        return new WebFetchResult(url, title, content, originalLength, truncated);
    }

    /**
     * 从 HTML 中提取正文纯文本。
     * 策略：移除 script/style/nav/footer/header/aside/noscript，优先取 article/main，否则取 body。
     */
    String extractMainText(String html) {
        Document doc = Jsoup.parse(html);
        doc.select("script, style, nav, footer, header, aside, noscript").remove();

        Element main = doc.selectFirst("article");
        if (main == null) {
            main = doc.selectFirst("main");
        }
        if (main == null) {
            main = doc.body();
        }
        if (main == null) {
            return "";
        }
        String text = main.text();
        if (text == null) {
            return "";
        }
        // 压缩连续空白
        return text.replaceAll("[\\s\\u00A0]+", " ").trim();
    }
}
