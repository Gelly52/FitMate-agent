package com.itgeo.fitmate.api.agent.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上下文压缩 SSE 事件载荷。
 * <p>
 * event=context_compressing：压缩进行中，仅 event 字段有意义。
 * event=context_compressed：压缩完成，携带压缩元数据。
 * event=context_compress_failed：压缩失败，携带 reason。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompressEventPayload {
    /** 事件名：context_compressing / context_compressed / context_compress_failed。 */
    private String event;
    /** 被压缩的消息条数（仅 compressed 事件）。 */
    private Integer compressedCount;
    /** 压缩前 prompt_tokens（仅 compressed 事件）。 */
    private Integer tokenBefore;
    /** 压缩后摘要估算 token（仅 compressed 事件）。 */
    private Integer tokenAfter;
    /** 当前模型上下文窗口大小，前端用于刷新圆环。 */
    private Integer contextWindow;
    /** 失败原因（仅 failed 事件）。 */
    private String reason;
}
