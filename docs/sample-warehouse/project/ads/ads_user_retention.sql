-- ============================================================
-- ADS 层：用户留存分析（ClickHouse）
-- ============================================================

-- 次日 / 7日 / 30日 留存
SELECT
    toDate(install_dt)                                         AS install_date,
    countDistinctIf(user_id, day_offset = 1)                   AS day_1_retained,
    countDistinctIf(user_id, day_offset = 7)                   AS day_7_retained,
    countDistinctIf(user_id, day_offset = 30)                  AS day_30_retained,
    round(day_1_retained * 100.0 / total_new_users, 2)        AS day_1_rate,
    round(day_7_retained * 100.0 / total_new_users, 2)        AS day_7_rate,
    round(day_30_retained * 100.0 / total_new_users, 2)       AS day_30_rate
FROM (
    SELECT
        a.install_dt,
        b.user_id,
        dateDiff('day', a.install_dt, b.active_dt)             AS day_offset
    FROM dim_user_install a
    INNER JOIN fact_user_active b ON a.user_id = b.user_id
)
GROUP BY install_date
ORDER BY install_date DESC
LIMIT 30;
