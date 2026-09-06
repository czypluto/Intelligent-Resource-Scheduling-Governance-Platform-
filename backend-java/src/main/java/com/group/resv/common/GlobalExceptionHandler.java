package com.group.resv.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
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

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(403).body(ApiResult.fail(403, "无权限执行该操作"));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResult<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        // 过期任务/支付/退票并发改写同一订单：后到者感知冲突，提示刷新
        return ResponseEntity.status(409).body(ApiResult.fail(409, "订单状态刚被更新，请刷新后重试"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleOther(Exception e) {
        log.error("未捕获异常", e);
        return ResponseEntity.status(500).body(ApiResult.fail(500, "系统内部错误"));
    }
}
