package com.itgeo.fitmate.api.agent.memory;

import cn.hutool.core.util.StrUtil;
import com.itgeo.fitmate.api.agent.config.AgentProperties;
import com.itgeo.fitmate.api.agent.dto.AgentExecuteContext;
import com.itgeo.fitmate.api.agent.memory.dto.MemoryLoadResult;
import com.itgeo.fitmate.api.chat.application.ChatSessionService;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatMessage;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 加载 Agent Loop 所需的短期对话记忆。
 * <p>
 * 实际加载逻辑委托给 ContextCompressService：若存在历史摘要则只取摘要之后的消息，
 * 否则回退到按条数 skip 截断的兜底策略。
 */
@Service
public class AgentMemoryService {

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private AgentProperties agentProperties;

    @Resource
    private ContextCompressService contextCompressService;

    /**
     * 加载参与 prompt 的历史消息（含摘要）。
     */
    public MemoryLoadResult loadRecentMessages(AgentExecuteContext context) {
        int fallbackWindowSize = agentProperties.getMemoryWindowSize() == null
                ? 50 : agentProperties.getMemoryWindowSize();
        return contextCompressService.loadMemoryWithContext(
                context.getChatSessionId(),
                fallbackWindowSize
        );
    }
}
