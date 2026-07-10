package com.itgeo.fitmate.api.agent.memory.longterm.tool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryExtractCounter;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriteRequest;
import com.itgeo.fitmate.api.agent.memory.longterm.application.MemoryWriter;
import com.itgeo.fitmate.api.agent.tool.ToolCall;
import com.itgeo.fitmate.api.agent.tool.ToolDescriptor;
import com.itgeo.fitmate.api.agent.tool.ToolExecutor;
import com.itgeo.fitmate.api.agent.tool.ToolResult;
import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.chat.application.ChatSessionService;
import com.itgeo.fitmate.api.chat.infrastructure.entity.ChatMessage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户长期记忆主动记录工具。
 * <p>
 * 当用户在对话中明确表达"记住这个"、"以后记得"、"帮我记下"等需要持久化的意图时，
 * Agent 可直接调用本工具写入长期记忆，无需等待每 N 轮的自动提取。
 * 写入后同步更新会话级提取计数器，避免本轮 finishWithAnswer 重复触发自动提取浪费 LLM 调用。
 * <p>
 * 去重保障：MemoryWriter 内部基于 content_hash (SHA-256) 做精确去重，重复内容会被跳过。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryRecordToolExecutor implements ToolExecutor {

    private static final Set<String> ALLOWED_TYPES = Set.of("FACT", "EPISODIC", "INSIGHT");

    private final MemoryWriter memoryWriter;
    private final MemoryExtractCounter memoryExtractCounter;
    private final ChatSessionService chatSessionService;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "memory.record",
                "主动记录一条用户长期记忆。当用户明确要求记住某信息（如\"记住我目标是减脂到15%\"、"
                        + "\"帮我记下我腰伤不能深蹲\"）或对话中出现值得长期保存的稳定事实/关键事件/分析结论时调用。"
                        + "memory_type 含义与 metadata 字段选择见系统提示词「长期记忆规则」段。",
                "{\"type\":\"object\",\"properties\":{"
                        + "\"memory_type\":{\"type\":\"string\",\"enum\":[\"FACT\",\"EPISODIC\",\"INSIGHT\"],\"description\":\"记忆类型\"},"
                        + "\"content\":{\"type\":\"string\",\"description\":\"记忆内容,简洁陈述句\"},"
                        + "\"metadata\":{\"type\":\"object\",\"description\":\"可选元数据,无内容时可省略\",\"properties\":{"
                        + "\"category\":{\"type\":\"string\",\"enum\":[\"goal\",\"condition\",\"preference\",\"history\",\"training_style\",\"recovery\",\"recommendation\"],\"description\":\"类别\"},"
                        + "\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":\"关键词标签\"},"
                        + "\"occurred_at\":{\"type\":\"string\",\"description\":\"事件日期 yyyy-MM-dd,仅 EPISODIC\"},"
                        + "\"importance\":{\"type\":\"string\",\"enum\":[\"high\",\"medium\",\"low\"],\"description\":\"重要性,仅 EPISODIC\"}"
                        + "}}"
                        + "},\"required\":[\"memory_type\",\"content\"]}",
                false
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        Map<String, Object> args = call == null || call.getArguments() == null
                ? Map.of() : call.getArguments();

        // 1. 校验 memory_type
        String memoryType = asString(args.get("memory_type"));
        if (StrUtil.isBlank(memoryType)) {
            return ToolResult.error("memory_type 参数必填");
        }
        memoryType = memoryType.toUpperCase();
        if (!ALLOWED_TYPES.contains(memoryType)) {
            return ToolResult.error("memory_type 取值仅支持: " + ALLOWED_TYPES);
        }

        // 2. 校验 content
        String content = asString(args.get("content"));
        if (StrUtil.isBlank(content)) {
            return ToolResult.error("content 参数必填");
        }
        if (content.length() > 500) {
            return ToolResult.error("content 长度不能超过 500 字符");
        }

        // 3. 解析可选 metadata：空对象 {} 或 null 视为未提供，直接跳过
        Object metadataRaw = args.get("metadata");
        String metadataJson = null;
        if (metadataRaw instanceof Map<?, ?> metadataMap && !metadataMap.isEmpty()) {
            metadataJson = JSONUtil.toJsonStr(metadataMap);
        }

        Long userId = authenticatedUser.getUserId();
        Long sessionId = authenticatedUser.getSessionId();

        // 4. 写入（MemoryWriter 内部基于 content_hash 去重，重复返回 false）
        MemoryWriteRequest req = MemoryWriteRequest.builder()
                .userId(userId)
                .memoryType(memoryType)
                .content(content)
                .metadataJson(metadataJson)
                .source(sessionId != null ? "manual:session:" + sessionId : "manual")
                .build();
        boolean written;
        try {
            written = memoryWriter.writeIfNotIgnored(req);
        } catch (Exception e) {
            log.error("主动记忆写入失败 userId={} type={} content={}", userId, memoryType, content, e);
            return ToolResult.error("记忆写入失败: " + e.getMessage());
        }

        // 5. 同步更新提取计数器，避免本轮 finishWithAnswer 再次触发自动提取（已主动记录过）
        if (sessionId != null) {
            long currentUserMsgCount = countSessionUserMessages(sessionId);
            if (currentUserMsgCount > 0) {
                memoryExtractCounter.markExtracted(sessionId, currentUserMsgCount);
            }
        }

        String summary = written
                ? "已记录" + memoryType + "记忆: " + truncate(content, 80)
                : "该记忆已存在（内容重复），跳过写入";
        return ToolResult.ok(summary, Map.of(
                "memoryType", memoryType,
                "content", content,
                "written", written
        ));
    }

    /**
     * 统计当前会话内累计的 user 消息数，与 AgentLoopExecutor.finishWithAnswer 中的统计口径一致。
     * 本工具在 Agent Loop 工具调用阶段执行，此时本条 user 消息已落盘。
     */
    private long countSessionUserMessages(Long sessionId) {
        try {
            List<ChatMessage> messages = chatSessionService.listMessagesBySessionIdOnly(sessionId);
            if (messages == null || messages.isEmpty()) {
                return 0L;
            }
            return messages.stream()
                    .filter(m -> "user".equalsIgnoreCase(m.getRole()))
                    .count();
        } catch (Exception e) {
            log.warn("统计会话 user 消息数失败 sessionId={}", sessionId, e);
            return 0L;
        }
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }
}
