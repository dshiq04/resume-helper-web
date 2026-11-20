package com.jobtrack.common.exception;

import lombok.Getter;

/**
 * 可直接映射为 HTTP 响应的业务异常。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int status;
    private final String code;

    public BusinessException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(400, code, message);
    }

    public static BusinessException unauthorized(String code, String message) {
        return new BusinessException(401, code, message);
    }

    public static BusinessException forbidden(String code, String message) {
        return new BusinessException(403, code, message);
    }

    public static BusinessException notFound(String code, String message) {
        return new BusinessException(404, code, message);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(409, code, message);
    }

    public static BusinessException internal(String code, String message) {
        return new BusinessException(500, code, message);
    }
}
