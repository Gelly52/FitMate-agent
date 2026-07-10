package com.itgeo.fitmate.api.agent.memory.longterm.application.extractor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.agent.llm.LlmJsonSanitizer;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryExtractCounter;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriter;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriteRequest;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import com.itgeo.fitmate.api.agent.prompt.AgentPromptBuilder;
import com.itgeo.fitmate.api.agent.tool.ToolDescriptor;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.chat.dto.ReasoningStreamChunk;
import com.itgeo.fitmate.api.chat.infrastructure.ReasoningChatClient;
import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 会话级长期记忆提取器。
 * <p>
 * 复用 Agent Loop 决策 prompt 的前缀（系统提示词 + 工具 + 摘要 + 画像 + ## 最近对话），
 * 仅追加记忆提取后缀，从而命中 DeepSeek KV cache 的前缀部分，显著降低 token 消耗。
 * 通过注入用户已有 active 记忆，由 LLM 在提取阶段感知并跳过重复内容（语义级去重）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionMemoryExtractor {

    private final ReasoningChatClient reasoningChatClient;
    private final PromptTemplateManager promptTemplateManager;
    private final MemoryWriter memoryWriter;
    private final MemoryProperties properties;
    private final AgentPromptBuilder agentPromptBuilder;
    private final UserMemoryMapper userMemoryMapper;
    private final MemoryExtractCounter memoryExtractCounter;

    /**
     * 从会话中提取长期记忆。
     *
     * @param userId             用户ID
     * @param sessionId          会话ID
     * @param memory             最近对话消息列表（与 Agent 决策 prompt 的 ## 最近对话 部分一致）
     * @param tools              Agent 允许使用的工具列表（用于复现 KV cache 前缀）
     * @param summarySection     历史摘要区块文本（与 Agent 决策 prompt 一致）
     * @param userProfileSection 用户画像区块文本（与 Agent 决策 prompt 一致）
     * @param currentUserMsgCount 当前会话内累计的用户消息数（用于更新提取计数）
     * @param authenticatedUser  受理阶段捕获的登录上下文，用于在 memoryTaskExecutor 线程上恢复 UserContextHolder，
     *                           使 ReasoningChatClient 命中用户自定义 LLM 配置（与 Agent 决策路径一致以复用 KV cache）
     */
    @Async("memoryTaskExecutor")
    public void extract(Long userId,
                        Long sessionId,
                        List<Map<String, String>> memory,
                        List<ToolDescriptor> tools,
                        String summarySection,
                        String userProfileSection,
                        long currentUserMsgCount,
                        AuthenticatedUserContext authenticatedUser) {
        if (!properties.isEnabled()) {
            return;
        }

        // 把受理阶段的登录上下文绑定到当前 memoryTaskExecutor 线程，下游 ReasoningChatClient
        // 通过 UserContextHolder 读取用户态，命中用户自定义 LLM 配置 + 与 Agent 决策路径共享 KV cache。
        UserContextHolder.set(authenticatedUser);
        try {
            doExtract(userId, sessionId, memory, tools, summarySection, userProfileSection, currentUserMsgCount);
        } finally {
            UserContextHolder.clear();
        }
    }

    private void doExtract(Long userId,
                           Long sessionId,
                           List<Map<String, String>> memory,
                           List<ToolDescriptor> tools,
                           String summarySection,
                           String userProfileSection,
                           long currentUserMsgCount) {
        // 过短对话过滤
        int turns = memory == null ? 0 : memory.size();
        int chars = memory == null ? 0
                : memory.stream().mapToInt(m -> m.getOrDefault("content", "").length()).sum();
        if (turns < properties.getExtract().getMinConversationTurns()
                || chars < properties.getExtract().getMinConversationChars()) {
            log.debug("会话过短（turns={}, chars={}），跳过记忆提取", turns, chars);
            return;
        }

        // 加载用户已有的 active 记忆（仅 FACT / INSIGHT，用于语义去重）
        String existingMemoriesText = loadExistingMemoriesText(userId);

        // 复用 Agent 决策 prompt 前缀（命中 KV cache）+ 追加记忆提取后缀
        String promptPrefix = agentPromptBuilder.buildPromptPrefix(memory, tools, summarySection, userProfileSection);
        String suffix = promptTemplateManager.buildMemoryExtractSuffix(existingMemoriesText);
        String fullPrompt = promptPrefix + suffix;

        // LLM 提取：复用 ReasoningChatClient（与 Agent 决策同源，命中同一用户配置与 KV cache 前缀）
        String llmOutput;
        try {
            StringBuilder outputBuilder = new StringBuilder();
            reasoningChatClient.stream(fullPrompt, null).toStream().forEach(chunk -> {
                if (chunk == null || chunk.getUsage() != null) {
                    return;
                }
                if (StrUtil.isNotBlank(chunk.getContent())) {
                    outputBuilder.append(chunk.getContent());
                }
            });
            llmOutput = outputBuilder.toString();
        } catch (Exception e) {
            log.error("会话记忆提取 LLM 调用失败 userId={} sessionId={}", userId, sessionId, e);
            return;
        }

        // 解析 JSON
        List<MemoryExtractResult.ExtractedMemory> memories;
        try {
            JSONObject json = JSONUtil.parseObj(LlmJsonSanitizer.sanitize(llmOutput));
            memories = json.getBeanList("memories", MemoryExtractResult.ExtractedMemory.class);
        } catch (Exception e) {
            log.warn("会话记忆提取 JSON 解析失败 userId={} sessionId={} output={}", userId, sessionId, llmOutput, e);
            return;
        }

        // 写入（MemoryWriter 内部基于 content_hash 兜底精确去重）
        String source = "session:" + sessionId;
        int written = 0;
        for (MemoryExtractResult.ExtractedMemory m : memories) {
            String metadataJson = m.getMetadata() != null ? JSONUtil.toJsonStr(m.getMetadata()) : null;
            MemoryWriteRequest req = MemoryWriteRequest.builder()
                    .userId(userId)
                    .memoryType(m.getType())
                    .content(m.getContent())
                    .metadataJson(metadataJson)
                    .source(source)
                    .build();
            if (memoryWriter.writeIfNotIgnored(req)) {
                written++;
            }
        }

        // 更新提取计数（无论是否新增，都已处理过本轮）
        memoryExtractCounter.markExtracted(sessionId, currentUserMsgCount);
        log.info("会话记忆提取完成 userId={} sessionId={} 提取 {} 条，新增 {} 条",
                userId, sessionId, memories.size(), written);
    }

    /**
     * 加载用户当前 active 状态的 FACT / INSIGHT 记忆，拼装为去重提示文本。
     * 仅选取稳定/洞察类型用于去重（EPISODIC 与 SNAPSHOT 具有时效性，重复提取意义低）。
     * 限制最多 50 条，避免后缀过长稀释 KV cache 命中收益。
     */
    private String loadExistingMemoriesText(Long userId) {
        try {
            List<UserMemory> existing = userMemoryMapper.selectList(new LambdaQueryWrapper<UserMemory>()
                    .eq(UserMemory::getUserId, userId)
                    .eq(UserMemory::getStatus, "active")
                    .in(UserMemory::getMemoryType, List.of("FACT", "INSIGHT"))
                    .orderByDesc(UserMemory::getCreatedAt)
                    .last("LIMIT 50"));
            if (existing.isEmpty()) {
                return "";
            }
            return existing.stream()
                    .map(m -> "- [" + StrUtil.nullToEmpty(m.getMemoryType()) + "] " + StrUtil.nullToEmpty(m.getContent()))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("加载已有记忆失败 userId={}", userId, e);
            return "";
        }
    }
}
