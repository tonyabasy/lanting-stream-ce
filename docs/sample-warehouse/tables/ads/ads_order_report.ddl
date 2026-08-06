-- ============================================================
-- ADS 层：订单实时报表（ClickHouse，经 JDBC connector 读写）
-- ============================================================

CREATE TABLE IF NOT EXISTS ads_order_report (
    report_dt    STRING COMMENT '报表日期',
    total_orders BIGINT,
    gmv          DOUBLE,
    avg_price    DOUBLE,
    uv           BIGINT,
    arpu         DOUBLE
) COMMENT '订单实时报表（ClickHouse）'
WITH (
    'connector' = 'jdbc',
    'url' = 'jdbc:clickhouse://localhost:8123/ads',
    'table-name' = 'ads_order_report',
    'driver' = 'com.clickhouse.jdbc.ClickHouseDriver'
);
