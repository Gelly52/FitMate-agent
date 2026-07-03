# Web Search / Web Fetch 工具实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把联网搜索能力从"纯聊天路径的前置增强器"改造为 Agent Loop 中的两个工具 `web.search` 和 `web.fetch`，让模型可自主决策何时搜索、何时抓取网页全文，并由前端 `internetEnabled` 开关控制工具可见性。

**Architecture:** 新增两个 `ToolExecutor` 实现（`WebSearchToolExecutor` 复用现有 `SearXngService`；`WebFetchToolExecutor` 通过新 `WebFetchService` 用 OkHttp+Jsoup 抓取并解析网页正文为纯文本），在 `AgentLoopExecutor.resolveAllowedTools` 中按 `internetEnabled` 开关过滤这两个工具，复用与 `kb.search`/`rag.search` 一致的开关过滤模式。

**Tech Stack:** Java 17 / Spring Boot / OkHttp 4.12 / Jsoup（新增）/ Hutool / Lombok / JUnit 5 / Vue 3

---

## 背景与根因

当前联网搜索基于本地 SearXng（`http://127.0.0.1:6080/search`），实现为 `SearXngServiceImpl`。它只在纯聊天路径 `ChatServiceImpl.doInternetSearch` 中作为前置增强器被调用，**没有注册为 Agent Loop 工具**。`ChatServiceImpl.doAgentInternetSearch` 是死代码，Agent 主链路 `AgentLoopExecutor.run` 完全不调用它。因此 Agent 模式下即使打开 `internetEnabled` 开关，模型也看不到搜索能力，会回复"没有搜索功能"。

`chatEntity.internetEnabled` 在 `AgentLoopExecutor.resolveSourceType`（第 430-438 行）只用于给 SSE 消息打 `sourceType="internet"` 标签，实际不触发联网。

现有 5 个 Agent 工具：`date.now`、`kb.search`、`rag.search`、`body_metrics.query`、`training_log.query`，都实现 `ToolExecutor` 接口，通过 Spring Bean 自动注册到 `ToolRegistry`，再由 `fitmate.agent.enabled-tools` 白名单过滤。`kb.search`/`rag.search` 在 `resolveAllowedTools` 中按 `knowledgeBaseEnabled`/`ragEnabled` 动态过滤——本计划新增的 `web.search`/`web.fetch` 采用完全相同的模式。

---

## 文件结构

**新增文件：**
- `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/search/dto/WebFetchResult.java` — web.fetch 结果 DTO
- `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/search/application/WebFetchService.java` — 网页抓取服务契约
- `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/search/application/impl/WebFetchServiceImpl.java` — 抓取+Jsoup 正文提取实现
- `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/WebSearchToolExecutor.java` — `web.search` 工具执行器
- `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/WebFetchToolExecutor.java` — `web.fetch` 工具执行器
- `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/search/application/impl/WebFetchServiceImplTest.java` — 正文提取逻辑单元测试

**修改文件：**
- `FitMate-backend/FitMate-api/pom.xml` — 新增 Jsoup 依赖
- `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java` — `resolveAllowedTools` 新增 internet 过滤分支
- `FitMate-backend/FitMate-api/src/main/resources/application.yml` — `enabled-tools` 列表新增两个工具
- `FitMate-backend/FitMate-api/src/main/resources/prompts/agent-system.md` — 补充 web 工具使用说明
- `FitMate-frontend/src/pages/chat/components/ChatInput.vue` — 按钮文字 `Search` → `WebSearch`

**不在范围内：**
- 不改动 `SearXngServiceImpl`、`SearXngService`、`SearchResult`、`InternetController`
- 不改动纯聊天路径 `ChatServiceImpl.doInternetSearch`（与 Agent 工具模式并行存在）
- 不改动 MCP Server 工具体系
- 不改动前端 `internetSearchSelected` 状态机和 `internetEnabled` 传参逻辑

---

## Task 1: 添加 Jsoup 依赖

**Files:**
- Modify: `FitMate-backend/FitMate-api/pom.xml`（在 okhttp 依赖之后插入）

- [ ] **Step 1: 在 pom.xml 中添加 Jsoup 依赖**

在 `FitMate-backend/FitMate-api/pom.xml` 的 okhttp 依赖块（约第 113-117 行）之后，插入 Jsoup 依赖：

```xml
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
            <version>4.12.0</version>
        </dependency>
        <dependency>
            <groupId>org.jsoup</groupId>
            <artifactId>jsoup</artifactId>
            <version>1.17.2</version>
        </dependency>
```

- [ ] **Step 2: 验证依赖下载成功**

Run: `mvn -pl FitMate-backend/FitMate-api -am dependency:resolve -DincludeArtifactIds=jsoup`
Expected: BUILD SUCCESS，且输出中可见 jsoup:1.17.2 已解析。

- [ ] **Step 3: 编译验证**

Run: `mvn -pl FitMate-backend/FitMate-api -am compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add FitMate-backend/FitMate-api/pom.xml
git commit -m "chore: add jsoup dependency for web fetch html parsing"
```

---

## Task 2: 创建 WebFetchResult DTO

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/search/dto/WebFetchResult.java`

- [ ] **Step 1: 创建 WebFetchResult.java**

```java
package com.itgeo.fitmate.api.search.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * web.fetch 工具抓取网页后的结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebFetchResult {

    /** 抓取的目标 URL。 */
    private String url;
    /** 页面标题。 */
    private String title;
    /** 提取后的正文纯文本（已去除脚本/导航等噪音，可能被截断）。 */
    private String content;
    /** 正文长度（截断前原始长度，供模型判断是否完整）。 */
    private Integer contentLength;
    /** 是否因超长被截断。 */
    private Boolean truncated;
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl FitMate-backend/FitMate-api -am compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/search/dto/WebFetchResult.java
git commit -m "feat: add WebFetchResult dto for web fetch tool"
```

---

## Task 3: 创建 WebFetchService 接口

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/search/application/WebFetchService.java`

- [ ] **Step 1: 创建 WebFetchService.java**

```java
package com.itgeo.fitmate.api.search.application;

import com.itgeo.fitmate.api.search.dto.WebFetchResult;

/**
 * 网页抓取服务契约。
 * 负责抓取指定 URL 的 HTML 并提取正文纯文本。
 */
public interface WebFetchService {

    /**
     * 抓取指定 URL 并提取正文。
     *
     * @param url 目标 URL（仅支持 http/https）
     * @return 抓取并解析后的结果
     * @throws IllegalArgumentException URL 非法或不支持
     * @throws RuntimeException 抓取或解析失败
     */
    WebFetchResult fetch(String url);
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl FitMate-backend/FitMate-api -am compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/search/application/WebFetchService.java
git commit -m "feat: add WebFetchService interface"
```

---

## Task 4: 编写 WebFetchServiceImpl 正文提取的失败测试（TDD）

正文提取是核心复杂逻辑，先写测试固定预期行为。

**Files:**
- Create: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/search/application/impl/WebFetchServiceImplTest.java`

- [ ] **Step 1: 创建测试类**

```java
package com.itgeo.fitmate.api.search.application.impl;

import com.itgeo.fitmate.api.search.dto.WebFetchResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebFetchServiceImpl 正文提取逻辑单元测试。
 * 通过反射调用 private extractMainText 方法，避免依赖网络。
 */
class WebFetchServiceImplTest {

    private String callExtract(String html) throws Exception {
        WebFetchServiceImpl service = new WebFetchServiceImpl(null, null);
        Method method = WebFetchServiceImpl.class.getDeclaredMethod("extractMainText", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, html);
    }

    @Test
    void extractMainText_removesScriptAndStyle() throws Exception {
        String html = "<html><head><style>.a{color:red}</style></head>"
                + "<body><script>alert(1)</script><p>正文内容</p></body></html>";
        String text = callExtract(html);
        assertTrue(text.contains("正文内容"), "应保留正文");
        assertFalse(text.contains("alert"), "应移除 script 内容");
        assertFalse(text.contains("color:red"), "应移除 style 内容");
    }

    @Test
    void extractMainText_removesNavFooterAside() throws Exception {
        String html = "<html><body>"
                + "<nav>导航菜单</nav>"
                + "<article><p>文章主体</p></article>"
                + "<aside>侧边栏</aside>"
                + "<footer>页脚</footer>"
                + "</body></html>";
        String text = callExtract(html);
        assertTrue(text.contains("文章主体"), "应保留 article 主体");
        assertFalse(text.contains("导航菜单"), "应移除 nav");
        assertFalse(text.contains("侧边栏"), "应移除 aside");
        assertFalse(text.contains("页脚"), "应移除 footer");
    }

    @Test
    void extractMainText_prefersArticleOverBody() throws Exception {
        String html = "<html><body>"
                + "<div>外层噪音</div>"
                + "<article><p>文章正文</p></article>"
                + "</body></html>";
        String text = callExtract(html);
        assertTrue(text.contains("文章正文"));
    }

    @Test
    void extractMainText_collapsesWhitespace() throws Exception {
        String html = "<html><body><p>第一段</p><p>第二段</p></body></html>";
        String text = callExtract(html);
        assertTrue(text.contains("第一段"));
        assertTrue(text.contains("第二段"));
        assertFalse(text.contains("  "), "不应有连续多空格");
    }

    @Test
    void extractMainText_emptyHtmlReturnsEmpty() throws Exception {
        String text = callExtract("<html><body></body></html>");
        assertNotNull(text);
        assertTrue(text.isEmpty() || text.isBlank(), "空页面应返回空白");
    }
}
```

- [ ] **Step 2: 运行测试验证失败（extractMainText 方法尚不存在）**

Run: `mvn -pl FitMate-backend/FitMate-api test -Dtest=WebFetchServiceImplTest`
Expected: 编译失败（`extractMainText` 方法不存在）或测试全部 FAIL。

---

## Task 5: 实现 WebFetchServiceImpl

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/search/application/impl/WebFetchServiceImpl.java`

- [ ] **Step 1: 创建 WebFetchServiceImpl.java**

```java
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
```

- [ ] **Step 2: 运行测试验证通过**

Run: `mvn -pl FitMate-backend/FitMate-api test -Dtest=WebFetchServiceImplTest`
Expected: 5 个测试全部 PASS。

- [ ] **Step 3: 编译验证**

Run: `mvn -pl FitMate-backend/FitMate-api -am compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/search/application/impl/WebFetchServiceImpl.java
git add FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/search/application/impl/WebFetchServiceImplTest.java
git commit -m "feat: implement WebFetchServiceImpl with jsoup-based main text extraction"
```

---

## Task 6: 创建 WebSearchToolExecutor

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/WebSearchToolExecutor.java`

- [ ] **Step 1: 创建 WebSearchToolExecutor.java**

```java
package com.itgeo.fitmate.api.agent.tool;

import cn.hutool.core.util.StrUtil;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.search.application.SearXngService;
import com.itgeo.fitmate.api.search.dto.SearchResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * web.search 工具执行器。
 * 复用 SearXngService 执行联网搜索，返回标题/URL/摘要列表。
 * 受 internetEnabled 开关控制（在 AgentLoopExecutor.resolveAllowedTools 中过滤）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebSearchToolExecutor implements ToolExecutor {

    private final SearXngService searXngService;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "web.search",
                "联网搜索引擎，获取互联网上的最新信息。当用户问题涉及知识库外的实时内容、新闻、最新数据或需要外部权威资料时调用此工具。"
                        + "返回结果为标题、链接、摘要列表。参数: {\"query\": \"搜索词\"}",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}",
                true
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        String query = argumentText(call, "query");
        if (StrUtil.isBlank(query)) {
            return ToolResult.error("query 不能为空");
        }
        try {
            List<SearchResult> results = searXngService.search(query);
            if (results == null || results.isEmpty()) {
                return ToolResult.ok("未检索到相关结果", List.of());
            }
            String summary = String.format("联网搜索命中 %d 条结果", results.size());
            return ToolResult.ok(summary, results);
        } catch (Exception e) {
            log.warn("web.search 执行失败, query={}", query, e);
            return ToolResult.error("联网搜索失败: " + e.getMessage());
        }
    }

    private String argumentText(ToolCall call, String key) {
        Object value = call == null || call.getArguments() == null ? null : call.getArguments().get(key);
        return value == null ? null : String.valueOf(value);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl FitMate-backend/FitMate-api -am compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/WebSearchToolExecutor.java
git commit -m "feat: add web.search tool executor"
```

---

## Task 7: 创建 WebFetchToolExecutor

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/WebFetchToolExecutor.java`

- [ ] **Step 1: 创建 WebFetchToolExecutor.java**

```java
package com.itgeo.fitmate.api.agent.tool;

import cn.hutool.core.util.StrUtil;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.search.application.WebFetchService;
import com.itgeo.fitmate.api.search.dto.WebFetchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * web.fetch 工具执行器。
 * 抓取指定 URL 的网页正文（纯文本），用于在 web.search 后深入获取某条结果页面的全文。
 * 受 internetEnabled 开关控制（在 AgentLoopExecutor.resolveAllowedTools 中过滤）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebFetchToolExecutor implements ToolExecutor {

    private final WebFetchService webFetchService;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "web.fetch",
                "抓取指定 URL 网页的正文纯文本。先用 web.search 找到相关链接，再对本条最有价值的结果调用此工具获取全文。"
                        + "仅支持 http/https 协议的 HTML 页面，返回标题和正文（超过 8000 字符会截断）。"
                        + "参数: {\"url\": \"https://...\"}",
                "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}",
                true
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        String url = argumentText(call, "url");
        if (StrUtil.isBlank(url)) {
            return ToolResult.error("url 不能为空");
        }
        try {
            WebFetchResult result = webFetchService.fetch(url);
            if (result == null) {
                return ToolResult.error("抓取结果为空");
            }
            String summary = String.format("已抓取: %s（标题: %s, 正文 %d 字符%s）",
                    result.getUrl(),
                    StrUtil.blankToDefault(result.getTitle(), "(无标题)"),
                    result.getContentLength() == null ? 0 : result.getContentLength(),
                    Boolean.TRUE.equals(result.getTruncated()) ? ", 已截断" : "");
            return ToolResult.ok(summary, result);
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            log.warn("web.fetch 执行失败, url={}", url, e);
            return ToolResult.error("网页抓取失败: " + e.getMessage());
        }
    }

    private String argumentText(ToolCall call, String key) {
        Object value = call == null || call.getArguments() == null ? null : call.getArguments().get(key);
        return value == null ? null : String.valueOf(value);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl FitMate-backend/FitMate-api -am compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/WebFetchToolExecutor.java
git commit -m "feat: add web.fetch tool executor"
```

---

## Task 8: 修改 AgentLoopExecutor 添加 internet 开关过滤

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java`（`resolveAllowedTools` 方法，第 291-308 行）

- [ ] **Step 1: 修改 resolveAllowedTools 方法**

将 `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java` 的 `resolveAllowedTools` 方法（第 291-308 行）替换为：

```java
    private List<ToolDescriptor> resolveAllowedTools(AgentExecuteContext context) {
        boolean knowledgeBaseEnabled = context.getChatEntity() != null
                && !Boolean.FALSE.equals(context.getChatEntity().getKnowledgeBaseEnabled());
        boolean ragEnabled = knowledgeBaseEnabled
                && context.getChatEntity() != null
                && Boolean.TRUE.equals(context.getChatEntity().getRagEnabled());
        boolean internetEnabled = context.getChatEntity() != null
                && Boolean.TRUE.equals(context.getChatEntity().getInternetEnabled());
        return toolRegistry.allowedDescriptors().stream()
                .filter(tool -> {
                    if ("kb.search".equals(tool.getName())) {
                        return knowledgeBaseEnabled;
                    }
                    if ("rag.search".equals(tool.getName())) {
                        return ragEnabled;
                    }
                    if ("web.search".equals(tool.getName()) || "web.fetch".equals(tool.getName())) {
                        return internetEnabled;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl FitMate-backend/FitMate-api -am compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/core/AgentLoopExecutor.java
git commit -m "feat: filter web.search/web.fetch tools by internetEnabled switch"
```

---

## Task 9: 更新 application.yml enabled-tools

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/resources/application.yml`（`fitmate.agent.enabled-tools` 列表，约第 86-91 行）

- [ ] **Step 1: 在 enabled-tools 列表末尾新增 web.search 和 web.fetch**

将 `FitMate-backend/FitMate-api/src/main/resources/application.yml` 第 86-91 行：

```yaml
    enabled-tools:
      - date.now
      - kb.search
      - rag.search
      - body_metrics.query
      - training_log.query
```

替换为：

```yaml
    enabled-tools:
      - date.now
      - kb.search
      - rag.search
      - body_metrics.query
      - training_log.query
      - web.search
      - web.fetch
```

- [ ] **Step 2: 同步更新 application-dev.yml（若该文件也包含 enabled-tools）**

Run: `grep -n "enabled-tools" FitMate-backend/FitMate-api/src/main/resources/application-dev.yml`

若该文件也显式列出 `enabled-tools`，则同步追加 `web.search` 和 `web.fetch` 两行；若未显式列出（继承主配置），则无需改动。

- [ ] **Step 3: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/resources/application.yml
git add FitMate-backend/FitMate-api/src/main/resources/application-dev.yml
git commit -m "chore: register web.search and web.fetch in enabled-tools"
```

---

## Task 10: 更新 agent-system.md 提示词

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/resources/prompts/agent-system.md`

- [ ] **Step 1: 在「## 工具调用原则」节后追加「## 联网搜索规则」**

在 `FitMate-backend/FitMate-api/src/main/resources/prompts/agent-system.md` 末尾（第 33 行之后）追加：

```markdown

## 联网搜索规则

1. 当用户问题涉及知识库外的实时信息、最新数据、新闻或需要外部权威资料时，先调用 `web.search` 获取相关链接列表。

2. `web.search` 返回的是标题+链接+摘要。若摘要已足以回答，直接进入 `final`；若需要某条结果的全文，再调用 `web.fetch` 抓取该 URL。

3. 同一 URL 不要重复调用 `web.fetch`。一次 `web.search` 最多再对 1-2 条最有价值的结果调用 `web.fetch`，避免浪费。

4. 若 `web.search` 未返回相关结果，应直接进入 `final` 并诚实告知用户未找到相关信息，不得编造内容。

5. 联网获取的信息应在 `final` 答案中标注来源（引用对应 URL）。
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-backend/FitMate-api/src/main/resources/prompts/agent-system.md
git commit -m "docs: add web search/fetch guidance to agent system prompt"
```

---

## Task 11: 前端 ChatInput.vue 按钮文字 Search → WebSearch

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/components/ChatInput.vue`（第 40 行）

- [ ] **Step 1: 修改按钮文字**

将 `FitMate-frontend/src/pages/chat/components/ChatInput.vue` 第 40 行：

```vue
        Search
```

替换为：

```vue
        WebSearch
```

- [ ] **Step 2: Commit**

```bash
git add FitMate-frontend/src/pages/chat/components/ChatInput.vue
git commit -m "ui: rename search toggle button to WebSearch"
```

---

## Task 12: 端到端手动验证

**Files:** 无（仅运行验证）

- [ ] **Step 1: 完整编译后端**

Run: `mvn -pl FitMate-backend/FitMate-api -am clean package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 2: 启动后端服务**

确认 SearXng 已在 `http://127.0.0.1:6080/search` 运行，然后启动 FitMate-api 服务。
Expected: 服务正常启动，日志无报错。

- [ ] **Step 3: 启动前端**

在 `FitMate-frontend` 目录运行 `npm run dev`，打开聊天页。
Expected: 输入框上方的按钮文字显示为 "WebSearch"（原 "Search"）。

- [ ] **Step 4: 验证开关关闭时模型看不到 web 工具**

在聊天页关闭 "WebSearch" 按钮（非高亮），输入"帮我搜索一下今天北京的天气"。
Expected: 模型回复中不包含 web.search/web.fetch 调用，且会说明当前无联网能力（与原有 3 个工具一致），或基于已有知识回答。

- [ ] **Step 5: 验证开关打开时模型可调用 web.search**

打开 "WebSearch" 按钮（高亮），输入"帮我搜索一下今天北京的天气"。
Expected: Agent 执行轨迹中出现 `web.search` 工具调用步骤，观察结果包含搜索结果列表；最终答案包含天气信息并标注来源 URL。

- [ ] **Step 6: 验证 web.fetch 抓取全文**

打开 "WebSearch" 按钮，输入"搜索一下 Java 21 的新特性，并详细说明虚拟线程"。
Expected: Agent 先调用 `web.search`，再对某条结果调用 `web.fetch` 获取全文，最终答案基于抓取的正文内容回答。

- [ ] **Step 7: 验证非 HTML URL 的错误处理**

打开 "WebSearch" 按钮，输入"抓取这个网址的内容: https://example.com/nonexistent"。
Expected: `web.fetch` 返回错误结果（HTTP 404 或类似），Agent 在最终答案中告知用户抓取失败。

---

## 验收标准

1. ✅ Agent 模式下打开 "WebSearch" 开关后，模型能调用 `web.search` 和 `web.fetch` 工具
2. ✅ 关闭 "WebSearch" 开关后，这两个工具从可用工具列表中消失，模型不再尝试调用
3. ✅ `web.search` 复用现有 SearXng 服务，返回标题/URL/摘要列表
4. ✅ `web.fetch` 抓取 URL 并返回 Jsoup 提取的正文纯文本（超 8000 字符截断）
5. ✅ 前端按钮文字显示为 "WebSearch"
6. ✅ 现有 5 个工具（date.now/kb.search/rag.search/body_metrics.query/training_log.query）行为不受影响
7. ✅ WebFetchServiceImpl 正文提取单元测试全部通过
8. ✅ 纯聊天路径 `doInternetSearch` 不受影响（与 Agent 工具模式并行存在）

---

## 风险与回滚

**风险 1：SearXng 服务未运行**
- 表现：`web.search` 抛 RuntimeException，工具返回错误
- 处理：错误已被 `WebSearchToolExecutor.execute` 捕获并转为 `ToolResult.error`，模型会收到错误信息并据此回复用户。不影响 Agent 主流程。

**风险 2：某些网站反爬或非 HTML 内容**
- 表现：`web.fetch` 返回 403 或 Content-Type 非 HTML
- 处理：已在实现中校验 Content-Type，非 HTML 抛异常被工具执行器捕获转错误结果。

**风险 3：Jsoup 依赖冲突**
- 表现：编译或启动时 NoClassDefFoundError
- 处理：Jsoup 1.17.2 无传递依赖，与项目现有依赖无冲突。若发生，检查依赖树 `mvn dependency:tree`。

**回滚方案：**
所有改动均为新增文件 + 少量修改，回滚步骤：
1. 从 `application.yml` 的 `enabled-tools` 移除 `web.search`/`web.fetch`（即使代码存在也不会被加载）
2. 或 `git revert` 相关提交
