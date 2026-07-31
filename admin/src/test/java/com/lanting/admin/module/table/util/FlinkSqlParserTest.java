package com.lanting.admin.module.table.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FlinkSqlParser 单元测试。
 *
 * @author wangzhao
 */
class FlinkSqlParserTest {

    @Test
    void shouldParseKafkaDdl() {
        String ddl = """
                CREATE TABLE user_log (
                    user_id STRING,
                    event_time TIMESTAMP(3),
                    event_type STRING COMMENT '事件类型',
                    page_id STRING
                ) WITH (
                    'connector' = 'kafka',
                    'topic' = 'user_log_topic',
                    'properties.bootstrap.servers' = 'localhost:9092',
                    'format' = 'json'
                )
                """;

        FlinkSqlParser.Table metadata = FlinkSqlParser.parseCreateTable(ddl);

        assertThat(metadata.tableName()).isEqualTo("user_log");
        assertThat(metadata.connector()).isEqualTo("kafka");
        assertThat(metadata.partitionKeys()).isEmpty();

        assertThat(metadata.ifNotExists()).isFalse();
        assertThat(metadata.properties())
                .containsEntry("connector", "kafka")
                .containsEntry("topic", "user_log_topic")
                .containsEntry("format", "json");

        List<FlinkSqlParser.Column> columns = metadata.columns();
        assertThat(columns).hasSize(4);
        assertField(columns.get(0), "user_id", "STRING", null, 0);
        assertField(columns.get(1), "event_time", "TIMESTAMP(3)", null, 1);
        assertField(columns.get(2), "event_type", "STRING", "事件类型", 2);
        assertField(columns.get(3), "page_id", "STRING", null, 3);
    }

    @Test
    void shouldParseHivePartitionedDdl() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS orders (
                    order_id BIGINT,
                    user_id STRING,
                    amount DECIMAL(10, 2),
                    dt STRING
                ) PARTITIONED BY (dt)
                WITH (
                    'connector' = 'hive',
                    'hive-conf-dir' = '/etc/hive/conf'
                )
                """;

        FlinkSqlParser.Table metadata = FlinkSqlParser.parseCreateTable(ddl);

        assertThat(metadata.tableName()).isEqualTo("orders");
        assertThat(metadata.ifNotExists()).isTrue();
        assertThat(metadata.connector()).isEqualTo("hive");
        assertThat(metadata.partitionKeys()).containsExactly("dt");
        assertThat(metadata.properties()).containsEntry("hive-conf-dir", "/etc/hive/conf");

        List<FlinkSqlParser.Column> columns = metadata.columns();
        assertThat(columns).hasSize(4);
        assertField(columns.get(0), "order_id", "BIGINT", null, 0);
        assertField(columns.get(2), "amount", "DECIMAL(10, 2)", null, 2);
    }

    @Test
    void shouldParseFileSystemPartitionedDdl() {
        String ddl = """
                CREATE TABLE events (
                    event_id STRING,
                    event_time TIMESTAMP_LTZ(3),
                    region STRING
                ) PARTITIONED BY (region)
                WITH (
                    'connector' = 'filesystem',
                    'path' = 's3://bucket/events',
                    'format' = 'parquet'
                )
                """;

        FlinkSqlParser.Table metadata = FlinkSqlParser.parseCreateTable(ddl);

        assertThat(metadata.tableName()).isEqualTo("events");
        assertThat(metadata.ifNotExists()).isFalse();
        assertThat(metadata.connector()).isEqualTo("filesystem");
        assertThat(metadata.partitionKeys()).containsExactly("region");
        assertThat(metadata.properties()).containsEntry("path", "s3://bucket/events");
        assertThat(metadata.columns()).hasSize(3);
        assertField(metadata.columns().get(1), "event_time", "TIMESTAMP_LTZ(3)", null, 1);
    }

    @Test
    void shouldParseJdbcDdl() {
        String ddl = """
                CREATE TABLE products (
                    id BIGINT,
                    name VARCHAR(255),
                    price DOUBLE,
                    PRIMARY KEY (id) NOT ENFORCED
                ) WITH (
                    'connector' = 'jdbc',
                    'url' = 'jdbc:mysql://localhost:3306/db',
                    'table-name' = 'products'
                )
                """;

        FlinkSqlParser.Table metadata = FlinkSqlParser.parseCreateTable(ddl);

        assertThat(metadata.tableName()).isEqualTo("products");
        assertThat(metadata.ifNotExists()).isFalse();
        assertThat(metadata.connector()).isEqualTo("jdbc");
        assertThat(metadata.properties()).containsEntry("url", "jdbc:mysql://localhost:3306/db");
        assertThat(metadata.columns()).hasSize(3);
        assertField(metadata.columns().get(1), "name", "VARCHAR(255)", null, 1);
    }

    @Test
    void shouldParseDorisDdl() {
        String ddl = """
                CREATE TABLE doris_sink (
                    user_id BIGINT,
                    name STRING,
                    age INT
                ) WITH (
                    'connector' = 'doris',
                    'fenodes' = '127.0.0.1:8030',
                    'table.identifier' = 'db.tbl',
                    'username' = 'root',
                    'password' = ''
                )
                """;

        FlinkSqlParser.Table metadata = FlinkSqlParser.parseCreateTable(ddl);

        assertThat(metadata.tableName()).isEqualTo("doris_sink");
        assertThat(metadata.ifNotExists()).isFalse();
        assertThat(metadata.connector()).isEqualTo("doris");
        assertThat(metadata.properties()).containsEntry("fenodes", "127.0.0.1:8030");
        assertThat(metadata.columns()).hasSize(3);
    }

    @Test
    void shouldRejectEmptyDdl() {
        assertThatThrownBy(() -> FlinkSqlParser.parseCreateTable(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DDL content is empty");
    }

    @Test
    void shouldRejectNonCreateTableDdl() {
        assertThatThrownBy(() -> FlinkSqlParser.parseCreateTable("SELECT * FROM t"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only CREATE TABLE DDL is supported");
    }

    private void assertField(FlinkSqlParser.Column field, String name, String type, String comment, int ordinal) {
        assertThat(field.name()).isEqualTo(name);
        assertThat(field.type()).isEqualTo(type);
        assertThat(field.comment()).isEqualTo(comment);
        assertThat(field.ordinal()).isEqualTo(ordinal);
    }
}
