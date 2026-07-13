# ETL Process

## ODS → DWD

```sql
-- 1. 去重（基于 event_id 取第一条）
INSERT INTO dwd_user_event
SELECT *
FROM (
    SELECT *,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY ts) AS rn
    FROM ods_user_log
) WHERE rn = 1;
```

## DWD → DWS

```sql
-- 按天 + 维度聚合
INSERT INTO dws_order_summary
SELECT
    dt,
    category_id,
    COUNT(DISTINCT order_id)   AS order_count,
    SUM(amount)                AS total_amount,
    COUNT(DISTINCT user_id)    AS user_count
FROM dwd_order_detail
GROUP BY dt, category_id;
```

## DWS → ADS

ADS 层通过 ClickHouse Routine Load 从 Kafka Topic 消费 DWS 层变更数据，或通过批任务从 Hudi 导出。
