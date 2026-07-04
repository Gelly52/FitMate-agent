package com.itgeo.fitmate.api.fitness.heartrate.controller;

import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.fitness.heartrate.application.HeartRateService;
import com.itgeo.fitmate.api.fitness.heartrate.dto.HeartRateLogRequest;
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
 * 心率记录相关接口。
 */
@Slf4j
@RestController
@RequestMapping("/heart-rate")
public class HeartRateController {

    @Resource
    private HeartRateService heartRateService;

    @PostMapping("/log")
    public LeeResult logHeartRate(@RequestBody HeartRateLogRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            heartRateService.logHeartRate(userId, request, "manual");
            return LeeResult.ok();
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("保存心率记录失败", e);
            return LeeResult.errorException("保存心率记录失败");
        }
    }

    @GetMapping("/recent")
    public LeeResult getRecentHeartRate(@RequestParam(required = false) Integer limit) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(heartRateService.getRecentHeartRate(userId, limit));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("查询最近心率记录失败", e);
            return LeeResult.errorException("查询最近心率记录失败");
        }
    }
}
