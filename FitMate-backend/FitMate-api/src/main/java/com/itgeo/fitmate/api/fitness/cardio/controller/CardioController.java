package com.itgeo.fitmate.api.fitness.cardio.controller;

import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.fitness.cardio.application.CardioService;
import com.itgeo.fitmate.api.fitness.cardio.dto.CardioLogRequest;
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
 * 有氧训练相关接口。
 */
@Slf4j
@RestController
@RequestMapping("/cardio")
public class CardioController {

    @Resource
    private CardioService cardioService;

    @PostMapping("/log")
    public LeeResult logCardio(@RequestBody CardioLogRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            cardioService.logCardio(userId, request, "manual");
            return LeeResult.ok();
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("保存有氧训练记录失败", e);
            return LeeResult.errorException("保存有氧训练记录失败");
        }
    }

    @GetMapping("/recent")
    public LeeResult getRecentCardio(@RequestParam(required = false) Integer limit) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(cardioService.getRecentCardio(userId, limit));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("查询最近有氧训练记录失败", e);
            return LeeResult.errorException("查询最近有氧训练记录失败");
        }
    }
}
