-- 铁路购票系统 - 购票业务表（MySQL 8）
-- 说明：sys_user 在原 001 已建，此处 ALTER 补年龄/证件字段。
--       已有库上重复执行若报 duplicate column 可忽略（仅首次需要）。

ALTER TABLE sys_user
    ADD COLUMN age     INT         DEFAULT NULL COMMENT '年龄' AFTER name,
    ADD COLUMN gender  VARCHAR(8)  DEFAULT NULL COMMENT '性别' AFTER age,
    ADD COLUMN id_type VARCHAR(16) DEFAULT '身份证' COMMENT '证件类型' AFTER gender,
    ADD COLUMN id_no   VARCHAR(32) DEFAULT NULL COMMENT '证件号' AFTER id_type;

-- 列车（车底）
CREATE TABLE IF NOT EXISTS train (
    id      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    code    VARCHAR(16) NOT NULL COMMENT '车次号，如 G101',
    name    VARCHAR(64) DEFAULT NULL COMMENT '列车名，如 北京南-上海虹桥',
    kind    VARCHAR(16) DEFAULT NULL COMMENT '车型 G/D/K...',
    PRIMARY KEY (id),
    UNIQUE KEY uk_train_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '列车';

-- 车站
CREATE TABLE IF NOT EXISTS station (
    id   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    code VARCHAR(16) NOT NULL COMMENT '站码，如 BJP',
    name VARCHAR(64) NOT NULL COMMENT '站名，如 北京南',
    PRIMARY KEY (id),
    UNIQUE KEY uk_station_name (name),
    UNIQUE KEY uk_station_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '车站';

-- 车次经停时刻（某列车固定停站序列）
CREATE TABLE IF NOT EXISTS train_stop (
    id          BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键',
    train_id    BIGINT     NOT NULL COMMENT '列车 id',
    seq         INT        NOT NULL COMMENT '停站顺序 0,1,2..',
    station_id  BIGINT     NOT NULL COMMENT '车站 id',
    arrive_time TIME       DEFAULT NULL COMMENT '到达时刻',
    depart_time TIME       DEFAULT NULL COMMENT '出发时刻',
    PRIMARY KEY (id),
    UNIQUE KEY uk_train_seq (train_id, seq),
    KEY idx_stop_train (train_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '列车经停站';

-- 车次运行日（具体哪天开行）
CREATE TABLE IF NOT EXISTS trip (
    id          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    train_id    BIGINT NOT NULL COMMENT '列车 id',
    travel_date DATE   NOT NULL COMMENT '开行日期',
    status      VARCHAR(16) DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trip_train_date (train_id, travel_date),
    KEY idx_trip_date (travel_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '车次运行日';

-- 车次席别票价与总席位
CREATE TABLE IF NOT EXISTS trip_class (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    trip_id     BIGINT        NOT NULL COMMENT '运行日 id',
    seat_class  VARCHAR(32)   NOT NULL COMMENT '席别：商务座/一等座/二等座/硬座...',
    price_cents BIGINT        NOT NULL COMMENT '全程票价（分）',
    total_seats INT           NOT NULL COMMENT '总席位',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_trip_class (trip_id, seat_class)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '车次席别库存';

-- 购票订单
CREATE TABLE IF NOT EXISTS ticket_order (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_id     VARCHAR(64)  NOT NULL COMMENT '幂等键',
    order_no       VARCHAR(32)  NOT NULL COMMENT '订单号',
    user_id        BIGINT       NOT NULL COMMENT '下单用户 id',
    trip_id        BIGINT       NOT NULL COMMENT '车次运行日 id',
    seat_class     VARCHAR(32)  NOT NULL COMMENT '席别',
    from_station   VARCHAR(64)  NOT NULL COMMENT '出发站名快照',
    to_station     VARCHAR(64)  NOT NULL COMMENT '到达站名快照',
    passenger_name VARCHAR(64)  NOT NULL COMMENT '乘车人姓名快照',
    passenger_id   VARCHAR(32)  DEFAULT NULL COMMENT '乘车人证件号快照',
    price_cents    BIGINT       NOT NULL COMMENT '成交票价（分）',
    status         VARCHAR(16)  NOT NULL COMMENT 'PENDING/PAID/CANCELLED/EXPIRED',
    created_at     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    paid_at        DATETIME     DEFAULT NULL COMMENT '支付时间',
    cancelled_at   DATETIME     DEFAULT NULL COMMENT '取消/退票时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_torder_request (request_id),
    UNIQUE KEY uk_torder_no (order_no),
    KEY idx_torder_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '购票订单';

-- 常用联系人
CREATE TABLE IF NOT EXISTS contact (
    id      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT      NOT NULL COMMENT '所属用户 id',
    name    VARCHAR(64) NOT NULL COMMENT '乘车人姓名',
    id_type VARCHAR(16) DEFAULT '身份证' COMMENT '证件类型',
    id_no   VARCHAR(32) DEFAULT NULL COMMENT '证件号',
    phone   VARCHAR(32) DEFAULT NULL COMMENT '手机号',
    PRIMARY KEY (id),
    KEY idx_contact_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '常用联系人';

-- 旧库迁移说明：本文件面向全新初始化。既有旧库若缺 update_time / idx_trip_date，
-- 已通过手工执行等价 ALTER 补齐（见历史提交 0333e0f），无需重复执行。

-- 儿童票前置规则支撑（旧库重复执行报 duplicate column 可忽略）
ALTER TABLE contact ADD COLUMN age INT DEFAULT NULL COMMENT '年龄(儿童票判定)' AFTER phone;
ALTER TABLE ticket_order
    ADD COLUMN ticket_type VARCHAR(16) NOT NULL DEFAULT 'ADULT' COMMENT 'ADULT/CHILD/STUDENT' AFTER seat_class,
    ADD COLUMN passenger_age INT DEFAULT NULL COMMENT '乘车人年龄快照(儿童票判定)' AFTER passenger_id;

ALTER TABLE sys_user ADD COLUMN student TINYINT(1) DEFAULT NULL COMMENT '是否全日制在校学生' AFTER role;
