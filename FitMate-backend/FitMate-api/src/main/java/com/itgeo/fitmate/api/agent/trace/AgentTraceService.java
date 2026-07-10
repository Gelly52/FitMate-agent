package com.itgeo.fitmate.api.agent.trace;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.agent.application.AgentRunService;
import com.itgeo.fitmate.api.agent.dto.AgentExecuteContext;
import com.itgeo.fitmate.api.agent.dto.AgentStepEvent;
import com.itgeo.fitmate.api.agent.infrastructure.entity.AgentStep;
import com.itgeo.fitmate.api.sse.domain.SSEMsgType;
import com.itgeo.fitmate.api.sse.infrastructure.SSEServer;
import jakarta.annotation.Resource;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Agent 动态 trace 落库与 SSE 推送服务。
 */
@Service
public class AgentTraceService {

    @Resource
    private AgentRunService agentRunService;

    public AgentStep startEvent(AgentExecuteContext context,
                                String eventType,
                                String stepName,
                                String toolName,
                                String toolCallId,
                                Integer iterationNo,
                                String inputJson,
                                String message) {
        // 原 8 参数版本委托给带 subagentRunId 的重载，传 null 表示非 subagent 事件
        return startEvent(context, eventType, stepName, toolName, toolCallId,
                null, iterationNo, inputJson, message);
    }

    /**
     * 启动一条带 subagentRunId 的事件轨迹，用于 subagent_started / subagent_finished 事件。
     * <p>
     * spawn 分支调用此重载，把 Sub-Agent runId 落到 step 记录上，
     * 后续 emitEvent 时会从 step 取出 subagentRunId 写入 SSE 事件载荷。
     *
     * @param subagentRunId Sub-Agent run ID；非 subagent 事件传 null
     */
    public AgentStep startEvent(AgentExecuteContext context,
                                String eventType,
                                String stepName,
                                String toolName,
                                String toolCallId,
                                Long subagentRunId,
                                Integer iterationNo,
                                String inputJson,
                                String message) {
        // 从内存计数器取下一个 stepNo，避免每次 createStep 都 SELECT MAX(stepNo)
        AtomicInteger counter = context.getStepNoCounter();
        Integer stepNo = counter == null ? null : counter.incrementAndGet();
        AgentStep step = agentRunService.createStep(
                context.getRunId(),
                eventType,
                stepName,
                "running",
                toolName,
                toolCallId,
                subagentRunId,
                iterationNo,
                inputJson,
                stepNo
        );
        emitEvent(context, step, eventType, "running", message, inputJson, null, null, null);
        return step;
    }

    public void finishEvent(AgentExecuteContext context,
                            AgentStep step,
                            String eventType,
                            String outputJson,
                            Long durationMs,
                            String message) {
        agentRunService.markStepSuccess(step.getId(), eventType, outputJson, durationMs);
        step.setStepStatus("success");
        step.setEventType(StrUtil.blankToDefault(eventType, step.getEventType()));
        step.setOutputJson(outputJson);
        step.setDurationMs(durationMs);
        emitEvent(context, step, step.getEventType(), "success", message, step.getInputJson(), outputJson, null, durationMs);
    }

    public void failEvent(AgentExecuteContext context,
                          AgentStep step,
                          String eventType,
                          String errorMessage,
                          Long durationMs,
                          String message) {
        agentRunService.markStepFailed(step.getId(), eventType, errorMessage, durationMs);
        step.setStepStatus("failed");
        step.setEventType(StrUtil.blankToDefault(eventType, step.getEventType()));
        step.setErrorMessage(errorMessage);
        step.setDurationMs(durationMs);
        emitEvent(context, step, step.getEventType(), "failed", message, step.getInputJson(), null, errorMessage, durationMs);
    }

    public void emitEvent(AgentExecuteContext context,
                          AgentStep step,
                          String eventType,
                          String stepStatus,
                          String message,
                          String inputJson,
                          String outputJson,
                          String errorMessage,
                          Long durationMs) {
        if (context == null || context.getAuthenticatedUser() == null) {
            return;
        }
        String sseClientId = context.getAuthenticatedUser().getSseClientId();
        if (StrUtil.isBlank(sseClientId)) {
            return;
        }

        AgentStepEvent event = new AgentStepEvent();
        event.setStepId(step.getId());
        event.setRunId(context.getRunId());
        event.setStepNo(step.getStepNo());
        event.setStepName(step.getStepName());
        event.setStepStatus(stepStatus);
        event.setEventType(eventType);
        event.setToolName(step.getToolName());
        event.setToolCallId(step.getToolCallId());
        event.setSubagentRunId(step.getSubagentRunId());
        event.setIterationNo(step.getIterationNo());
        event.setDurationMs(durationMs);
        event.setInputJson(inputJson);
        event.setOutputJson(outputJson);
        event.setErrorMessage(errorMessage);
        event.setMessage(message);

        SSEServer.sendMsg(sseClientId, JSONUtil.toJsonStr(event), SSEMsgType.AGENT_STEP);
    }
}
