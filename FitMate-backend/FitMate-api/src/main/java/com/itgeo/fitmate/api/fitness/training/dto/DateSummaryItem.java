package com.itgeo.fitmate.api.fitness.training.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按日期展示的摘要项。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DateSummaryItem {
    /** 日期字符串，格式 yyyy-MM-dd。 */
    private String date;
    /** 当日摘要内容。 */
    private String summary;
}
