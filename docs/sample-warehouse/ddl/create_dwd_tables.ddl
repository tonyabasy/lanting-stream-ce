-- ============================================================
-- DWD 层建表 DDL
-- Hudi COPY_ON_WRITE 表
-- ============================================================

-- dim_user：用户维度表
CREATE TABLE IF NOT EXISTS dim_user (
    user_id   BIGINT,
    username  STRING,
    province  STRING,
    city      STRING,
    age       INT,
    gender    STRING,
    dt        STRING
) USING HUDI
PARTITIONED BY (dt)
OPTIONS (
    'hoodie.table.type' = 'COPY_ON_WRITE',
    'hoodie.datasource.write.recordkey.field' = 'user_id',
    'hoodie.datasource.write.precombine.field' = 'dt'
);

-- dwd_user_event：用户事件明细
CREATE TABLE IF NOT EXISTS dwd_user_event (
    event_id   STRING,
    user_id    BIGINT,
    event_type STRING,
    page       STRING,
    ts         BIGINT,
    event_name STRING,
    province   STRING,
    dt         STRING
) USING HUDI
PARTITIONED BY (dt)
OPTIONS (
    'hoodie.table.type' = 'COPY_ON_WRITE',
    'hoodie.datasource.write.recordkey.field' = 'event_id',
    'hoodie.datasource.write.precombine.field' = 'ts'
);

-- dwd_order_detail：订单明细
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
) USING HUDI
PARTITIONED BY (dt)
OPTIONS (
    'hoodie.table.type' = 'COPY_ON_WRITE',
    'hoodie.datasource.write.recordkey.field' = 'order_id',
    'hoodie.datasource.write.precombine.field' = 'update_time'
);
