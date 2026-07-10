package com.itgeo.fitmate.api.rag.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.rag.application.DocumentService;
import com.itgeo.fitmate.api.rag.application.RagBenchmarkService;
import com.itgeo.fitmate.api.rag.dto.RagBenchmarkEvaluateRequest;
import com.itgeo.fitmate.api.rag.infrastructure.entity.RagDocument;
import com.itgeo.fitmate.api.rag.infrastructure.mapper.RagDocumentMapper;
import com.itgeo.fitmate.api.rag.infrastructure.parser.DocumentParserFactory;
import com.itgeo.fitmate.api.wiki.application.WikiCompileService;
import com.itgeo.fitmate.api.wiki.application.impl.WikiCompileAsyncRunner;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiCompileJob;
import com.itgeo.fitmate.common.response.LeeResult;
import jakarta.annotation.Resource;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 手动 RAG 文档上传与检索控制器。
 * <p>
 * 提供手动上传文档、手动检索片段等入口。
 * 正式对话主链路已统一收敛到 Agent 模式（/agent/execute），本控制器不再提供独立问答入口。
 */
@Slf4j
@RestController
@RequestMapping("rag")
public class RagController {

    @Resource
    private DocumentService documentService;

    @Resource
    private RagBenchmarkService ragBenchmarkService;

    @Resource
    private DocumentParserFactory documentParserFactory;

    @Resource
    private RagDocumentMapper ragDocumentMapper;

    @Resource
    private WikiCompileService wikiCompileService;

    @Resource
    private WikiCompileAsyncRunner wikiCompileAsyncRunner;

    /**
     * 上传当前用户自己的 RAG 文档。
     */
    @PostMapping("/uploadRagDoc")
    public LeeResult uploadRagDoc(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return LeeResult.errorMsg("上传文件不能为空");
            }

            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isBlank()) {
                return LeeResult.errorMsg("文件名不能为空");
            }

            if (!documentParserFactory.isSupported(fileName)) {
                return LeeResult.errorMsg("不支持的文件格式，当前仅支持: "
                        + String.join(", ", documentParserFactory.getSupportedExtensions()));
            }

            Long userId = UserContextHolder.getRequired().getUserId();
            List<Document> documentList = documentService.loadText(
                    file.getResource(),
                    fileName,
                    userId
            );

            // 投递 Wiki 编译任务（异步），失败不影响 RAG 上传
            try {
                RagDocument latestDoc = ragDocumentMapper.selectOne(
                        new LambdaQueryWrapper<RagDocument>()
                                .eq(RagDocument::getUserId, userId)
                                .orderByDesc(RagDocument::getCreatedAt)
                                .last("LIMIT 1"));
                if (latestDoc != null) {
                    Long spaceId = wikiCompileService.getOrCreateUserSpace(userId);
                    WikiCompileJob job = wikiCompileService.submitCompileJob(
                            spaceId, latestDoc.getId(), userId);
                    wikiCompileAsyncRunner.runAsync(job.getId());
                }
            } catch (Exception e) {
                log.warn("Wiki 编译任务投递失败（不影响 RAG 上传）: {}", e.getMessage());
            }

            return LeeResult.ok(documentList);
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("RAG文档上传失败", e);
            return LeeResult.errorException("RAG文档上传失败");
        }
    }

    /**
     * 手动检索当前用户的 RAG 文档片段。
     */
    @GetMapping("/doSearch")
    public LeeResult doSearch(@RequestParam String question) {
        Long userId = UserContextHolder.getRequired().getUserId();
        return LeeResult.ok(documentService.doSearch(question, userId, 4));
    }

    /**
     * 查询当前用户已上传的 RAG 文档列表。
     */
    @GetMapping("/docs")
    public LeeResult getUploadedDocs() {
        Long userId = UserContextHolder.getRequired().getUserId();
        return LeeResult.ok(documentService.listUserDocuments(userId));
    }

    /**
     * 删除当前用户指定的 RAG 文档（同步清理向量/关键词索引）。
     */
    @DeleteMapping("/docs/{docId}")
    public LeeResult deleteRagDoc(@PathVariable Long docId) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            documentService.deleteDocument(userId, docId);
            return LeeResult.ok();
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("RAG文档删除失败 docId={}", docId, e);
            return LeeResult.errorException("RAG文档删除失败");
        }
    }

    /**
     * 兼容旧调用方式的 RAG 配置读取入口。
     */
    @PostMapping("/config")
    public LeeResult ragConfig() {
        return LeeResult.ok(documentService.getRagConfig());
    }

    /**
     * 查询当前手动 RAG 配置。
     */
    @GetMapping("/config")
    public LeeResult getRagConfig() {
        return LeeResult.ok(documentService.getRagConfig());
    }

    /**
     * 执行 RAG benchmark 评测。
     */
    @PostMapping("/benchmark/evaluate")
    public LeeResult benchmarkEvaluate(@RequestBody RagBenchmarkEvaluateRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(ragBenchmarkService.evaluate(userId, request));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("RAG benchmark 评测失败", e);
            return LeeResult.errorException("RAG benchmark 评测失败");
        }
    }
}
