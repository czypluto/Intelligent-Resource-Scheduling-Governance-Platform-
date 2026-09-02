package com.group.resv.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResult<Void>> handleBiz(BizException e) {
        int code = e.getCode();
        int status = code >= 400 && code <= 599 ? code : 500;
        return ResponseEntity.status(status).body(ApiResult.fail(code, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleOther(Exception e) {
        log.error("未捕获异常", e);
        return ResponseEntity.status(500).body(ApiResult.fail(500, "系统内部错误"));
    }
}
