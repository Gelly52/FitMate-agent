package com.itgeo.fitmate.mcp.rag;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itgeo.fitmate.common.enums.ListSortEnum;
import com.itgeo.fitmate.mcp.rag.RagManageTool.QueryRagBenchmarkRunRequest;
import com.itgeo.fitmate.mcp.rag.RagManageTool.QueryRagDocumentRequest;
import com.itgeo.fitmate.mcp.rag.infrastructure.entity.RagBenchmarkRun;
import com.itgeo.fitmate.mcp.rag.infrastructure.entity.RagDocumentMeta;
import com.itgeo.fitmate.mcp.rag.infrastructure.mapper.RagBenchmarkRunMapper;
import com.itgeo.fitmate.mcp.rag.infrastructure.mapper.RagDocumentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RagManageTool 多用户隔离测试。
 * <p>
 * 重点覆盖 userId 为 null 时不返回全量用户数据，防止 LLM 不传 userId 导致跨用户泄露。
 */
@ExtendWith(MockitoExtension.class)
class RagManageToolTest {

    @Mock
    private RagDocumentMapper ragDocumentMapper;
    @Mock
    private RagBenchmarkRunMapper ragBenchmarkRunMapper;

    @InjectMocks
    private RagManageTool ragManageTool;

    private static final Long USER_A_ID = 1001L;
    private static final Long USER_B_ID = 1002L;

    // ==================== queryRagDocuments ====================

    @Test
    void queryRagDocuments_withUserId_shouldQueryWithUserIdFilter() {
        QueryRagDocumentRequest req = new QueryRagDocumentRequest();
        req.setUserId(USER_A_ID);
        when(ragDocumentMapper.selectList(any())).thenReturn(List.of());

        ragManageTool.queryRagDocuments(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<RagDocumentMeta>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(ragDocumentMapper, times(1)).selectList(captor.capture());
        // 确认 SQL 包含 user_id 条件
        String sql = captor.getValue().getCustomSqlSegment();
        assertTrue(sql.contains("user_id"), "查询必须包含 user_id 过滤: " + sql);
    }

    /**
     * 核心安全场景：LLM 不传 userId 时，不应查询 DB，直接返回空列表。
     */
    @Test
    void queryRagDocuments_nullUserId_shouldReturnEmptyAndNotQueryDb() {
        QueryRagDocumentRequest req = new QueryRagDocumentRequest();
        req.setUserId(null);

        List<RagDocumentMeta> result = ragManageTool.queryRagDocuments(req);

        assertTrue(result.isEmpty(), "userId 为 null 时必须返回空列表");
        verify(ragDocumentMapper, never()).selectList(any());
    }

    /**
     * 核心安全场景：request 为 null 时，不应查询 DB。
     */
    @Test
    void queryRagDocuments_nullRequest_shouldReturnEmptyAndNotQueryDb() {
        List<RagDocumentMeta> result = ragManageTool.queryRagDocuments(null);

        assertTrue(result.isEmpty(), "request 为 null 时必须返回空列表");
        verify(ragDocumentMapper, never()).selectList(any());
    }

    // ==================== queryRagBenchmarkRuns ====================

    @Test
    void queryRagBenchmarkRuns_withUserId_shouldQueryWithUserIdFilter() {
        QueryRagBenchmarkRunRequest req = new QueryRagBenchmarkRunRequest();
        req.setUserId(USER_A_ID);
        when(ragBenchmarkRunMapper.selectList(any())).thenReturn(List.of());

        ragManageTool.queryRagBenchmarkRuns(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<RagBenchmarkRun>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(ragBenchmarkRunMapper, times(1)).selectList(captor.capture());
        String sql = captor.getValue().getCustomSqlSegment();
        assertTrue(sql.contains("user_id"), "查询必须包含 user_id 过滤: " + sql);
    }

    /**
     * 核心安全场景：LLM 不传 userId 时，不应查询 DB，直接返回空列表。
     */
    @Test
    void queryRagBenchmarkRuns_nullUserId_shouldReturnEmptyAndNotQueryDb() {
        QueryRagBenchmarkRunRequest req = new QueryRagBenchmarkRunRequest();
        req.setUserId(null);

        List<RagBenchmarkRun> result = ragManageTool.queryRagBenchmarkRuns(req);

        assertTrue(result.isEmpty(), "userId 为 null 时必须返回空列表");
        verify(ragBenchmarkRunMapper, never()).selectList(any());
    }

    @Test
    void queryRagBenchmarkRuns_nullRequest_shouldReturnEmptyAndNotQueryDb() {
        List<RagBenchmarkRun> result = ragManageTool.queryRagBenchmarkRuns(null);

        assertTrue(result.isEmpty(), "request 为 null 时必须返回空列表");
        verify(ragBenchmarkRunMapper, never()).selectList(any());
    }
}
