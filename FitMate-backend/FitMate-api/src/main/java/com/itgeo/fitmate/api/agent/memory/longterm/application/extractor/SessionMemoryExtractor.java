package com.itgeo.fitmate.api.agent.memory.longterm.application.extractor;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriter;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriteRequest;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionMemoryExtractor {

    private final ChatModel chatModel;
    private final PromptTemplateManager promptTemplateManager;
    private final MemoryWriter memoryWriter;
    private final MemoryProperties properties;

    @Async("memoryTaskExecutor")
    public void extract(Long userId, Long sessionId, List<Map<String, String>> conversation) {
        if (!properties.isEnabled()) {
            return;
        }

        // 过短对话过滤
        int turns = conversation.size();
        int chars = conversation.stream().mapToInt(m -> m.getOrDefault("content", "").length()).sum();
        if (turns < properties.getExtract().getMinConversationTurns()
                || chars < properties.getExtract().getMinConversationChars()) {
            log.debug("会话过短（turns={}, chars={}），跳过记忆提取", turns, chars);
            return;
        }

        // 构建 conversation 文本
        String conversationText = conversation.stream()
                .map(m -> m.get("role") + ": " + m.get("content"))
                .collect(Collectors.joining("\n"));

        // LLM 提取
        String promptText = promptTemplateManager.buildMemoryExtractPrompt(conversationText);
        String llmOutput;
        try {
            llmOutput = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("会话记忆提取 LLM 调用失败 userId={} sessionId={}", userId, sessionId, e);
            return;
        }

        // 解析 JSON
        List<MemoryExtractResult.ExtractedMemory> memories;
        try {
            JSONObject json = JSONUtil.parseObj(llmOutput);
            memories = json.getBeanList("memories", MemoryExtractResult.ExtractedMemory.class);
        } catch (Exception e) {
            log.warn("会话记忆提取 JSON 解析失败 userId={} sessionId={} output={}", userId, sessionId, llmOutput, e);
            return;
        }

        // 写入
        String source = "session:" + sessionId;
        for (MemoryExtractResult.ExtractedMemory m : memories) {
            String metadataJson = m.getMetadata() != null ? JSONUtil.toJsonStr(m.getMetadata()) : null;
            MemoryWriteRequest req = MemoryWriteRequest.builder()
                    .userId(userId)
                    .memoryType(m.getType())
                    .content(m.getContent())
                    .metadataJson(metadataJson)
                    .source(source)
                    .build();
            memoryWriter.writeIfNotIgnored(req);
        }
        log.info("会话记忆提取完成 userId={} sessionId={} 提取 {} 条", userId, sessionId, memories.size());
    }
}
