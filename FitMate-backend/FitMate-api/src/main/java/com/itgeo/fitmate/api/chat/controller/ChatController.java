package com.itgeo.fitmate.api.chat.controller;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.chat.application.ChatService;
import com.itgeo.fitmate.api.chat.application.ChatSessionService;
import com.itgeo.fitmate.api.chat.dto.ChatEntity;
import com.itgeo.fitmate.api.chat.dto.RollbackRequest;
import com.itgeo.fitmate.common.response.LeeResult;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
}
