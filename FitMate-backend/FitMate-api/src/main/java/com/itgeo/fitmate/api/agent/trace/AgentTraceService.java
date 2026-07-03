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
        AgentStep step = agentRunService.createStep(
                context.getRunId(),
                eventType,
                stepName,
                "running",
                toolName,
                toolCallId,
                iterationNo,
                inputJson
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
        event.setIterationNo(step.getIterationNo());
        event.setDurationMs(durationMs);
        event.setInputJson(inputJson);
        event.setOutputJson(outputJson);
        event.setErrorMessage(errorMessage);
        event.setMessage(message);

        SSEServer.sendMsg(sseClientId, JSONUtil.toJsonStr(event), SSEMsgType.AGENT_STEP);
    }
}
