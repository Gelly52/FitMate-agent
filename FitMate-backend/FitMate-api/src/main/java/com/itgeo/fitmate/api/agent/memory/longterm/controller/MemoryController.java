package com.itgeo.fitmate.api.agent.memory.longterm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itgeo.fitmate.api.agent.memory.longterm.application.ProfileBuilder;
import com.itgeo.fitmate.api.agent.memory.longterm.controller.dto.MemoryListResponse;
import com.itgeo.fitmate.api.agent.memory.longterm.controller.dto.ProfileResponse;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserProfile;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserProfileMapper;
import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.common.response.LeeResult;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户长期记忆管理 API
 * 提供记忆列表查询、单条删除、清空、画像查看与重建等接口
 */
@RestController
@RequestMapping("/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final UserMemoryMapper memoryMapper;
    private final UserProfileMapper profileMapper;
    private final ProfileBuilder profileBuilder;

    /**
     * 查询当前用户的记忆列表（分页，可按类型过滤）
     */
    @GetMapping("/list")
    public LeeResult list(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContextHolder.getRequired().getUserId();
        Page<UserMemory> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<UserMemory> wrapper = new LambdaQueryWrapper<UserMemory>()
                .eq(UserMemory::getUserId, userId)
                .ne(UserMemory::getStatus, "ignored");
        if (type != null && !type.isBlank()) {
            wrapper.eq(UserMemory::getMemoryType, type);
        }
        wrapper.orderByDesc(UserMemory::getCreatedAt);
        Page<UserMemory> result = memoryMapper.selectPage(pageObj, wrapper);

        MemoryListResponse resp = new MemoryListResponse();
        resp.setItems(result.getRecords().stream().map(m -> {
            MemoryListResponse.Item item = new MemoryListResponse.Item();
            item.setId(m.getId());
            item.setMemoryType(m.getMemoryType());
            item.setContent(m.getContent());
            item.setMetadataJson(m.getMetadataJson());
            item.setSource(m.getSource());
            item.setStatus(m.getStatus());
            item.setCreatedAt(m.getCreatedAt());
            return item;
        }).collect(Collectors.toList()));
        resp.setTotal(result.getTotal());
        resp.setPage(page);
        resp.setSize(size);
        return LeeResult.ok(resp);
    }

    /**
     * 删除单条记忆（标记为 ignored）
     */
    @DeleteMapping("/{id}")
    public LeeResult delete(@PathVariable Long id) {
        Long userId = UserContextHolder.getRequired().getUserId();
        UserMemory memory = memoryMapper.selectById(id);
        if (memory == null || !memory.getUserId().equals(userId)) {
            return LeeResult.build(404, "记忆不存在", null);
        }
        memory.setStatus("ignored");
        memoryMapper.updateById(memory);
        profileBuilder.asyncRebuild(userId);
        return LeeResult.ok();
    }

    /**
     * 清空当前用户全部记忆（标记为 ignored）
     */
    @DeleteMapping("/all")
    public LeeResult deleteAll() {
        Long userId = UserContextHolder.getRequired().getUserId();
        List<UserMemory> memories = memoryMapper.selectList(new LambdaQueryWrapper<UserMemory>()
                .eq(UserMemory::getUserId, userId)
                .ne(UserMemory::getStatus, "ignored"));
        for (UserMemory m : memories) {
            m.setStatus("ignored");
            memoryMapper.updateById(m);
        }
        profileBuilder.asyncRebuild(userId);
        return LeeResult.ok();
    }

    /**
     * 查看当前用户画像
     */
    @GetMapping("/profile")
    public LeeResult getProfile() {
        Long userId = UserContextHolder.getRequired().getUserId();
        UserProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
        ProfileResponse resp = new ProfileResponse();
        if (profile == null) {
            return LeeResult.ok(resp); // 空画像
        }
        resp.setProfileText(profile.getProfileText());
        resp.setProfileTagsJson(profile.getProfileTagsJson());
        resp.setMemoryVersion(profile.getMemoryVersion());
        resp.setGeneratedAt(profile.getGeneratedAt());
        return LeeResult.ok(resp);
    }

    /**
     * 触发画像重建（异步）
     */
    @PostMapping("/profile/rebuild")
    public LeeResult rebuildProfile() {
        Long userId = UserContextHolder.getRequired().getUserId();
        profileBuilder.asyncRebuild(userId);
        return LeeResult.ok();
    }
}
