package com.itgeo.fitmate.api.chat.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.auth.dto.LlmConfigItem;
import com.itgeo.fitmate.api.auth.dto.LlmConfigSaveRequest;
import com.itgeo.fitmate.api.auth.infrastructure.entity.UserPreference;
import com.itgeo.fitmate.api.auth.infrastructure.mapper.UserPreferenceMapper;
import com.itgeo.fitmate.api.chat.infrastructure.LlmConfigCipher;
import com.itgeo.fitmate.api.config.LlmConfigProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLM 配置解析器：DB 覆盖 env。
 * DB 有 llm_config_json 时解密并使用 DB 值；DB 无值时回退 env 默认值。
 */
@Slf4j
@Component
public class LlmConfigResolver {

    @Resource
    private UserPreferenceMapper userPreferenceMapper;

    @Resource
    private LlmConfigCipher cipher;

    @Resource
    private LlmConfigProperties llmConfigProperties;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 解析当前登录用户的 LLM 配置。
     * 无登录用户时回退 env 默认值（兼容系统级调用）。
     */
    public ResolvedLlmConfig resolveForCurrentUser() {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return resolveByUserId(userId);
        } catch (Exception e) {
            log.warn("无登录用户上下文，回退 env 默认 LLM 配置: {}", e.getMessage());
            return resolveFromEnv();
        }
    }

    /** 解析指定用户的 LLM 配置 */
    public ResolvedLlmConfig resolveByUserId(Long userId) {
        UserPreference pref = loadPreference(userId);
        if (pref == null || StrUtil.isBlank(pref.getLlmConfigJson())) {
            return resolveFromEnv();
        }
        try {
            JSONObject json = JSONUtil.parseObj(pref.getLlmConfigJson());
            ResolvedLlmConfig config = new ResolvedLlmConfig();
            config.setBaseUrl(json.getStr("baseUrl", envDefault().getBaseUrl()));
            String encryptedKey = json.getStr("apiKey", "");
            config.setApiKey(StrUtil.isBlank(encryptedKey) ? envDefault().getApiKey() : cipher.decrypt(encryptedKey));
            config.setModel(json.getStr("model", envDefault().getModel()));
            config.setMaxInputContextTokens(json.getInt("maxInputContextTokens", envDefault().getMaxInputContextTokens()));
            config.setMaxOutputContextTokens(json.getInt("maxOutputContextTokens", envDefault().getMaxOutputContextTokens()));
            config.setThinkingEnabled(json.getBool("thinkingEnabled", envDefault().getThinkingEnabled()));
            config.setReasoningEffort(json.getStr("reasoningEffort", envDefault().getReasoningEffort()));
            return config;
        } catch (Exception e) {
            log.warn("解析用户 LLM 配置 JSON 失败，回退 env: userId={}", userId, e);
            return resolveFromEnv();
        }
    }

    /** 获取脱敏配置（供 GET 接口返回） */
    public LlmConfigItem getByUserId(Long userId) {
        ResolvedLlmConfig resolved = resolveByUserId(userId);
        LlmConfigItem item = new LlmConfigItem();
        item.setBaseUrl(resolved.getBaseUrl());
        item.setApiKey(cipher.mask(resolved.getApiKey()));
        item.setModel(resolved.getModel());
        item.setMaxInputContextTokens(resolved.getMaxInputContextTokens());
        item.setMaxOutputContextTokens(resolved.getMaxOutputContextTokens());
        item.setThinkingEnabled(resolved.getThinkingEnabled());
        item.setReasoningEffort(resolved.getReasoningEffort());
        return item;
    }

    /** 保存用户 LLM 配置（apiKey 加密落库；apiKey 为空保留原密文） */
    public void saveByUserId(Long userId, LlmConfigSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("LLM 配置不能为空");
        }
        if (StrUtil.isBlank(request.getBaseUrl())) {
            throw new IllegalArgumentException("API 地址不能为空");
        }
        UserPreference existing = loadPreference(userId);
        String existingEncryptedKey = "";
        if (existing != null && StrUtil.isNotBlank(existing.getLlmConfigJson())) {
            try {
                JSONObject oldJson = JSONUtil.parseObj(existing.getLlmConfigJson());
                existingEncryptedKey = oldJson.getStr("apiKey", "");
            } catch (Exception ignored) {
            }
        }
        // apiKey：请求非空则加密新值；为空则保留原密文（若有）
        String encryptedKey;
        if (StrUtil.isNotBlank(request.getApiKey())) {
            encryptedKey = cipher.encrypt(request.getApiKey());
        } else {
            encryptedKey = existingEncryptedKey;
        }
        if (StrUtil.isBlank(encryptedKey)) {
            throw new IllegalArgumentException("API Key 不能为空");
        }

        JSONObject json = new JSONObject();
        json.set("baseUrl", request.getBaseUrl());
        json.set("apiKey", encryptedKey);
        json.set("model", StrUtil.blankToDefault(request.getModel(), envDefault().getModel()));
        json.set("maxInputContextTokens", request.getMaxInputContextTokens() != null ? request.getMaxInputContextTokens() : envDefault().getMaxInputContextTokens());
        json.set("maxOutputContextTokens", request.getMaxOutputContextTokens() != null ? request.getMaxOutputContextTokens() : envDefault().getMaxOutputContextTokens());
        json.set("thinkingEnabled", request.getThinkingEnabled() != null ? request.getThinkingEnabled() : envDefault().getThinkingEnabled());
        json.set("reasoningEffort", StrUtil.blankToDefault(request.getReasoningEffort(), envDefault().getReasoningEffort()));
        String jsonStr = json.toString();

        try {
            if (existing == null) {
                UserPreference pref = new UserPreference();
                pref.setUserId(userId);
                pref.setPreferencesJson(objectMapper.writeValueAsString(new com.itgeo.fitmate.api.auth.dto.UserPreferenceItem()));
                pref.setLlmConfigJson(jsonStr);
                userPreferenceMapper.insert(pref);
            } else {
                existing.setLlmConfigJson(jsonStr);
                userPreferenceMapper.updateById(existing);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("序列化默认 preferencesJson 失败", e);
        }
    }

    private ResolvedLlmConfig resolveFromEnv() {
        LlmConfigProperties.DefaultConfig d = envDefault();
        ResolvedLlmConfig config = new ResolvedLlmConfig();
        config.setBaseUrl(d.getBaseUrl());
        config.setApiKey(d.getApiKey());
        config.setModel(d.getModel());
        config.setMaxInputContextTokens(d.getMaxInputContextTokens());
        config.setMaxOutputContextTokens(d.getMaxOutputContextTokens());
        config.setThinkingEnabled(d.getThinkingEnabled());
        config.setReasoningEffort(d.getReasoningEffort());
        return config;
    }

    private LlmConfigProperties.DefaultConfig envDefault() {
        return llmConfigProperties.getDefaultConfig();
    }

    private UserPreference loadPreference(Long userId) {
        LambdaQueryWrapper<UserPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPreference::getUserId, userId);
        return userPreferenceMapper.selectOne(wrapper);
    }
}
