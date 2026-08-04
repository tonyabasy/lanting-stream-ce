-- ============================================================
-- DWS 层：订单日汇总
-- Source: Hudi 表 `dwd_order_detail`
-- Target: Hudi 表 `dws_order_summary` (MOR)
-- ============================================================

INSERT INTO `hudi`.`dws_order_summary`
SELECT
    dt,
    COUNT(DISTINCT order_id)                    AS order_count,
    COUNT(DISTINCT user_id)                     AS user_count,
    SUM(amount)                                 AS total_amount,
    AVG(amount)                                 AS avg_order_amount,
    SUM(CASE WHEN status = 'COMPLETED'
        THEN amount ELSE 0 END)                 AS completed_amount,
    COUNT(DISTINCT CASE WHEN status = 'COMPLETED'
        THEN order_id END)                      AS completed_count,
    ROUND(
        COUNT(DISTINCT CASE WHEN status = 'COMPLETED'
            THEN order_id END) * 1.0 /
        NULLIF(COUNT(DISTINCT order_id), 0),
        4
    ) AS completion_rate
FROM `hudi`.`dwd_order_detail`
GROUP BY dt;
