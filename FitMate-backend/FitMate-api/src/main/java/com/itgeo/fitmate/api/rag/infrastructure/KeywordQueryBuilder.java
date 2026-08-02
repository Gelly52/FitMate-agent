package com.itgeo.fitmate.api.rag.infrastructure;

import cn.hutool.core.util.StrUtil;
import com.huaban.analysis.jieba.JiebaSegmenter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关键词检索查询串构建工具。
 *
 * RediSearch 对查询串中的多个词项默认按 AND 组合，自然语言长句分词后
 * 几乎不可能有文档同时命中全部词项，导致关键词路零召回。
 * 此工具将查询语句用 jieba 切词、过滤单字与标点后拼为 OR 查询，
 * 依靠 RediSearch 自身的 TF-IDF 打分让命中词项多的 chunk 排前。
 */
public final class KeywordQueryBuilder {

    private static final JiebaSegmenter SEGMENTER = new JiebaSegmenter();

    private KeywordQueryBuilder() {
    }

    /**
     * 将自然语言问题转为 RediSearch OR 查询片段，如 "深蹲|膝盖|角度"。
     * 无有效词项时返回空串（调用方应跳过关键词检索）。
     */
    public static String toOrQuery(String question) {
        if (StrUtil.isBlank(question)) {
            return "";
        }
        Set<String> tokens = SEGMENTER.sentenceProcess(question.trim()).stream()
                .map(String::trim)
                // 单字大多是"我/的/怎"之类的噪声；保留长度>=2 的词与英文/数字词项
                .filter(t -> t.length() >= 2 || t.matches("[A-Za-z0-9]+"))
                .filter(t -> t.chars().anyMatch(Character::isLetterOrDigit))
                .map(KeywordQueryBuilder::escapeToken)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return String.join("|", tokens);
    }

    /** 转义 RediSearch 查询语法中的特殊字符 */
    private static String escapeToken(String token) {
        return token.replaceAll("([,.<>{}\\[\\]\"':;!@#$%^&*()\\-+=~|/\\\\?])", "");
    }
}
