package com.itgeo.fitmate.api.fitness.diet.controller;

import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.fitness.diet.application.DietService;
import com.itgeo.fitmate.api.fitness.diet.dto.DietLogRequest;
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
 * 饮食记录相关接口。
 */
@Slf4j
@RestController
@RequestMapping("/diet")
public class DietController {

    @Resource
    private DietService dietService;

    @PostMapping("/log")
    public LeeResult logDiet(@RequestBody DietLogRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            dietService.logDiet(userId, request, "manual");
            return LeeResult.ok();
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("保存饮食记录失败", e);
            return LeeResult.errorException("保存饮食记录失败");
        }
    }

    @GetMapping("/recent")
    public LeeResult getRecentDiet(@RequestParam(required = false) Integer limit) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(dietService.getRecentDiet(userId, limit));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("查询最近饮食记录失败", e);
            return LeeResult.errorException("查询最近饮食记录失败");
        }
    }
}
