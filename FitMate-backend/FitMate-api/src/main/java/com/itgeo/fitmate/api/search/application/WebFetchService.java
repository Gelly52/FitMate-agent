package com.itgeo.fitmate.api.search.application;

import com.itgeo.fitmate.api.search.dto.WebFetchResult;

/**
 * 网页抓取服务契约。
 * 负责抓取指定 URL 的 HTML 并提取正文纯文本。
 */
public interface WebFetchService {

    /**
     * 抓取指定 URL 并提取正文。
     *
     * @param url 目标 URL（仅支持 http/https）
     * @return 抓取并解析后的结果
     * @throws IllegalArgumentException URL 非法或不支持
     * @throws RuntimeException 抓取或解析失败
     */
    WebFetchResult fetch(String url);
}
