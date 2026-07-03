package com.itgeo.fitmate.api.fitness.training.controller;

import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.fitness.training.application.TrainingService;
import com.itgeo.fitmate.api.fitness.training.dto.TrainingLogRequest;
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
 * 训练日志相关接口。
 */
@Slf4j
@RestController
@RequestMapping("/training")
public class TrainingController {

    @Resource
    private TrainingService trainingService;

    /**
     * 记录当前登录用户的训练日志。
     *
     * @param request 训练日志请求体
     * @return 通用响应结果
     */
    @PostMapping("/log")
    public LeeResult logTraining(@RequestBody TrainingLogRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            trainingService.logTraining(userId, request);
            return LeeResult.ok();
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("保存训练记录失败", e);
            return LeeResult.errorException("保存训练记录失败");
        }
    }

    /**
     * 查询当前登录用户最近的训练摘要。
     *
     * @param limit 返回条数，为空时使用服务默认值
     * @return 通用响应结果
     */
    @GetMapping("/recent")
    public LeeResult getRecentTraining(@RequestParam(required = false) Integer limit) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(trainingService.getRecentTraining(userId, limit));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("查询最近训练记录失败", e);
            return LeeResult.errorException("查询最近训练记录失败");
        }
    }
}
