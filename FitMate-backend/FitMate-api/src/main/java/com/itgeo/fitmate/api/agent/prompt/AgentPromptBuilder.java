package com.itgeo.fitmate.api.agent.prompt;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.agent.dto.AgentExecuteContext;
import com.itgeo.fitmate.api.agent.tool.ToolDescriptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * 集中构造 Agent Loop 提示词。
 */
@Component
public class AgentPromptBuilder {

    private static final String PROMPT_PATH = "prompts/agent-system.md";

    public String buildDecisionPrompt(AgentExecuteContext context,
                                      List<Map<String, String>> memory,
                                      List<Map<String, Object>> observations,
                                      List<ToolDescriptor> tools,
                                      String wikiContext) {
        return buildDecisionPrompt(context, memory, observations, tools, wikiContext, null);
    }

    /**
     * 构造 Agent Loop 决策 prompt。
     *
     * @param summarySection 历史摘要区块文本，无摘要时传 null 或空串
     */
    public String buildDecisionPrompt(AgentExecuteContext context,
                                      List<Map<String, String>> memory,
                                      List<Map<String, Object>> observations,
                                      List<ToolDescriptor> tools,
                                      String wikiContext,
                                      String summarySection) {
        return buildDecisionPrompt(context, memory, observations, tools, wikiContext, summarySection, null);
    }

    /**
     * 构造 Agent Loop 决策 prompt（带用户画像区块）。
     *
     * @param summarySection      历史摘要区块文本，无摘要时传 null 或空串
     * @param userProfileSection  用户画像区块文本（如 "## 用户画像\n..."），无画像时传 null 或空串
     */
    public String buildDecisionPrompt(AgentExecuteContext context,
                                      List<Map<String, String>> memory,
                                      List<Map<String, Object>> observations,
                                      List<ToolDescriptor> tools,
                                      String wikiContext,
                                      String summarySection,
                                      String userProfileSection) {
        StringBuilder prompt = new StringBuilder(loadSystemPrompt());
        prompt.append("\n\n## 可用工具\n").append(JSONUtil.toJsonStr(tools));
        if (StrUtil.isNotBlank(summarySection)) {
            prompt.append(summarySection);
        }
        if (StrUtil.isNotBlank(userProfileSection)) {
            prompt.append("\n\n").append(userProfileSection);
        }
        prompt.append("\n\n## 最近对话\n").append(JSONUtil.toJsonStr(memory));
        if (StrUtil.isNotBlank(wikiContext)) {
            prompt.append("\n\n").append(wikiContext);
        }
        prompt.append("\n\n## 已获得的工具观察结果\n").append(JSONUtil.toJsonStr(observations));
        prompt.append("\n\n## 当前用户问题\n").append(context.getChatEntity().getMessage());
        prompt.append("\n\n请只输出一段合法 JSON。");
        return prompt.toString();
    }

    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource(PROMPT_PATH);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Agent系统提示词读取失败", e);
        }
    }
}
