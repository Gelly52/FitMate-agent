package com.itgeo.fitmate.api.agent.controller;

import com.itgeo.fitmate.api.agent.application.AgentCancelService;
import com.itgeo.fitmate.api.agent.application.AgentExecuteService;
import com.itgeo.fitmate.api.agent.application.AgentRunService;
import com.itgeo.fitmate.api.agent.dto.AgentExecuteAckResponse;
import com.itgeo.fitmate.api.agent.dto.AgentRunDetailResponse;
import com.itgeo.fitmate.api.agent.memory.ContextCompressService;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.chat.application.ChatSessionService;
import com.itgeo.fitmate.api.chat.dto.ChatEntity;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatSession;
import com.itgeo.fitmate.common.response.LeeResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 控制器。
 *
 * 职责：
 * 1. 接收 Agent 执行与查询请求；
 * 2. 从鉴权上下文提取当前登录用户；
 * 3. 调用对应服务完成受理或查询；
 * 4. 统一包装 LeeResult 响应。
 */
@Slf4j
@RestController
@RequestMapping("/agent")
public class AgentController {

    @Resource
    private AgentExecuteService agentExecuteService;

    @Resource
    private AgentRunService agentRunService;

    @Resource
    private AgentCancelService agentCancelService;

    @Resource
    private ContextCompressService contextCompressService;

    @Resource
    private ChatSessionService chatSessionService;

/**
     * 接收一条 Agent 执行请求并返回受理结果。
     *
     * @param chatEntity 聊天请求体
     * @return 受理结果
     */
    @PostMapping("/execute")
    public LeeResult execute(@RequestBody ChatEntity chatEntity) {
        try {
            AuthenticatedUserContext authenticatedUser = UserContextHolder.getRequired();
            AgentExecuteAckResponse ack = agentExecuteService.execute(authenticatedUser, chatEntity);
            return LeeResult.ok(ack);
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("Agent 任务受理失败", e);
            return LeeResult.errorException("Agent 任务受理失败");
        }
    }

/**
     * 查询当前登录用户最近的 Agent run 列表。
     */
    @GetMapping("/runs")
    public LeeResult listRuns(@RequestParam(required = false) String status,
                         @RequestParam(required = false, defaultValue = "10") Integer limit) {
        try {
            AuthenticatedUserContext authenticatedUser = UserContextHolder.getRequired();
            return LeeResult.ok(
                    agentRunService.listRuns(authenticatedUser.getUserId(), status, limit)
            );
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("查询Agent运行列表失败", e);
            return LeeResult.errorException("查询Agent运行列表失败");
        }
    }

/**
     * 查询当前登录用户指定的 Agent run 详情。
     */
    @GetMapping("/runs/{runId}")
    public LeeResult getRunDetail(@PathVariable Long runId) {
        try {
            AuthenticatedUserContext authenticatedUser = UserContextHolder.getRequired();
            AgentRunDetailResponse detail = agentRunService.getRunDetail(
                    authenticatedUser.getUserId(),
                    runId
            );
            if (detail == null) {
                return LeeResult.errorMsg("Agent运行记录不存在");
            }
            return LeeResult.ok(detail);
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("查询Agent运行详情失败, runId={}", runId, e);
            return LeeResult.errorException("查询Agent运行详情失败");
        }
    }

    /**
     * 主动触发上下文压缩。
     * <p>
     * 异步执行，立即返回；压缩过程与结果通过长连接 SSE 通道推送：
     * context_compressing → context_compressed / context_compress_failed。
     *
     * @param sessionId 会话ID
     * @return 受理结果
     */
    @PostMapping("/sessions/{sessionId}/compress")
    public LeeResult compressSession(@PathVariable Long sessionId) {
        try {
            AuthenticatedUserContext user = UserContextHolder.getRequired();
            // 校验会话归属
            ChatSession session = chatSessionService.findByIdAndUserId(sessionId, user.getUserId());
            if (session == null) {
                return LeeResult.errorMsg("会话不存在或无权访问");
            }
            // 异步触发压缩，结果通过 SSE 推送
            contextCompressService.compressManuallyAsync(sessionId, user.getSseClientId());
            return LeeResult.ok();
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("主动压缩上下文失败, sessionId={}", sessionId, e);
            return LeeResult.errorException("主动压缩上下文失败");
        }
    }

    /**
     * 取消正在执行的 Agent 任务。
     *
     * @param runId 要取消的 run ID
     * @return 取消结果
     */
    @PostMapping("/cancel")
    public LeeResult cancel(@RequestParam Long runId) {
        try {
            AuthenticatedUserContext authenticatedUser = UserContextHolder.getRequired();
            boolean cancelled = agentCancelService.cancel(authenticatedUser.getUserId(), runId);
            return LeeResult.ok(cancelled);
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("取消Agent任务失败, runId={}", runId, e);
            return LeeResult.errorException("取消Agent任务失败");
        }
    }
}
