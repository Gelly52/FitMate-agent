package com.itgeo.fitmate.api.search.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * SearXng 搜索响应。
 */
@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class SearXngResponse {

    /** 原始查询词。 */
    private String query;
    /** 搜索结果列表。 */
    private List<SearchResult> results;

}
