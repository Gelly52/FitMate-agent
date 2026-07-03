package com.itgeo.fitmate.api.rag.infrastructure.parser;

/**
 * 文档解析器接口 - 将不同格式的文档统一解析为纯文本
 */
public interface DocumentParser {

    /**
     * 判断是否支持该文件扩展名
     * @param fileExtension 文件扩展名（不含点号），如 "pdf"、"txt"
     */
    boolean supports(String fileExtension);

    /**
     * 将文档解析为纯文本
     * @param filePath 文件在磁盘上的绝对路径
     * @return 解析后的纯文本内容
     */
    String parse(String filePath) throws Exception;
}
