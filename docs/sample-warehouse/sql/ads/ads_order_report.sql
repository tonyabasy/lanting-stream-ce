-- ============================================================
-- ADS 层：订单实时报表（ClickHouse）
-- Source: DWS 层 Kafka Topic / Hudi 快照导出
-- ============================================================

-- ClickHouse 建表（由 DDL 负责），此处为查询示例

-- 当日实时订单概览
SELECT
    toDate(now())                                              AS report_dt,
    count()                                                    AS total_orders,
    sum(amount)                                                AS gmv,
    avg(amount)                                                AS avg_price,
    uniqExact(user_id)                                         AS uv,
    sum(amount) / uniqExact(user_id)                           AS arpu
FROM dwd_order_detail
WHERE dt = today();

-- 按小时趋势
SELECT
    toHour(toDateTime(create_time / 1000))                     AS hour,
    count()                                                    AS orders,
    sum(amount)                                                AS amount
FROM dwd_order_detail
WHERE dt = today()
GROUP BY hour
ORDER BY hour;
