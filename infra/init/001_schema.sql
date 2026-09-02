-- 集团智能资源预约与管控中台 - 建表脚本（MySQL 8）
-- 数据由 Java 端 DataInitializer 在空表时播种演示数据。

CREATE TABLE IF NOT EXISTS sys_user (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    username   VARCHAR(64)  NOT NULL,
    password   VARCHAR(128) NOT NULL COMMENT 'BCrypt 密文',
    name       VARCHAR(64)  NOT NULL,
    department VARCHAR(64)  DEFAULT NULL,
    position   VARCHAR(64)  DEFAULT NULL COMMENT '职级：高管/部门正职/高级工程师/实习生等',
    role       VARCHAR(32)  DEFAULT NULL,
    created_at DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统用户';

CREATE TABLE IF NOT EXISTS resource (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    type         VARCHAR(64)  NOT NULL COMMENT '资源类型编码，如 EXEC_SHUTTLE',
    name         VARCHAR(128) NOT NULL,
    description  VARCHAR(512) DEFAULT NULL,
    total_stock  INT          NOT NULL,
    reserve_date DATE         DEFAULT NULL COMMENT '班车类按天记录',
    status       VARCHAR(16)  DEFAULT NULL,
    created_at   DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_resource_type (type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '可预约资源';

CREATE TABLE IF NOT EXISTS reservation_order (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    request_id  VARCHAR(64)  NOT NULL COMMENT '幂等键',
    user_id     BIGINT       NOT NULL,
    resource_id BIGINT       NOT NULL,
    seat_no     VARCHAR(32)  DEFAULT NULL,
    status      VARCHAR(32)  NOT NULL,
    created_at  DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_request (request_id),
    UNIQUE KEY uk_order_user_resource (user_id, resource_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '预约订单';

CREATE TABLE IF NOT EXISTS permission_rule (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    resource_type       VARCHAR(64)  NOT NULL,
    required_positions  VARCHAR(255) DEFAULT NULL COMMENT '逗号分隔的职级',
    allowed_departments VARCHAR(255) DEFAULT NULL COMMENT '逗号分隔的部门，空=不限',
    note                VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_type (resource_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '确定性权限规则';
