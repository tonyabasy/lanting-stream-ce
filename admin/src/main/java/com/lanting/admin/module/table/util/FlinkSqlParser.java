package com.lanting.admin.module.table.util;

import com.lanting.admin.module.table.model.*;
import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.flink.sql.parser.ddl.SqlDistribution;
import org.apache.flink.sql.parser.ddl.SqlTableColumn;
import org.apache.flink.sql.parser.ddl.constraint.SqlTableConstraint;
import org.apache.flink.sql.parser.ddl.table.SqlCreateTable;
import org.apache.flink.sql.parser.impl.FlinkSqlParserImpl;

import java.util.*;

/**
 * Flink SQL DDL 解析工具类。
 *
 * <p>从 {@code CREATE TABLE (...) WITH (...)} 语句中提取表名、连接器、字段列表、分区、
 * 水位线、主键、分布等全部子句。解析结果模型见 {@code model/} 包（DdlTable 等）。
 *
 * @author wangzhao
 */
public final class FlinkSqlParser {

    private FlinkSqlParser() {
        // utility class
    }

    /**
     * 解析 Flink SQL DDL。
     *
     * @param ddl DDL 文本
     * @return 结构化元数据
     * @throws IllegalArgumentException 解析失败或不支持的语句类型
     */
    public static FlinkTable parseCreateTable(String ddl) {
        if (ddl == null || ddl.isBlank()) {
            throw new IllegalArgumentException("DDL content is empty");
        }

        try {
            SqlParser.Config config = SqlParser.config()
                    .withParserFactory(FlinkSqlParserImpl.FACTORY)
                    .withUnquotedCasing(Casing.UNCHANGED);
            SqlParser parser = SqlParser.create(ddl, config);
            SqlNode sqlNode = parser.parseQuery();

            if (!(sqlNode instanceof SqlCreateTable createTable)) {
                throw new IllegalArgumentException(
                        "Only CREATE TABLE DDL is supported, got: " + sqlNode.getClass().getSimpleName());
            }

            return extractTable(createTable);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse DDL: " + e.getMessage(), e);
        }
    }

    private static FlinkTable extractTable(SqlCreateTable createTable) {
        String tableName = createTable.getName().names.getFirst();
        LinkedHashMap<String, String> properties = extractProperties(createTable);
        String connector = properties.get("connector");
        List<String> partitionKeys = createTable.getPartitionKeyList();
        List<FlinkColumn> columns = extractColumns(createTable);

        return new FlinkTable(
                tableName,
                createTable.isIfNotExists(),
                columns,
                extractWatermark(createTable),
                extractPrimaryKeys(createTable),
                createTable.getComment(),
                extractDistribution(createTable),
                partitionKeys,
                properties,
                connector
        );
    }

    private static LinkedHashMap<String, String> extractProperties(SqlCreateTable createTable) {
        Map<String, String> properties = createTable.getProperties();
        if (properties == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(properties);
    }

    private static List<FlinkColumn> extractColumns(SqlCreateTable createTable) {
        SqlNodeList columnList = createTable.getColumnList();
        if (columnList == null || columnList.isEmpty()) {
            return Collections.emptyList();
        }

        // 三类列按 DDL 顺序放入同一 List（ordinal 全局递增，保持原始交错顺序）
        List<FlinkColumn> columns = new ArrayList<>();
        int ordinal = 0;
        for (SqlNode node : columnList) {
            if (node instanceof SqlTableColumn.SqlRegularColumn rc) {
                columns.add(FlinkColumn.physical(rc.getName().names.getFirst(),
                        rc.getType().toString(), rc.getComment(), ordinal));
            } else if (node instanceof SqlTableColumn.SqlMetadataColumn mc) {
                columns.add(FlinkColumn.metadata(mc.getName().names.getFirst(),
                        mc.getType().toString(), mc.getComment(), ordinal,
                        mc.getMetadataAlias().orElse(null), mc.isVirtual()));
            } else if (node instanceof SqlTableColumn.SqlComputedColumn cc) {
                columns.add(FlinkColumn.computed(cc.getName().names.getFirst(),
                        cc.getExpr().toString(), ordinal));
            }
            ordinal++;
        }
        return columns;
    }

    /**
     * 提取主键（表级 + 列级约束兜底）。
     */
    private static List<String> extractPrimaryKeys(SqlCreateTable createTable) {
        return createTable.getFullConstraints().stream()
                .filter(SqlTableConstraint::isPrimaryKey)
                .flatMap(constraint -> Arrays.stream(constraint.getColumnNames()))
                .distinct()
                .toList();
    }

    /**
     * 提取分布（DISTRIBUTED BY ... INTO n BUCKETS）。
     */
    private static FlinkDistribution extractDistribution(SqlCreateTable createTable) {
        SqlDistribution distribution = createTable.getDistribution();
        if (distribution == null) {
            return null;
        }
        List<String> by = new ArrayList<>();
        SqlNodeList bucketColumns = distribution.getBucketColumns();
        if (bucketColumns != null) {
            for (SqlNode node : bucketColumns) {
                by.add(node.toString());
            }
        }
        Long buckets = null;
        if (distribution.getBucketCount() != null) {
            buckets = distribution.getBucketCount().getValueAs(Long.class);
        }
        return new FlinkDistribution(by, buckets);
    }

    /**
     * 提取水位线（WATERMARK FOR field AS expr）。
     */
    private static FlinkWatermark extractWatermark(SqlCreateTable createTable) {
        return createTable.getWatermark()
                .map(wm -> new FlinkWatermark(
                        wm.getEventTimeColumnName().names.getFirst(),
                        wm.getWatermarkStrategy().toString()))
                .orElse(null);
    }

    /**
     * 序列化 FlinkTable（表单数据）→ Flink DDL 文本。
     *
     * <p>与 {@link #parseCreateTable} 对称：parse 文本→结构，本方法结构→文本。
     * 子句顺序对齐 Flink 语法：`) [COMMENT] [DISTRIBUTED BY] [PARTITIONED BY] WITH`。
     *
     * @param table 表单数据（解析模型）
     * @return DDL 文本
     */
    public static String createTableToString(FlinkTable table) {
        List<String> lines = new ArrayList<>();

        String head = "CREATE TABLE " + (table.ifNotExists() ? "IF NOT EXISTS " : "") + table.tableName() + " (";
        lines.add(head);

        // 列（单 List，天然保持 DDL 顺序，无需排序）
        List<FlinkColumn> columns = table.columns();
        boolean hasWatermark = table.watermark() != null;
        boolean hasPrimaryKey = table.primaryKeys() != null && !table.primaryKeys().isEmpty();
        for (int i = 0; i < columns.size(); i++) {
            // 列后还有列/WATERMARK/PK 则需逗号
            boolean hasNext = i < columns.size() - 1 || hasWatermark || hasPrimaryKey;
            String comma = hasNext ? "," : "";
            lines.add("    " + serializeColumn(columns.get(i)) + comma);
        }

        // WATERMARK（列之后；若后无 PRIMARY KEY 则不加逗号）
        if (hasWatermark) {
            String comma = hasPrimaryKey ? "," : "";
            lines.add("    WATERMARK FOR " + table.watermark().field()
                    + " AS " + stripBackticks(table.watermark().expr()) + comma);
        }

        // PRIMARY KEY（列之后）
        if (hasPrimaryKey) {
            lines.add("    PRIMARY KEY (" + String.join(", ", table.primaryKeys()) + ") NOT ENFORCED");
        }
        lines.add(")");

        // COMMENT
        if (table.comment() != null && !table.comment().isBlank()) {
            lines.add("COMMENT '" + escapeSql(table.comment().trim()) + "'");
        }

        // DISTRIBUTED（在 PARTITIONED 之前，Flink 语法顺序）
        if (table.distribution() != null && !table.distribution().by().isEmpty()) {
            long buckets = table.distribution().buckets() == null ? 1 : table.distribution().buckets();
            lines.add("DISTRIBUTED BY (" + String.join(", ", table.distribution().by())
                    + ") INTO " + buckets + " BUCKETS");
        }

        // PARTITIONED BY
        if (table.partitionKeys() != null && !table.partitionKeys().isEmpty()) {
            lines.add("PARTITIONED BY (" + String.join(", ", table.partitionKeys()) + ")");
        }

        // WITH 属性（connector 必填置顶）
        LinkedHashMap<String, String> props = new LinkedHashMap<>();
        if (table.connector() != null && !table.connector().isBlank()) {
            props.put("connector", table.connector().trim());
        }
        if (table.properties() != null) {
            table.properties().forEach((k, v) -> {
                if (k != null && v != null && !k.isBlank() && !v.isBlank()) {
                    props.put(k.trim(), v.trim());
                }
            });
        }
        lines.add("WITH (");
        int propCount = props.size();
        int idx = 0;
        for (var entry : props.entrySet()) {
            String comma = idx < propCount - 1 ? "," : "";
            lines.add("    '" + escapeSql(entry.getKey()) + "' = '" + escapeSql(entry.getValue()) + "'" + comma);
            idx++;
        }
        lines.add(")");

        return String.join("\n", lines);
    }

    /**
     * 单列序列化：physical/metadata 可编辑，computed 原样 expr
     */
    private static String serializeColumn(FlinkColumn c) {
        if (FlinkColumn.TYPE_COMPUTED.equals(c.columnType())) {
            // Calcite unparse 会给标识符加反引号，重新生成 DDL 时去掉
            return c.name() + " AS " + stripBackticks(c.expr());
        }
        String name = c.name();
        String type = c.type() == null ? "" : c.type();
        String comment = c.comment() != null && !c.comment().isBlank()
                ? " COMMENT '" + escapeSql(c.comment().trim()) + "'" : "";
        String metadata = FlinkColumn.TYPE_METADATA.equals(c.columnType())
                ? " METADATA FROM '" + escapeSql(c.metadataFrom() == null ? "" : c.metadataFrom()) + "'"
                + (c.virtual() ? " VIRTUAL" : "")
                : "";
        return name + "  " + type + comment + metadata;
    }

    /**
     * 去掉 Calcite unparse 给标识符加的反引号（`xxx` → xxx）
     */
    private static String stripBackticks(String sql) {
        return sql.replace("`", "");
    }

    /**
     * SQL 字符串转义（单引号翻倍）
     */
    private static String escapeSql(String v) {
        return v.replace("'", "''");
    }
}
