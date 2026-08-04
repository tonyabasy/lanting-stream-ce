-- ============================================================
-- ODS 层：订单流水接入
-- Source: Kafka topic `ods_order`
-- Target: Hudi 表 `ods_order` (COPY_ON_WRITE)
-- ============================================================

CREATE TEMPORARY TABLE kafka_order (
    order_id    BIGINT,
    user_id     BIGINT,
    amount      DOUBLE,
    status      STRING,
    create_time BIGINT,
    update_time BIGINT
) WITH (
    'connector' = 'kafka',
    'topic'     = 'ods_order',
    'properties.bootstrap.servers' = 'kafka:9092',
    'properties.group.id' = 'lanting_ods_group',
    'format'    = 'json',
    'scan.startup.mode' = 'latest-offset'
);

INSERT INTO `hudi`.`ods_order`
SELECT
    order_id,
    user_id,
    amount,
    status,
    create_time,
    update_time,
    FROM_UNIXTIME(create_time / 1000, 'yyyy-MM-dd') AS dt
FROM kafka_order;
