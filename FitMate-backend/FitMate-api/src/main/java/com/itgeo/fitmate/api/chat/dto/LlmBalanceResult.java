package com.itgeo.fitmate.api.chat.dto;

import java.util.List;
import lombok.Data;

/**
 * DeepSeek 余额查询结果。
 */
@Data
public class LlmBalanceResult {
    /** 账户是否有余额可供 API 调用 */
    private Boolean isAvailable;
    /** 各币种余额明细 */
    private List<LlmBalanceInfo> balanceInfos;
}
