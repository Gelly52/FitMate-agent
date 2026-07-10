package com.itgeo.fitmate.api.chat.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.auth.dto.McpConfigItem;
import com.itgeo.fitmate.api.auth.dto.McpConfigSaveRequest;
import com.itgeo.fitmate.api.auth.dto.McpServerConfig;
import com.itgeo.fitmate.api.auth.infrastructure.entity.UserPreference;
import com.itgeo.fitmate.api.auth.infrastructure.mapper.UserPreferenceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP 配置解析器：DB 读取用户自定义 MCP server 列表。
 * 无 DB 记录时返回空列表（用户未配置自定义 MCP server）。
 * 注意：MCP 配置不含敏感信息，无需加密。
 */
@Slf4j
@Component
public class McpConfigResolver {

    @Resource
    private UserPreferenceMapper userPreferenceMapper;

    @Resource
    private ObjectMapper objectMapper;

    /** 解析指定用户的 MCP server 配置列表 */
    public List<McpServerConfig> resolveByUserId(Long userId) {
        UserPreference pref = loadPreference(userId);
        if (pref == null || StrUtil.isBlank(pref.getMcpConfigJson())) {
            return new ArrayList<>();
        }
        try {
            List<McpServerConfig> servers = JSONUtil.toList(pref.getMcpConfigJson(), McpServerConfig.class);
            return servers != null ? servers : new ArrayList<>();
        } catch (Exception e) {
            log.warn("解析用户 MCP 配置 JSON 失败，回退空列表: userId={}", userId, e);
            return new ArrayList<>();
        }
    }

    /** 获取配置（供 GET 接口返回） */
    public McpConfigItem getByUserId(Long userId) {
        McpConfigItem item = new McpConfigItem();
        item.setServers(resolveByUserId(userId));
        return item;
    }

    /** 保存用户 MCP 配置 */
    public void saveByUserId(Long userId, McpConfigSaveRequest request) {
        List<McpServerConfig> servers = request == null ? null : request.getServers();
        // 规范化：过滤掉 name/url 为空的项，补全 sseEndpoint 默认值
        List<McpServerConfig> normalized = normalize(servers);
        String jsonStr = JSONUtil.toJsonStr(normalized);

        UserPreference existing = loadPreference(userId);
        try {
            if (existing == null) {
                UserPreference pref = new UserPreference();
                pref.setUserId(userId);
                pref.setPreferencesJson(objectMapper.writeValueAsString(new com.itgeo.fitmate.api.auth.dto.UserPreferenceItem()));
                pref.setMcpConfigJson(jsonStr);
                userPreferenceMapper.insert(pref);
            } else {
                existing.setMcpConfigJson(jsonStr);
                userPreferenceMapper.updateById(existing);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("序列化默认 preferencesJson 失败", e);
        }
    }

    /** 规范化 server 列表：过滤无效项，补全默认 sseEndpoint */
    private List<McpServerConfig> normalize(List<McpServerConfig> servers) {
        if (servers == null || servers.isEmpty()) {
            return new ArrayList<>();
        }
        List<McpServerConfig> result = new ArrayList<>();
        for (McpServerConfig s : servers) {
            if (s == null || StrUtil.isBlank(s.getName()) || StrUtil.isBlank(s.getUrl())) {
                continue;
            }
            if (StrUtil.isBlank(s.getSseEndpoint())) {
                s.setSseEndpoint("/sse");
            }
            if (s.getEnabled() == null) {
                s.setEnabled(true);
            }
            result.add(s);
        }
        return result;
    }

    private UserPreference loadPreference(Long userId) {
        LambdaQueryWrapper<UserPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPreference::getUserId, userId);
        return userPreferenceMapper.selectOne(wrapper);
    }
}
