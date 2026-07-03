package com.itgeo.fitmate.common.exception;

/**
 * Shared error codes for API responses and service exceptions.
 */
public enum ErrorCode {
    OK(0, "ok"),
    BAD_REQUEST(400, "bad request"),
    UNAUTHORIZED(401, "unauthorized"),
    INTERNAL_ERROR(500, "internal error");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
