package com.itgeo.fitmate.api.agent.application.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.agent.application.AgentRunService;
import com.itgeo.fitmate.api.agent.dto.AgentRunDetailResponse;
import com.itgeo.fitmate.api.agent.dto.AgentRunListItemResponse;
import com.itgeo.fitmate.api.agent.dto.AgentRunStepResponse;
import com.itgeo.fitmate.api.agent.infrastructure.entity.AgentRun;
import com.itgeo.fitmate.api.agent.infrastructure.entity.AgentStep;
import com.itgeo.fitmate.api.agent.infrastructure.mapper.AgentRunMapper;
import com.itgeo.fitmate.api.agent.infrastructure.mapper.AgentStepMapper;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatSession;
import com.itgeo.fitmate.api.chat.infrastructure.mapper.ChatSessionMapper;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent run / step 持久化与查询服务实现。
 *
 * 职责：
 * 1. 创建 t_agent_run 主记录；
 * 2. 在执行过程中维护 run / 动态 trace 的状态流转、输入输出和错误信息；
 * 3. 为控制器提供运行列表、运行详情及轨迹映射结果。
 *
 * 说明：
 * - 本类只负责持久化与查询，不负责模型调用、SSE 推送或异步调度；
 * - run / step 状态统一使用 pending / running / success / failed；
 * - JSON 字段当前统一按 String 存储，便于首期落库和查询。
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class AgentRunServiceImpl implements AgentRunService {

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private AgentRunMapper agentRunMapper;

    @Resource
    private AgentStepMapper agentStepMapper;

    @Override
    public AgentRun findByUserIdAndBotMsgId(Long userId, String botMsgId) {
        // 1. 幂等查询入参不完整时，直接返回 null
        if (userId == null || StrUtil.isBlank(botMsgId)) {
            return null;
        }

        // 2. 基于 userId + botMsgId 查询已有 run，供后续 execute 做幂等判断
        return agentRunMapper.selectOne(
                new LambdaQueryWrapper<AgentRun>()
                        .eq(AgentRun::getUserId, userId)
                        .eq(AgentRun::getBotMsgId, botMsgId.trim())
                        .orderByDesc(AgentRun::getId)
                        .last("limit 1"));
    }

    @Override
    public AgentRun findByIdAndUserId(Long userId, Long runId) {
        if (userId == null || runId == null) {
            return null;
        }
        return agentRunMapper.selectOne(
                new LambdaQueryWrapper<AgentRun>()
                        .eq(AgentRun::getId, runId)
                        .eq(AgentRun::getUserId, userId)
                        .last("limit 1")
        );
    }

    @Override
    public Long createRun(Long userId, Long chatSessionId, String botMsgId, String requestText) {
        // 1. 校验创建 run 所需核心参数
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (StrUtil.isBlank(botMsgId)) {
            throw new IllegalArgumentException("botMsgId不能为空");
        }
        if (StrUtil.isBlank(requestText)) {
            throw new IllegalArgumentException("请求文本不能为空");
        }

        // 2. 初始化 run 主记录：
        //    - 初始状态固定 pending
        //    - startedAt / finishedAt 先不填
        AgentRun run = new AgentRun();
        run.setUserId(userId);
        run.setChatSessionId(chatSessionId);
        run.setBotMsgId(botMsgId.trim());
        run.setRequestText(requestText.trim());
        run.setStatus("pending");
        agentRunMapper.insert(run);

        // 3. 返回数据库生成的 runId，供后续步骤初始化与状态流转使用
        return run.getId();
    }

    @Override
    public AgentStep createStep(Long runId,
                                String eventType,
                                String stepName,
                                String stepStatus,
                                String toolName,
                                String toolCallId,
                                Integer iterationNo,
                                String inputJson) {
        ensureRunExists(runId);

        AgentStep step = new AgentStep();
        step.setAgentRunId(runId);
        step.setStepNo(nextStepNo(runId));
        step.setStepName(StrUtil.blankToDefault(stepName, StrUtil.blankToDefault(eventType, "Agent事件")));
        step.setStepStatus(StrUtil.blankToDefault(stepStatus, "running"));
        step.setEventType(eventType);
        step.setToolName(StrUtil.blankToDefault(toolName, null));
        step.setToolCallId(StrUtil.blankToDefault(toolCallId, null));
        step.setIterationNo(iterationNo);
        step.setInputJson(normalizeJson(inputJson));
        if ("running".equals(step.getStepStatus())) {
            step.setStartedAt(LocalDateTime.now());
        }
        agentStepMapper.insert(step);
        return step;
    }

    @Override
    public void markStepSuccess(Long stepId, String eventType, String outputJson, Long durationMs) {
        AgentStep existing = findRequiredStep(stepId);

        AgentStep update = new AgentStep();
        update.setId(existing.getId());
        update.setStepStatus("success");
        update.setEventType(StrUtil.blankToDefault(eventType, existing.getEventType()));
        update.setOutputJson(normalizeJson(outputJson));
        update.setErrorMessage(null);
        update.setDurationMs(durationMs);
        update.setFinishedAt(LocalDateTime.now());

        agentStepMapper.updateById(update);
    }

    @Override
    public void markStepFailed(Long stepId, String eventType, String errorMessage, Long durationMs) {
        AgentStep existing = findRequiredStep(stepId);

        AgentStep update = new AgentStep();
        update.setId(existing.getId());
        update.setStepStatus("failed");
        update.setEventType(StrUtil.blankToDefault(eventType, existing.getEventType()));
        update.setErrorMessage(normalizeErrorMessage(errorMessage, "步骤执行失败"));
        update.setDurationMs(durationMs);
        update.setFinishedAt(LocalDateTime.now());

        agentStepMapper.updateById(update);
    }

    @Override
    public void markRunRunning(Long runId) {
        // 1. 确保目标 run 存在
        ensureRunExists(runId);

        // 2. 更新 run 状态为 running，并记录开始时间
        AgentRun update = new AgentRun();
        update.setId(runId);
        update.setStatus("running");
        update.setStartedAt(LocalDateTime.now());

        agentRunMapper.updateById(update);
    }

    @Override
    public void markRunSuccess(Long runId, String resultJson) {
        // 1. 确保目标 run 存在
        ensureRunExists(runId);

        // 2. 更新 run 状态为 success，记录结果摘要和完成时间
        AgentRun update = new AgentRun();
        update.setId(runId);
        update.setStatus("success");
        update.setResultJson(normalizeJson(resultJson));
        update.setErrorMessage(null);
        update.setFinishedAt(LocalDateTime.now());

        agentRunMapper.updateById(update);
    }

    @Override
    public void markRunFailed(Long runId, String errorMessage) {
        // 1. 确保目标 run 存在
        ensureRunExists(runId);

        // 2. 更新 run 状态为 failed，记录错误信息和完成时间
        AgentRun update = new AgentRun();
        update.setId(runId);
        update.setStatus("failed");
        update.setErrorMessage(normalizeErrorMessage(errorMessage, "Agent执行失败"));
        update.setFinishedAt(LocalDateTime.now());

        agentRunMapper.updateById(update);
    }

    @Override
    public void markRunCancelled(Long runId, String reason) {
        if (runId == null) {
            return;
        }
        AgentRun update = new AgentRun();
        update.setId(runId);
        update.setStatus("cancelled");
        update.setErrorMessage(reason);
        update.setFinishedAt(LocalDateTime.now());
        agentRunMapper.updateById(update);
    }

    @Override
    public void markStepRunning(Long runId, Integer stepNo, String inputJson) {
        // 1. 先查出目标步骤，确保该 run 下这个 stepNo 真实存在
        AgentStep existing = findRequiredStep(runId, stepNo);

        // 2. 更新步骤状态为 running，并记录输入和开始时间
        AgentStep update = new AgentStep();
        update.setId(existing.getId());
        update.setStepStatus("running");
        update.setInputJson(normalizeJson(inputJson));
        update.setStartedAt(LocalDateTime.now());

        agentStepMapper.updateById(update);
    }

    @Override
    public void markStepSuccess(Long runId, Integer stepNo, String outputJson) {
        // 1. 查出目标步骤
        AgentStep existing = findRequiredStep(runId, stepNo);

        // 2. 更新步骤状态为 success，记录输出和完成时间
        AgentStep update = new AgentStep();
        update.setId(existing.getId());
        update.setStepStatus("success");
        update.setOutputJson(normalizeJson(outputJson));
        update.setErrorMessage(null);
        update.setFinishedAt(LocalDateTime.now());

        agentStepMapper.updateById(update);
    }

    @Override
    public void markStepFailed(Long runId, Integer stepNo, String errorMessage) {
        // 1. 查出目标步骤
        AgentStep existing = findRequiredStep(runId, stepNo);

        // 2. 更新步骤状态为 failed，记录错误和完成时间
        AgentStep update = new AgentStep();
        update.setId(existing.getId());
        update.setStepStatus("failed");
        update.setErrorMessage(normalizeErrorMessage(errorMessage, "步骤执行失败"));
        update.setFinishedAt(LocalDateTime.now());

        agentStepMapper.updateById(update);
    }


    /**
     * 查询当前用户最近的 run 列表，并映射为列表接口使用的响应结构。
     */
    @Override
    public List<AgentRunListItemResponse> listRuns(Long userId, String status, Integer limit) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }

        int safeLimit = normalizeListLimit(limit);

        LambdaQueryWrapper<AgentRun> wrapper = new LambdaQueryWrapper<AgentRun>()
                .eq(AgentRun::getUserId, userId)
                .orderByDesc(AgentRun::getId)
                .last("limit " + safeLimit);

        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(AgentRun::getStatus, status.trim());
        }

        List<AgentRun> runs = agentRunMapper.selectList(wrapper);
        return runs.stream()
                .map(this::toListItemResponse)
                .collect(Collectors.toList());
    }

    /**
     * 查询指定 run 的详情，并补齐该 run 下的 step 列表。
     */

    @Override
    public AgentRunDetailResponse getRunDetail(Long userId, Long runId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (runId == null) {
            throw new IllegalArgumentException("runId不能为空");
        }
        AgentRun run = agentRunMapper.selectOne(
                new LambdaQueryWrapper<AgentRun>()
                        .eq(AgentRun::getId, runId)
                        .eq(AgentRun::getUserId, userId)
                        .last("limit 1")
        );
        if (run == null) return null;

        List<AgentStep> steps = agentStepMapper.selectList(
                new LambdaQueryWrapper<AgentStep>()
                        .eq(AgentStep::getAgentRunId, runId)
                        .orderByAsc(AgentStep::getStepNo)
        );

        AgentRunDetailResponse response = new AgentRunDetailResponse();
        response.setRunId(run.getId());
        response.setChatSessionId(run.getChatSessionId());
        response.setSessionCode(resolveSessionCode(run.getChatSessionId()));
        response.setBotMsgId(run.getBotMsgId());
        response.setRequestText(run.getRequestText());
        response.setStatus(run.getStatus());
        response.setResultJson(run.getResultJson());
        response.setErrorMessage(run.getErrorMessage());
        response.setCreatedAt(run.getCreatedAt());
        response.setStartedAt(run.getStartedAt());
        response.setFinishedAt(run.getFinishedAt());
        response.setSteps(
                steps.stream()
                        .map(this::toStepResponse)
                        .collect(Collectors.toList())
        );
        return response;

    }

    /**
     * 校验目标 run 主记录存在，供状态流转更新链路复用。
     */
    private void ensureRunExists(Long runId) {
        // 1. runId 不能为空
        if (runId == null) {
            throw new IllegalArgumentException("runId不能为空");
        }
        // 2. run 主记录必须存在，否则后续状态更新没有意义
        if (agentRunMapper.selectById(runId) == null) {
            throw new IllegalArgumentException("Agent运行记录不存在");
        }
    }

    /**
     * 查询指定 run 下的 step 记录；不存在时直接抛出异常。
     */
    private AgentStep findRequiredStep(Long runId, Integer stepNo) {
        // 1. runId 和 stepNo 都不能为空
        if (runId == null) {
            throw new IllegalArgumentException("runId不能为空");
        }
        if (stepNo == null) {
            throw new IllegalArgumentException("stepNo不能为空");
        }

        // 2. 按 runId + stepNo 查唯一的步骤记录
        AgentStep step = agentStepMapper.selectOne(
                new LambdaQueryWrapper<AgentStep>()
                        .eq(AgentStep::getAgentRunId, runId)
                        .eq(AgentStep::getStepNo, stepNo)
                        .last("limit 1")
        );

        // 3. 如果没查到，说明步骤未初始化或步骤编号非法
        if (step == null) {
            throw new IllegalArgumentException("Agent步骤不存在");
        }
        return step;
    }

    /**
     * 按 step 主键查询动态 trace 记录。
     */
    private AgentStep findRequiredStep(Long stepId) {
        if (stepId == null) {
            throw new IllegalArgumentException("stepId不能为空");
        }
        AgentStep step = agentStepMapper.selectById(stepId);
        if (step == null) {
            throw new IllegalArgumentException("Agent步骤不存在");
        }
        return step;
    }

    /**
     * 获取下一个动态 trace 序号，仅用于前端排序展示。
     */
    private Integer nextStepNo(Long runId) {
        AgentStep latest = agentStepMapper.selectOne(
                new LambdaQueryWrapper<AgentStep>()
                        .eq(AgentStep::getAgentRunId, runId)
                        .orderByDesc(AgentStep::getStepNo)
                        .last("limit 1")
        );
        if (latest == null || latest.getStepNo() == null) {
            return 1;
        }
        return latest.getStepNo() + 1;
    }

    /**
     * 规范化 JSON 文本字段。
     *
     * 规则：
     * - 空白字符串统一转为 null；
     * - 非空时去掉首尾空格。
     *
     * 说明：
     * - 当前 JSON 列先按 String 持久化；
     * - 调用方应保证传入的是合法 JSON 字符串。
     *
     * @param json 原始 JSON 文本
     * @return 规范化后的 JSON 文本
     */
    private String normalizeJson(String json) {
        return StrUtil.isBlank(json) ? null : json.trim();
    }

    /**
     * 规范化错误消息，确保不超过 500 个字符。
     *
     * @param message 原始错误消息
     * @param defaultMessage 默认错误消息
     * @return 规范化后的错误消息
     */
    private String normalizeErrorMessage(String message, String defaultMessage) {
        String normalized = StrUtil.isBlank(message) ? defaultMessage : message.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    /**
     * 将 run 主记录映射为列表项响应对象。
     */
    private AgentRunListItemResponse toListItemResponse(AgentRun run) {
        AgentRunListItemResponse response = new AgentRunListItemResponse();
        response.setRunId(run.getId());
        response.setChatSessionId(run.getChatSessionId());
        response.setSessionCode(resolveSessionCode(run.getChatSessionId()));
        response.setBotMsgId(run.getBotMsgId());
        response.setRequestText(run.getRequestText());
        response.setStatus(run.getStatus());
        response.setErrorMessage(run.getErrorMessage());
        response.setCreatedAt(run.getCreatedAt());
        response.setStartedAt(run.getStartedAt());
        response.setFinishedAt(run.getFinishedAt());
        return response;
    }

    /**
     * 将 step 记录映射为详情响应中的步骤对象。
     */
    private AgentRunStepResponse toStepResponse(AgentStep step) {
        AgentRunStepResponse response = new AgentRunStepResponse();
        response.setStepId(step.getId());
        response.setStepNo(step.getStepNo());
        response.setStepName(step.getStepName());
        response.setStepStatus(step.getStepStatus());
        response.setEventType(step.getEventType());
        response.setToolName(step.getToolName());
        response.setToolCallId(step.getToolCallId());
        response.setIterationNo(step.getIterationNo());
        response.setDurationMs(step.getDurationMs());
        response.setInputJson(step.getInputJson());
        response.setOutputJson(step.getOutputJson());
        response.setErrorMessage(step.getErrorMessage());
        response.setStartedAt(step.getStartedAt());
        response.setFinishedAt(step.getFinishedAt());
        return response;
    }

    /**
     * 根据 chatSessionId 解析 sessionCode，供查询结果补齐展示字段。
     */
    private String resolveSessionCode(Long chatSessionId) {
        if (chatSessionId == null) {
            return null;
        }
        ChatSession session = chatSessionMapper.selectById(chatSessionId);
        return session == null ? null : session.getSessionCode();
    }

    /**
     * 规范化列表查询条数，统一默认值并限制最大返回上限。
     */
    private int normalizeListLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 10;
        }
        return Math.min(limit, 50);
    }
}
