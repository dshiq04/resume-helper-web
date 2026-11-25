package com.jobtrack.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结构：{@code {"code": 200, "message": "success", "data": ...}}。
 *
 * <p>code 使用 HTTP 状态码语义：成功固定为 200，失败为对应的错误码；
 * message 成功时为 success，失败时为可直接展示的错误信息；
 * 失败时不返回 data。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    public static final int SUCCESS_CODE = 200;
    public static final String SUCCESS_MESSAGE = "success";

    private int code;
    private String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
