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
import com.itgeo.fitmate.api.agent.memory.AgentMemoryService;
import com.itgeo.fitmate.api.agent.memory.ContextCompressService;
import com.itgeo.fitmate.api.agent.memory.dto.MemoryLoadResult;
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

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            if (Duration.between(runStarted, Instant.now()).toSeconds() > maxDurationSeconds) {
                throw new IllegalStateException("Agent执行超时");
            }
            if (cancellationRegistry.isCancelled(context.getRunId())) {
                throw new AgentCancelledException(extractPartialContent(context));
            }

            String prompt = agentPromptBuilder.buildDecisionPrompt(context, memory, observations, allowedTools, wikiContext, summarySection);
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
                        sendThinkingChunk(context, reasoningDelta);
                    }
                    if (StrUtil.isNotBlank(contentDelta)) {
                        decisionContent.append(contentDelta);
                    }
                });
                decisionText = decisionContent.toString();
                Map<String, Object> llmOutput = new LinkedHashMap<>();
                llmOutput.put("decision", sanitizeDecision(decisionText));
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
                finishWithAnswer(context, finalAnswer, observations);
                return;
            }

            if (!"tool_call".equalsIgnoreCase(action)) {
                finishWithAnswer(context, sanitizeDecision(decisionText), observations);
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

        throw new IllegalStateException("Agent达到最大循环次数仍未生成最终答案");
    }

    private void finishWithAnswer(AgentExecuteContext context, String finalAnswer, List<Map<String, Object>> observations) {
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
        sendContentChunk(context, finalAnswer);
        TokenUsage accumulatedUsage = context.getAccumulatedUsage();
        String usageJson = (accumulatedUsage != null && accumulatedUsage.getTotalTokens() != null)
                ? JSONUtil.toJsonStr(accumulatedUsage) : null;
        chatSessionService.finishAssistantMessage(
                context.getAssistantMessageId(),
                finalAnswer,
                observations.isEmpty() ? null : JSONUtil.toJsonStr(observations),
                usageJson
        );

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
    }

    private List<ToolDescriptor> resolveAllowedTools(AgentExecuteContext context) {
        boolean knowledgeBaseEnabled = context.getChatEntity() != null
                && !Boolean.FALSE.equals(context.getChatEntity().getKnowledgeBaseEnabled());
        boolean ragEnabled = knowledgeBaseEnabled
                && context.getChatEntity() != null
                && Boolean.TRUE.equals(context.getChatEntity().getRagEnabled());
        boolean internetEnabled = context.getChatEntity() != null
                && Boolean.TRUE.equals(context.getChatEntity().getInternetEnabled());
        return toolRegistry.allowedDescriptors().stream()
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
        String json = sanitizeDecision(raw);
        if (!JSONUtil.isTypeJSON(json)) {
            throw new IllegalStateException("模型未返回合法 JSON 决策: " + abbreviate(json));
        }
        return JSONUtil.parseObj(json);
    }

    private String sanitizeDecision(String raw) {
        String text = StrUtil.blankToDefault(raw, "").trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        return text;
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
     * 提取已生成的部分内容用于回填。
     * 当前实现返回空字符串，因为 Agent Loop 中最终答案是一次性生成的，
     * 取消时通常没有完整 finalAnswer；thinking 内容已通过 SSE 推送给前端。
     */
    private String extractPartialContent(AgentExecuteContext context) {
        return "";
    }
}
