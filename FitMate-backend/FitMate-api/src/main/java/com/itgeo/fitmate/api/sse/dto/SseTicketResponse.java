package com.itgeo.fitmate.api.sse.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 建链票据响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SseTicketResponse {

    /** SSE 建链票据。 */
    private String ticket;
    /** 票据有效期，单位秒。 */
    private Long expiresInSeconds;
}
