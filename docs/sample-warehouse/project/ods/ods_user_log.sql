-- ============================================================
-- ODS 层：用户行为日志接入
-- Source: Kafka topic `ods_user_log`
-- Target: Hudi 表 `ods_user_log` (COPY_ON_WRITE)
-- ============================================================

CREATE TEMPORARY TABLE kafka_source (
    event_id  STRING,
    user_id   BIGINT,
    event_type STRING,
    page      STRING,
    ts        BIGINT,
    extra     STRING,
    `dt`      STRING  -- 事件日期 yyyy-MM-dd，由 Flink 计算列生成
) WITH (
    'connector' = 'kafka',
    'topic'     = 'ods_user_log',
    'properties.bootstrap.servers' = 'kafka:9092',
    'properties.group.id' = 'lanting_ods_group',
    'format'    = 'json',
    'scan.startup.mode' = 'latest-offset'
);

-- 写入 Hudi
INSERT INTO `hudi`.`ods_user_log`
SELECT
    event_id,
    user_id,
    event_type,
    page,
    ts,
    extra,
    FROM_UNIXTIME(ts / 1000, 'yyyy-MM-dd') AS dt
FROM kafka_source;
