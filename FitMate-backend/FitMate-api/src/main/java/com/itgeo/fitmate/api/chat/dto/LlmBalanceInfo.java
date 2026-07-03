package com.itgeo.fitmate.api.chat.dto;

import lombok.Data;

/**
 * DeepSeek 余额信息项（单一币种）。
 */
@Data
public class LlmBalanceInfo {
    /** 货币：CNY / USD */
    private String currency;
    /** 总可用余额（含赠金与充值） */
    private String totalBalance;
    /** 未过期的赠金余额 */
    private String grantedBalance;
    /** 充值余额 */
    private String toppedUpBalance;
}
