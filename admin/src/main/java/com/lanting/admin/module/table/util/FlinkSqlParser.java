package com.lanting.admin.module.table.util;

import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.flink.sql.parser.ddl.SqlTableColumn;
import org.apache.flink.sql.parser.ddl.table.SqlCreateTable;
import org.apache.flink.sql.parser.impl.FlinkSqlParserImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Flink SQL DDL 解析工具类。
 *
 * <p>从 {@code CREATE TABLE (...) WITH (...)} 语句中提取表名、连接器、字段列表和分区字段。
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
    public static Table parseCreateTable(String ddl) {
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

    private static Table extractTable(SqlCreateTable createTable) {
        String tableName = createTable.getName().names.getFirst();
        Map<String, String> properties = extractProperties(createTable);
        String connector = properties.get("connector");
        List<String> partitionKeys = createTable.getPartitionKeyList();
        List<Column> columns = extractColumns(createTable);

        return new Table(tableName, createTable.isIfNotExists(), properties, connector, partitionKeys, columns);
    }

    private static Map<String, String> extractProperties(SqlCreateTable createTable) {
        Map<String, String> properties = createTable.getProperties();
        return properties == null ? Collections.emptyMap() : Map.copyOf(properties);
    }

    private static List<Column> extractColumns(SqlCreateTable createTable) {
        org.apache.calcite.sql.SqlNodeList columnList = createTable.getColumnList();
        if (columnList == null || columnList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Column> columns = new ArrayList<>();
        int ordinal = 0;
        for (SqlNode node : columnList) {
            if (node instanceof SqlTableColumn.SqlRegularColumn regular) {
                String name = regular.getName().names.getFirst();
                String type = regular.getType().toString();
                String comment = regular.getComment();
                columns.add(new Column(name, type, comment, ordinal++));
            }
        }
        return columns;
    }

    /**
     * DDL 解析结果。
     */
    public record Table(
            String tableName,
            boolean ifNotExists,
            Map<String, String> properties,
            String connector,
            List<String> partitionKeys,
            List<Column> columns
    ) {

        public Table {
            partitionKeys = partitionKeys == null ? Collections.emptyList() : List.copyOf(partitionKeys);
            columns = columns == null ? Collections.emptyList() : List.copyOf(columns);
            properties = properties == null ? Collections.emptyMap() : Map.copyOf(properties);
        }
    }

    /**
     * 字段定义。
     */
    public record Column(String name, String type, String comment, int ordinal) {

    }
}
