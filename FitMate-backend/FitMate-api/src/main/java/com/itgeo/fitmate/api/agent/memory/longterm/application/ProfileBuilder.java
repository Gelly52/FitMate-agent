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
import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileBuilder {

    private final UserMemoryMapper memoryMapper;
    private final UserProfileMapper profileMapper;
    private final ChatModel chatModel;
    private final PromptTemplateManager promptTemplateManager;
    private final MemoryProperties properties;

    @Async("memoryTaskExecutor")
    public void asyncRebuild(Long userId) {
        rebuild(userId);
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
        // 查询全部 active 记忆
        List<UserMemory> allMemories = memoryMapper.selectList(new LambdaQueryWrapper<UserMemory>()
                .eq(UserMemory::getUserId, userId)
                .eq(UserMemory::getStatus, "active"));

        if (allMemories.isEmpty()) {
            log.debug("用户无记忆，跳过画像生成 userId={}", userId);
            return;
        }

        // 按类型分组
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

        // LLM 生成
        String promptText = promptTemplateManager.buildProfileBuildPrompt(facts, episodics, snapshot, insights);
        String llmOutput = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();

        // 解析
        JSONObject json = JSONUtil.parseObj(LlmJsonSanitizer.sanitize(llmOutput));
        String profileText = json.getStr("profile_text");
        String tagsJson = json.getJSONArray("tags") != null ? json.getJSONArray("tags").toString() : null;

        // 计算版本号
        int memoryVersion = allMemories.stream()
                .mapToInt(m -> m.getId() != null ? m.getId().intValue() : 0)
                .max().orElse(0);

        // upsert
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
