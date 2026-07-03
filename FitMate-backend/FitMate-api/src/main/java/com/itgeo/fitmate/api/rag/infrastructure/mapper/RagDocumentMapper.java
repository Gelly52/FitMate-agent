package com.itgeo.fitmate.api.rag.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.rag.infrastructure.entity.RagDocument;

/**
 * RAG 文档索引 Mapper。
 *
 * 说明：
 * 1. 仅负责 t_rag_document 的基础 CRUD；
 * 2. 当前查询场景主要是按 userId 拉取文档列表。
 */
public interface RagDocumentMapper extends BaseMapper<RagDocument> {
}
