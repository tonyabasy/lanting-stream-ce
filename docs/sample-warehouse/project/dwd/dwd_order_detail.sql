-- ============================================================
-- DWD 层：订单明细（维度退化、字段补齐）
-- Source: Hudi 表 `ods_order`
-- Target: Hudi 表 `dwd_order_detail` (COPY_ON_WRITE)
-- ============================================================

INSERT INTO `hudi`.`dwd_order_detail`
SELECT
    o.order_id,
    o.user_id,
    o.amount,
    o.status,
    CASE o.status
        WHEN 'CREATED'     THEN '已创建'
        WHEN 'PAID'        THEN '已支付'
        WHEN 'SHIPPED'     THEN '已发货'
        WHEN 'COMPLETED'   THEN '已完成'
        WHEN 'CANCELLED'   THEN '已取消'
        ELSE o.status
    END AS status_name,
    -- 业务分类（金额区间）
    CASE
        WHEN o.amount < 50       THEN '小额(<50)'
        WHEN o.amount < 200      THEN '中额(50-200)'
        WHEN o.amount < 1000     THEN '大额(200-1000)'
        ELSE '超大额(>1000)'
    END AS amount_level,
    o.create_time,
    o.update_time,
    o.dt
FROM `hudi`.`ods_order` o;
