-- ============================================================
-- DWD 层：用户维度表
-- Hudi COPY_ON_WRITE 表
-- ============================================================

CREATE TABLE IF NOT EXISTS dim_user (
    user_id   BIGINT,
    username  STRING,
    province  STRING,
    city      STRING,
    age       INT,
    gender    STRING,
    dt        STRING
) PARTITIONED BY (dt)
WITH (
    'connector' = 'hudi',
    'hoodie.table.type' = 'COPY_ON_WRITE',
    'hoodie.datasource.write.recordkey.field' = 'user_id',
    'hoodie.datasource.write.precombine.field' = 'dt'
);
