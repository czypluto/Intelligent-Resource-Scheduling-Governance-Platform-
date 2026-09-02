package com.group.resv.common;

/**
 * 统一响应。code=0 表示成功；非 0 对应业务/HTTP 错误码。
 */
public record ApiResult<T>(int code, String msg, T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data);
    }

    public static ApiResult<Void> ok() {
        return new ApiResult<>(0, "ok", null);
    }

    public static <T> ApiResult<T> fail(int code, String msg) {
        return new ApiResult<>(code, msg, null);
    }
}
