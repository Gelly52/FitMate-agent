package com.itgeo.fitmate.api.search.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 联网搜索结果。
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class SearchResult {

    /** 结果标题。 */
    private String title;
    /** 结果链接。 */
    private String url;
    /** 结果摘要内容。 */
    private String content;
    /** 搜索引擎返回的相关度分。 */
    private double score;

}
