package com.itgeo.fitmate.api.chat.infrastructure;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.chat.dto.LlmBalanceInfo;
import com.itgeo.fitmate.api.chat.dto.LlmBalanceResult;
import com.itgeo.fitmate.api.chat.dto.LlmModelItem;
import com.itgeo.fitmate.api.chat.dto.LlmTestResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 代理调用 DeepSeek list-models 与 test 接口。
 * 超时 10 秒，避免拖死设置页。
 */
@Slf4j
@Component
public class LlmProxyClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** GET /models 代理，返回模型列表 */
    public List<LlmModelItem> listModels(String baseUrl, String apiKey) {
        String url = normalizeBaseUrl(baseUrl) + "/models";
        log.warn("list-models 诊断 apiKey.len={} mask={}", apiKey == null ? -1 : apiKey.length(), maskKey(apiKey));
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("list-models 失败 status=" + response.statusCode());
            }
            JSONObject body = JSONUtil.parseObj(response.body());
            JSONArray data = body.getJSONArray("data");
            List<LlmModelItem> models = new ArrayList<>();
            if (data != null) {
                for (int i = 0; i < data.size(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    LlmModelItem model = new LlmModelItem();
                    model.setId(item.getStr("id"));
                    model.setOwnedBy(item.getStr("owned_by"));
                    models.add(model);
                }
            }
            return models;
        } catch (Exception e) {
            log.warn("list-models 代理失败 url={}", url, e);
            throw new IllegalStateException("拉取模型列表失败: " + e.getMessage(), e);
        }
    }

    /** GET /user/balance 代理，返回账户余额详情 */
    public LlmBalanceResult getBalance(String baseUrl, String apiKey) {
        String url = normalizeBaseUrl(baseUrl) + "/user/balance";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("get-balance 失败 status=" + response.statusCode());
            }
            JSONObject body = JSONUtil.parseObj(response.body());
            LlmBalanceResult result = new LlmBalanceResult();
            result.setIsAvailable(body.getBool("is_available"));
            JSONArray infos = body.getJSONArray("balance_infos");
            List<LlmBalanceInfo> list = new ArrayList<>();
            if (infos != null) {
                for (int i = 0; i < infos.size(); i++) {
                    JSONObject item = infos.getJSONObject(i);
                    LlmBalanceInfo info = new LlmBalanceInfo();
                    info.setCurrency(item.getStr("currency"));
                    info.setTotalBalance(item.getStr("total_balance"));
                    info.setGrantedBalance(item.getStr("granted_balance"));
                    info.setToppedUpBalance(item.getStr("topped_up_balance"));
                    list.add(info);
                }
            }
            result.setBalanceInfos(list);
            return result;
        } catch (Exception e) {
            log.warn("get-balance 代理失败 url={}", url, e);
            throw new IllegalStateException("查询余额失败: " + e.getMessage(), e);
        }
    }

    /** 测活：极简 chat completion（max_tokens=1, thinking=disabled） */
    public LlmTestResult testConnection(String baseUrl, String apiKey, String model) {
        LlmTestResult result = new LlmTestResult();
        result.setModel(model);
        result.setOk(false);
        String url = normalizeBaseUrl(baseUrl) + "/chat/completions";
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
            body.put("max_tokens", 1);
            body.put("stream", false);
            body.put("thinking", Map.of("type", "disabled"));
            String requestBody = JSONUtil.toJsonStr(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            result.setLatencyMs(System.currentTimeMillis() - start);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                result.setOk(true);
            } else {
                result.setError("HTTP " + response.statusCode() + ": " + truncate(response.body(), 200));
            }
        } catch (Exception e) {
            result.setLatencyMs(System.currentTimeMillis() - start);
            result.setError(truncate(e.getMessage(), 200));
        }
        return result;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = StrUtil.blankToDefault(baseUrl, "https://api.deepseek.com");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String maskKey(String key) {
        if (key == null || key.isEmpty()) return "<empty>";
        if (key.length() < 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
