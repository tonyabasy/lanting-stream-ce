-- ============================================================
-- ADS 层：订单实时报表（ClickHouse）
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
