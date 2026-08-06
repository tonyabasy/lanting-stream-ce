-- ============================================================
-- DWD 层：订单明细
-- Hudi COPY_ON_WRITE 表
-- ============================================================

CREATE TABLE IF NOT EXISTS dwd_order_detail (
    order_id     BIGINT,
    user_id      BIGINT,
    amount       DOUBLE,
    status       STRING,
    status_name  STRING,
    amount_level STRING,
    create_time  BIGINT,
    update_time  BIGINT,
    dt           STRING
) PARTITIONED BY (dt)
WITH (
    'connector' = 'hudi',
    'hoodie.table.type' = 'COPY_ON_WRITE',
    'hoodie.datasource.write.recordkey.field' = 'order_id',
    'hoodie.datasource.write.precombine.field' = 'update_time'
);
