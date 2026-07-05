package com.itgeo.fitmate.api.agent.core;

/**
 * JSON 字符串反转义工具。
 * 将 JSON 字符串值中的转义序列（如换行、制表、引号、反斜杠、Unicode）还原为实际字符。
 */
public final class JsonStringUnescaper {

    private JsonStringUnescaper() {}

    /**
     * 反转义 StringBuilder 中的 JSON 转义序列，返回反转义后的字符串。
     * 不修改入参 StringBuilder（调用方负责清理）。
     */
    public static String unescape(StringBuilder escaped) {
        if (escaped == null || escaped.length() == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder(escaped.length());
        int i = 0;
        int len = escaped.length();
        while (i < len) {
            char c = escaped.charAt(i);
            if (c != '\\' || i + 1 >= len) {
                out.append(c);
                i++;
                continue;
            }
            char next = escaped.charAt(i + 1);
            switch (next) {
                case 'n': out.append('\n'); i += 2; break;
                case 't': out.append('\t'); i += 2; break;
                case 'r': out.append('\r'); i += 2; break;
                case '"': out.append('"'); i += 2; break;
                case '\\': out.append('\\'); i += 2; break;
                case '/': out.append('/'); i += 2; break;
                case 'b': out.append('\b'); i += 2; break;
                case 'f': out.append('\f'); i += 2; break;
                case 'u':
                    if (i + 5 < len) {
                        String hex = escaped.substring(i + 2, i + 6);
                        try {
                            out.append((char) Integer.parseInt(hex, 16));
                            i += 6;
                        } catch (NumberFormatException e) {
                            out.append(c);
                            i++;
                        }
                    } else {
                        // 不完整的 unicode 转义，原样保留
                        out.append(c);
                        i++;
                    }
                    break;
                default:
                    out.append(c);
                    i++;
            }
        }
        return out.toString();
    }

    /**
     * 检查 unescape 后的字符串末尾是否对应原始 StringBuilder 中不完整的转义序列，
     * 如果是，把不完整部分回填到 pending，返回排除不完整部分的安全字符串。
     *
     * @param unescaped 已经反转义的内容（unescape 的返回值）
     * @param pending 原始累积缓冲（会被修改：清空并回填不完整转义部分）
     * @return 可安全推送的内容（不含末尾不完整转义）
     */
    public static String retainIncompleteEscape(String unescaped, StringBuilder pending) {
        if (pending == null || pending.length() == 0) {
            return unescaped == null ? "" : unescaped;
        }
        // 从末尾找最后一个未配对的 \
        String raw = pending.toString();
        int lastBackslash = -1;
        for (int i = raw.length() - 1; i >= 0; i--) {
            if (raw.charAt(i) == '\\') {
                // 检查前面是否已有偶数个连续 \（被转义的 \）
                int backslashes = 0;
                for (int j = i; j >= 0 && raw.charAt(j) == '\\'; j--) backslashes++;
                if (backslashes % 2 == 1) {
                    lastBackslash = i;
                    break;
                }
            }
        }

        if (lastBackslash < 0) {
            pending.setLength(0);
            return unescaped == null ? "" : unescaped;
        }

        String incomplete = raw.substring(lastBackslash);
        // 检查是否是完整的 unicode 转义（6 字符）
        if (incomplete.length() >= 6 && incomplete.startsWith("\\u")) {
            try {
                Integer.parseInt(incomplete.substring(2, 6), 16);
                pending.setLength(0);
                return unescaped == null ? "" : unescaped;
            } catch (NumberFormatException ignored) {}
        }
        // 检查是否是完整的两字符转义（如 \n \"）
        if (incomplete.length() == 2) {
            char esc = incomplete.charAt(1);
            if (esc == 'n' || esc == 't' || esc == 'r' || esc == '"'
                || esc == '\\' || esc == '/' || esc == 'b' || esc == 'f') {
                pending.setLength(0);
                return unescaped == null ? "" : unescaped;
            }
        }

        // 不完整：回填到 pending，从 unescaped 中减去对应字符
        pending.setLength(0);
        pending.append(incomplete);
        // unescaped 对应的"已处理"部分长度 = unescaped.length - (incomplete 对应的字符数)
        // 不完整转义在 unescaped 中表现为单个 '\' 字符
        int safeLen = unescaped == null ? 0 : unescaped.length();
        if (safeLen > 0 && unescaped.charAt(safeLen - 1) == '\\') {
            safeLen--;
        }
        return unescaped == null ? "" : unescaped.substring(0, safeLen);
    }
}
