package com.lanting.admin.module.table.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Flink DDL 解析结果模型。
 *
 * <p>字段顺序对齐 Flink 官方 CREATE TABLE 语法定义：
 * {@code table_name → 列定义 → WATERMARK → 约束 → COMMENT → DISTRIBUTED → PARTITIONED BY → WITH}。
 * 由 {@code FlinkSqlParser} 解析产出，供表单数据源（deserialize 接口）、DDL 检查等复用。
 * 列统一存于 {@code columns}（单 List，保持 DDL 原始交错顺序）。
 *
 * @author wangzhao
 */
public record FlinkTable(
        String tableName, // 表名（table_name）
        boolean ifNotExists, // 是否 IF NOT EXISTS
        List<FlinkColumn> columns, // 列定义（physical/metadata/computed 交错，保持 DDL 顺序）
        FlinkWatermark watermark, // WATERMARK 定义
        List<String> primaryKeys, // PRIMARY KEY 约束字段
        String comment, // 表级 COMMENT
        FlinkDistribution distribution, // DISTRIBUTED BY 定义
        List<String> partitionKeys, // PARTITIONED BY 分区字段
        LinkedHashMap<String, String> properties, // WITH 连接器属性（含 connector）
        String connector // 连接器类型（从 WITH 的 connector 键提取）
) {
    public static final LinkedHashMap<String, String> EMPTY_PROPERTIES = new LinkedHashMap<>();

    public FlinkTable {
        columns = columns == null ? Collections.emptyList() : List.copyOf(columns);
        primaryKeys = primaryKeys == null ? Collections.emptyList() : List.copyOf(primaryKeys);
        partitionKeys = partitionKeys == null ? Collections.emptyList() : List.copyOf(partitionKeys);
        properties = properties == null ? EMPTY_PROPERTIES : new LinkedHashMap<>(properties);
    }
}
