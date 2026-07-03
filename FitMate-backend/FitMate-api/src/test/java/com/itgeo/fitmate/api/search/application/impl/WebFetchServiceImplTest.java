package com.itgeo.fitmate.api.search.application.impl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebFetchServiceImpl 正文提取逻辑单元测试。
 * 通过反射调用 extractMainText 方法，避免依赖网络。
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
