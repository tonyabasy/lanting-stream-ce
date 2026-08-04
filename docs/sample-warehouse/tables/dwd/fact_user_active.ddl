-- ============================================================
-- DWD 层：用户活跃事实表
-- Hudi COPY_ON_WRITE 表
-- 字段由 ads_user_retention.sql 用法推断（user_id / active_dt）
-- ============================================================

CREATE TABLE IF NOT EXISTS fact_user_active (
    user_id   BIGINT,
    active_dt STRING
) USING HUDI
PARTITIONED BY (active_dt)
OPTIONS (
    'hoodie.table.type' = 'COPY_ON_WRITE',
    'hoodie.datasource.write.recordkey.field' = 'user_id',
    'hoodie.datasource.write.precombine.field' = 'active_dt'
);
