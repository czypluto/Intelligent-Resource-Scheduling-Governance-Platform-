-- 铁路购票系统 - 操作审计表（供人工接管 / 追责 / 事故回溯）
CREATE TABLE IF NOT EXISTS action_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT       DEFAULT NULL COMMENT '操作人 id',
    module     VARCHAR(32)  NOT NULL COMMENT '模块：ticket/rail/auth',
    action     VARCHAR(64)  NOT NULL COMMENT '动作：buy/pay/cancel/createTrip...',
    request_id VARCHAR(64)  DEFAULT NULL COMMENT '幂等键/请求标识',
    detail     VARCHAR(2000) DEFAULT NULL COMMENT '入参/结果摘要(JSON)',
    success    TINYINT      NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    err        VARCHAR(500) DEFAULT NULL COMMENT '失败信息',
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_action_user (user_id, created_at),
    KEY idx_action_req (request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作审计';
