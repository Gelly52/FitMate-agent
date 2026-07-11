package com.itgeo.fitmate.api.agent.memory.longterm.application;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.agent.llm.LlmJsonSanitizer;
import com.itgeo.fitmate.api.agent.memory.longterm.config.MemoryProperties;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserProfile;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserProfileMapper;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.auth.infrastructure.entity.User;
import com.itgeo.fitmate.api.auth.infrastructure.mapper.UserMapper;
import com.itgeo.fitmate.api.chat.infrastructure.ReasoningChatClient;
import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileBuilder {

    private final UserMemoryMapper memoryMapper;
    private final UserProfileMapper profileMapper;
    private final ReasoningChatClient reasoningChatClient;
    private final PromptTemplateManager promptTemplateManager;
    private final MemoryProperties properties;
    private final UserMapper userMapper;

    @Async("memoryTaskExecutor")
    public void asyncRebuild(Long userId) {
        try {
            User user = userMapper.selectById(userId);
            if (user != null) {
                AuthenticatedUserContext ctx = AuthenticatedUserContext.builder()
                        .userId(user.getId())
                        .userKey(user.getUserKey())
                        .username(user.getUsername())
                        .nickname(user.getNickname())
                        .phone(user.getPhone())
                        .email(user.getUsername())
                        .build();
                UserContextHolder.set(ctx);
                log.info("画像异步重建设置用户上下文: userId={}", userId);
            }
            rebuild(userId);
        } finally {
            UserContextHolder.clear();
        }
    }

    public void rebuild(Long userId) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            doRebuild(userId);
        } catch (Exception e) {
            log.error("画像重建失败 userId={}", userId, e);
        }
    }

    private void doRebuild(Long userId) {
        List<UserMemory> allMemories = memoryMapper.selectList(new LambdaQueryWrapper<UserMemory>()
                .eq(UserMemory::getUserId, userId)
                .eq(UserMemory::getStatus, "active"));

        if (allMemories.isEmpty()) {
            log.debug("用户无记忆，跳过画像生成 userId={}", userId);
            return;
        }

        String facts = allMemories.stream()
                .filter(m -> "FACT".equals(m.getMemoryType()))
                .map(UserMemory::getContent)
                .collect(Collectors.joining("\n"));
        String episodics = allMemories.stream()
                .filter(m -> "EPISODIC".equals(m.getMemoryType()))
                .sorted(Comparator.comparing(UserMemory::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(UserMemory::getContent)
                .collect(Collectors.joining("\n"));
        String snapshot = allMemories.stream()
                .filter(m -> "SNAPSHOT".equals(m.getMemoryType()))
                .sorted(Comparator.comparing(UserMemory::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(1)
                .map(UserMemory::getContent)
                .findFirst().orElse(null);
        String insights = allMemories.stream()
                .filter(m -> "INSIGHT".equals(m.getMemoryType()))
                .map(UserMemory::getContent)
                .collect(Collectors.joining("\n"));

        String promptText = promptTemplateManager.buildProfileBuildPrompt(facts, episodics, snapshot, insights);
        String llmOutput = reasoningChatClient.call(promptText).getContent();

        JSONObject json = JSONUtil.parseObj(LlmJsonSanitizer.sanitize(llmOutput));
        String profileText = json.getStr("profile_text");
        String tagsJson = json.getJSONArray("tags") != null ? json.getJSONArray("tags").toString() : null;

        int memoryVersion = allMemories.stream()
                .mapToInt(m -> m.getId() != null ? m.getId().intValue() : 0)
                .max().orElse(0);

        UserProfile existing = profileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
        if (existing == null) {
            UserProfile profile = new UserProfile();
            profile.setUserId(userId);
            profile.setProfileText(profileText);
            profile.setProfileTagsJson(tagsJson);
            profile.setMemoryVersion(memoryVersion);
            profileMapper.insert(profile);
        } else {
            existing.setProfileText(profileText);
            existing.setProfileTagsJson(tagsJson);
            existing.setMemoryVersion(memoryVersion);
            profileMapper.updateById(existing);
        }
        log.info("画像重建完成 userId={}", userId);
    }
}
