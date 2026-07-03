package com.itgeo.fitmate.api.chat.application.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.chat.application.ChatService;
import com.itgeo.fitmate.api.chat.application.ChatSessionService;
import com.itgeo.fitmate.api.chat.dto.ChatEntity;
import com.itgeo.fitmate.api.chat.dto.ChatResponseEntity;
import com.itgeo.fitmate.api.chat.dto.ChatStreamChunkResponse;
import com.itgeo.fitmate.api.chat.dto.ReasoningStreamChunk;
import com.itgeo.fitmate.api.chat.dto.TokenUsage;
import com.itgeo.fitmate.api.chat.infrastructure.ReasoningChatClient;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatSession;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity.BodyMetrics;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.mapper.BodyMetricsMapper;
import com.itgeo.fitmate.api.fitness.training.infrastructure.entity.TrainingLog;
import com.itgeo.fitmate.api.fitness.training.infrastructure.mapper.TrainingLogMapper;
import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import com.itgeo.fitmate.api.rag.application.DocumentService;
import com.itgeo.fitmate.api.search.application.SearXngService;
import com.itgeo.fitmate.api.search.dto.SearchResult;
import com.itgeo.fitmate.api.sse.domain.SSEMsgType;
import com.itgeo.fitmate.api.sse.infrastructure.SSEServer;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 聊天能力服务实现。
 * <p>
 * 说明：
 * 1. 正式对话主链路已统一收敛到 Agent 模式，本类仅保留调试方法与 Agent 场景的流式发送入口；
 * 2. 所有正式聊天入口都要求显式传入 AuthenticatedUserContext；
 * 3. 实现类负责把流式分片通过 SSE 推送给前端，并在结束时发送 FINISH 事件。
 */
@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    @Resource
    private ObjectProvider<DocumentService> documentServiceProvider;
    @Resource
    private ChatSessionService chatSessionService;
    @Resource
    private PromptTemplateManager promptTemplateManager;
    // 查询用户数据来构建上下文，需要注入对应的 Mapper
    @Resource
    private TrainingLogMapper trainingLogMapper;
    @Resource
    private BodyMetricsMapper bodyMetricsMapper;

    /**
     * 增强数据容器
     * 用于存储 RAG 和联网搜索的数据
     */
    @Data
    private static class EnhancementData {
        private List<Document> ragContext;
        private List<SearchResult> searchResults;
    }

    private final ChatClient chatClient;

    @Resource
    private ReasoningChatClient reasoningChatClient;

    @Resource
    private SearXngService searXngService;

    public ChatServiceImpl(ChatClient.Builder chatClientBuilder,
                           ToolCallbackProvider tools,
                           ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder
                .defaultToolCallbacks(tools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Override
    public String chatTest(String prompt) {
        return chatClient.prompt(prompt).call().content();
    }

    @Override
    public Flux<ChatResponse> streamResponse(String prompt) {
        return chatClient.prompt(prompt).stream().chatResponse();
    }

    @Override
    public Flux<String> streamStr(String prompt) {
        return chatClient.prompt(prompt).stream().content();
    }

    /**
     * 执行 Agent 场景下的纯模型问答。
     * <p>
     * 步骤：
     * 1. 校验 Agent 链路传入的会话确实存在且属于当前用户；
     * 2. 使用 Agent 专用提示词包装当前问题；
     * 3. 发起流式调用，并把结果回填到既有 assistant 占位消息。
     */
    @Override
    public ChatResponseEntity doAgentChat(
            ChatEntity chatEntity,
            Long chatSessionId,
            Long assistantMessageId,
            Long runId,
            AuthenticatedUserContext authenticatedUser
    ) {
        String sourceType = resolveSourceType(chatEntity);
        ChatSession session = requireExistingSession(chatSessionId, authenticatedUser);

        // 1. 查询用户最近训练数据（最近14天）
        LocalDate fourteenDaysAgo = LocalDate.now().minusDays(14);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TrainingLog> trainingQuery =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        trainingQuery.eq("user_id", authenticatedUser.getUserId())
                .ge("training_date", fourteenDaysAgo)
                .orderByDesc("training_date");
        List<TrainingLog> recentLogs = trainingLogMapper.selectList(trainingQuery);

        // 2. 查询最新身体指标
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BodyMetrics> metricsQuery =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        metricsQuery.eq("user_id", authenticatedUser.getUserId())
                .orderByDesc("record_date")
                .last("LIMIT 1");
        BodyMetrics latestMetrics = bodyMetricsMapper.selectOne(metricsQuery);

        // 3. 构建用户上下文
        String userContext = promptTemplateManager.buildUserContext(
                authenticatedUser.getUserId(),
                recentLogs,
                latestMetrics
        );

        // 4. 构建最终提示词
        String finalPrompt = promptTemplateManager.buildAgentPrompt(
                userContext,
                chatEntity.getMessage()
        );
        String botMsgId = chatEntity.getBotMsgId();

        Flux<ReasoningStreamChunk> chunkFlux = reasoningChatClient.stream(finalPrompt);

        return streamAndSend(
                chunkFlux,
                authenticatedUser,
                botMsgId,
                session.getId(),
                session.getSessionCode(),
                assistantMessageId,
                runId,
                "agent",
                sourceType,
                null
        );
    }

    /**
     * 执行 Agent 场景下的联网增强问答。
     * <p>
     * 步骤：
     * 1. 校验 Agent 会话归属；
     * 2. 加载增强数据（Internet）；
     * 3. 构建提示词并发起流式调用；
     * 4. 将搜索来源一并透传给流式发送与最终回填逻辑。
     */
    @Override
    public ChatResponseEntity doAgentInternetSearch(
            ChatEntity chatEntity,
            Long chatSessionId,
            Long assistantMessageId,
            Long runId,
            AuthenticatedUserContext authenticatedUser
    ) {
        String sourceType = resolveSourceType(chatEntity);
        ChatSession session = requireExistingSession(chatSessionId, authenticatedUser);

        // 1. 加载增强数据
        EnhancementData data = loadEnhancementData(chatEntity, authenticatedUser);

        // 2. 构建提示词（Internet 模式不需要用户上下文）
        String finalPrompt = buildPromptByEnhancement(chatEntity, data, null);

        // 3. 调用模型
        Flux<ReasoningStreamChunk> chunkFlux = reasoningChatClient.stream(finalPrompt);

        // 4. 构建来源
        Object sources = buildSourcesByEnhancement(data, chatEntity);

        return streamAndSend(
                chunkFlux,
                authenticatedUser,
                chatEntity.getBotMsgId(),
                session.getId(),
                session.getSessionCode(),
                assistantMessageId,
                runId,
                "agent",
                sourceType,
                sources
        );
    }

    /**
     * 根据增强开关分发 Agent 场景执行路径。
     * <p>
     * 当前正式主链路同样只支持单开增强：
     * 1. 仅开 RAG 时，自动检索知识库后进入 Agent RAG 问答；
     * 2. 仅开联网时，进入 Agent 联网增强问答；
     * 3. 两者都关闭时，执行纯 Agent 问答；
     * 4. 两者同时开启时直接拒绝，不走 hybrid 预留分支。
     */
    @Override
    public ChatResponseEntity doAgentWithEnhancers(
            ChatEntity chatEntity,
            Long chatSessionId,
            Long assistantMessageId,
            Long runId,
            AuthenticatedUserContext authenticatedUser
    ) {
        boolean ragEnabled = isRagEnabled(chatEntity);
        boolean internetEnabled = isInternetEnabled(chatEntity);

        if (ragEnabled && internetEnabled) {
            throw new IllegalArgumentException("暂不支持同时开启知识库增强与联网补充");
        }
        if (ragEnabled) {
            return doAgentRagSearch(
                    chatEntity,
                    chatSessionId,
                    assistantMessageId,
                    runId,
                    authenticatedUser
            );
        }
        if (internetEnabled) {
            return doAgentInternetSearch(
                    chatEntity,
                    chatSessionId,
                    assistantMessageId,
                    runId,
                    authenticatedUser
            );
        }
        return doAgentChat(
                chatEntity,
                chatSessionId,
                assistantMessageId,
                runId,
                authenticatedUser
        );
    }

    /**
     * 消费模型返回的流式分片，并同步推送到 SSE。
     * <p>
     * 处理步骤：
     * 1. 逐片消费模型输出，解析 thinking 标签，分类推送 THINKING 或 ADD 事件；
     * 2. 汇总完整回答后，回填 assistant 占位消息内容与来源；
     * 3. 组装最终响应对象，补齐会话、运行与来源元数据；
     * 4. 在连接可用时发送 FINISH 事件，通知前端本轮输出结束。
     */
    private ChatResponseEntity streamAndSend(Flux<ReasoningStreamChunk> chunkFlux,
                                             AuthenticatedUserContext authenticatedUser,
                                             String botMsgId,
                                             Long chatSessionId,
                                             String sessionCode,
                                             Long assistantMessageId,
                                             Long runId,
                                             String sceneType,
                                             String sourceType,
                                             Object sources) {
        String sseClientId = authenticatedUser == null ? null : authenticatedUser.getSseClientId();
        final int[] chunkCount = {0};

        StringBuilder reasoningContent = new StringBuilder();
        StringBuilder normalContent = new StringBuilder();
        final TokenUsage[] lastUsage = {null};

        // 1. 逐片消费 DeepSeek 原生分片，分类推送 reasoning_content 与 content
        chunkFlux.toStream().forEach(chunk -> {
            if (chunk == null) {
                return;
            }

            // 捕获终止帧携带的 token 用量（usage-only chunk 的 reasoning/content 均为空）
            if (chunk.getUsage() != null) {
                lastUsage[0] = chunk.getUsage();
            }

            String thinkingChunk = StrUtil.blankToDefault(chunk.getReasoningContent(), "");
            String contentChunk = StrUtil.blankToDefault(chunk.getContent(), "");
            if (thinkingChunk.isEmpty() && contentChunk.isEmpty()) {
                return;
            }

            chunkCount[0]++;

            if (chunkCount[0] <= 10) {
                log.info("reasoning chunk preview, runId={}, botMsgId={}, chunkIndex={}, thinkingSize={}, contentSize={}",
                        runId, botMsgId, chunkCount[0], thinkingChunk.length(), contentChunk.length());
            }

            reasoningContent.append(thinkingChunk);
            normalContent.append(contentChunk);

            if (StrUtil.isNotBlank(sseClientId)) {
                // 推送思考内容（如果有）
                if (StrUtil.isNotBlank(thinkingChunk)) {
                    ChatStreamChunkResponse thinkingEvent = new ChatStreamChunkResponse();
                    thinkingEvent.setContentChunk(thinkingChunk);
                    thinkingEvent.setBotMsgId(botMsgId);
                    thinkingEvent.setRunId(runId);
                    thinkingEvent.setChatSessionId(chatSessionId);
                    thinkingEvent.setSessionCode(sessionCode);
                    thinkingEvent.setSceneType(sceneType);
                    thinkingEvent.setSourceType(sourceType);
                    thinkingEvent.setChunkType("thinking");

                    SSEServer.sendMsg(sseClientId, JSONUtil.toJsonStr(thinkingEvent), SSEMsgType.THINKING);
                }

                // 推送正式回答内容（如果有）
                if (StrUtil.isNotBlank(contentChunk)) {
                    ChatStreamChunkResponse addEvent = new ChatStreamChunkResponse();
                    addEvent.setContentChunk(contentChunk);
                    addEvent.setBotMsgId(botMsgId);
                    addEvent.setRunId(runId);
                    addEvent.setChatSessionId(chatSessionId);
                    addEvent.setSessionCode(sessionCode);
                    addEvent.setSceneType(sceneType);
                    addEvent.setSourceType(sourceType);
                    addEvent.setChunkType("content");

                    SSEServer.sendMsg(sseClientId, JSONUtil.toJsonStr(addEvent), SSEMsgType.ADD);
                }
            }

            log.debug("chat chunk received, botMsgId={}, thinkingSize={}, contentSize={}",
                    botMsgId, thinkingChunk.length(), contentChunk.length());
        });

        // 3. 获取完整正式回答内容，回填 assistant 占位消息内容、来源与 token 用量
        String fullContent = normalContent.toString();
        TokenUsage usage = lastUsage[0];
        String usageJson = (usage != null && usage.getTotalTokens() != null) ? JSONUtil.toJsonStr(usage) : null;
        chatSessionService.finishAssistantMessage(
                assistantMessageId,
                fullContent,
                sources == null ? null : JSONUtil.toJsonStr(sources),
                usageJson
        );

        // 4. 组装 FINISH 响应体，补齐会话、来源与用量元数据
        ChatResponseEntity chatResponseEntity = new ChatResponseEntity();
        chatResponseEntity.setMessage(fullContent);
        chatResponseEntity.setBotMsgId(botMsgId);
        chatResponseEntity.setRunId(runId);
        chatResponseEntity.setChatSessionId(chatSessionId);
        chatResponseEntity.setSessionCode(sessionCode);
        chatResponseEntity.setSceneType(sceneType);
        chatResponseEntity.setSourceType(sourceType);
        chatResponseEntity.setSources(sources);
        chatResponseEntity.setUsage(usage);

        // 5. 推送 FINISH 事件
        if (StrUtil.isNotBlank(sseClientId)) {
            SSEServer.sendMsg(
                    sseClientId,
                    JSONUtil.toJsonStr(chatResponseEntity),
                    SSEMsgType.FINISH
            );
        }

        log.info("chat stream finished, botMsgId={}, runId={}, contentLength={}, thinkingLength={}",
                botMsgId, runId, normalContent.length(), reasoningContent.length());
        return chatResponseEntity;
    }

    /**
     * 把 RAG 检索结果转换为前端可直接消费的来源结构。
     */
    private Object buildRagSources(List<Document> ragContext) {
        if (ragContext == null) {
            return List.of();
        }
        return ragContext.stream().map(doc -> {
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("title", doc.getMetadata() != null ? doc.getMetadata().getOrDefault("fileName", "知识库来源") : "知识库来源");
            item.put("snippet", doc.getText());
            item.put("url", "");
            item.put("extra", "");
            return item;
        }).collect(Collectors.toList());
    }

    /**
     * 把联网搜索结果转换为前端可直接消费的来源结构。
     */
    private Object buildInternetSources(List<SearchResult> searchResults) {
        if (searchResults == null) {
            return List.of();
        }
        return searchResults.stream().map(result -> {
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("title", result.getTitle() == null || result.getTitle().isBlank() ? "联网来源" : result.getTitle());
            item.put("snippet", result.getContent());
            item.put("url", result.getUrl());
            item.put("extra", "");
            return item;
        }).collect(Collectors.toList());
    }

    /**
     * 校验 Agent 运行链路传入的会话是否存在且属于当前用户。
     */
    private ChatSession requireExistingSession(Long chatSessionId, AuthenticatedUserContext authenticatedUser) {
        ChatSession session = chatSessionService.findByIdAndUserId(
                chatSessionId,
                authenticatedUser.getUserId()
        );
        if (session == null) {
            throw new IllegalArgumentException("Agent会话不存在或不属于当前用户");
        }
        return session;
    }

    private boolean isRagEnabled(ChatEntity chatEntity) {
        return chatEntity != null && Boolean.TRUE.equals(chatEntity.getRagEnabled());
    }

    private boolean isInternetEnabled(ChatEntity chatEntity) {
        return chatEntity != null && Boolean.TRUE.equals(chatEntity.getInternetEnabled());
    }

    /**
     * 根据增强类型加载对应数据
     */
    private EnhancementData loadEnhancementData(
            ChatEntity chatEntity,
            AuthenticatedUserContext authenticatedUser
    ) {
        EnhancementData data = new EnhancementData();

        if (isRagEnabled(chatEntity)) {
            data.setRagContext(loadRagContext(chatEntity, authenticatedUser));
        }
        if (isInternetEnabled(chatEntity)) {
            data.setSearchResults(searXngService.search(chatEntity.getMessage()));
        }

        return data;
    }

    /**
     * 提取 RAG 文本
     */
    private String extractRagText(List<Document> ragContext) {
        return ragContext == null ? "" : ragContext.stream()
                .map(Document::getText)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 提取联网搜索文本
     */
    private String extractInternetText(List<SearchResult> searchResults) {
        return searchResults == null ? "" : searchResults.stream()
                .filter(java.util.Objects::nonNull)
                .map(item -> "[来源] " + item.getUrl() + "\n[内容] " + item.getContent())
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 根据增强类型构建提示词
     *
     * @param chatEntity  聊天实体
     * @param data        增强数据
     * @param userContext 用户上下文（Agent 模式需要，Chat 模式传 null）
     * @return 构建好的提示词
     */
    private String buildPromptByEnhancement(
            ChatEntity chatEntity,
            EnhancementData data,
            String userContext
    ) {
        boolean ragEnabled = isRagEnabled(chatEntity);
        boolean internetEnabled = isInternetEnabled(chatEntity);
        String question = chatEntity.getMessage();

        // RAG 模式
        if (ragEnabled) {
            String ragText = extractRagText(data.getRagContext());
            return promptTemplateManager.buildRagPrompt(ragText, question);
        }

        // Internet 模式
        if (internetEnabled) {
            String internetText = extractInternetText(data.getSearchResults());
            return promptTemplateManager.buildInternetPrompt(internetText, question);
        }

        // 纯对话模式（Agent 模式：需要用户上下文）
        if (userContext != null) {
            return promptTemplateManager.buildAgentPrompt(userContext, question);
        }
        return promptTemplateManager.buildChatPrompt(null, question);
    }

    /**
     * 根据增强类型构建来源信息
     */
    private Object buildSourcesByEnhancement(EnhancementData data, ChatEntity chatEntity) {
        boolean ragEnabled = isRagEnabled(chatEntity);
        boolean internetEnabled = isInternetEnabled(chatEntity);

        if (ragEnabled) {
            return buildRagSources(data.getRagContext());
        }
        if (internetEnabled) {
            return buildInternetSources(data.getSearchResults());
        }
        return null;
    }

    private String resolveSourceType(ChatEntity chatEntity) {
        boolean ragEnabled = isRagEnabled(chatEntity);
        boolean internetEnabled = isInternetEnabled(chatEntity);

        if (ragEnabled && internetEnabled) {
            throw new IllegalArgumentException("暂不支持同时开启知识库增强与联网补充");
        }
        if (ragEnabled) {
            return "rag";
        }
        if (internetEnabled) {
            return "internet";
        }
        return "chat";
    }

    /**
     * 为当前问题自动加载当前用户的 RAG 上下文。
     * <p>
     * 当前固定检索 4 条候选片段，供自动 RAG 分支复用。
     */
    private List<Document> loadRagContext(ChatEntity chatEntity, AuthenticatedUserContext authenticatedUser) {
        return documentServiceProvider.getObject().doSearch(
                chatEntity.getMessage(),
                authenticatedUser.getUserId(),
                4
        );
    }

    /**
     * 执行 Agent 场景下的自动 RAG 问答。
     * <p>
     * 步骤：
     * 1. 校验 Agent 会话归属；
     * 2. 加载增强数据（RAG）；
     * 3. 构建提示词并发起流式调用；
     * 4. 把来源一并回填到 assistant 消息与 FINISH 响应中。
     */
    private ChatResponseEntity doAgentRagSearch(
            ChatEntity chatEntity,
            Long chatSessionId,
            Long assistantMessageId,
            Long runId,
            AuthenticatedUserContext authenticatedUser
    ) {
        String sourceType = resolveSourceType(chatEntity);
        ChatSession session = requireExistingSession(chatSessionId, authenticatedUser);

        // 1. 加载增强数据
        EnhancementData data = loadEnhancementData(chatEntity, authenticatedUser);

        // 2. 构建提示词（RAG 模式不需要用户上下文）
        String finalPrompt = buildPromptByEnhancement(chatEntity, data, null);

        // 3. 调用模型
        Flux<ReasoningStreamChunk> chunkFlux = reasoningChatClient.stream(finalPrompt);

        // 4. 构建来源
        Object sources = buildSourcesByEnhancement(data, chatEntity);

        return streamAndSend(
                chunkFlux,
                authenticatedUser,
                chatEntity.getBotMsgId(),
                session.getId(),
                session.getSessionCode(),
                assistantMessageId,
                runId,
                "agent",
                sourceType,
                sources
        );
    }
}
