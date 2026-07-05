package com.itgeo.fitmate.api.agent.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import com.itgeo.fitmate.api.agent.application.AgentRunService;
import com.itgeo.fitmate.api.agent.config.AgentProperties;
import com.itgeo.fitmate.api.agent.dto.AgentExecuteContext;
import com.itgeo.fitmate.api.agent.dto.AgentFinishResponse;
import com.itgeo.fitmate.api.agent.infrastructure.entity.AgentStep;
import com.itgeo.fitmate.api.agent.llm.LlmGateway;
import com.itgeo.fitmate.api.agent.llm.LlmJsonSanitizer;
import com.itgeo.fitmate.api.agent.memory.AgentMemoryService;
import com.itgeo.fitmate.api.agent.memory.ContextCompressService;
import com.itgeo.fitmate.api.agent.memory.dto.MemoryLoadResult;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryExtractCounter;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryReader;
import com.itgeo.fitmate.api.agent.memory.longterm.application.extractor.SessionMemoryExtractor;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.agent.prompt.AgentPromptBuilder;
import com.itgeo.fitmate.api.agent.tool.KbSearchContextHolder;
import com.itgeo.fitmate.api.agent.tool.ToolCall;
import com.itgeo.fitmate.api.agent.tool.ToolDescriptor;
import com.itgeo.fitmate.api.agent.tool.ToolRegistry;
import com.itgeo.fitmate.api.agent.tool.ToolResult;
import com.itgeo.fitmate.api.agent.tool.ToolRouter;
import com.itgeo.fitmate.api.agent.trace.AgentTraceService;
import com.itgeo.fitmate.api.chat.application.ChatSessionService;
import com.itgeo.fitmate.api.chat.dto.ChatEntity;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatMessage;
import com.itgeo.fitmate.api.chat.dto.ChatStreamChunkResponse;
import com.itgeo.fitmate.api.chat.dto.ReasoningStreamChunk;
import com.itgeo.fitmate.api.chat.dto.TokenUsage;
import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import com.itgeo.fitmate.api.rag.application.DocumentService;
import com.itgeo.fitmate.api.sse.domain.SSEMsgType;
import com.itgeo.fitmate.api.sse.infrastructure.SSEServer;
import com.itgeo.fitmate.api.wiki.application.WikiSearchService;
import com.itgeo.fitmate.api.wiki.config.WikiProperties;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

/**
 * 执行 LLM 决策、工具调用、观察结果回灌和最终回答的 Agent Loop。
 */
@Slf4j
@Component
public class AgentLoopExecutor {

    @Resource
    private AgentProperties agentProperties;

    @Resource
    private AgentMemoryService agentMemoryService;

    @Resource
    private ContextCompressService contextCompressService;

    @Resource
    private AgentPromptBuilder agentPromptBuilder;

    @Resource
    private LlmGateway llmGateway;

    @Resource
    private ToolRegistry toolRegistry;

    @Resource
    private ToolRouter toolRouter;

    @Resource
    private com.itgeo.fitmate.api.agent.mcp.McpToolRegistry mcpToolRegistry;

    @Resource
    private AgentTraceService agentTraceService;

    @Resource
    private AgentRunService agentRunService;

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private WikiSearchService wikiSearchService;

    @Resource
    private PromptTemplateManager promptTemplateManager;

    @Resource
    private DocumentService documentService;

    @Resource
    private WikiProperties wikiProperties;

    @Resource
    private AgentCancellationRegistry cancellationRegistry;

    @Resource
    private MemoryReader memoryReader;

    @Resource
    private SessionMemoryExtractor sessionMemoryExtractor;

    @Resource
    private MemoryExtractCounter memoryExtractCounter;

    @Resource
    private MemoryProperties memoryProperties;

    public void run(AgentExecuteContext context) {
        cancellationRegistry.register(context.getRunId());
        Instant runStarted = Instant.now();
        // 被动上下文压缩检查：超阈值则压缩历史，压缩结果通过 SSE 推送给前端
        contextCompressService.checkAndCompressIfNeeded(context);
        // 加载历史（含摘要）：若刚发生了压缩，只加载摘要之后的消息
        MemoryLoadResult memoryResult = agentMemoryService.loadRecentMessages(context);
        List<Map<String, String>> memory = memoryResult.getMessages();
        String summarySection = contextCompressService.buildSummaryPromptSection(memoryResult.getSummary());
        List<Map<String, Object>> observations = new ArrayList<>();
        List<ToolDescriptor> allowedTools = resolveAllowedTools(context);
        Set<String> allowedToolNames = allowedTools.stream()
                .map(ToolDescriptor::getName)
                .collect(Collectors.toSet());
        // Agent 启动前执行 Wiki 预检索（spec 7.2），把命中内容作为知识库背景注入首轮决策 prompt。
        String wikiContext = doAgentWikiSearch(context);

        agentTraceService.startEvent(
                context,
                "run_started",
                "Agent 开始执行",
                null,
                null,
                0,
                JSONUtil.toJsonStr(Map.of("message", context.getChatEntity().getMessage())),
                "Agent 已开始动态执行"
        );

        int maxIterations = normalizePositive(agentProperties.getMaxIterations(), 20);
        int maxToolCalls = normalizePositive(agentProperties.getMaxToolCalls(), 100);
        int maxDurationSeconds = normalizePositive(agentProperties.getMaxRunDurationSeconds(), 1800);
        int toolCallCount = 0;
        // 用户画像在本轮 Agent 执行期间不会变化（记忆写入在 run 结束后异步发生），
        // 在循环外计算一次即可，同时作为记忆提取 prompt 的前缀组成部分，保证与 Agent 决策 prompt 完全一致以命中 KV cache。
        String userProfileSection = memoryReader.loadProfileSection(context.getAuthenticatedUser().getUserId());

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            if (Duration.between(runStarted, Instant.now()).toSeconds() > maxDurationSeconds) {
                throw new IllegalStateException("Agent执行超时");
            }
            if (cancellationRegistry.isCancelled(context.getRunId())) {
                throw new AgentCancelledException(extractPartialContent(context));
            }

            String prompt = agentPromptBuilder.buildDecisionPrompt(context, memory, observations, allowedTools, wikiContext, summarySection, userProfileSection);
            AgentStep llmStep = agentTraceService.startEvent(
                    context,
                    "llm_started",
                    "LLM 决策",
                    null,
                    null,
                    iteration,
                    JSONUtil.toJsonStr(Map.of("observationCount", observations.size())),
                    "LLM 正在决定下一步"
            );

            Instant llmStarted = Instant.now();
            String decisionText;
            FinalAnswerStreamState streamState = new FinalAnswerStreamState();
            try {
                StringBuilder reasoningContent = new StringBuilder();
                StringBuilder decisionContent = new StringBuilder();
                llmGateway.streamWithReasoning(prompt).toStream().forEach(chunk -> {
                    if (cancellationRegistry.isCancelled(context.getRunId())) {
                        throw new AgentCancelledException(extractPartialContent(context));
                    }
                    if (chunk == null) {
                        return;
                    }
                    // usage-only 终止帧：累加到上下文，不参与内容拼接
                    if (chunk.getUsage() != null) {
                        Integer windowSize = chunk.getUsage().getContextWindow();
                        context.getAccumulatedUsage().accumulate(chunk.getUsage(), windowSize);
                        return;
                    }
                    String reasoningDelta = StrUtil.blankToDefault(chunk.getReasoningContent(), "");
                    String contentDelta = StrUtil.blankToDefault(chunk.getContent(), "");
                    if (StrUtil.isNotBlank(reasoningDelta)) {
                        reasoningContent.append(reasoningDelta);
                        context.getAccumulatedThinking().append(reasoningDelta);
                        sendThinkingChunk(context, reasoningDelta);
                    }
                    if (StrUtil.isNotBlank(contentDelta)) {
                        decisionContent.append(contentDelta);
                        // 状态机驱动 final_answer 流式推送
                        String answerDelta = streamState.onNext(contentDelta, decisionContent.toString());
                        if (StrUtil.isNotBlank(answerDelta)) {
                            sendContentChunk(context, answerDelta);
                        }
                    }
                });
                decisionText = decisionContent.toString();
                Map<String, Object> llmOutput = new LinkedHashMap<>();
                llmOutput.put("decision", LlmJsonSanitizer.sanitize(decisionText));
                if (StrUtil.isNotBlank(reasoningContent)) {
                    llmOutput.put("reasoningContent", reasoningContent.toString());
                }
                agentTraceService.finishEvent(
                        context,
                        llmStep,
                        "llm_finished",
                        JSONUtil.toJsonStr(llmOutput),
                        elapsedMs(llmStarted),
                        "LLM 已完成决策"
                );
            } catch (Exception e) {
                agentTraceService.failEvent(context, llmStep, "run_failed", e.getMessage(), elapsedMs(llmStarted), "LLM 决策失败");
                throw e;
            }

            JSONObject decision = parseDecision(decisionText);
            String action = decision.getStr("action");
            if ("final".equalsIgnoreCase(action)) {
                String finalAnswer = StrUtil.blankToDefault(decision.getStr("final_answer"), "已完成处理。请查看上方执行轨迹。");
                // 如果状态机已流式推送，跳过整段推送；否则走原逻辑
                if (!streamState.hasStreamed()) {
                    sendContentChunk(context, finalAnswer);
                }
                finishWithAnswer(context, finalAnswer, observations, memory, allowedTools, summarySection, userProfileSection);
                return;
            }

            if (!"tool_call".equalsIgnoreCase(action)) {
                // 异常决策分支：未流式推送过，需要显式推送一次 final_answer
                sendContentChunk(context, LlmJsonSanitizer.sanitize(decisionText));
                finishWithAnswer(context, LlmJsonSanitizer.sanitize(decisionText), observations, memory, allowedTools, summarySection, userProfileSection);
                return;
            }

            if (toolCallCount >= maxToolCalls) {
                throw new IllegalStateException("工具调用次数超过上限");
            }
            toolCallCount++;

            if (cancellationRegistry.isCancelled(context.getRunId())) {
                throw new AgentCancelledException(extractPartialContent(context));
            }
            ToolCall toolCall = toToolCall(decision);
            ToolResult result;
            AgentStep toolStep = agentTraceService.startEvent(
                    context,
                    "tool_call_started",
                    "调用工具: " + toolCall.getName(),
                    toolCall.getName(),
                    toolCall.getToolCallId(),
                    iteration,
                    JSONUtil.toJsonStr(toolCall),
                    "开始调用工具: " + toolCall.getName()
            );
            Instant toolStarted = Instant.now();
            if (!allowedToolNames.contains(toolCall.getName())) {
                result = ToolResult.error("当前请求不允许调用工具: " + toolCall.getName());
            } else {
                KbSearchContextHolder.setKbEnabled(context.getChatEntity() != null
                        && !Boolean.FALSE.equals(context.getChatEntity().getKnowledgeBaseEnabled()));
                KbSearchContextHolder.setRagEnabled(context.getChatEntity() != null
                        && Boolean.TRUE.equals(context.getChatEntity().getRagEnabled()));
                try {
                    result = toolRouter.execute(toolCall, context.getAuthenticatedUser());
                } finally {
                    KbSearchContextHolder.clear();
                }
            }

            Map<String, Object> observation = new LinkedHashMap<>();
            observation.put("toolCallId", toolCall.getToolCallId());
            observation.put("toolName", toolCall.getName());
            observation.put("success", result.isSuccess());
            observation.put("content", result.isSuccess() ? result.getContent() : result.getErrorMessage());
            observation.put("data", result.getData());
            observations.add(observation);

            if (result.isSuccess()) {
                agentTraceService.finishEvent(
                        context,
                        toolStep,
                        "tool_call_finished",
                        JSONUtil.toJsonStr(result),
                        elapsedMs(toolStarted),
                        "工具调用完成: " + toolCall.getName()
                );
            } else {
                agentTraceService.failEvent(
                        context,
                        toolStep,
                        "tool_call_failed",
                        result.getErrorMessage(),
                        elapsedMs(toolStarted),
                        "工具调用失败: " + toolCall.getName()
                );
            }
        }

        // 达到最大循环次数仍未生成最终答案：不抛异常，改为构造兜底答案走正常完成流程，
        // 避免前端因收到 failed 状态 FINISH 事件而崩溃，同时把本轮已收集的工具调用结果作为部分信息反馈给用户。
        log.warn("Agent达到最大循环次数仍未生成最终答案, runId={}, maxIterations={}", context.getRunId(), maxIterations);
        String fallbackAnswer = buildMaxIterationsFallback(observations);
        finishWithAnswer(context, fallbackAnswer, observations, memory, allowedTools, summarySection, userProfileSection);
    }

    private void finishWithAnswer(AgentExecuteContext context,
                                 String finalAnswer,
                                 List<Map<String, Object>> observations,
                                 List<Map<String, String>> memory,
                                 List<ToolDescriptor> allowedTools,
                                 String summarySection,
                                 String userProfileSection) {
        AgentStep finalStep = agentTraceService.startEvent(
                context,
                "final_answer",
                "生成最终答案",
                null,
                null,
                null,
                JSONUtil.toJsonStr(Map.of("observationCount", observations.size())),
                "开始生成最终答案"
        );
        Instant started = Instant.now();
        if (cancellationRegistry.isCancelled(context.getRunId())) {
            throw new AgentCancelledException(extractPartialContent(context));
        }
        // final_answer 已在调用方通过流式或整段 sendContentChunk 推送，这里不再重复推送
        TokenUsage accumulatedUsage = context.getAccumulatedUsage();
        String usageJson = (accumulatedUsage != null && accumulatedUsage.getTotalTokens() != null)
                ? JSONUtil.toJsonStr(accumulatedUsage) : null;
        chatSessionService.finishAssistantMessage(
                context.getAssistantMessageId(),
                finalAnswer,
                observations.isEmpty() ? null : JSONUtil.toJsonStr(observations),
                usageJson
        );

        // 持久化累积的思考内容（Agent 多轮决策循环合并）
        String thinkingContent = context.getAccumulatedThinking().toString();
        if (StrUtil.isNotBlank(thinkingContent)) {
            try {
                chatSessionService.saveThinking(context.getAssistantMessageId(), thinkingContent);
            } catch (Exception e) {
                log.warn("保存思考内容失败，messageId={}, runId={}, error={}",
                        context.getAssistantMessageId(), context.getRunId(), e.getMessage());
            }
        }

        AgentFinishResponse finish = new AgentFinishResponse(
                finalAnswer,
                context.getChatEntity().getBotMsgId(),
                context.getRunId(),
                "success",
                observations,
                context.getChatSessionId(),
                context.getChatEntity().getSessionCode(),
                context.getAccumulatedUsage()
        );
        agentTraceService.finishEvent(
                context,
                finalStep,
                "final_answer",
                JSONUtil.toJsonStr(finish),
                elapsedMs(started),
                "最终答案已生成"
        );
        agentRunService.markRunSuccess(context.getRunId(), JSONUtil.toJsonStr(finish));
        SSEServer.sendMsg(context.getAuthenticatedUser().getSseClientId(), JSONUtil.toJsonStr(finish), SSEMsgType.FINISH);

        // 触发会话记忆提取（异步）：每 N 轮用户消息触发一次，复用 Agent 决策 prompt 前缀以命中 KV cache
        try {
            Long userId = context.getAuthenticatedUser().getUserId();
            Long sessionId = context.getChatSessionId();
            if (userId == null || sessionId == null) {
                return;
            }
            List<ChatMessage> messages = chatSessionService.listMessagesBySessionIdOnly(sessionId);
            long currentUserMsgCount = messages == null ? 0
                    : messages.stream().filter(m -> "user".equalsIgnoreCase(m.getRole())).count();
            long lastExtracted = memoryExtractCounter.getLastExtractedUserMsgCount(sessionId);
            int triggerRounds = normalizePositive(memoryProperties.getExtract().getTriggerRounds(), 5);
            if (currentUserMsgCount - lastExtracted < triggerRounds) {
                log.debug("记忆提取未到触发轮次（current={} last={} trigger={}），跳过 sessionId={}",
                        currentUserMsgCount, lastExtracted, triggerRounds, sessionId);
                return;
            }
            // 复用 Agent 决策 prompt 的 memory 段（已加载的最近对话），保持前缀一致以命中 KV cache
            // 同时把 authenticatedUser 透传到 memoryTaskExecutor 线程，确保下游 LLM 调用与 Agent 决策同源
            sessionMemoryExtractor.extract(userId, sessionId, memory, allowedTools, summarySection, userProfileSection, currentUserMsgCount, context.getAuthenticatedUser());
        } catch (Exception e) {
            log.warn("触发会话记忆提取失败", e);
        }
    }

    private List<ToolDescriptor> resolveAllowedTools(AgentExecuteContext context) {
        boolean knowledgeBaseEnabled = context.getChatEntity() != null
                && !Boolean.FALSE.equals(context.getChatEntity().getKnowledgeBaseEnabled());
        boolean ragEnabled = knowledgeBaseEnabled
                && context.getChatEntity() != null
                && Boolean.TRUE.equals(context.getChatEntity().getRagEnabled());
        boolean internetEnabled = context.getChatEntity() != null
                && Boolean.TRUE.equals(context.getChatEntity().getInternetEnabled());
        List<ToolDescriptor> tools = toolRegistry.allowedDescriptors().stream()
                .filter(tool -> {
                    if ("kb.search".equals(tool.getName())) {
                        return knowledgeBaseEnabled;
                    }
                    if ("rag.search".equals(tool.getName())) {
                        return ragEnabled;
                    }
                    if ("web.search".equals(tool.getName()) || "web.fetch".equals(tool.getName())) {
                        return internetEnabled;
                    }
                    return true;
                })
                .collect(Collectors.toList());
        // 追加用户的 MCP 工具（按 userId 隔离，不受 enabled-tools 白名单管控）
        Long userId = context.getAuthenticatedUser() != null ? context.getAuthenticatedUser().getUserId() : null;
        if (userId != null) {
            mcpToolRegistry.ensureLoaded(userId);
            List<ToolDescriptor> mcpTools = mcpToolRegistry.getDescriptors(userId);
            if (mcpTools != null && !mcpTools.isEmpty()) {
                tools.addAll(mcpTools);
            }
        }
        return tools;
    }

    /**
     * Agent 启动前的 Wiki 预检索（spec 7.2）。
     * <p>
     * 仅当 knowledgeBaseEnabled=true 时执行 Wiki 检索；若 ragEnabled=true 再叠加 RAG 预检索。
     * 命中内容通过 {@link PromptTemplateManager#buildWikiPrompt} 拼装为"## 知识库 Wiki（预检索）"
     * 区块，注入首轮决策 prompt，让 LLM 知悉已有 wiki 资料并具备调用 kb.search 的动机。
     * 任一检索阶段异常都不阻断主流程，仅记录告警。
     */
    private String doAgentWikiSearch(AgentExecuteContext context) {
        if (context == null || context.getChatEntity() == null || context.getAuthenticatedUser() == null) {
            return "";
        }
        ChatEntity chatEntity = context.getChatEntity();
        boolean knowledgeBaseEnabled = !Boolean.FALSE.equals(chatEntity.getKnowledgeBaseEnabled());
        if (!knowledgeBaseEnabled) {
            return "";
        }
        String question = chatEntity.getMessage();
        if (StrUtil.isBlank(question)) {
            return "";
        }
        Long userId = context.getAuthenticatedUser().getUserId();
        if (userId == null) {
            return "";
        }

        int topK = normalizePositive(wikiProperties.getRetrieval().getDefaultTopK(), 4);

        StringBuilder contentBuilder = new StringBuilder();

        // 1. Wiki 检索
        try {
            List<WikiPage> wikiPages = wikiSearchService.search(question, userId, topK);
            if (wikiPages != null && !wikiPages.isEmpty()) {
                for (WikiPage page : wikiPages) {
                    contentBuilder.append("### ").append(page.getTitle() == null ? "" : page.getTitle()).append("\n")
                            .append(page.getContentMd() == null ? "" : page.getContentMd())
                            .append("\n\n");
                }
            }
        } catch (Exception e) {
            log.warn("Agent Wiki 预检索失败, userId={}, question={}", userId, question, e);
        }

        // 2. RAG 叠加检索（ragEnabled=true 时）
        boolean ragEnabled = Boolean.TRUE.equals(chatEntity.getRagEnabled());
        if (ragEnabled) {
            try {
                List<Document> ragDocs = documentService.doSearch(question, userId, topK);
                if (ragDocs != null && !ragDocs.isEmpty()) {
                    contentBuilder.append("### 原始文档片段（RAG 预检索）\n");
                    for (Document doc : ragDocs) {
                        contentBuilder.append(doc.getText() == null ? "" : doc.getText()).append("\n\n");
                    }
                }
            } catch (Exception e) {
                log.warn("Agent RAG 预检索失败, userId={}, question={}", userId, question, e);
            }
        }

        if (contentBuilder.length() == 0) {
            return "";
        }
        return promptTemplateManager.buildWikiPrompt(contentBuilder.toString(), question);
    }

    private ToolCall toToolCall(JSONObject decision) {
        ToolCall toolCall = new ToolCall();
        toolCall.setToolCallId(StrUtil.blankToDefault(decision.getStr("tool_call_id"), UUID.randomUUID().toString()));
        toolCall.setName(decision.getStr("tool_name"));
        JSONObject args = decision.getJSONObject("arguments");
        toolCall.setArguments(args == null ? Map.of() : args.toBean(Map.class));
        return toolCall;
    }

    private JSONObject parseDecision(String raw) {
        String json = LlmJsonSanitizer.sanitize(raw);
        if (!JSONUtil.isTypeJSON(json)) {
            throw new IllegalStateException("模型未返回合法 JSON 决策: " + abbreviate(json));
        }
        return JSONUtil.parseObj(json);
    }

    private void sendContentChunk(AgentExecuteContext context, String content) {
        ChatStreamChunkResponse chunk = new ChatStreamChunkResponse();
        chunk.setContentChunk(content);
        chunk.setBotMsgId(context.getChatEntity().getBotMsgId());
        chunk.setRunId(context.getRunId());
        chunk.setChatSessionId(context.getChatSessionId());
        chunk.setSessionCode(context.getChatEntity().getSessionCode());
        chunk.setSceneType("agent");
        chunk.setSourceType(resolveSourceType(context));
        chunk.setChunkType("content");
        SSEServer.sendMsg(context.getAuthenticatedUser().getSseClientId(), JSONUtil.toJsonStr(chunk), SSEMsgType.ADD);
    }

    private void sendThinkingChunk(AgentExecuteContext context, String content) {
        if (StrUtil.isBlank(content)) {
            return;
        }
        ChatStreamChunkResponse chunk = new ChatStreamChunkResponse();
        chunk.setContentChunk(content);
        chunk.setBotMsgId(context.getChatEntity().getBotMsgId());
        chunk.setRunId(context.getRunId());
        chunk.setChatSessionId(context.getChatSessionId());
        chunk.setSessionCode(context.getChatEntity().getSessionCode());
        chunk.setSceneType("agent");
        chunk.setSourceType(resolveSourceType(context));
        chunk.setChunkType("thinking");
        SSEServer.sendMsg(context.getAuthenticatedUser().getSseClientId(), JSONUtil.toJsonStr(chunk), SSEMsgType.THINKING);
    }

    private String resolveSourceType(AgentExecuteContext context) {
        if (context.getChatEntity() != null && Boolean.TRUE.equals(context.getChatEntity().getRagEnabled())) {
            return "rag";
        }
        if (context.getChatEntity() != null && Boolean.TRUE.equals(context.getChatEntity().getInternetEnabled())) {
            return "internet";
        }
        return "chat";
    }

    private long elapsedMs(Instant started) {
        return Duration.between(started, Instant.now()).toMillis();
    }

    private int normalizePositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private String abbreviate(String text) {
        if (text == null || text.length() <= 200) {
            return text;
        }
        return text.substring(0, 200) + "...";
    }

    /**
     * 达到最大循环次数时的兜底答案：汇总本轮已收集的工具调用结果，避免直接抛异常导致前端崩溃。
     */
    private String buildMaxIterationsFallback(List<Map<String, Object>> observations) {
        StringBuilder sb = new StringBuilder();
        sb.append("> ⚠️ **已达到 Agent 最大循环次数**，未能给出完整最终答案。以下是本轮已收集到的工具调用结果：\n\n");
        if (observations == null || observations.isEmpty()) {
            sb.append("本轮未收集到任何工具调用结果，建议改写问题或缩小范围后重试。");
            return sb.toString();
        }
        for (int i = 0; i < observations.size(); i++) {
            Map<String, Object> obs = observations.get(i);
            Object toolName = obs.getOrDefault("toolName", "unknown");
            Object success = obs.getOrDefault("success", Boolean.FALSE);
            Object content = obs.get("content");
            sb.append("### ").append(i + 1).append(". 工具: ").append(toolName)
                    .append("（").append(Boolean.TRUE.equals(success) ? "成功" : "失败").append("）\n");
            if (content != null) {
                String text = content.toString();
                if (text.length() > 800) {
                    text = text.substring(0, 800) + "...(已截断)";
                }
                sb.append(text).append("\n\n");
            }
        }
        sb.append("---\n\n你可以基于以上已收集到的信息重新提问，或调整问题范围以获得更精准的回答。");
        return sb.toString();
    }

    /**
     * 提取已生成的部分内容用于回填。
     * 当前实现返回空字符串，因为 Agent Loop 中最终答案是一次性生成的，
     * 取消时通常没有完整 finalAnswer；thinking 内容已通过 SSE 推送给前端。
     */
    private String extractPartialContent(AgentExecuteContext context) {
        return "";
    }
}
