package com.itgeo.fitmate.api.chat.dto;

import lombok.Data;

/**
 * 回滚请求体。
 */
@Data
public class RollbackRequest {
    /** 会话ID。 */
    private Long sessionId;
    /** 机器人消息ID，用于定位要回滚的位置。 */
    private String botMsgId;
}
