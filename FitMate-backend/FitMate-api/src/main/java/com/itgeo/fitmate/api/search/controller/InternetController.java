package com.itgeo.fitmate.api.search.controller;

import com.itgeo.fitmate.api.search.application.SearXngService;
import com.itgeo.fitmate.common.response.LeeResult;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 联网搜索控制器。
 * <p>
 * 仅提供搜索结果调试查看入口。
 * 正式对话主链路已统一收敛到 Agent 模式（/agent/execute），本控制器不再提供独立问答入口。
 */
@RestController
@RequestMapping("internet")
public class InternetController {

    @Resource
    private SearXngService searXngService;

    /**
     * 调试查看原始联网搜索结果。
     */
    @GetMapping("/test")
    public Object test(@RequestParam("query") String query) {
        return LeeResult.ok(searXngService.search(query));
    }
}
