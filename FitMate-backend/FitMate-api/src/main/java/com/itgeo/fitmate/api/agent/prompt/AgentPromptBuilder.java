package com.itgeo.fitmate.api.agent.prompt;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.agent.dto.AgentExecuteContext;
import com.itgeo.fitmate.api.agent.tool.ToolDescriptor;
import com.itgeo.fitmate.api.skill.SkillRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * 集中构造 Agent Loop 提示词。
 */
@Component
@RequiredArgsConstructor
public class AgentPromptBuilder {

    private static final String PROMPT_PATH = "prompts/agent-system.md";

    private final SkillRegistry skillRegistry;

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
        // 复用前缀拼接逻辑，保持与 buildPromptPrefix 完全一致以命中 DeepSeek KV cache
        StringBuilder prompt = new StringBuilder(buildPromptPrefix(memory, tools, summarySection, userProfileSection));
        if (StrUtil.isNotBlank(wikiContext)) {
            prompt.append("\n\n").append(wikiContext);
        }
        prompt.append("\n\n## 已获得的工具观察结果\n").append(JSONUtil.toJsonStr(observations));
        // Sub-Agent 用主 Agent 分配的任务描述作为"当前用户问题"，前缀（system + 工具 + memory 等）与主 Agent 完全一致以命中 KV cache
        String userQuery = context.isSubAgent()
                ? context.getSubAgentTask()
                : context.getChatEntity().getMessage();
        prompt.append("\n\n## 当前用户问题\n").append(userQuery);
        prompt.append("\n\n请只输出一段合法 JSON。");
        // Sub-Agent 递归屏蔽：在 prompt 末尾追加约束，告知 LLM 当前是 Sub-Agent，不允许 spawn_subagent
        if (context.isSubAgent()) {
            prompt.append("\n\n## 当前角色约束\n")
                    .append("你当前是 Sub-Agent，只允许输出 `tool_call` 或 `final` 两种 JSON，")
                    .append("禁止输出 `spawn_subagent`（Sub-Agent 不支持递归派生）。")
                    .append("请基于主 Agent 分配的 task 完成子任务，给出 `final` 答案。");
        }
        return prompt.toString();
    }

    /**
     * 构造 Agent Loop 决策 prompt 的前缀部分（系统提示词 + 可用工具 + 历史摘要 + 用户画像 + 最近对话）。
     * <p>
     * 该前缀在单次 Agent run 内的所有迭代中保持稳定（memory 仅在 run 开始时加载一次），
     * 可作为 DeepSeek KV cache 的命中前缀被记忆提取调用复用：
     * 提取 prompt = [本前缀] + [记忆提取指令]，前缀部分从 Agent 缓存命中，仅提取指令为 miss token。
     *
     * @param memory             最近对话消息列表
     * @param tools              可用工具列表
     * @param summarySection     历史摘要区块文本，可为空
     * @param userProfileSection 用户画像区块文本，可为空
     * @return 前缀文本，以 "## 最近对话\n[memory JSON]" 结尾
     */
    public String buildPromptPrefix(List<Map<String, String>> memory,
                                    List<ToolDescriptor> tools,
                                    String summarySection,
                                    String userProfileSection) {
        StringBuilder prompt = new StringBuilder(loadSystemPrompt());
        prompt.append("\n\n## 可用工具\n").append(JSONUtil.toJsonStr(tools));
        String skillsSection = skillRegistry.buildSkillsSection();
        if (StrUtil.isNotBlank(skillsSection)) {
            prompt.append(skillsSection);
        }
        if (StrUtil.isNotBlank(summarySection)) {
            prompt.append(summarySection);
        }
        if (StrUtil.isNotBlank(userProfileSection)) {
            prompt.append("\n\n").append(userProfileSection);
        }
        prompt.append("\n\n## 最近对话\n").append(JSONUtil.toJsonStr(memory));
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
