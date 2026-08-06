-- ============================================================
-- ODS 层：订单流水
-- Hudi COPY_ON_WRITE 表
-- ============================================================

CREATE TABLE IF NOT EXISTS ods_order (
    order_id    BIGINT,
    user_id     BIGINT,
    amount      DOUBLE,
    status      STRING,
    create_time BIGINT,
    update_time BIGINT,
    dt          STRING
) PARTITIONED BY (dt)
WITH (
    'connector' = 'hudi',
    'hoodie.table.type' = 'COPY_ON_WRITE',
    'hoodie.datasource.write.recordkey.field' = 'order_id',
    'hoodie.datasource.write.precombine.field' = 'update_time'
);
