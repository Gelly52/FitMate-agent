package com.itgeo.fitmate.api.rag.infrastructure.chunking;

import java.util.List;
import org.springframework.ai.transformer.splitter.TextSplitter;

/**
 * 自定义文本切分器，按连续空行拆分文本段落。
 */
public class CustomTextSplitter extends TextSplitter {
    /**
     * 适配父类切分入口，返回拆分后的段落列表。
     *
     * @param text 原始文本
     * @return 段落列表
     */
    @Override
    protected List<String> splitText(String text) {
        return List.of(split(text));
    }

    /**
     * 按连续空行拆分文本。
     *
     * @param text 原始文本
     * @return 段落数组
     */
    public String[] split(String text) {
        return text.split("\\s*\\R\\s*\\R\\s*");
    }
}
