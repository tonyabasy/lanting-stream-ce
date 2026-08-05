package com.lanting.admin.module.table.model;

import java.util.Collections;
import java.util.List;

/**
 * 分布定义（DISTRIBUTED BY ... INTO n BUCKETS）。
 *
 * @author wangzhao
 */
public record FlinkDistribution(
        /** 分布字段（bucket_column_name 列表） */
        List<String> by,
        /** 桶数（INTO n BUCKETS，可能为 null） */
        Long buckets) {

    public FlinkDistribution {
        by = by == null ? Collections.emptyList() : List.copyOf(by);
    }
}
