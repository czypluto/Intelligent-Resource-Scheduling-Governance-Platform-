-- 铁路购票系统 - 购票业务表（MySQL 8）
-- 说明：sys_user 在原 001 已建，此处 ALTER 补年龄/证件字段。
--       已有库上重复执行若报 duplicate column 可忽略（仅首次需要）。

ALTER TABLE sys_user
    ADD COLUMN age     INT         DEFAULT NULL COMMENT '年龄' AFTER name,
    ADD COLUMN gender  VARCHAR(8)  DEFAULT NULL COMMENT '性别' AFTER age,
    ADD COLUMN id_type VARCHAR(16) DEFAULT '身份证' AFTER gender,
    ADD COLUMN id_no   VARCHAR(32) DEFAULT NULL COMMENT '证件号' AFTER id_type;

-- 列车（车底）
CREATE TABLE IF NOT EXISTS train (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    code    VARCHAR(16) NOT NULL COMMENT '车次号，如 G101',
    name    VARCHAR(64) DEFAULT NULL COMMENT '列车名，如 北京南-上海虹桥',
    kind    VARCHAR(16) DEFAULT NULL COMMENT 'G/D/K...',
    PRIMARY KEY (id),
    UNIQUE KEY uk_train_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '列车';

-- 车站
CREATE TABLE IF NOT EXISTS station (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    code    VARCHAR(16) NOT NULL,
    name    VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_station_name (name),
    UNIQUE KEY uk_station_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '车站';

-- 车次经停时刻（某列车固定停站序列）
CREATE TABLE IF NOT EXISTS train_stop (
    id          BIGINT     NOT NULL AUTO_INCREMENT,
    train_id    BIGINT     NOT NULL,
    seq         INT        NOT NULL COMMENT '停站顺序 0,1,2..',
    station_id  BIGINT     NOT NULL,
    arrive_time TIME       DEFAULT NULL,
    depart_time TIME       DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_train_seq (train_id, seq),
    KEY idx_stop_train (train_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '列车经停站';

-- 车次运行日（具体哪天开行）
CREATE TABLE IF NOT EXISTS trip (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    train_id    BIGINT NOT NULL,
    travel_date DATE   NOT NULL,
    status      VARCHAR(16) DEFAULT 'OPEN',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trip_train_date (train_id, travel_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '车次运行日';

-- 车次席别票价与总席位
CREATE TABLE IF NOT EXISTS trip_class (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    trip_id     BIGINT        NOT NULL,
    seat_class  VARCHAR(32)   NOT NULL COMMENT '商务座/一等座/二等座/硬座...',
    price_cents BIGINT        NOT NULL COMMENT '全程票价（分）',
    total_seats INT           NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trip_class (trip_id, seat_class)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '车次席别库存';

-- 购票订单（原 reservation_order 弃用，另起）
CREATE TABLE IF NOT EXISTS ticket_order (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    request_id    VARCHAR(64)  NOT NULL COMMENT '幂等键',
    order_no      VARCHAR(32)  NOT NULL COMMENT '订单号',
    user_id       BIGINT       NOT NULL,
    trip_id       BIGINT       NOT NULL,
    seat_class    VARCHAR(32)  NOT NULL,
    from_station  VARCHAR(64)  NOT NULL,
    to_station    VARCHAR(64)  NOT NULL,
    passenger_name VARCHAR(64) NOT NULL,
    passenger_id  VARCHAR(32)  DEFAULT NULL COMMENT '乘车人证件号快照',
    price_cents   BIGINT       NOT NULL,
    status        VARCHAR(16)  NOT NULL COMMENT 'PENDING/PAID/CANCELLED/EXPIRED',
    created_at    DATETIME     DEFAULT NULL,
    paid_at       DATETIME     DEFAULT NULL,
    cancelled_at  DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_torder_request (request_id),
    UNIQUE KEY uk_torder_no (order_no),
    KEY idx_torder_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '购票订单';

-- 常用联系人
CREATE TABLE IF NOT EXISTS contact (
    id       BIGINT      NOT NULL AUTO_INCREMENT,
    user_id  BIGINT      NOT NULL,
    name     VARCHAR(64) NOT NULL,
    id_type  VARCHAR(16) DEFAULT '身份证',
    id_no    VARCHAR(32) DEFAULT NULL,
    phone    VARCHAR(32) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_contact_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '常用联系人';
