package com.itgeo.fitmate.api.prompt;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * 提示词模板管理器。
 * <p>
 * 所有提示词统一在 prompts/ 目录下分文件管理，本类负责加载文件并提供占位符替换。
 * 文件列表：
 * - prompts/agent-system.md       → Agent 主系统提示词（由 AgentPromptBuilder 直接加载）
 * - prompts/wiki-schema.md         → Wiki 结构定义（由 WikiCompileServiceImpl 直接加载）
 * - prompts/wiki-context.md        → Wiki 预检索上下文区块
 * - prompts/wiki-compile.md        → Wiki 编译提示词
 * - prompts/memory-extract.md      → 用户记忆提取后缀
 * - prompts/profile-build.md       → 用户画像生成
 * - prompts/context-compress.md    → 对话压缩（由 ContextCompressService 直接加载）
 */
@Component
@Slf4j
public class PromptTemplateManager {

    private static final String WIKI_CONTEXT_PATH = "prompts/wiki-context.md";
    private static final String WIKI_COMPILE_PATH = "prompts/wiki-compile.md";
    private static final String MEMORY_EXTRACT_PATH = "prompts/memory-extract.md";
    private static final String PROFILE_BUILD_PATH = "prompts/profile-build.md";
    private static final String CONTEXT_COMPRESS_PATH = "prompts/context-compress.md";

    private String wikiContextTemplate;
    private String wikiCompileTemplate;
    private String memoryExtractTemplate;
    private String profileBuildTemplate;
    private String contextCompressTemplate;

    @PostConstruct
    public void init() {
        wikiContextTemplate = loadResource(WIKI_CONTEXT_PATH);
        wikiCompileTemplate = loadResource(WIKI_COMPILE_PATH);
        memoryExtractTemplate = loadResource(MEMORY_EXTRACT_PATH);
        profileBuildTemplate = loadResource(PROFILE_BUILD_PATH);
        contextCompressTemplate = loadResource(CONTEXT_COMPRESS_PATH);
    }

    private String loadResource(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("提示词文件读取失败: " + path, e);
        }
    }

    // ==================== Wiki ====================

    /**
     * 构建 Wiki 预检索上下文区块文本，用于注入 Agent 首轮决策 prompt。
     */
    public String buildWikiPrompt(String wikiContent, String question) {
        return wikiContextTemplate
                .replace("{wikiContent}", wikiContent == null ? "(无命中)" : wikiContent)
                .replace("{question}", question == null ? "" : question);
    }

    /**
     * 构建 Wiki 编译提示词。
     */
    public String buildWikiCompilePrompt(String schemaContent, String rawContent, String indexContent) {
        return wikiCompileTemplate
                .replace("{schema_content}", schemaContent == null ? "" : schemaContent)
                .replace("{raw_content}", rawContent == null ? "" : rawContent)
                .replace("{index_content}", indexContent == null ? "" : indexContent);
    }

    // ==================== Memory Extract ====================

    /**
     * 构建用户记忆提取提示词后缀，追加在 Agent 决策 prompt 前缀之后。
     */
    public String buildMemoryExtractSuffix(String existingMemories) {
        return memoryExtractTemplate.replace("{existing_memories}",
                existingMemories == null || existingMemories.isBlank() ? "（无）" : existingMemories);
    }

    // ==================== Profile Build ====================

    /**
     * 构建用户画像生成提示词。
     */
    public String buildProfileBuildPrompt(String facts, String episodics, String snapshot, String insights) {
        return profileBuildTemplate
                .replace("{facts}", facts == null ? "无" : facts)
                .replace("{episodics}", episodics == null ? "无" : episodics)
                .replace("{snapshot}", snapshot == null ? "无" : snapshot)
                .replace("{insights}", insights == null ? "无" : insights);
    }

    // ==================== Context Compress ====================

    /**
     * 构建对话压缩提示词。
     *
     * @param lastSummary 既有摘要文本，无摘要时传 null
     * @param dialog      待压缩的对话 JSON
     * @return 填充后的 prompt
     */
    public String buildContextCompressPrompt(String lastSummary, String dialog) {
        return contextCompressTemplate
                .replace("{last_summary}", lastSummary == null || lastSummary.isBlank() ? "（无）" : lastSummary)
                .replace("{dialog}", dialog == null ? "[]" : dialog);
    }
}
