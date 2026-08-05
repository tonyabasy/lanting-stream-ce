package com.lanting.admin.module.table.model;

/**
 * 列定义（physical / metadata / computed 统一模型）。
 *
 * <p>三类列在 DDL 中交错排列，解析后放入同一 List 保持原始顺序（ordinal 全局递增）。
 * 字段按列类型使用：
 * <ul>
 *   <li>physical：name / type / comment / ordinal</li>
 *   <li>metadata：name / type / comment / ordinal / metadataFrom / virtual</li>
 *   <li>computed：name / expr / ordinal（无 type）</li>
 * </ul>
 *
 * @author wangzhao
 */
public record FlinkColumn(
        /** 列名（column_name） */
        String name,
        /** 列类型（physical/metadata 有值；computed 为 null） */
        String type,
        /** 列注释（column_comment） */
        String comment,
        /** 列顺序（0-based，全局递增，保持 DDL 顺序） */
        int ordinal,
        /** 列类型：physical / metadata / computed */
        String columnType,
        /** 仅 metadata 列：METADATA FROM 'xxx' 的来源 */
        String metadataFrom,
        /** 仅 metadata 列：是否 VIRTUAL */
        boolean virtual,
        /** 仅 computed 列：计算表达式（AS 后部分） */
        String expr) {

    /** 列类型常量 */
    public static final String TYPE_PHYSICAL = "physical";
    public static final String TYPE_METADATA = "metadata";
    public static final String TYPE_COMPUTED = "computed";

    /** 物理列构造 */
    public static FlinkColumn physical(String name, String type, String comment, int ordinal) {
        return new FlinkColumn(name, type, comment, ordinal, TYPE_PHYSICAL, null, false, null);
    }

    /** 元数据列构造 */
    public static FlinkColumn metadata(String name, String type, String comment, int ordinal,
                                       String metadataFrom, boolean virtual) {
        return new FlinkColumn(name, type, comment, ordinal, TYPE_METADATA, metadataFrom, virtual, null);
    }

    /** 计算列构造（无 type） */
    public static FlinkColumn computed(String name, String expr, int ordinal) {
        return new FlinkColumn(name, null, null, ordinal, TYPE_COMPUTED, null, false, expr);
    }
}
