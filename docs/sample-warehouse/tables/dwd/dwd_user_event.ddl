-- ============================================================
-- DWD 层：用户事件明细
-- Hudi COPY_ON_WRITE 表
-- ============================================================

CREATE TABLE IF NOT EXISTS dwd_user_event (
    event_id   STRING,
    user_id    BIGINT,
    event_type STRING,
    page       STRING,
    ts         BIGINT,
    event_name STRING,
    province   STRING,
    dt         STRING
) USING HUDI
PARTITIONED BY (dt)
OPTIONS (
    'hoodie.table.type' = 'COPY_ON_WRITE',
    'hoodie.datasource.write.recordkey.field' = 'event_id',
    'hoodie.datasource.write.precombine.field' = 'ts'
);
