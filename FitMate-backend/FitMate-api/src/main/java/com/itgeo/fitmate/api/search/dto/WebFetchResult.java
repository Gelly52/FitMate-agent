package com.itgeo.fitmate.api.search.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * web.fetch 工具抓取网页后的结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebFetchResult {

    /** 抓取的目标 URL。 */
    private String url;
    /** 页面标题。 */
    private String title;
    /** 提取后的正文纯文本（已去除脚本/导航等噪音，可能被截断）。 */
    private String content;
    /** 正文长度（截断前原始长度，供模型判断是否完整）。 */
    private Integer contentLength;
    /** 是否因超长被截断。 */
    private Boolean truncated;
}
