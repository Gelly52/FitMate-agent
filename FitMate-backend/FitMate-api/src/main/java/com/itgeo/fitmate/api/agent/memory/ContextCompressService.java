package com.itgeo.fitmate.api.agent.memory;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.agent.config.AgentProperties;
import com.itgeo.fitmate.api.agent.config.ContextCompressProperties;
import com.itgeo.fitmate.api.agent.dto.AgentExecuteContext;
import com.itgeo.fitmate.api.agent.memory.dto.CompressEventPayload;
import com.itgeo.fitmate.api.agent.memory.dto.MemoryLoadResult;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.chat.application.ChatSessionService;
import com.itgeo.fitmate.api.chat.application.LlmConfigResolver;
import com.itgeo.fitmate.api.chat.application.ResolvedLlmConfig;
import com.itgeo.fitmate.api.chat.dto.ReasoningStreamChunk;
import com.itgeo.fitmate.api.chat.infrastructure.ReasoningChatClient;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatMessage;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ContextSummary;
import com.itgeo.fitmate.api.chat.infrastructure.mapper.ContextSummaryMapper;
import com.itgeo.fitmate.api.config.LlmConfigProperties;
import com.itgeo.fitmate.api.sse.domain.SSEMsgType;
import com.itgeo.fitmate.api.sse.infrastructure.SSEServer;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 上下文压缩服务。
 * <p>
 * 被动压缩：在 AgentLoopExecutor.run 开头调用 checkAndCompressIfNeeded，复用当前 SSE 通道。
 * 主动压缩：HTTP 触发 compressManuallyAsync，异步执行，复用长连接 SSE 通道。
 * 两者共用 doCompress 核心逻辑。
 */
@Slf4j
@Service
public class ContextCompressService {

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private ContextSummaryMapper contextSummaryMapper;

    @Resource
    private AgentProperties agentProperties;

    @Resource
    private ReasoningChatClient reasoningChatClient;

    @Resource
    private LlmConfigResolver llmConfigResolver;

    @Resource
    private LlmConfigProperties llmConfigProperties;

    @Resource
    private com.itgeo.fitmate.api.prompt.PromptTemplateManager promptTemplateManager;

    /** 主动压缩用的单线程异步执行器，避免阻塞 HTTP 请求。 */

    private final Executor manualCompressExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "context-compress-manual");
        t.setDaemon(true);
        return t;
    });

    /**
     * 被动入口：在 AgentLoopExecutor.run 开头调用。
     * 检查阈值，超阈值则执行压缩，复用当前 SSE 通道推送事件。
     *
     * @return true 表示发生了压缩（调用方应基于最新摘要重新加载 memory）
     */
    public boolean checkAndCompressIfNeeded(AgentExecuteContext context) {
        ContextCompressProperties props = resolveProps();
        if (!Boolean.TRUE.equals(props.getEnabled())) {
            return false;
        }
        try {
            return doCompress(context.getChatSessionId(),
                    context.getAuthenticatedUser().getSseClientId(),
                    false,
                    "auto");
        } catch (Exception e) {
            log.error("被动上下文压缩失败, sessionId={}", context.getChatSessionId(), e);
            return false;
        }
    }

    /**
     * 主动入口：HTTP 触发，异步执行。
     * 立即返回，压缩过程与结果通过长连接 SSE 通道推送。
     */
    public void compressManuallyAsync(Long sessionId, String sseClientId) {
        manualCompressExecutor.execute(() -> {
            try {
                doCompress(sessionId, sseClientId, true, "manual");
            } catch (Exception e) {
                log.error("主动上下文压缩失败, sessionId={}", sessionId, e);
                sendEvent(sseClientId, new CompressEventPayload(
                        "context_compress_failed", null, null, null, null, e.getMessage()));
            }
        });
    }

    /**
     * 加载历史时使用：取最新摘要 + 摘要之后的原始消息。
     * 无摘要时回退条数兜底（skip 旧消息）。
     */
    public MemoryLoadResult loadMemoryWithContext(Long sessionId, int fallbackWindowSize) {
        ContextSummary latest = getLatestSummary(sessionId);
        List<ChatMessage> messages = listMessagesBySessionId(sessionId);

        if (latest == null) {
            // 无摘要：原条数兜底
            int windowSize = fallbackWindowSize <= 0 ? 20 : fallbackWindowSize;
            List<Map<String, String>> fallback = messages.stream()
                    .filter(m -> StrUtil.isNotBlank(m.getContent()))
                    .skip(Math.max(0, messages.size() - windowSize))
                    .map(this::toRoleContentMap)
                    .collect(Collectors.toList());
            return new MemoryLoadResult(null, fallback);
        }

        // 有摘要：取 compressedToSeq 之后的消息
        int afterSeq = latest.getCompressedToSeq();
        List<Map<String, String>> afterMessages = messages.stream()
                .filter(m -> m.getSeqNo() != null && m.getSeqNo() > afterSeq)
                .filter(m -> StrUtil.isNotBlank(m.getContent()))
                .map(this::toRoleContentMap)
                .collect(Collectors.toList());
        return new MemoryLoadResult(latest, afterMessages);
    }

    /** 构建 prompt 中的摘要区块文本。 */
    public String buildSummaryPromptSection(ContextSummary summary) {
        if (summary == null || StrUtil.isBlank(summary.getSummaryContent())) {
            return "";
        }
        return "\n\n## 对话历史摘要\n" + summary.getSummaryContent()
                + "\n（已压缩 " + summary.getCompressedMessageCount() + " 条历史消息）";
    }

    // ============================================================
    // 核心压缩逻辑
    // ============================================================

    /**
     * 统一核心压缩方法。
     *
     * @param sessionId   会话ID
     * @param sseClientId SSE 客户端ID（用于推送事件）
     * @param force       true=跳过阈值检查（主动触发）；false=检查阈值
     * @param triggerType 触发类型 auto/manual
     * @return true 表示发生了压缩
     */
    private boolean doCompress(Long sessionId, String sseClientId, boolean force, String triggerType) {
        ContextCompressProperties props = resolveProps();
        if (!Boolean.TRUE.equals(props.getEnabled())) {
            return false;
        }

        // 1. 阈值检查
        if (!force && !exceedsThreshold(sessionId)) {
            return false;
        }

        // 2. 确定压缩范围
        List<ChatMessage> allMessages = listMessagesBySessionId(sessionId);
        if (allMessages.isEmpty()) {
            return false;
        }
        ContextSummary lastSummary = getLatestSummary(sessionId);
        int fromSeq = lastSummary != null ? lastSummary.getCompressedToSeq() + 1 : 0;
        int lastSeq = allMessages.stream()
                .mapToInt(m -> m.getSeqNo() == null ? 0 : m.getSeqNo())
                .max().orElse(0);
        int keepCount = props.getKeepRecentCount() == null ? 6 : props.getKeepRecentCount();
        int keepFromSeq = lastSeq - keepCount + 1;
        if (keepFromSeq <= fromSeq) {
            // 可压缩范围不足
            log.debug("可压缩范围不足, sessionId={}, fromSeq={}, keepFromSeq={}", sessionId, fromSeq, keepFromSeq);
            return false;
        }

        final int finalFromSeq = fromSeq;
        List<ChatMessage> toCompress = allMessages.stream()
                .filter(m -> m.getSeqNo() != null && m.getSeqNo() >= finalFromSeq && m.getSeqNo() < keepFromSeq)
                .filter(m -> StrUtil.isNotBlank(m.getContent()))
                .collect(Collectors.toList());
        if (toCompress.isEmpty()) {
            return false;
        }

        // 3. 推送 compressing 事件
        sendEvent(sseClientId, new CompressEventPayload("context_compressing", null, null, null, null, null));

        // 4. 调用 LLM 生成摘要
        Integer tokenBefore = resolveLastPromptTokens(allMessages);
        Integer contextWindow = resolveContextWindow();
        String summaryContent;
        try {
            summaryContent = generateSummary(lastSummary, toCompress, props);
        } catch (Exception e) {
            log.error("生成摘要失败, sessionId={}", sessionId, e);
            sendEvent(sseClientId, new CompressEventPayload(
                    "context_compress_failed", null, null, null, null, "生成摘要失败: " + e.getMessage()));
            return false;
        }
        if (StrUtil.isBlank(summaryContent)) {
            sendEvent(sseClientId, new CompressEventPayload(
                    "context_compress_failed", null, null, null, null, "摘要内容为空"));
            return false;
        }

        // 5. 持久化
        Integer tokenAfter = estimateTokens(summaryContent);
        ContextSummary record = new ContextSummary();
        record.setSessionId(sessionId);
        record.setSummaryContent(summaryContent);
        record.setCompressedFromSeq(fromSeq);
        record.setCompressedToSeq(keepFromSeq - 1);
        record.setCompressedMessageCount(toCompress.size());
        record.setTokenBefore(tokenBefore);
        record.setTokenAfter(tokenAfter);
        record.setTriggerType(triggerType);
        contextSummaryMapper.insert(record);

        // 6. 推送 compressed 事件
        sendEvent(sseClientId, new CompressEventPayload(
                "context_compressed",
                toCompress.size(),
                tokenBefore,
                tokenAfter,
                contextWindow,
                null));
        log.info("上下文压缩完成, sessionId={}, compressed={}, tokenBefore={}, tokenAfter={}",
                sessionId, toCompress.size(), tokenBefore, tokenAfter);
        return true;
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private boolean exceedsThreshold(Long sessionId) {
        List<ChatMessage> messages = listMessagesBySessionId(sessionId);
        Integer promptTokens = resolveLastPromptTokens(messages);
        if (promptTokens == null) {
            // 无 usage 可读，回退条数判断
            long validCount = messages.stream()
                    .filter(m -> StrUtil.isNotBlank(m.getContent()))
                    .count();
            int windowSize = agentProperties.getMemoryWindowSize() == null ? 50 : agentProperties.getMemoryWindowSize();
            return validCount > windowSize;
        }
        Integer contextWindow = resolveContextWindow();
        ContextCompressProperties props = resolveProps();
        double ratio = props.getThresholdRatio() == null ? 0.8 : props.getThresholdRatio();
        int threshold = (int) (contextWindow * ratio);
        return promptTokens > threshold;
    }

    /**
     * 取最近一条 assistant 消息 usage_json 中的 prompt_tokens。
     */
    private Integer resolveLastPromptTokens(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (!"assistant".equalsIgnoreCase(m.getRole()) || StrUtil.isBlank(m.getUsageJson())) {
                continue;
            }
            try {
                JSONObject usage = JSONUtil.parseObj(m.getUsageJson());
                return usage.getInt("promptTokens");
            } catch (Exception e) {
                log.warn("解析 usageJson 失败, messageId={}", m.getId(), e);
                return null;
            }
        }
        return null;
    }

    private Integer resolveContextWindow() {
        Integer defaultWindow = llmConfigProperties.getDefaultConfig().getMaxInputContextTokens();
        try {
            ResolvedLlmConfig config = llmConfigResolver.resolveForCurrentUser();
            Integer window = config.getMaxInputContextTokens();
            return window != null && window > 0 ? window : defaultWindow;
        } catch (Exception e) {
            return defaultWindow;
        }
    }

    private String generateSummary(ContextSummary lastSummary, List<ChatMessage> toCompress, ContextCompressProperties props) {
        List<Map<String, String>> dialog = toCompress.stream()
                .map(this::toRoleContentMap)
                .collect(Collectors.toList());
        String fullPrompt = promptTemplateManager.buildContextCompressPrompt(
                lastSummary != null ? lastSummary.getSummaryContent() : null,
                JSONUtil.toJsonStr(dialog)
        );

        // 显式传入 max_tokens（DeepSeek V4 输出上限 384K，摘要场景用 summary-max-tokens 硬约束）
        Integer maxTokens = props.getSummaryMaxTokens() != null && props.getSummaryMaxTokens() > 0
                ? props.getSummaryMaxTokens() : 1024;
        StringBuilder summary = new StringBuilder();
        reasoningChatClient.stream(fullPrompt, maxTokens).toStream().forEach(chunk -> {
            if (chunk == null || chunk.getUsage() != null) {
                return;
            }
            if (StrUtil.isNotBlank(chunk.getContent())) {
                summary.append(chunk.getContent());
            }
        });
        return summary.toString().trim();
    }

    private void sendEvent(String sseClientId, CompressEventPayload payload) {
        if (StrUtil.isBlank(sseClientId)) {
            return;
        }
        try {
            SSEServer.sendMsg(sseClientId, JSONUtil.toJsonStr(payload), SSEMsgType.CUSTOM_EVENT);
        } catch (Exception e) {
            log.warn("推送压缩事件失败, sseClientId={}", sseClientId, e);
        }
    }

    private ContextSummary getLatestSummary(Long sessionId) {
        LambdaQueryWrapper<ContextSummary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContextSummary::getSessionId, sessionId)
                .orderByDesc(ContextSummary::getCompressedToSeq)
                .last("limit 1");
        return contextSummaryMapper.selectOne(wrapper);
    }

    private List<ChatMessage> listMessagesBySessionId(Long sessionId) {
        // 压缩服务在受信链路内，直接按 sessionId 查询，跳过用户校验
        return chatSessionService.listMessagesBySessionIdOnly(sessionId);
    }

    private Map<String, String> toRoleContentMap(ChatMessage message) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("role", StrUtil.blankToDefault(message.getRole(), "unknown"));
        map.put("content", message.getContent());
        return map;
    }

    /** 粗略估算 token：中文约 1 字符 = 1 token，英文约 4 字符 = 1 token，取折中。 */
    private Integer estimateTokens(String text) {
        if (StrUtil.isBlank(text)) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 2.5);
    }

    private ContextCompressProperties resolveProps() {
        ContextCompressProperties props = agentProperties.getContextCompress();
        return props != null ? props : new ContextCompressProperties();
    }
}
