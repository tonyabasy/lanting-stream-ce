-- ============================================================
-- ODS 层：用户行为日志
-- Hudi COPY_ON_WRITE 表
-- ============================================================

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
