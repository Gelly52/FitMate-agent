package com.itgeo.fitmate.api.rag.application;

import com.itgeo.fitmate.api.rag.dto.RagConfigResponse;
import com.itgeo.fitmate.api.rag.dto.RagDocumentItem;
import com.itgeo.fitmate.api.rag.dto.RagSearchResult;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;

/**
 * 文档向量化与检索服务。
 *
 * 说明：
 * 1. 当前主要服务手动 RAG 上传、检索、文档列表与配置读取接口；
 * 2. `userId` 是文档归属与检索隔离的核心上下文字段；
 * 3. 检索结果应在向量库检索侧完成用户范围约束，而不是在返回后再做内存过滤。
 */
public interface DocumentService {

    /**
     * 读取上传文件、解析多种格式为纯文本、补齐 metadata、切分文档并写入向量库。
     *
     * @param resource 文本文件资源
     * @param fileName 文件名
     * @param userId 当前用户主键
     * @return 原始读取出的文档列表
     */
    List<Document> loadText(Resource resource, String fileName, Long userId);

    /**
     * 根据问题检索当前用户可见的文档片段。
     *
     * @param question 提问内容
     * @param userId 当前用户主键
     * @param topK 期望返回数量
     * @return 已按当前用户范围约束后的检索结果
     */
    List<Document> doSearch(String question, Long userId, Integer topK);


    RagSearchResult doSearchWithTrace(String question, Long userId, Integer topK);

    /**
     * 查询当前用户已上传的 RAG 文档列表。
     *
     * @param userId 当前用户主键
     * @return 当前用户的文档元信息列表
     */
    List<RagDocumentItem> listUserDocuments(Long userId);

    /**
     * 删除指定 RAG 文档及其全部向量/关键词索引。
     * <p>
     * 删除前校验 userId 归属，防止越权删除他人文档。
     *
     * @param userId 当前用户主键（用于归属校验）
     * @param docId  待删除文档主键
     */
    void deleteDocument(Long userId, Long docId);

    /**
     * 获取当前手动 RAG 配置快照。
     *
     * @return 代码侧生效中的 RAG 配置
     */
    RagConfigResponse getRagConfig();

}
