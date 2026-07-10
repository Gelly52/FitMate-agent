package com.itgeo.fitmate.api.agent.application.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.agent.infrastructure.entity.AgentRun;
import com.itgeo.fitmate.api.agent.infrastructure.entity.AgentStep;
import com.itgeo.fitmate.api.agent.infrastructure.mapper.AgentRunMapper;
import com.itgeo.fitmate.api.agent.infrastructure.mapper.AgentStepMapper;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatThinking;
import com.itgeo.fitmate.api.chat.infrastructure.mapper.ChatThinkingMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Agent 运行记录定时清理任务。
 * <p>
 * 清理策略：按 {@code created_at} 早于 {@code now - retentionDays} 的记录删除，
 * 依次清理 {@code t_agent_step}、对应的 {@code t_agent_run}、{@code t_chat_thinking}。
 * <p>
 * 设计要点：
 * 1. AgentStep 是 Agent 执行轨迹的明细，长期累积会占空间；
 * 2. AgentRun 是运行主表，与 step 同生命周期清理，避免孤儿 run；
 * 3. ChatThinking 是 LLM 思考内容，按 message_id 关联，30 天后基本无需复现；
 * 4. 通过配置 {@code fitmate.agent.retention-days} 控制 TTL，默认 30 天；
 * 5. 通过配置 {@code fitmate.agent.cleanup-enabled} 控制是否启用，默认 true；
 * 6. 不清理 ChatMessage，保留对话主体内容与历史会话标题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunCleanupScheduler {

    private final AgentRunMapper agentRunMapper;
    private final AgentStepMapper agentStepMapper;
    private final ChatThinkingMapper chatThinkingMapper;

    /**
     * 保留天数，默认 30 天。
     */
    @Value("${fitmate.agent.retention-days:30}")
    private int retentionDays;

    /**
     * 是否启用清理，默认开启。
     */
    @Value("${fitmate.agent.cleanup-enabled:true}")
    private boolean cleanupEnabled;

    /**
     * 每天凌晨 3 点执行（错开快照聚合的 2 点）。
     */
    @Scheduled(cron = "${fitmate.agent.cleanup-cron:0 0 3 * * *}")
    public void cleanupExpiredAgentRuns() {
        if (!cleanupEnabled) {
            return;
        }
        if (retentionDays <= 0) {
            log.warn("retentionDays={} 非法，跳过清理", retentionDays);
            return;
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        log.info("开始清理过期 Agent 记录，threshold={}（保留 {} 天）", threshold, retentionDays);

        // 1. 先查过期的 AgentRun 主键
        List<AgentRun> expiredRuns = agentRunMapper.selectList(
                new LambdaQueryWrapper<AgentRun>()
                        .lt(AgentRun::getCreatedAt, threshold)
                        .select(AgentRun::getId)
        );
        if (expiredRuns.isEmpty()) {
            log.info("无过期 Agent 记录需要清理");
            return;
        }

        List<Long> expiredRunIds = expiredRuns.stream()
                .map(AgentRun::getId)
                .collect(Collectors.toList());

        // 2. 按主键批量删除 AgentStep
        int stepDeleted = agentStepMapper.delete(
                new LambdaQueryWrapper<AgentStep>()
                        .in(AgentStep::getAgentRunId, expiredRunIds)
        );

        // 3. 按主键批量删除 AgentRun
        int runDeleted = agentRunMapper.delete(
                new LambdaQueryWrapper<AgentRun>()
                        .in(AgentRun::getId, expiredRunIds)
        );

        // 4. 删除过期的 ChatThinking（按 created_at 直接过滤，无需关联 run）
        int thinkingDeleted = chatThinkingMapper.delete(
                new LambdaQueryWrapper<ChatThinking>()
                        .lt(ChatThinking::getCreatedAt, threshold)
        );

        log.info("清理过期 Agent 记录完成：run={} step={} thinking={}", runDeleted, stepDeleted, thinkingDeleted);
    }
}
