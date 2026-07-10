package com.itgeo.fitmate.api.chat.dto;

import lombok.Data;

/**
 * 会话重命名请求体。
 */
@Data
public class ChatSessionRenameRequest {
    /** 新标题。 */
    private String title;
}
