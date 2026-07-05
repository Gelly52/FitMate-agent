package com.itgeo.fitmate.api.agent.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinalAnswerStreamStateTest {

    @Test
    void standardPath_actionFirst_thenFinalAnswer() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        // 模拟 LLM 流式输出：先 action，后 final_answer
        String r1 = state.onNext("{\"action\":\"final\",", "{\"action\":\"final\",");
        assertEquals("", r1);
        assertFalse(state.hasStreamed());

        String r2 = state.onNext("\"final_answer\":\"hello", "{\"action\":\"final\",\"final_answer\":\"hello");
        assertEquals("hello", r2);
        assertTrue(state.hasStreamed());

        String r3 = state.onNext(" world\"", "{\"action\":\"final\",\"final_answer\":\"hello world\"");
        assertEquals(" world", r3);
    }

    @Test
    void fieldOrderReversed_finalAnswerFirst() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        String r1 = state.onNext("{\"final_answer\":\"hello", "{\"final_answer\":\"hello");
        assertEquals("hello", r1);
        assertTrue(state.hasStreamed());
    }

    @Test
    void toolCallPath_neverStreams() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        String r = state.onNext("{\"action\":\"tool_call\",\"tool_name\":\"kb.search\"",
                "{\"action\":\"tool_call\",\"tool_name\":\"kb.search\"");
        assertEquals("", r);
        assertFalse(state.hasStreamed());
    }

    @Test
    void escapedNewline_inFinalAnswer() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        state.onNext("{\"action\":\"final\",\"final_answer\":\"line1",
                "{\"action\":\"final\",\"final_answer\":\"line1");
        String r = state.onNext("\\nline2\"", "{\"action\":\"final\",\"final_answer\":\"line1\\nline2\"");
        assertEquals("\nline2", r);
    }

    @Test
    void escapedQuote_inFinalAnswer() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        state.onNext("{\"action\":\"final\",\"final_answer\":\"say ",
                "{\"action\":\"final\",\"final_answer\":\"say ");
        String r = state.onNext("\\\"hi\\\"\"", "{\"action\":\"final\",\"final_answer\":\"say \\\"hi\\\"\"");
        assertEquals("\"hi\"", r);
    }

    @Test
    void emptyFinalAnswer_hasStreamedTrue_noContent() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        String r1 = state.onNext("{\"action\":\"final\",\"final_answer\":\"\"",
                "{\"action\":\"final\",\"final_answer\":\"\"");
        assertEquals("", r1);
        assertTrue(state.hasStreamed());
    }

    @Test
    void markdownCodeBlockWrapper_fallsBackToFailed() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        // LLM 输出带 markdown 代码块包裹
        String r = state.onNext("```json\n{\"action\":\"final\"",
                "```json\n{\"action\":\"final\"");
        assertEquals("", r);
        assertFalse(state.hasStreamed());
        // 状态机进入 FAILED，后续不再尝试
        String r2 = state.onNext(",\"final_answer\":\"hello\"}", "```json\n{\"action\":\"final\",\"final_answer\":\"hello\"}");
        assertEquals("", r2);
    }

    @Test
    void trailingFieldAfterFinalAnswer_stopsAtClosingQuote() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        state.onNext("{\"action\":\"final\",\"final_answer\":\"hello",
                "{\"action\":\"final\",\"final_answer\":\"hello");
        // 遇到闭合 " 后，后续字段不推送
        String r = state.onNext("\",\"reason\":\"done\"}", "{\"action\":\"final\",\"final_answer\":\"hello\",\"reason\":\"done\"}");
        assertEquals("", r);
    }

    @Test
    void incompleteEscapeAtChunkBoundary_retained() {
        FinalAnswerStreamState state = new FinalAnswerStreamState();
        state.onNext("{\"action\":\"final\",\"final_answer\":\"hello",
                "{\"action\":\"final\",\"final_answer\":\"hello");
        // 末尾单个 \ 不完整
        String r1 = state.onNext("\\", "{\"action\":\"final\",\"final_answer\":\"hello\\");
        assertEquals("", r1);
        // 下一块补全 \n
        String r2 = state.onNext("nworld\"", "{\"action\":\"final\",\"final_answer\":\"hello\\nworld\"");
        assertEquals("\nworld", r2);
    }
}
