-- ============================================================
-- DWD 层：用户安装维度表
-- Hudi COPY_ON_WRITE 表
-- 字段由 ads_user_retention.sql 用法推断（install_dt / user_id）
-- ============================================================

CREATE TABLE IF NOT EXISTS dim_user_install (
    user_id    BIGINT,
    install_dt STRING,
    channel    STRING
) PARTITIONED BY (install_dt)
WITH (
    'connector' = 'hudi',
    'hoodie.table.type' = 'COPY_ON_WRITE',
    'hoodie.datasource.write.recordkey.field' = 'user_id',
    'hoodie.datasource.write.precombine.field' = 'install_dt'
);
