package com.itgeo.fitmate.api.agent.memory.longterm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper.UserMemoryMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryWriter {

    private final UserMemoryMapper memoryMapper;

    /**
     * 写入记忆：若相同 content_hash 已存在（active 或 ignored）则跳过。
     * @return true 表示新写入，false 表示跳过
     */
    public boolean writeIfNotIgnored(MemoryWriteRequest req) {
        String hash = req.computeHash();

        // 查询是否已存在相同 hash
        List<UserMemory> existing = memoryMapper.selectList(new LambdaQueryWrapper<UserMemory>()
                .eq(UserMemory::getUserId, req.getUserId())
                .eq(UserMemory::getContentHash, hash));
        if (!existing.isEmpty()) {
            log.debug("记忆已存在（hash={}），跳过写入", hash);
            return false;
        }

        UserMemory entity = new UserMemory();
        entity.setUserId(req.getUserId());
        entity.setMemoryType(req.getMemoryType());
        entity.setContent(req.getContent());
        entity.setMetadataJson(req.getMetadataJson());
        entity.setSource(req.getSource());
        entity.setContentHash(hash);
        entity.setStatus("active");
        memoryMapper.insert(entity);
        log.info("写入记忆 userId={} type={} source={}", req.getUserId(), req.getMemoryType(), req.getSource());
        return true;
    }
}
