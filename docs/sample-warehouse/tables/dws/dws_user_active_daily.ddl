-- ============================================================
-- DWS 层：用户日活跃汇总（备用）
-- Hudi MERGE_ON_READ 表
-- ============================================================

CREATE TABLE IF NOT EXISTS dws_user_active_daily (
    dt       STRING,
    dau      BIGINT,
    new_user BIGINT,
    active_user BIGINT
) USING HUDI
PARTITIONED BY (dt)
OPTIONS (
    'hoodie.table.type' = 'MERGE_ON_READ',
    'hoodie.datasource.write.recordkey.field' = 'dt'
);
