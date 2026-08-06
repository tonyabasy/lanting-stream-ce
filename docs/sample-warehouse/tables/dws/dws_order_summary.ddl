-- ============================================================
-- DWS 层：订单日汇总
-- Hudi MERGE_ON_READ 表（写入优化）
-- ============================================================

CREATE TABLE IF NOT EXISTS dws_order_summary (
    dt                STRING,
    order_count       BIGINT,
    user_count        BIGINT,
    total_amount      DOUBLE,
    avg_order_amount  DOUBLE,
    completed_amount  DOUBLE,
    completed_count   BIGINT,
    completion_rate   DOUBLE
) PARTITIONED BY (dt)
WITH (
    'connector' = 'hudi',
    'hoodie.table.type' = 'MERGE_ON_READ',
    'hoodie.datasource.write.recordkey.field' = 'dt',
    'hoodie.datasource.write.precombine.field' = 'dt'
);
