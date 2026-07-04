package com.itgeo.fitmate.api.fitness.metrics.controller;

import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.fitness.metrics.application.BodyMetricsService;
import com.itgeo.fitmate.api.fitness.metrics.dto.BodyMetricsLogRequest;
import com.itgeo.fitmate.common.response.LeeResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 身体指标相关接口。
 */
@Slf4j
@RestController
@RequestMapping("/body-metrics")
public class BodyMetricsController {

    @Resource
    private BodyMetricsService bodyMetricsService;

    /**
     * 记录当前登录用户的身体指标。
     *
     * @param request 身体指标请求体
     * @return 通用响应结果
     */
    @PostMapping("/log")
    public LeeResult logBodyMetrics(@RequestBody BodyMetricsLogRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            bodyMetricsService.logBodyMetrics(userId, request);
            return LeeResult.ok();
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("保存身体指标失败", e);
            return LeeResult.errorException("保存身体指标失败");
        }
    }

    /**
     * 查询当前登录用户最近的身体指标摘要。
     *
     * @param limit 返回条数，为空时使用服务默认值
     * @return 通用响应结果
     */
    @GetMapping("/recent")
    public LeeResult getRecentBodyMetrics(@RequestParam(required = false) Integer limit) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(bodyMetricsService.getRecentBodyMetrics(userId, limit));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("查询最近身体指标失败", e);
            return LeeResult.errorException("查询最近身体指标失败");
        }
    }

    /**
     * 查询当前登录用户的身体指标派生指标（BMI、体重变化率）。
     */
    @GetMapping("/summary")
    public LeeResult getBodyMetricsSummary() {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(bodyMetricsService.getBodyMetricsSummary(userId));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("查询身体指标汇总失败", e);
            return LeeResult.errorException("查询身体指标汇总失败");
        }
    }
}
