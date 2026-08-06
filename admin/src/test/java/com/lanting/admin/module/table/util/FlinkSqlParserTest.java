package com.lanting.admin.module.table.util;

import com.lanting.admin.module.table.model.FlinkColumn;
import com.lanting.admin.module.table.model.FlinkTable;
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

        FlinkTable metadata = FlinkSqlParser.parseCreateTable(ddl);

        assertThat(metadata.tableName()).isEqualTo("user_log");
        assertThat(metadata.connector()).isEqualTo("kafka");
        assertThat(metadata.partitionKeys()).isEmpty();

        assertThat(metadata.ifNotExists()).isFalse();
        assertThat(metadata.properties())
                .containsEntry("connector", "kafka")
                .containsEntry("topic", "user_log_topic")
                .containsEntry("format", "json");

        List<FlinkColumn> columns = metadata.columns();
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

        FlinkTable metadata = FlinkSqlParser.parseCreateTable(ddl);

        assertThat(metadata.tableName()).isEqualTo("orders");
        assertThat(metadata.ifNotExists()).isTrue();
        assertThat(metadata.connector()).isEqualTo("hive");
        assertThat(metadata.partitionKeys()).containsExactly("dt");
        assertThat(metadata.properties()).containsEntry("hive-conf-dir", "/etc/hive/conf");

        List<FlinkColumn> columns = metadata.columns();
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

        FlinkTable metadata = FlinkSqlParser.parseCreateTable(ddl);

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

        FlinkTable metadata = FlinkSqlParser.parseCreateTable(ddl);

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

        FlinkTable metadata = FlinkSqlParser.parseCreateTable(ddl);

        assertThat(metadata.tableName()).isEqualTo("doris_sink");
        assertThat(metadata.ifNotExists()).isFalse();
        assertThat(metadata.connector()).isEqualTo("doris");
        assertThat(metadata.properties()).containsEntry("fenodes", "127.0.0.1:8030");
        assertThat(metadata.columns()).hasSize(3);
    }

    /**
     * 全字段 DDL：覆盖支持矩阵（docs/backend/editor-tables-design.md）全部子句。
     * 本次计划支持：physical / metadata / WATERMARK / PRIMARY KEY / COMMENT /
     * PARTITIONED BY / DISTRIBUTED / WITH Options；computed 提取但表单只读。
     */
    @Test
    void shouldParseAllSupportedClauses() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS ods_order (
                    order_id    BIGINT COMMENT '订单ID',
                    user_id     BIGINT,
                    amount      DOUBLE,
                    status      STRING,
                    create_time TIMESTAMP(3),
                    dt          STRING,
                    kafka_partition STRING METADATA FROM 'partition',
                    gmv         AS amount * (1 - refund_rate),
                    WATERMARK FOR create_time AS create_time - INTERVAL '5' SECOND,
                    PRIMARY KEY (order_id) NOT ENFORCED
                ) COMMENT '订单流水表'
                DISTRIBUTED BY (order_id) INTO 4 BUCKETS
                PARTITIONED BY (dt)
                WITH (
                    'connector' = 'hudi',
                    'hoodie.table.type' = 'COPY_ON_WRITE'
                )
                """;

        FlinkTable metadata = FlinkSqlParser.parseCreateTable(ddl);

        // 基本信息
        assertThat(metadata.tableName()).isEqualTo("ods_order");
        assertThat(metadata.ifNotExists()).isTrue();
        assertThat(metadata.comment()).isEqualTo("订单流水表");
        assertThat(metadata.connector()).isEqualTo("hudi");

        // WITH Options
        assertThat(metadata.properties())
                .containsEntry("connector", "hudi")
                .containsEntry("hoodie.table.type", "COPY_ON_WRITE");

        // PARTITIONED BY
        assertThat(metadata.partitionKeys()).containsExactly("dt");

        // DISTRIBUTED
        assertThat(metadata.distribution()).isNotNull();
        assertThat(metadata.distribution().by()).containsExactly("order_id");
        assertThat(metadata.distribution().buckets()).isEqualTo(4L);

        // PRIMARY KEY
        assertThat(metadata.primaryKeys()).containsExactly("order_id");

        // WATERMARK（Calcite unparse 会给标识符加反引号）
        assertThat(metadata.watermark()).isNotNull();
        assertThat(metadata.watermark().field()).isEqualTo("create_time");
        assertThat(metadata.watermark().expr()).isEqualTo("`create_time` - INTERVAL '5' SECOND");

        // 列（单 list，8 列按 DDL 交错顺序：6 physical + 1 metadata + 1 computed）
        List<FlinkColumn> columns = metadata.columns();
        assertThat(columns).hasSize(8);
        assertField(columns.get(0), "order_id", "BIGINT", "订单ID", 0);
        assertField(columns.get(4), "create_time", "TIMESTAMP(3)", null, 4);
        assertField(columns.get(5), "dt", "STRING", null, 5);
        // metadata 列在第 7 位（kafka_partition），computed 列在最后（gmv）
        assertThat(columns.get(6).columnType()).isEqualTo(FlinkColumn.TYPE_METADATA);
        assertThat(columns.get(6).name()).isEqualTo("kafka_partition");
        assertThat(columns.get(7).columnType()).isEqualTo(FlinkColumn.TYPE_COMPUTED);
        assertThat(columns.get(7).name()).isEqualTo("gmv");
        // 物理列无元数据信息
        assertThat(columns.get(0).metadataFrom()).isNull();
        assertThat(columns.get(0).virtual()).isFalse();

        // metadata 列（单 list 中按 columnType 过滤）
        List<FlinkColumn> metadataColumns = metadata.columns().stream()
                .filter(c -> FlinkColumn.TYPE_METADATA.equals(c.columnType()))
                .toList();
        assertThat(metadataColumns).hasSize(1);
        FlinkColumn partition = metadataColumns.getFirst();
        assertThat(partition.name()).isEqualTo("kafka_partition");
        assertThat(partition.type()).isEqualTo("STRING");
        assertThat(partition.metadataFrom()).isEqualTo("partition");
        assertThat(partition.virtual()).isFalse();

        // computed 列（提取但不编辑；Calcite unparse 给标识符加反引号）
        List<FlinkColumn> computed = metadata.columns().stream()
                .filter(c -> FlinkColumn.TYPE_COMPUTED.equals(c.columnType()))
                .toList();
        assertThat(computed).hasSize(1);
        assertThat(computed.getFirst().name()).isEqualTo("gmv");
        assertThat(computed.getFirst().expr()).isEqualTo("`amount` * (1 - `refund_rate`)");
    }

    @Test
    void shouldParseDdlWithTrailingSemicolon() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS t_semi (
                    id   BIGINT,
                    name STRING
                ) WITH (
                    'connector' = 'hudi',
                    'table.type' = 'COPY_ON_WRITE'
                );
                """;

        FlinkTable metadata = FlinkSqlParser.parseCreateTable(ddl);

        assertThat(metadata.tableName()).isEqualTo("t_semi");
        assertThat(metadata.connector()).isEqualTo("hudi");
        assertThat(metadata.ifNotExists()).isTrue();
        assertThat(metadata.columns()).hasSize(2);
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

    /**
     * round-trip：parseCreateTable → createTableToString → 再 parse，结构应一致。
     * 覆盖支持矩阵全部子句（physical/metadata/computed/watermark/PK/comment/distributed/partition/with）。
     */
    @Test
    void shouldRoundTripSerializeThenParse() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS ods_order (
                    order_id        BIGINT COMMENT '订单ID',
                    user_id         BIGINT,
                    amount          DOUBLE,
                    create_time     TIMESTAMP(3),
                    kafka_partition STRING METADATA FROM 'partition',
                    gmv             AS amount * (1 - refund_rate),
                    WATERMARK FOR create_time AS create_time - INTERVAL '5' SECOND,
                    PRIMARY KEY (order_id) NOT ENFORCED
                ) COMMENT '订单流水表'
                DISTRIBUTED BY (order_id) INTO 4 BUCKETS
                PARTITIONED BY (dt)
                WITH (
                    'connector' = 'hudi',
                    'hoodie.table.type' = 'COPY_ON_WRITE'
                )
                """;

        FlinkTable parsed = FlinkSqlParser.parseCreateTable(ddl);
        String serialized = FlinkSqlParser.createTableToString(parsed);
        FlinkTable reparsed = FlinkSqlParser.parseCreateTable(serialized);

        // 基本信息
        assertThat(reparsed.tableName()).isEqualTo("ods_order");
        assertThat(reparsed.ifNotExists()).isTrue();
        assertThat(reparsed.connector()).isEqualTo("hudi");
        assertThat(reparsed.comment()).isEqualTo("订单流水表");

        // 分区/分布/主键/水位线
        assertThat(reparsed.partitionKeys()).containsExactly("dt");
        assertThat(reparsed.distribution()).isNotNull();
        assertThat(reparsed.distribution().by()).containsExactly("order_id");
        assertThat(reparsed.distribution().buckets()).isEqualTo(4L);
        assertThat(reparsed.primaryKeys()).containsExactly("order_id");
        assertThat(reparsed.watermark()).isNotNull();
        assertThat(reparsed.watermark().field()).isEqualTo("create_time");

        // 三类列（单 list，按 columnType 过滤）
        assertThat(reparsed.columns()).hasSize(6);
        assertThat(reparsed.columns().stream()
                .filter(c -> FlinkColumn.TYPE_METADATA.equals(c.columnType()))
                .map(FlinkColumn::name)).containsExactly("kafka_partition");
        assertThat(reparsed.columns().stream()
                .filter(c -> FlinkColumn.TYPE_METADATA.equals(c.columnType()))
                .map(FlinkColumn::metadataFrom)).containsExactly("partition");
        assertThat(reparsed.columns().stream()
                .filter(c -> FlinkColumn.TYPE_COMPUTED.equals(c.columnType()))
                .map(FlinkColumn::name)).containsExactly("gmv");

        // WITH 属性
        assertThat(reparsed.properties()).containsEntry("hoodie.table.type", "COPY_ON_WRITE");
    }

    private void assertField(FlinkColumn field, String name, String type, String comment, int ordinal) {
        assertThat(field.name()).isEqualTo(name);
        assertThat(field.type()).isEqualTo(type);
        assertThat(field.comment()).isEqualTo(comment);
        assertThat(field.ordinal()).isEqualTo(ordinal);
    }
}
