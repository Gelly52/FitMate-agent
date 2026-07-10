package com.itgeo.fitmate.api.skill;

import cn.hutool.core.util.StrUtil;
import com.itgeo.fitmate.api.agent.tool.ToolCall;
import com.itgeo.fitmate.api.agent.tool.ToolDescriptor;
import com.itgeo.fitmate.api.agent.tool.ToolExecutor;
import com.itgeo.fitmate.api.agent.tool.ToolResult;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 技能加载工具（L2 层）。
 * <p>
 * Agent 调用此工具按名称加载技能的完整内容，返回后按技能步骤执行。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SkillLoadToolExecutor implements ToolExecutor {

    private final SkillRegistry skillRegistry;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "skill.load",
                "加载指定技能的完整内容。当用户问题匹配某技能的触发条件时调用，返回技能的执行步骤与输出要求。参数: {\"skill_name\": \"技能名称\"}",
                "{\"type\":\"object\",\"properties\":{\"skill_name\":{\"type\":\"string\",\"description\":\"技能名称，如'分析本周训练'\"}},\"required\":[\"skill_name\"]}",
                true
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        String skillName = call == null || call.getArguments() == null
                ? null : String.valueOf(call.getArguments().get("skill_name"));
        if (StrUtil.isBlank(skillName)) {
            return ToolResult.error("skill_name 参数不能为空");
        }

        SkillInfo skill = skillRegistry.findByName(skillName);
        if (skill == null) {
            return ToolResult.error("未找到技能: " + skillName);
        }

        log.info("加载技能: {}, userId={}", skillName,
                authenticatedUser != null ? authenticatedUser.getUserId() : null);
        return ToolResult.ok("已加载技能: " + skillName, skill.getContent());
    }
}
