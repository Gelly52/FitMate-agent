package com.itgeo.fitmate.api.chat.infrastructure;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.chat.application.LlmConfigResolver;
import com.itgeo.fitmate.api.chat.application.ResolvedLlmConfig;
import com.itgeo.fitmate.api.chat.dto.ReasoningStreamChunk;
import com.itgeo.fitmate.api.chat.dto.TokenUsage;
import com.itgeo.fitmate.api.config.LlmConfigProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * OpenAI-compatible reasoning chat client that preserves reasoning_content.
 */
@Slf4j
@Component
public class ReasoningChatClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final LlmConfigResolver llmConfigResolver;
    private final LlmConfigProperties llmConfigProperties;

    public ReasoningChatClient(LlmConfigResolver llmConfigResolver, LlmConfigProperties llmConfigProperties) {
        this.llmConfigResolver = llmConfigResolver;
        this.llmConfigProperties = llmConfigProperties;
    }

    public ReasoningStreamChunk call(String prompt) {
        return call(prompt, null);
    }

    /**
     * @param maxTokens 覆盖 max_tokens；传 null 用 config 默认值
     */
    public ReasoningStreamChunk call(String prompt, Integer maxTokens) {
        StringBuilder reasoning = new StringBuilder();
        StringBuilder content = new StringBuilder();
        TokenUsage[] usageHolder = new TokenUsage[1];
        stream(prompt, maxTokens).toStream().forEach(chunk -> {
            if (chunk.getUsage() != null) {
                usageHolder[0] = chunk.getUsage();
                return;
            }
            if (StrUtil.isNotBlank(chunk.getReasoningContent())) {
                reasoning.append(chunk.getReasoningContent());
            }
            if (StrUtil.isNotBlank(chunk.getContent())) {
                content.append(chunk.getContent());
            }
        });
        return new ReasoningStreamChunk(reasoning.toString(), content.toString(), usageHolder[0]);
    }

    public Flux<ReasoningStreamChunk> stream(String prompt) {
        return stream(prompt, null);
    }

    /**
     * @param maxTokens 覆盖 max_tokens；传 null 用 config 默认值
     */
    public Flux<ReasoningStreamChunk> stream(String prompt, Integer maxTokens) {
        ResolvedLlmConfig config = llmConfigResolver.resolveForCurrentUser();
        log.info("LLM stream 调用: model={}, thinkingEnabled={}, reasoningEffort={}",
                config.getModel(), config.getThinkingEnabled(), config.getReasoningEffort());
        String requestBody = buildRequestBody(prompt, config, maxTokens);

        return Flux.<ReasoningStreamChunk>create(sink -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(buildChatCompletionsUrl(config.getBaseUrl())))
                        .timeout(Duration.ofMinutes(10))
                        .header("Authorization", "Bearer " + config.getApiKey())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                        .build();

                long sendStart = System.currentTimeMillis();
                HttpResponse<Stream<String>> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofLines()
                );
                long sendEnd = System.currentTimeMillis();
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    sink.error(new IllegalStateException("Reasoning chat request failed, status=" + response.statusCode()));
                    return;
                }

                long forEachStart = System.currentTimeMillis();
                try (Stream<String> lines = response.body()) {
                    lines.forEach(line -> handleSseLine(line, sink, config));
                }
                long forEachEnd = System.currentTimeMillis();
                log.info("[STREAM-DIAG] send()={}ms, forEach()={}ms (on {})",
                        sendEnd - sendStart, forEachEnd - forEachStart,
                        Thread.currentThread().getName());
                sink.complete();
            } catch (Exception error) {
                log.error("Reasoning chat stream failed", error);
                sink.error(error);
            }
            // subscribeOn(boundedElastic) 让本 lambda 在独立线程执行，
            // 使下游 toStream().forEach 能并行消费 sink.next() 推送的 chunk，
            // 避免 2785 个 chunk 堆积到整轮 LLM 响应结束后才被一次性消费。
        }).subscribeOn(Schedulers.boundedElastic(), false);
    }

    private String buildRequestBody(String prompt, ResolvedLlmConfig config) {
        return buildRequestBody(prompt, config, null);
    }

    /**
     * @param maxTokens 非 null 时覆盖 config 的 max_tokens（用于压缩等需要独立限制输出的场景）
     */
    private String buildRequestBody(String prompt, ResolvedLlmConfig config, Integer maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));
        body.put("reasoning_effort", StrUtil.blankToDefault(config.getReasoningEffort(), "high"));
        body.put("thinking", Map.of("type", Boolean.TRUE.equals(config.getThinkingEnabled()) ? "enabled" : "disabled"));
        // 优先使用显式传入的 maxTokens（压缩场景），否则回退到用户配置
        Integer effectiveMaxTokens = (maxTokens != null && maxTokens > 0) ? maxTokens : config.getMaxOutputContextTokens();
        if (effectiveMaxTokens != null && effectiveMaxTokens > 0) {
            body.put("max_tokens", effectiveMaxTokens);
        }
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        return JSONUtil.toJsonStr(body);
    }

    private String buildChatCompletionsUrl(String baseUrl) {
        String normalized = StrUtil.blankToDefault(baseUrl, "https://api.deepseek.com");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/chat/completions";
    }

    private void handleSseLine(String line, reactor.core.publisher.FluxSink<ReasoningStreamChunk> sink, ResolvedLlmConfig config) {
        if (line == null || !line.startsWith("data:")) {
            return;
        }
        String data = line.substring("data:".length()).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) {
            return;
        }
        JSONObject root = JSONUtil.parseObj(data);
        JSONArray choices = root.getJSONArray("choices");

        // 场景1：usage-only 终止帧（choices 为空但 usage 存在，OpenAI 标准行为）
        if (choices == null || choices.isEmpty()) {
            TokenUsage usage = parseUsage(root.getJSONObject("usage"), config);
            if (usage != null) {
                sink.next(new ReasoningStreamChunk(null, null, usage));
            }
            return;
        }

        // 场景2：正常内容分片
        JSONObject choice = choices.getJSONObject(0);
        JSONObject delta = choice == null ? null : choice.getJSONObject("delta");
        if (delta != null) {
            String reasoning = delta.getStr("reasoning_content", "");
            String content = delta.getStr("content", "");
            if (StrUtil.isNotBlank(reasoning) && !Boolean.TRUE.equals(config.getThinkingEnabled())) {
                log.warn("收到 reasoning_content 但 thinkingEnabled=false, 可能存在配置不一致");
            }
            if (StrUtil.isNotBlank(reasoning) || StrUtil.isNotBlank(content)) {
                sink.next(new ReasoningStreamChunk(reasoning, content));
            }
        }

        // DeepSeek 实际行为：usage 附在最后一个内容 chunk 上（带 finish_reason 的那个），
        // 而非独立发送 choices 为空的 usage-only 帧，因此每个 chunk 都需要检查 usage。
        TokenUsage usage = parseUsage(root.getJSONObject("usage"), config);
        if (usage != null) {
            sink.next(new ReasoningStreamChunk(null, null, usage));
        }
    }

    /**
     * 解析 DeepSeek/OpenAI 流式终止帧中的 usage。
     * reasoning_tokens 位于 completion_tokens_details，已计入 completion。
     * prompt_cache_hit_tokens / prompt_cache_miss_tokens 为 DeepSeek 硬盘缓存命中情况。
     */
    private TokenUsage parseUsage(JSONObject usage, ResolvedLlmConfig config) {
        if (usage == null) {
            return null;
        }
        Integer promptTokens = usage.getInt("prompt_tokens");
        Integer completionTokens = usage.getInt("completion_tokens");
        Integer totalTokens = usage.getInt("total_tokens");
        Integer reasoningTokens = null;
        JSONObject completionDetails = usage.getJSONObject("completion_tokens_details");
        if (completionDetails != null) {
            reasoningTokens = completionDetails.getInt("reasoning_tokens");
        }
        // DeepSeek KV Cache 字段（直接在 usage 顶层）
        Integer cacheHitTokens = usage.getInt("prompt_cache_hit_tokens");
        Integer cacheMissTokens = usage.getInt("prompt_cache_miss_tokens");
        Integer configuredWindowSize = config.getMaxInputContextTokens();
        Integer defaultWindowSize = llmConfigProperties.getDefaultConfig().getMaxInputContextTokens();
        Integer windowSize = configuredWindowSize != null && configuredWindowSize > 0 ? configuredWindowSize : defaultWindowSize;
        return new TokenUsage(
                promptTokens,
                completionTokens,
                totalTokens,
                reasoningTokens,
                totalTokens,
                windowSize,
                cacheHitTokens,
                cacheMissTokens
        );
    }
}
