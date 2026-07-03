package com.itgeo.fitmate.api.agent.application;

import com.itgeo.fitmate.api.agent.dto.AgentExecuteAckResponse;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.chat.dto.ChatEntity;

/**
 * Agent 任务受理服务契约。
 *
 * 职责：
 * 1. 定义 /agent/execute 的同步受理入口；
 * 2. 返回本次请求的受理 ack；
 * 3. 不承担 run 运行态推进与结果生成职责。
 */
public interface AgentExecuteService {

/**
     * 受理一条 Agent 执行请求，并返回同步 ack。
     *
     * @param authenticatedUser 当前登录用户上下文
     * @param chatEntity 聊天请求体
     * @return 受理结果
     */
    AgentExecuteAckResponse execute(AuthenticatedUserContext authenticatedUser, ChatEntity chatEntity);
}
