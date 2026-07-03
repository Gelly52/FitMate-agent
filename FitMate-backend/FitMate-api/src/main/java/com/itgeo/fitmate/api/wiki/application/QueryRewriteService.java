package com.itgeo.fitmate.api.wiki.application;

public interface QueryRewriteService {

    /**
     * 基于 Wiki 检索结果改写用户问题，用于提升 RAG 召回率。
     *
     * @param question    原问题
     * @param wikiContent Wiki 检索结果拼接的文本
     * @return 改写后的 query（若 Wiki 为空或无需改写，返回原问题）
     */
    String rewrite(String question, String wikiContent);
}
