package com.itgeo.fitmate.api.agent.application.impl;

import com.itgeo.fitmate.api.agent.application.AgentCancelService;
import com.itgeo.fitmate.api.agent.application.AgentRunService;
import com.itgeo.fitmate.api.agent.core.AgentCancellationRegistry;
import com.itgeo.fitmate.api.agent.infrastructure.entity.AgentRun;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AgentCancelServiceImpl implements AgentCancelService {

    @Resource
    private AgentCancellationRegistry cancellationRegistry;

    @Resource
    private AgentRunService agentRunService;

    @Override
    public boolean cancel(Long userId, Long runId) {
        if (userId == null || runId == null) {
            return false;
        }
        // 权限校验：确认 run 归属当前用户
        AgentRun run = agentRunService.findByIdAndUserId(userId, runId);
        if (run == null) {
            log.warn("取消Agent失败: run不存在或无权访问, userId={}, runId={}", userId, runId);
            return false;
        }
        // 仅 running/pending 状态可取消
        String status = run.getStatus();
        if (!"running".equals(status) && !"pending".equals(status)) {
            log.info("取消Agent跳过: run已结束, runId={}, status={}", runId, status);
            return false;
        }
        cancellationRegistry.cancel(runId);
        return true;
    }
}
