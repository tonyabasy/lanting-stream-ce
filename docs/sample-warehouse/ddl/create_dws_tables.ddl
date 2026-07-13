-- ============================================================
-- DWS 层建表 DDL
-- Hudi MERGE_ON_READ 表（写入优化）
-- ============================================================

-- dws_order_summary：订单日汇总
CREATE TABLE IF NOT EXISTS dws_order_summary (
    dt                STRING,
    order_count       BIGINT,
    user_count        BIGINT,
    total_amount      DOUBLE,
    avg_order_amount  DOUBLE,
    completed_amount  DOUBLE,
    completed_count   BIGINT,
    completion_rate   DOUBLE
) USING HUDI
PARTITIONED BY (dt)
OPTIONS (
    'hoodie.table.type' = 'MERGE_ON_READ',
    'hoodie.datasource.write.recordkey.field' = 'dt',
    'hoodie.datasource.write.precombine.field' = 'dt'
);

-- dws_user_active_daily：用户日活跃汇总（备用）
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

-- ============================================================
-- ClickHouse ADS 层建表
-- ============================================================

CREATE TABLE IF NOT EXISTS ads_order_report (
    report_dt     Date,
    total_orders  UInt64,
    gmv           Float64,
    avg_price     Float64,
    uv            UInt64,
    arpu          Float64
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(report_dt)
ORDER BY report_dt;
