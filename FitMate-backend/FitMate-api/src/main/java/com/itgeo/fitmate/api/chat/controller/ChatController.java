package com.itgeo.fitmate.api.chat.controller;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.chat.application.ChatService;
import com.itgeo.fitmate.api.chat.application.ChatSessionService;
import com.itgeo.fitmate.api.chat.dto.ChatEntity;
import com.itgeo.fitmate.api.chat.dto.ChatSessionRenameRequest;
import com.itgeo.fitmate.api.chat.dto.RollbackRequest;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatSession;
import com.itgeo.fitmate.common.response.LeeResult;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 聊天相关控制器。
 * <p>
 * 说明：
 * 1. 正式对话主链路已统一收敛到 Agent 模式（/agent/execute），本控制器仅保留调试入口与聊天记录查询；
 * 2. /chat/chatTest 用于本地联调，不进入正式会话与 SSE 推送；
 * 3. /chat/records 仅供前端恢复历史会话使用，查询身份始终以当前登录用户为准。
 */
@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    @Resource
    private ChatSessionService chatSessionService;

    /**
     * 调试用同步聊天入口，不走 SSE、不落库。
     */
    @PostMapping("/chatTest")
    public String chatTest(@RequestBody ChatEntity chatEntity) {
        return chatService.chatTest(chatEntity.getMessage());
    }

    /**
     * 查询当前登录用户的聊天历史记录。
     */
    @GetMapping("/records")
    public LeeResult getRecords(
            @RequestParam(required = false) String who,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        // 1. 查询身份始终以当前登录用户为准
        AuthenticatedUserContext authenticatedUser = UserContextHolder.getRequired();

        // 2. who 仅用于兼容前端已有调用，真实查询身份仍以当前登录用户为准
        return LeeResult.ok(
                chatSessionService.getChatRecords(
                        authenticatedUser.getUserId(),
                        sessionId,
                        limit
                )
        );
    }

    /**
     * 回滚：删除指定 botMsgId 对应的用户消息及其之后的所有消息。
     * 用于「重试」功能——把用户消息内容回填输入框后，删除该消息及之后的历史。
     *
     * @param body 请求体，包含 sessionId 和 botMsgId
     * @return 删除的消息条数
     */
    @PostMapping("/rollback")
    public LeeResult rollback(@RequestBody RollbackRequest body) {
        try {
            AuthenticatedUserContext user = UserContextHolder.getRequired();
            if (body == null || body.getSessionId() == null || body.getBotMsgId() == null) {
                return LeeResult.errorMsg("sessionId和botMsgId不能为空");
            }
            int deleted = chatSessionService.deleteMessagesFromBotMsgId(
                    user.getUserId(),
                    body.getSessionId(),
                    body.getBotMsgId()
            );
            return LeeResult.ok(deleted);
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("回滚消息失败", e);
            return LeeResult.errorException("回滚消息失败");
        }
    }

    /**
     * 按消息ID查询思考内容。
     * <p>
     * 用于历史会话加载时按需展开思考内容：前端默认折叠，用户点击展开时调本接口加载。
     * 会校验消息所属会话归属当前登录用户，不属于时返回空字符串。
     *
     * @param messageId 消息主键
     * @return 思考内容字符串；不存在或无权访问时返回空字符串
     */
    @GetMapping("/thinking/{messageId}")
    public LeeResult getThinking(@PathVariable Long messageId) {
        AuthenticatedUserContext user = UserContextHolder.getRequired();
        String thinking = chatSessionService.getThinkingByMessageId(user.getUserId(), messageId);
        return LeeResult.ok(thinking == null ? "" : thinking);
    }

    /**
     * 删除指定会话及其全部关联数据（消息、思考内容、上下文压缩摘要、AgentRun 与 AgentStep）。
     * <p>
     * 删除当前活动会话时，前端应同步清空聊天界面并回到「新建会话」状态。
     *
     * @param sessionId 会话ID
     * @return 实际删除的消息条数
     */
    @DeleteMapping("/sessions/{sessionId}")
    public LeeResult deleteSession(@PathVariable Long sessionId) {
        try {
            AuthenticatedUserContext user = UserContextHolder.getRequired();
            if (sessionId == null) {
                return LeeResult.errorMsg("sessionId不能为空");
            }
            int deleted = chatSessionService.deleteSession(user.getUserId(), sessionId);
            return LeeResult.ok(deleted);
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("删除会话失败 sessionId={}", sessionId, e);
            return LeeResult.errorException("删除会话失败");
        }
    }

    /**
     * 重命名指定会话标题。
     *
     * @param sessionId 会话ID
     * @param body 请求体，包含新标题
     * @return 更新后的会话对象
     */
    @PutMapping("/sessions/{sessionId}/title")
    public LeeResult renameSession(
            @PathVariable Long sessionId,
            @RequestBody ChatSessionRenameRequest body) {
        try {
            AuthenticatedUserContext user = UserContextHolder.getRequired();
            if (sessionId == null) {
                return LeeResult.errorMsg("sessionId不能为空");
            }
            if (body == null || body.getTitle() == null || body.getTitle().trim().isEmpty()) {
                return LeeResult.errorMsg("标题不能为空");
            }
            ChatSession updated = chatSessionService.renameSession(
                    user.getUserId(),
                    sessionId,
                    body.getTitle()
            );
            return LeeResult.ok(updated);
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("重命名会话失败 sessionId={}", sessionId, e);
            return LeeResult.errorException("重命名会话失败");
        }
    }
}
