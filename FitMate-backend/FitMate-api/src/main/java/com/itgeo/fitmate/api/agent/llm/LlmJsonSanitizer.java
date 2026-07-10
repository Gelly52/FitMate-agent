package com.itgeo.fitmate.api.agent.llm;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.json.JSONUtil;

/**
 * LLM 输出 JSON 清洗工具。
 *
 * LLM 经常将 JSON 用 markdown code fence（```json ... ```）包裹，
 * 或在 JSON 前后附加解释性文本，导致直接 parseObj 失败。
 * 此工具统一处理这些情况，提取合法 JSON 字符串。
 */
public final class LlmJsonSanitizer {

    private LlmJsonSanitizer() {
    }

    /**
     * 清洗 LLM 输出，提取合法 JSON 字符串。
     *
     * 处理顺序：
     * 1. 去除 markdown code fence（```json ... ``` 或 ``` ... ```）
     * 2. 去除 JSON 前后的非 JSON 文本
     * 3. 尝试修复截断的 JSON（补全缺失的引号和括号）
     *
     * @param raw LLM 原始输出
     * @return 可被 JSONUtil.parseObj 解析的字符串
     */
    public static String sanitize(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "";
        }
        String text = raw.trim();

        // 1. 去除 markdown code fence
        text = stripCodeFence(text);

        // 2. 如果仍不是 JSON，尝试从文本中提取第一个 JSON 对象
        if (!text.startsWith("{") && !text.startsWith("[")) {
            text = extractFirstJsonBlock(text);
        }

        // 3. 尝试修复截断的 JSON
        if (StrUtil.isNotBlank(text) && !JSONUtil.isTypeJSON(text)) {
            text = tryRepairTruncatedJson(text);
        }

        return text;
    }

    /**
     * 去除 markdown code fence。
     * 处理 ```json、```JSON、``` 等变体，以及 fence 前后的空白文本。
     */
    private static String stripCodeFence(String text) {
        // 匹配开头的 ```json / ```JSON / ``` 等
        text = ReUtil.delFirst("(?s)^\\s*```[a-zA-Z]*\\s*", text);
        // 匹配结尾的 ```
        text = ReUtil.delLast("(?s)```\\s*$", text);
        return text.trim();
    }

    /**
     * 从混合文本中提取第一个 JSON 对象或数组。
     * 找到第一个 { 或 [，然后用括号匹配找到对应的闭合位置。
     */
    private static String extractFirstJsonBlock(String text) {
        int start = -1;
        char openChar = 0;
        char closeChar = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                start = i;
                openChar = '{';
                closeChar = '}';
                break;
            }
            if (c == '[') {
                start = i;
                openChar = '[';
                closeChar = ']';
                break;
            }
        }
        if (start < 0) {
            return text;
        }

        // 用括号深度匹配，跳过字符串内的括号
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == openChar) {
                depth++;
            } else if (c == closeChar) {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        // 括号未闭合（可能是截断），返回从 start 到末尾的内容
        return text.substring(start);
    }

    /**
     * 尝试修复截断的 JSON。
     * 当 LLM 输出因 max_tokens 截断时，JSON 可能不完整：
     * - 字符串未闭合（缺少结尾的 "）
     * - 对象未闭合（缺少 }）
     * 此方法尝试补全这些缺失的部分。
     */
    private static String tryRepairTruncatedJson(String text) {
        if (StrUtil.isBlank(text) || !text.startsWith("{")) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text);

        // 统计未闭合的字符串和括号
        boolean inString = false;
        boolean escape = false;
        int braceDepth = 0;
        int bracketDepth = 0;

        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') braceDepth++;
            else if (c == '}') braceDepth--;
            else if (c == '[') bracketDepth++;
            else if (c == ']') bracketDepth--;
        }

        // 如果字符串未闭合，补一个引号
        if (inString) {
            sb.append('"');
        }

        // 补全缺失的闭合括号
        while (bracketDepth > 0) {
            sb.append(']');
            bracketDepth--;
        }
        while (braceDepth > 0) {
            sb.append('}');
            braceDepth--;
        }

        return sb.toString();
    }
}
