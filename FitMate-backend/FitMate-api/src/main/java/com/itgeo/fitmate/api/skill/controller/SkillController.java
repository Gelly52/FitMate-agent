package com.itgeo.fitmate.api.skill.controller;

import com.itgeo.fitmate.api.skill.SkillRegistry;
import com.itgeo.fitmate.common.response.LeeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 技能查询接口（供前端 settings 页面展示）。
 */
@Slf4j
@RestController
@RequestMapping("/skill")
@RequiredArgsConstructor
public class SkillController {

    private final SkillRegistry skillRegistry;

    /**
     * 查询所有可用技能元信息（不含技能正文）。
     */
    @GetMapping("/list")
    public LeeResult list() {
        return LeeResult.ok(skillRegistry.listMetadata());
    }
}
