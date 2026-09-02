package com.group.resv.common;

/**
 * 业务异常。code 同时用作 HTTP 状态码与业务码。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
