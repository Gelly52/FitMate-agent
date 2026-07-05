package com.itgeo.fitmate.mcp.rag;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itgeo.fitmate.common.enums.ListSortEnum;
import com.itgeo.fitmate.mcp.rag.infrastructure.entity.RagBenchmarkRun;
import com.itgeo.fitmate.mcp.rag.infrastructure.entity.RagDocumentMeta;
import com.itgeo.fitmate.mcp.rag.infrastructure.mapper.RagBenchmarkRunMapper;
import com.itgeo.fitmate.mcp.rag.infrastructure.mapper.RagDocumentMapper;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RagManageTool {

    private final RagDocumentMapper ragDocumentMapper;
    private final RagBenchmarkRunMapper ragBenchmarkRunMapper;

    public RagManageTool(RagDocumentMapper ragDocumentMapper,
                         RagBenchmarkRunMapper ragBenchmarkRunMapper) {
        this.ragDocumentMapper = ragDocumentMapper;
        this.ragBenchmarkRunMapper = ragBenchmarkRunMapper;
    }

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QueryRagDocumentRequest {
        @ToolParam(description = "文档所属用户ID", required = false)
        private Long userId;

        @ToolParam(description = "文档文件名关键字", required = false)
        private String fileName;

        @ToolParam(description = "文档文件哈希", required = false)
        private String fileHash;

        @ToolParam(description = "文档向量状态", required = false)
        private String vectorStatus;

        @ToolParam(description = "排序方式：asc/desc", required = false)
        private ListSortEnum sortEnum;
    }

    @Tool(description = "按条件查询RAG文档元数据")
    public List<RagDocumentMeta> queryRagDocuments(QueryRagDocumentRequest request) {
        log.info("调用MCP工具：queryRagDocuments");
        log.info("RAG文档查询参数 request：{}", request);

        QueryWrapper<RagDocumentMeta> queryWrapper = new QueryWrapper<>();

        if (request != null) {
            if (request.getUserId() != null) {
                queryWrapper.eq("user_id", request.getUserId());
            }
            if (StringUtils.isNotBlank(request.getFileName())) {
                queryWrapper.like("file_name", request.getFileName().trim());
            }
            if (StringUtils.isNotBlank(request.getFileHash())) {
                queryWrapper.eq("file_hash", request.getFileHash().trim());
            }
            if (StringUtils.isNotBlank(request.getVectorStatus())) {
                queryWrapper.eq("vector_status", request.getVectorStatus().trim());
            }
        }

        if (request.getSortEnum() == ListSortEnum.ASC) {
            queryWrapper.orderByAsc("created_at");
        } else {
            queryWrapper.orderByDesc("created_at");
        }
        return ragDocumentMapper.selectList(queryWrapper);
    }

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QueryRagBenchmarkRunRequest {
        @ToolParam(description = "评测任务发起用户ID", required = false)
        private Long userId;

        @ToolParam(description = "评测数据集名称关键字", required = false)
        private String datasetName;

        @ToolParam(description = "评测任务状态：pending/running/success/failed", required = false)
        private String status;

        @ToolParam(description = "排序方式：asc/desc", required = false)
        private ListSortEnum sortEnum;
    }

    @Tool(description = "按条件查询RAG评测任务记录")
    public List<RagBenchmarkRun> queryRagBenchmarkRuns(QueryRagBenchmarkRunRequest request) {
        log.info("调用MCP工具：queryRagBenchmarkRuns");
        log.info("RAG评测任务查询参数 request：{}", request);

        QueryWrapper<RagBenchmarkRun> queryWrapper = new QueryWrapper<>();

        if (request != null) {
            if (request.getUserId() != null) {
                queryWrapper.eq("user_id", request.getUserId());
            }
            if (StringUtils.isNotBlank(request.getDatasetName())) {
                queryWrapper.like("dataset_name", request.getDatasetName().trim());
            }
            if (StringUtils.isNotBlank(request.getStatus())) {
                queryWrapper.eq("status", request.getStatus().trim());
            }
        }

        if (request.getSortEnum() == ListSortEnum.ASC) {
            queryWrapper.orderByAsc("created_at");
        } else {
            queryWrapper.orderByDesc("created_at");
        }
        return ragBenchmarkRunMapper.selectList(queryWrapper);
    }
}
