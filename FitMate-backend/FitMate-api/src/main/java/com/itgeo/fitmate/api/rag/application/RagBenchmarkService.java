package com.itgeo.fitmate.api.rag.application;

import com.itgeo.fitmate.api.rag.dto.RagBenchmarkEvaluateRequest;
import com.itgeo.fitmate.api.rag.dto.RagBenchmarkEvaluateResponse;

/**
 * RAG 检索 benchmark 服务。
 *
 * 当前评测关注的是文件级检索命中情况，即目标文件是否出现在本次 topK 召回结果中。
 */
public interface RagBenchmarkService {

    /**
     * 评测当前用户的 RAG 检索效果。
     *
     * @param userId 当前用户主键
     * @param request 评测请求
     * @return 文件级命中结果与统计指标
     */
    RagBenchmarkEvaluateResponse evaluate(Long userId, RagBenchmarkEvaluateRequest request);
}
