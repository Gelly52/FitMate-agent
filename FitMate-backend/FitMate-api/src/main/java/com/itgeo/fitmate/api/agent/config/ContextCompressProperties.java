package com.itgeo.fitmate.api.agent.config;

import lombok.Data;

/**
 * 上下文压缩配置，绑定 fitmate.agent.context-compress。
 */
@Data
public class ContextCompressProperties {
    /** 是否启用压缩。 */
    private Boolean enabled = true;
    /** 压缩阈值占 contextWindow 的比例。 */
    private Double thresholdRatio = 0.8;
    /** 压缩时保留最近 N 条不压缩（约 N/2 轮）。 */
    private Integer keepRecentCount = 6;
    /** 摘要输出 max_tokens。 */
    private Integer summaryMaxTokens = 1024;
}
