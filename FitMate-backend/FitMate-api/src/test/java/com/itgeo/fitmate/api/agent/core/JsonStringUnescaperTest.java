package com.itgeo.fitmate.api.agent.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonStringUnescaperTest {

    @Test
    void unescape_basicSequences() {
        StringBuilder sb = new StringBuilder("hello\\nworld\\ttab\\rcr");
        assertEquals("hello\nworld\ttab\rcr", JsonStringUnescaper.unescape(sb));
    }

    @Test
    void unescape_quotesAndBackslash() {
        StringBuilder sb = new StringBuilder("say \\\"hi\\\" and \\\\path");
        assertEquals("say \"hi\" and \\path", JsonStringUnescaper.unescape(sb));
    }

    @Test
    void unescape_unicodeSequence() {
        StringBuilder sb = new StringBuilder("\\u4e2d\\u6587");
        assertEquals("中文", JsonStringUnescaper.unescape(sb));
    }

    @Test
    void unescape_forwardSlash() {
        StringBuilder sb = new StringBuilder("http:\\/\\/example.com");
        assertEquals("http://example.com", JsonStringUnescaper.unescape(sb));
    }

    @Test
    void unescape_noEscapeChars_returnsAsIs() {
        StringBuilder sb = new StringBuilder("plain text");
        assertEquals("plain text", JsonStringUnescaper.unescape(sb));
    }

    @Test
    void unescape_emptyBuilder_returnsEmpty() {
        StringBuilder sb = new StringBuilder("");
        assertEquals("", JsonStringUnescaper.unescape(sb));
    }

    @Test
    void retainIncompleteEscape_trailingSingleBackslash() {
        StringBuilder pending = new StringBuilder("hello\\");
        String result = JsonStringUnescaper.retainIncompleteEscape("hello", pending);
        assertEquals("hello", result);
        assertEquals("\\", pending.toString());
    }

    @Test
    void retainIncompleteEscape_trailingPartialUnicode() {
        StringBuilder pending = new StringBuilder("hello\\u4e2");
        String result = JsonStringUnescaper.retainIncompleteEscape("hello", pending);
        assertEquals("hello", result);
        assertEquals("\\u4e2", pending.toString());
    }

    @Test
    void retainIncompleteEscape_noTrailingEscape_returnsAll() {
        StringBuilder pending = new StringBuilder("hello");
        String result = JsonStringUnescaper.retainIncompleteEscape("hello", pending);
        assertEquals("hello", result);
        assertEquals("", pending.toString());
    }

    @Test
    void retainIncompleteEscape_completeEscape_returnsAll() {
        StringBuilder pending = new StringBuilder("hello\\n");
        String result = JsonStringUnescaper.retainIncompleteEscape("hello\n", pending);
        assertEquals("hello\n", result);
        assertEquals("", pending.toString());
    }
}
