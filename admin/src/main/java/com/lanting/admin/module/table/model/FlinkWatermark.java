package com.lanting.admin.module.table.model;

/**
 * 水位线定义（WATERMARK FOR field AS expr）。
 *
 * @author wangzhao
 */
public record FlinkWatermark(
        /** 事件时间字段（rowtime_column_name） */
        String field,
        /** 水位线策略表达式（watermark_strategy_expression） */
        String expr) {

}
