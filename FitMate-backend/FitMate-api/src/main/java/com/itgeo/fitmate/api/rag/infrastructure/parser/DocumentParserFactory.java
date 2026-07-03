package com.itgeo.fitmate.api.rag.infrastructure.parser;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DocumentParserFactory {
    @Autowired
    private List<DocumentParser> parsers;

    /**
     * 根据文件名获取对应的文档解析器
     */
    public DocumentParser getParser(String fileName) {
        String ext = extractExtension(fileName);
        return parsers.stream()
                .filter(p -> p.supports(ext))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的文件格式: " + ext));
    }

    /**
     * 检查是否支持该文件格式
     */
    public boolean isSupported(String fileName) {
        try {
            String ext = extractExtension(fileName);
            return parsers.stream().anyMatch(p -> p.supports(ext));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 获取所有支持的文件扩展名
     */
    public List<String> getSupportedExtensions() {
        return List.of("txt", "pdf", "docx", "md", "markdown", "csv", "png", "jpg", "jpeg", "webp", "bmp");
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            throw new IllegalArgumentException("文件名缺少扩展名: " + fileName);
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }
}
