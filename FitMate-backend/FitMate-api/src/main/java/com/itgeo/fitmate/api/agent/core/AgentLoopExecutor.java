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

    @Resource
    private com.itgeo.fitmate.api.agent.config.SubAgentProperties subAgentProperties;

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
                "Agent started",
                null,
                null,
                0,
                JSONUtil.toJsonStr(Map.of("message", context.getChatEntity().getMessage())),
                "Agent execution started"
        );

        int maxIterations = normalizePositive(agentProperties.getMaxIterations(), 100);
        int maxToolCalls = normalizePositive(agentProperties.getMaxToolCalls(), 300);
        int maxDurationSeconds = normalizePositive(agentProperties.getMaxRunDurationSeconds(), 1800);
        // 用户画像在本轮 Agent 执行期间不会变化（记忆写入在 run 结束后异步发生），
        // 在循环外计算一次即可，同时作为记忆提取 prompt 的前缀组成部分，保证与 Agent 决策 prompt 完全一致以命中 KV cache。
        String userProfileSection = memoryReader.loadProfileSection(context.getAuthenticatedUser().getUserId());

        runLoop(context, runStarted, memory, observations, allowedTools, allowedToolNames,
                wikiContext, summarySection, userProfileSection, maxIterations, maxToolCalls, maxDurationSeconds);
    }

    /**
     * Agent ReAct 主循环体。主 Agent 与 Sub-Agent 共用此方法。
     * <p>
     * 调用方负责在调用前完成状态准备（记忆加载、工具解析、Wiki 预检索、预算计算等），
     * 本方法只负责循环执行 LLM 决策 → 工具调用 → 观察回灌，直到产出 final_answer 或达到上限。
     *
     * @param runStarted 循环起始时间，用于超时判断
     * @param memory 已加载的历史对话（只读）
     * @param observations 观察结果累积列表（本方法内追加）
     * @param allowedTools 当前 run 允许的工具描述符列表（只读，用于 prompt 构建）
     * @param allowedToolNames 允许工具名集合（只读，用于工具调用校验）
     * @param wikiContext Wiki 预检索内容（只读，注入首轮 prompt）
     * @param summarySection 上下文压缩摘要段（只读）
     * @param userProfileSection 用户画像段（只读，KV cache 命中关键）
     * @param maxIterations 最大迭代轮次
     * @param maxToolCalls 最大工具调用次数
     * @param maxDurationSeconds 最大执行时长（秒）
     */
    private void runLoop(AgentExecuteContext context,
                         Instant runStarted,
                         List<Map<String, String>> memory,
                         List<Map<String, Object>> observations,
                         List<ToolDescriptor> allowedTools,
                         Set<String> allowedToolNames,
                         String wikiContext,
                         String summarySection,
                         String userProfileSection,
                         int maxIterations,
                         int maxToolCalls,
                         int maxDurationSeconds) {
        int toolCallCount = 0;

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            if (Duration.between(runStarted, Instant.now()).toSeconds() > maxDurationSeconds) {
                throw new IllegalStateException("Agent execution timed out");
            }
            if (isContextCancelled(context)) {
                throw new AgentCancelledException(extractPartialContent(context));
            }

            String prompt = agentPromptBuilder.buildDecisionPrompt(context, memory, observations, allowedTools, wikiContext, summarySection, userProfileSection);
            AgentStep llmStep = agentTraceService.startEvent(
                    context,
                    "llm_started",
                    "Agent: Let me think...",
                    null,
                    null,
                    iteration,
                    JSONUtil.toJsonStr(Map.of("observationCount", observations.size())),
                    "Deciding next step..."
            );

            Instant llmStarted = Instant.now();
            String decisionText;
            FinalAnswerStreamState streamState = new FinalAnswerStreamState();
            try {
                StringBuilder reasoningContent = new StringBuilder();
                StringBuilder decisionContent = new StringBuilder();
                long[] firstChunkTime = {0};
                int[] chunkCount = {0};
                llmGateway.streamWithReasoning(prompt).toStream().forEach(chunk -> {
                    if (firstChunkTime[0] == 0) {
                        firstChunkTime[0] = System.currentTimeMillis();
                    }
                    chunkCount[0]++;
                    if (isContextCancelled(context)) {
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
                log.info("[STREAM-DIAG2] iteration={} firstChunkDelay={}ms totalConsume={}ms chunkCount={} on={}",
                        iteration,
                        firstChunkTime[0] - llmStarted.toEpochMilli(),
                        System.currentTimeMillis() - llmStarted.toEpochMilli(),
                        chunkCount[0],
                        Thread.currentThread().getName());
                decisionText = decisionContent.toString();
                Map<String, Object> llmOutput = new LinkedHashMap<>();
                llmOutput.put("decision", LlmJsonSanitizer.sanitize(decisionText));
                if (StrUtil.isNotBlank(reasoningContent)) {
                    llmOutput.put("reasoningContent", reasoningContent.toString());
                }
                flushSseBuffers(context);
                agentTraceService.finishEvent(
                        context,
                        llmStep,
                        "llm_finished",
                        JSONUtil.toJsonStr(llmOutput),
                        elapsedMs(llmStarted),
                        "Done thinking"
                );
            } catch (Exception e) {
                agentTraceService.failEvent(context, llmStep, "run_failed", e.getMessage(), elapsedMs(llmStarted), "Reasoning failed");
                throw e;
            }

            JSONObject decision = parseDecision(decisionText);
            String action = decision.getStr("action");
            if ("final".equalsIgnoreCase(action)) {
                String finalAnswer = StrUtil.blankToDefault(decision.getStr("final_answer"), "Processing complete. Please review the execution trace above.");
                // 如果状态机已流式推送，跳过整段推送；否则走原逻辑
                if (!streamState.hasStreamed()) {
                    sendContentChunk(context, finalAnswer);
                }
                finishWithAnswer(context, finalAnswer, observations, memory, allowedTools, summarySection, userProfileSection);
                return;
            }

            if ("spawn_subagent".equalsIgnoreCase(action)) {
                // Sub-Agent 派生分支：主 Agent 决策将复杂子任务交给 Sub-Agent 执行。
                // spawnSubAgent 内部完成 Sub-Agent run 创建、独立 runLoop 执行、结果回写到 observations，
                // 随后 continue 让主 Agent 下一轮基于 Sub-Agent 结果继续决策。
                spawnSubAgent(context, decision, observations, memory, allowedTools, summarySection, userProfileSection);
                continue;
            }

            if (!"tool_call".equalsIgnoreCase(action)) {
                // 异常决策分支：未流式推送过，需要显式推送一次 final_answer
                sendContentChunk(context, LlmJsonSanitizer.sanitize(decisionText));
                finishWithAnswer(context, LlmJsonSanitizer.sanitize(decisionText), observations, memory, allowedTools, summarySection, userProfileSection);
                return;
            }

            if (toolCallCount >= maxToolCalls) {
                throw new IllegalStateException("Tool call limit exceeded");
            }
            toolCallCount++;

            if (isContextCancelled(context)) {
                throw new AgentCancelledException(extractPartialContent(context));
            }
            ToolCall toolCall = toToolCall(decision);
            ToolResult result;
            AgentStep toolStep = agentTraceService.startEvent(
                    context,
                    "tool_call_started",
                    "Calling tool: " + toolCall.getName(),
                    toolCall.getName(),
                    toolCall.getToolCallId(),
                    iteration,
                    JSONUtil.toJsonStr(toolCall),
                    "Calling tool: " + toolCall.getName()
            );
            Instant toolStarted = Instant.now();
            if (!allowedToolNames.contains(toolCall.getName())) {
                result = ToolResult.error("Tool not allowed in current request: " + toolCall.getName());
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
                        "Tool finished: " + toolCall.getName()
                );
            } else {
                agentTraceService.failEvent(
                        context,
                        toolStep,
                        "tool_call_failed",
                        result.getErrorMessage(),
                        elapsedMs(toolStarted),
                        "Tool failed: " + toolCall.getName()
                );
            }
        }

        // 达到最大循环次数仍未生成最终答案：不抛异常，改为构造兜底答案走正常完成流程，
        // 避免前端因收到 failed 状态 FINISH 事件而崩溃，同时把本轮已收集的工具调用结果作为部分信息反馈给用户。
        log.warn("Agent reached max iterations without final answer, runId={}, maxIterations={}", context.getRunId(), maxIterations);
        String fallbackAnswer = buildMaxIterationsFallback(observations);
        finishWithAnswer(context, fallbackAnswer, observations, memory, allowedTools, summarySection, userProfileSection);
    }

    /**
     * Sub-Agent 派生分支实现。
     * <p>
     * 流程：
     * 1. 从 LLM 决策 JSON 解析 task（必填）与 allowed_tools（可选，逗号分隔）；
     * 2. createSubAgentRun 创建独立 Sub-Agent run（携带 parentRunId）；
     * 3. 设置主 Agent context.activeSubAgentRunId（用于取消级联）；
     * 4. 推送 subagent_started AGENT_STEP 事件（携带 subagentRunId）；
     * 5. forSubAgent 构造 Sub-Agent context，工具集与主 Agent 一致（KV cache 命中关键）；
     * 6. 标记 Sub-Agent run 为 running；
     * 7. 递归调用 runLoop 执行 Sub-Agent 循环；
     * 8. 推送 subagent_finished 事件，把 Sub-Agent 结果回写到主 Agent observations；
     * 9. 清除 activeSubAgentRunId，主 Agent 下一轮迭代基于 Sub-Agent 结果继续决策。
     * <p>
     * 异常处理：Sub-Agent 执行失败时标记 run failed，回写错误 observation，不 re-throw，
     * 让主 Agent 能基于错误信息继续决策（如重试或给出兜底答案）。
     *
     * @param context 主 Agent 上下文
     * @param decision LLM 决策 JSON（含 task / allowed_tools）
     * @param observations 主 Agent 观察结果累积列表，Sub-Agent 完成后追加一条
     * @param memory 主 Agent 已加载的历史对话（Sub-Agent 复用作为上下文，KV cache 命中）
     * @param allowedTools 主 Agent 当前允许工具（Sub-Agent 复用全集，工具列表前缀一致以命中 KV cache）
     * @param summarySection 上下文压缩摘要段
     * @param userProfileSection 用户画像段（KV cache 命中关键）
     */
    private void spawnSubAgent(AgentExecuteContext context,
                               JSONObject decision,
                               List<Map<String, Object>> observations,
                               List<Map<String, String>> memory,
                               List<ToolDescriptor> allowedTools,
                               String summarySection,
                               String userProfileSection) {
        // 0. 递归屏蔽兜底：Sub-Agent 不允许派生 Sub-Agent，防止无限递归
        if (context.isSubAgent()) {
            throw new IllegalStateException("Sub-Agent spawning Sub-Agent is not allowed (recursion guard)");
        }

        // 1. 解析 Sub-Agent 任务描述（必填）
        String task = decision.getStr("task");
        if (StrUtil.isBlank(task)) {
            throw new IllegalStateException("spawn_subagent decision missing 'task' field");
        }
        task = task.trim();

        // 2. 解析可选 allowed_tools（逗号分隔字符串）；未指定时为 null，表示复用主 Agent 工具全集
        String allowedToolsStr = decision.getStr("allowed_tools");
        Set<String> subAgentAllowedTools = null;
        if (StrUtil.isNotBlank(allowedToolsStr)) {
            subAgentAllowedTools = java.util.Arrays.stream(allowedToolsStr.split(","))
                    .map(String::trim)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toSet());
        }

        // 3. 创建 Sub-Agent run（携带 parentRunId）
        Long parentRunId = context.getRunId();
        Long subRunId = agentRunService.createSubAgentRun(
                parentRunId,
                context.getAuthenticatedUser().getUserId(),
                context.getChatSessionId(),
                task
        );

        // 4. 设置主 Agent context.activeSubAgentRunId，供取消级联使用
        context.setActiveSubAgentRunId(subRunId);

        // 5. 推送 subagent_started 事件（携带 subagentRunId，让前端建立父子关系）
        AgentStep subStartStep = agentTraceService.startEvent(
                context,
                "subagent_started",
                "Sub-agent spawned",
                null,
                null,
                subRunId,
                null,
                JSONUtil.toJsonStr(Map.of("subRunId", subRunId, "task", task)),
                "Sub-agent spawned, executing subtask"
        );
        Instant subStarted = Instant.now();

        // 6. 构造 Sub-Agent context（forSubAgent 工厂方法）
        AgentExecuteContext subContext = AgentExecuteContext.forSubAgent(
                context, subRunId, task, subAgentAllowedTools);

        // 7. 注册 Sub-Agent run 到取消注册表 + 标记 running
        // register 确保 isCancelled(subRunId) 能正常工作，且 finally 中 unregister 清理避免内存泄漏
        cancellationRegistry.register(subRunId);
        agentRunService.markRunRunning(subRunId);

        // 8. 计算 Sub-Agent 预算（独立配置，避免 Sub-Agent 消耗主 Agent 预算）
        int subMaxIterations = normalizePositive(subAgentProperties.getMaxIterations(), 40);
        int subMaxToolCalls = normalizePositive(subAgentProperties.getMaxToolCalls(), 80);
        int subMaxDurationSeconds = normalizePositive(subAgentProperties.getMaxRunDurationSeconds(), 600);

        // Sub-Agent 工具描述符列表（用于 prompt 构建）与主 Agent 全集一致，保持 prompt 前缀一致以命中 KV cache；
        // subAgentAllowedTools 仅在工具调用校验层生效，限制 Sub-Agent 实际可调用的工具子集。
        // 若 LLM 未指定 allowed_tools（null 或空），Sub-Agent 可用全部工具。
        Set<String> subAllowedToolNames;
        if (subAgentAllowedTools != null && !subAgentAllowedTools.isEmpty()) {
            subAllowedToolNames = subAgentAllowedTools;
        } else {
            subAllowedToolNames = allowedTools.stream()
                    .map(ToolDescriptor::getName)
                    .collect(Collectors.toSet());
        }
        List<Map<String, Object>> subObservations = new ArrayList<>();

        try {
            // 9. 递归调用 runLoop 执行 Sub-Agent 循环
            runLoop(subContext,
                    subStarted,
                    memory,
                    subObservations,
                    allowedTools,
                    subAllowedToolNames,
                    null,  // Sub-Agent 不再做 Wiki 预检索（主 Agent 已注入 wikiContext 到前缀）
                    summarySection,
                    userProfileSection,
                    subMaxIterations,
                    subMaxToolCalls,
                    subMaxDurationSeconds);

            // 10. Sub-Agent 正常完成：推送 subagent_finished 事件，回写成功 observation
            String subResult = subContext.getSubAgentResult();
            agentTraceService.finishEvent(
                    context,
                    subStartStep,
                    "subagent_finished",
                    JSONUtil.toJsonStr(Map.of("subRunId", subRunId, "resultLength",
                            subResult == null ? 0 : subResult.length())),
                    elapsedMs(subStarted),
                    "Sub-agent completed"
            );

            Map<String, Object> observation = new LinkedHashMap<>();
            observation.put("toolCallId", "subagent-" + subRunId);
            observation.put("toolName", "subagent");
            observation.put("success", true);
            observation.put("content", StrUtil.blankToDefault(subResult, "Sub-agent returned no result"));
            observation.put("data", Map.of("subRunId", subRunId, "task", task));
            observations.add(observation);
        } catch (Exception e) {
            // Sub-Agent 异常处理：区分取消与失败两种语义
            boolean isCancelled = e instanceof AgentCancelledException;
            String errorMessage = e.getMessage();
            if (isCancelled) {
                // 取消级联：主 Agent 被取消导致 Sub-Agent 中断，标记 cancelled 保持语义一致
                log.info("Sub-agent interrupted due to parent run cancellation, parentRunId={}, subRunId={}", parentRunId, subRunId);
                agentRunService.markRunCancelled(subRunId, "Parent agent cancelled, sub-agent cascade interrupted");
            } else {
                // 真正失败：标记 failed
                log.warn("Sub-agent execution failed, parentRunId={}, subRunId={}", parentRunId, subRunId, e);
                agentRunService.markRunFailed(subRunId, errorMessage);
            }
            agentTraceService.failEvent(
                    context,
                    subStartStep,
                    "subagent_finished",
                    errorMessage,
                    elapsedMs(subStarted),
                    isCancelled ? "Sub-agent cancelled" : "Sub-agent failed: " + errorMessage
            );

            Map<String, Object> observation = new LinkedHashMap<>();
            observation.put("toolCallId", "subagent-" + subRunId);
            observation.put("toolName", "subagent");
            observation.put("success", false);
            observation.put("content", isCancelled
                    ? "Sub-agent interrupted due to parent agent cancellation"
                    : "Sub-agent failed: " + errorMessage);
            observation.put("data", Map.of("subRunId", subRunId, "task", task,
                    "error", errorMessage, "cancelled", isCancelled));
            observations.add(observation);
        } finally {
            // 11. 清除 activeSubAgentRunId + unregister Sub-Agent run 避免内存泄漏
            context.setActiveSubAgentRunId(null);
            cancellationRegistry.unregister(subRunId);
        }
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
                "Ready to respond",
                null,
                null,
                null,
                JSONUtil.toJsonStr(Map.of("observationCount", observations.size())),
                "Generating response..."
        );
        Instant started = Instant.now();
        if (isContextCancelled(context)) {
            throw new AgentCancelledException(extractPartialContent(context));
        }

        // Sub-Agent 短路分支：Sub-Agent 不创建 ChatMessage、不推送 FINISH、不触发记忆提取，
        // 只把 finalAnswer 写入 context.subAgentResult 供主 Agent 读取，并标记 Sub-Agent run 成功。
        // final_answer 事件仍正常推送，让前端能看到 Sub-Agent 生成最终答案的轨迹。
        if (context.isSubAgent()) {
            context.setSubAgentResult(finalAnswer);
            agentTraceService.finishEvent(
                    context,
                    finalStep,
                    "final_answer",
                    JSONUtil.toJsonStr(Map.of("finalAnswer", finalAnswer, "observationCount", observations.size())),
                    elapsedMs(started),
                    "Sub-agent ready"
            );
            agentRunService.markRunSuccess(context.getRunId(),
                    JSONUtil.toJsonStr(Map.of("finalAnswer", finalAnswer)));
            flushSseBuffers(context);
            return;
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
                log.warn("Failed to save thinking content, messageId={}, runId={}, error={}",
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
                "Ready to respond"
        );
        agentRunService.markRunSuccess(context.getRunId(), JSONUtil.toJsonStr(finish));
        // FINISH 之前 flush 残留 chunk buffer，确保前端先收完 chunk 再收到结束信号
        flushSseBuffers(context);
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
                log.debug("Memory extraction not yet triggered (current={} last={} trigger={}), skipping sessionId={}",
                        currentUserMsgCount, lastExtracted, triggerRounds, sessionId);
                return;
            }
            // 复用 Agent 决策 prompt 的 memory 段（已加载的最近对话），保持前缀一致以命中 KV cache
            // 同时把 authenticatedUser 透传到 memoryTaskExecutor 线程，确保下游 LLM 调用与 Agent 决策同源
            sessionMemoryExtractor.extract(userId, sessionId, memory, allowedTools, summarySection, userProfileSection, currentUserMsgCount, context.getAuthenticatedUser());
        } catch (Exception e) {
            log.warn("Session memory extraction failed", e);
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
            log.warn("Agent Wiki pre-search failed, userId={}, question={}", userId, question, e);
        }

        // 2. RAG 叠加检索（ragEnabled=true 时）
        boolean ragEnabled = Boolean.TRUE.equals(chatEntity.getRagEnabled());
        if (ragEnabled) {
            try {
                List<Document> ragDocs = documentService.doSearch(question, userId, topK);
                if (ragDocs != null && !ragDocs.isEmpty()) {
                    contentBuilder.append("### Document fragments (RAG pre-retrieval)\n");
                    for (Document doc : ragDocs) {
                        contentBuilder.append(doc.getText() == null ? "" : doc.getText()).append("\n\n");
                    }
                }
            } catch (Exception e) {
                log.warn("Agent RAG pre-search failed, userId={}, question={}", userId, question, e);
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
            throw new IllegalStateException("Model did not return valid JSON decision: " + abbreviate(json));
        }
        return JSONUtil.parseObj(json);
    }

    private void sendContentChunk(AgentExecuteContext context, String content) {
        if (StrUtil.isBlank(content)) {
            return;
        }
        SseChunkBuffer buffer = context.getContentSseBuffer();
        if (buffer == null) {
            buffer = new SseChunkBuffer();
            context.setContentSseBuffer(buffer);
        }
        buffer.append(content);
        if (buffer.shouldFlush()) {
            String drained = buffer.drain();
            if (drained != null) {
                doSendContentChunk(context, drained);
            }
        }
    }

    private void sendThinkingChunk(AgentExecuteContext context, String content) {
        if (StrUtil.isBlank(content)) {
            return;
        }
        SseChunkBuffer buffer = context.getThinkingSseBuffer();
        if (buffer == null) {
            buffer = new SseChunkBuffer();
            context.setThinkingSseBuffer(buffer);
        }
        buffer.append(content);
        if (buffer.shouldFlush()) {
            String drained = buffer.drain();
            if (drained != null) {
                doSendThinkingChunk(context, drained);
            }
        }
    }

    /**
     * flush 所有 SSE chunk buffer 的残留内容。
     * 必须在 FINISH 事件之前调用，确保前端先收到完整 chunk 再收到结束信号。
     * 顺序：thinking 先于 content，保证前端渲染顺序正确。
     * <p>
     * public 暴露：供 AgentAsyncServiceImpl 在异常/取消路径发送 FINISH 之前调用。
     */
    public void flushSseBuffers(AgentExecuteContext context) {
        SseChunkBuffer thinking = context.getThinkingSseBuffer();
        if (thinking != null && thinking.hasPending()) {
            String drained = thinking.drain();
            if (drained != null) {
                doSendThinkingChunk(context, drained);
            }
        }
        SseChunkBuffer content = context.getContentSseBuffer();
        if (content != null && content.hasPending()) {
            String drained = content.drain();
            if (drained != null) {
                doSendContentChunk(context, drained);
            }
        }
    }

    private void doSendContentChunk(AgentExecuteContext context, String content) {
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

    private void doSendThinkingChunk(AgentExecuteContext context, String content) {
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
        sb.append("> ⚠️ **Agent reached maximum iterations** without producing a complete answer. Below are the tool call results collected so far:\n\n");
        if (observations == null || observations.isEmpty()) {
            sb.append("No tool call results were collected. Please try rephrasing your question or narrowing the scope.");
            return sb.toString();
        }
        for (int i = 0; i < observations.size(); i++) {
            Map<String, Object> obs = observations.get(i);
            Object toolName = obs.getOrDefault("toolName", "unknown");
            Object success = obs.getOrDefault("success", Boolean.FALSE);
            Object content = obs.get("content");
            sb.append("### ").append(i + 1).append(". Tool: ").append(toolName)
                    .append(" (").append(Boolean.TRUE.equals(success) ? "success" : "failed").append(")\n");
            if (content != null) {
                String text = content.toString();
                if (text.length() > 800) {
                    text = text.substring(0, 800) + "...(truncated)";
                }
                sb.append(text).append("\n\n");
            }
        }
        sb.append("---\n\nYou may rephrase your question based on the above results, or narrow the scope for a more precise answer.");
        return sb.toString();
    }

    /**
     * 判断当前 context 是否已被取消。
     * <p>
     * 主 Agent：检查自身 runId 是否被取消。
     * Sub-Agent：检查自身 runId 或父 runId 是否被取消（取消级联）。
     * <p>
     * 取消级联设计：Sub-Agent 不暴露独立取消接口，主 Agent 被取消时，
     * Sub-Agent 通过 parentRunId 检查感知取消，在下一次检查点抛 AgentCancelledException。
     *
     * @param context 当前 Agent 上下文（主 Agent 或 Sub-Agent）
     * @return 已取消返回 true，否则 false
     */
    private boolean isContextCancelled(AgentExecuteContext context) {
        if (cancellationRegistry.isCancelled(context.getRunId())) {
            return true;
        }
        // Sub-Agent 取消级联：父 run 被取消时，子 run 也视为取消
        Long parentRunId = context.getParentRunId();
        return parentRunId != null && cancellationRegistry.isCancelled(parentRunId);
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
