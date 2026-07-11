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
 *
 * 配置语义：
 * - DB 中 llm_config_json 为 NULL/空 → 完全使用系统默认配置（跟随 env 动态变化）
 * - DB 中有配置且 apiKey 非空 → 使用用户自定义 key；其他字段缺省也回退 env 默认值
 * - 用户可通过 resetByUserId 将配置清空为 NULL，回到系统默认状态
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
            log.info("用户无 DB 配置，回退 env 默认值: userId={}", userId);
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
        UserPreference pref = loadPreference(userId);
        boolean usingDefault = pref == null || StrUtil.isBlank(pref.getLlmConfigJson());
        ResolvedLlmConfig resolved = resolveByUserId(userId);
        LlmConfigItem item = new LlmConfigItem();
        item.setBaseUrl(resolved.getBaseUrl());
        item.setApiKey(cipher.mask(resolved.getApiKey()));
        item.setModel(resolved.getModel());
        item.setMaxInputContextTokens(resolved.getMaxInputContextTokens());
        item.setMaxOutputContextTokens(resolved.getMaxOutputContextTokens());
        item.setThinkingEnabled(resolved.getThinkingEnabled());
        item.setReasoningEffort(resolved.getReasoningEffort());
        item.setUsingDefault(usingDefault);
        return item;
    }

    /**
     * 重置用户 LLM 配置为系统默认（将 llm_config_json 清空为 NULL）。
     * 重置后所有 LLM 调用均使用环境变量默认配置。
     */
    public LlmConfigItem resetByUserId(Long userId) {
        UserPreference existing = loadPreference(userId);
        if (existing != null) {
            existing.setLlmConfigJson(null);
            userPreferenceMapper.updateById(existing);
            log.info("LLM 配置已重置为系统默认: userId={}", userId);
        }
        return getByUserId(userId);
    }

    /**
     * 保存用户 LLM 配置。
     * apiKey 处理规则：
     * - 请求中 apiKey 非空且非脱敏值 → 加密新 key 存入 DB
     * - 请求中 apiKey 为空或为脱敏值，且 DB 已有旧 key → 保留旧 key
     * - 请求中 apiKey 为空或为脱敏值，且 DB 无旧 key → apiKey 字段留空，解析时回退 env 默认
     *   （不再自动将 env 默认 key 加密写入 DB，避免钉死默认 key 导致无法跟随 env 更新）
     */
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
        String requestApiKey = request.getApiKey();
        boolean isMasked = StrUtil.isNotBlank(requestApiKey) && requestApiKey.contains("****");
        String encryptedKey;
        if (StrUtil.isNotBlank(requestApiKey) && !isMasked) {
            encryptedKey = cipher.encrypt(requestApiKey);
        } else if (StrUtil.isNotBlank(existingEncryptedKey)) {
            encryptedKey = existingEncryptedKey;
        } else {
            encryptedKey = "";
        }
        if (StrUtil.isBlank(encryptedKey) && StrUtil.isBlank(requestApiKey)) {
            // 用户未填写自定义 key，允许仅保存其他配置项（apiKey 留空，resolve 时回退 env 默认）
        } else if (StrUtil.isBlank(encryptedKey)) {
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
            log.info("LLM 配置已保存: userId={}, model={}, thinkingEnabled={}, reasoningEffort={}, usingCustomKey={}",
                    userId, json.getStr("model"), json.getBool("thinkingEnabled"), json.getStr("reasoningEffort"),
                    StrUtil.isNotBlank(encryptedKey));
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
