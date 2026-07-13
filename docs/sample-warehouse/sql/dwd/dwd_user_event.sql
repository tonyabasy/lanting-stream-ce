-- ============================================================
-- DWD 层：用户事件明细（清洗 + 维表补齐）
-- Source: Hudi 表 `ods_user_log`
-- Target: Hudi 表 `dwd_user_event` (COPY_ON_WRITE)
-- ============================================================

-- 1. 读取 ODS 层最新数据
CREATE TEMPORARY VIEW ods AS
SELECT * FROM `hudi`.`ods_user_log`
WHERE `dt` = DATE_FORMAT(CURRENT_TIMESTAMP, 'yyyy-MM-dd');

-- 2. 去重：同一 event_id 保留最早到达的一条
CREATE TEMPORARY VIEW deduped AS
SELECT * FROM (
    SELECT *,
           ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY ts) AS rn
    FROM ods
) WHERE rn = 1;

-- 3. 维表关联补齐（模拟 lookup join）
-- 实际维表替换为 JDBC connector
INSERT INTO `hudi`.`dwd_user_event`
SELECT
    d.event_id,
    d.user_id,
    d.event_type,
    d.page,
    d.ts,
    CASE
        WHEN d.event_type = 'page_view'  THEN '浏览'
        WHEN d.event_type = 'add_cart'   THEN '加购'
        WHEN d.event_type = 'purchase'   THEN '下单'
        WHEN d.event_type = 'favorite'   THEN '收藏'
        ELSE '其他'
    END AS event_name,
    COALESCE(u.province, '未知') AS province,
    d.dt
FROM deduped d
LEFT JOIN `hudi`.`dim_user` u ON d.user_id = u.user_id;
