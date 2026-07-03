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
