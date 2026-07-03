package com.itgeo.fitmate.api.chat.dto;

import lombok.Data;

/**
 * DeepSeek 模型列表项。
 */
@Data
public class LlmModelItem {
    private String id;
    private String ownedBy;
}
