-- ============================================================
-- ODS 层建表 DDL
-- Hudi COPY_ON_WRITE 表
-- ============================================================

-- ods_user_log：用户行为日志
CREATE TABLE IF NOT EXISTS ods_user_log (
    event_id   STRING,
    user_id    BIGINT,
    event_type STRING,
    page       STRING,
    ts         BIGINT,
    extra      STRING,
    dt         STRING
) USING HUDI
PARTITIONED BY (dt)
OPTIONS (
    'hoodie.table.type' = 'COPY_ON_WRITE',
    'hoodie.datasource.write.recordkey.field' = 'event_id',
    'hoodie.datasource.write.precombine.field' = 'ts',
    'hoodie.datasource.write.hive_style_partitioning' = 'true'
);

-- ods_order：订单流水
CREATE TABLE IF NOT EXISTS ods_order (
    order_id    BIGINT,
    user_id     BIGINT,
    amount      DOUBLE,
    status      STRING,
    create_time BIGINT,
    update_time BIGINT,
    dt          STRING
) USING HUDI
PARTITIONED BY (dt)
OPTIONS (
    'hoodie.table.type' = 'COPY_ON_WRITE',
    'hoodie.datasource.write.recordkey.field' = 'order_id',
    'hoodie.datasource.write.precombine.field' = 'update_time'
);
