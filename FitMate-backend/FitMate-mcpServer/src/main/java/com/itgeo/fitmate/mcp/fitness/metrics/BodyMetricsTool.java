package com.itgeo.fitmate.mcp.fitness.metrics;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itgeo.fitmate.domain.common.ListSortEnum;
import com.itgeo.fitmate.mcp.fitness.metrics.infrastructure.entity.BodyMetrics;
import com.itgeo.fitmate.mcp.fitness.metrics.infrastructure.mapper.BodyMetricsMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class BodyMetricsTool {
    private final BodyMetricsMapper bodyMetricsMapper;

    public BodyMetricsTool(BodyMetricsMapper bodyMetricsMapper) {
        this.bodyMetricsMapper = bodyMetricsMapper;
    }

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CreateBodyMetricsRequest {
        @ToolParam(description = "当前用户ID")
        private Long userId;

        @ToolParam(description = "记录日期，格式 yyyy-MM-dd")
        private String recordDate;

        @ToolParam(description = "体重数据，单位kg", required = false)
        private BigDecimal weight;

        @ToolParam(description = "体脂率数据，单位%", required = false)
        private BigDecimal bodyFat;

        @ToolParam(description = "睡眠时长，单位h", required = false)
        private BigDecimal sleepHours;

        @ToolParam(description = "疲劳度：低/中/高", required = false)
        private String fatigueLevel;

        @ToolParam(description = "体测备注", required = false)
        private String note;

        @ToolParam(description = "体测摘要", required = false)
        private String summary;
    }

    @Tool(description = "新增一条身体指标记录")
    public String createBodyMetrics(CreateBodyMetricsRequest request) {
        log.info("调用MCP工具：createBodyMetrics");
        log.info("身体指标请求参数 request：{}", request);

        if (request == null || request.getUserId() == null || StringUtils.isBlank(request.getRecordDate())) {
            return "身体指标创建失败：userId 和 recordDate 不能为空";
        }

        BodyMetrics bodyMetrics = new BodyMetrics();
        bodyMetrics.setUserId(request.getUserId());
        bodyMetrics.setRecordDate(LocalDate.parse(request.getRecordDate()));
        bodyMetrics.setWeight(request.getWeight());
        bodyMetrics.setBodyFat(request.getBodyFat());
        bodyMetrics.setSleepHours(request.getSleepHours());
        bodyMetrics.setFatigueLevel(trimToNull(request.getFatigueLevel()));
        bodyMetrics.setNote(trimToNull(request.getNote()));
        bodyMetrics.setSummary(trimToNull(request.getSummary()));
        bodyMetrics.setCreatedAt(LocalDateTime.now());
        bodyMetrics.setUpdatedAt(LocalDateTime.now());

        bodyMetricsMapper.insert(bodyMetrics);
        return "身体指标创建成功，ID=" + bodyMetrics.getId();
    }

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QueryBodyMetricsRequest {
        @ToolParam(description = "查询用户ID", required = false)
        private Long userId;

        @ToolParam(description = "记录日期，格式 yyyy-MM-dd", required = false)
        private String recordDate;

        @ToolParam(description = "疲劳度：低/中/高", required = false)
        private String fatigueLevel;

        @ToolParam(description = "体测摘要关键字", required = false)
        private String summaryKeyword;

        @ToolParam(description = "排序方式：asc/desc", required = false)
        private ListSortEnum sortEnum;
    }

    @Tool(description = "按条件查询身体指标记录列表")
    public List<BodyMetrics> queryBodyMetrics(QueryBodyMetricsRequest request) {
        log.info("调用MCP工具：queryBodyMetrics");
        log.info("身体指标查询参数 request：{}", request);

        QueryWrapper<BodyMetrics> queryWrapper = new QueryWrapper<>();

        if (request != null) {
            if (request.getUserId() != null) {
                queryWrapper.eq("user_id", request.getUserId());
            }
            if (StringUtils.isNotBlank(request.getRecordDate())) {
                queryWrapper.eq("record_date", LocalDate.parse(request.getRecordDate()));
            }
            if (StringUtils.isNotBlank(request.getFatigueLevel())) {
                queryWrapper.eq("fatigue_level", request.getFatigueLevel().trim());
            }
            if (StringUtils.isNotBlank(request.getSummaryKeyword())) {
                queryWrapper.like("summary", request.getSummaryKeyword().trim());
            }
        }

        ListSortEnum sortEnum = request.getSortEnum();
        if (sortEnum == ListSortEnum.ASC) {
            queryWrapper.orderByAsc("record_date").orderByAsc("created_at");
        } else {
            queryWrapper.orderByDesc("record_date").orderByDesc("created_at");
        }
        return bodyMetricsMapper.selectList(queryWrapper);
    }

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ModifyBodyMetricsRequest {
        @ToolParam(description = "身体指标记录ID")
        private Long id;

        @ToolParam(description = "修改后的记录日期，格式 yyyy-MM-dd", required = false)
        private String recordDate;

        @ToolParam(description = "修改后的体重数据，单位kg", required = false)
        private BigDecimal weight;

        @ToolParam(description = "修改后的体脂率数据，单位%", required = false)
        private BigDecimal bodyFat;

        @ToolParam(description = "修改后的睡眠时长，单位h", required = false)
        private BigDecimal sleepHours;

        @ToolParam(description = "修改后的疲劳度：低/中/高", required = false)
        private String fatigueLevel;

        @ToolParam(description = "修改后的体测备注", required = false)
        private String note;

        @ToolParam(description = "修改后的体测摘要", required = false)
        private String summary;
    }

    @Tool(description = "根据身体指标记录ID修改记录内容")
    public String modifyBodyMetrics(ModifyBodyMetricsRequest request) {
        log.info("调用MCP工具：modifyBodyMetrics");
        log.info("身体指标修改参数 request：{}", request);

        if (request == null || request.getId() == null) {
            return "身体指标修改失败：id 不能为空";
        }

        BodyMetrics bodyMetrics = new BodyMetrics();
        bodyMetrics.setId(request.getId());

        if (StringUtils.isNotBlank(request.getRecordDate())) {
            bodyMetrics.setRecordDate(LocalDate.parse(request.getRecordDate()));
        }
        if (request.getWeight() != null) {
            bodyMetrics.setWeight(request.getWeight());
        }
        if (request.getBodyFat() != null) {
            bodyMetrics.setBodyFat(request.getBodyFat());
        }
        if (request.getSleepHours() != null) {
            bodyMetrics.setSleepHours(request.getSleepHours());
        }
        if (StringUtils.isNotBlank(request.getFatigueLevel())) {
            bodyMetrics.setFatigueLevel(request.getFatigueLevel().trim());
        }
        if (StringUtils.isNotBlank(request.getNote())) {
            bodyMetrics.setNote(request.getNote().trim());
        }
        if (StringUtils.isNotBlank(request.getSummary())) {
            bodyMetrics.setSummary(request.getSummary().trim());
        }
        bodyMetrics.setUpdatedAt(LocalDateTime.now());

        int update = bodyMetricsMapper.updateById(bodyMetrics);
        return update > 0 ? "身体指标更新成功" : "身体指标更新失败，或记录不存在";
    }

    @Tool(description = "根据身体指标记录ID删除对应记录")
    public String deleteBodyMetrics(
            @ToolParam(description = "身体指标记录ID") Long id
    ) {
        log.info("调用MCP工具：deleteBodyMetrics, id={}", id);

        if (id == null) {
            return "身体指标删除失败：id 不能为空";
        }

        int deleted = bodyMetricsMapper.deleteById(id);
        return deleted > 0 ? "身体指标删除成功" : "身体指标删除失败，或记录不存在";
    }

    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
