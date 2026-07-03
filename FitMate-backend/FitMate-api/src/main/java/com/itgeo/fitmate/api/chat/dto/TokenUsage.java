package com.itgeo.fitmate.api.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 用量快照。
 * <p>
 * 字段语义：
 * 1. promptTokens / completionTokens / totalTokens / reasoningTokens 始终反映「最近一次 LLM 调用」的用量；
 *    其中 completionTokens 已包含 reasoningTokens（与 DeepSeek/OpenAI 口径一致）。
 * 2. cumulativeTotalTokens 为 Agent 多轮循环中的累计 total，用于计费统计；
 *    单轮场景下等于 totalTokens。
 * 3. contextWindow 为当前模型的上下文窗口大小，供前端计算圆环比例。
 * 4. cacheHitTokens / cacheMissTokens 反映 DeepSeek 上下文硬盘缓存命中情况。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsage {

    /** 最近一次 LLM 调用的 prompt token 数。 */
    private Integer promptTokens;
    /** 最近一次 LLM 调用的 completion token 数（含 reasoning）。 */
    private Integer completionTokens;
    /** 最近一次 LLM 调用的 total token 数。 */
    private Integer totalTokens;
    /** 最近一次 LLM 调用的 reasoning token 数（已计入 completion）。 */
    private Integer reasoningTokens;
    /** Agent 多轮循环累计 total token 数，单轮场景等于 totalTokens。 */
    private Integer cumulativeTotalTokens;
    /** 当前模型的上下文窗口大小。 */
    private Integer contextWindow;
    /** 本次请求 prompt 中命中硬盘缓存的 token 数（DeepSeek KV Cache）。 */
    private Integer cacheHitTokens;
    /** 本次请求 prompt 中未命中硬盘缓存的 token 数（DeepSeek KV Cache）。 */
    private Integer cacheMissTokens;

    /**
     * 累加一次 LLM 调用的 usage：
     * 1. 保留最近一次的 prompt/completion/total/reasoning/cache 快照；
     * 2. cumulativeTotalTokens 在原有累计基础上加上本次 total。
     *
     * @param delta       本次 LLM 调用的 usage
     * @param windowSize  当前模型上下文窗口大小
     */
    public void accumulate(TokenUsage delta, Integer windowSize) {
        if (delta == null) {
            return;
        }
        this.promptTokens = delta.promptTokens;
        this.completionTokens = delta.completionTokens;
        this.totalTokens = delta.totalTokens;
        this.reasoningTokens = delta.reasoningTokens;
        this.cacheHitTokens = delta.cacheHitTokens;
        this.cacheMissTokens = delta.cacheMissTokens;
        int prevCumulative = this.cumulativeTotalTokens == null ? 0 : this.cumulativeTotalTokens;
        int deltaTotal = delta.totalTokens == null ? 0 : delta.totalTokens;
        this.cumulativeTotalTokens = prevCumulative + deltaTotal;
        this.contextWindow = windowSize;
    }
}
