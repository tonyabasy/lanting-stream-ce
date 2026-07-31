package com.lanting.admin.module.table.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.lanting.admin.BaseIntegrationTest;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import com.lanting.admin.module.file.mapper.FileIndexMapper;
import com.lanting.admin.module.file.service.FileIndexService;
import com.lanting.admin.module.file.service.WorkspaceService;
import com.lanting.admin.module.table.dto.CreateTableRequest;
import com.lanting.admin.module.table.dto.UpdateTableRequest;
import com.lanting.admin.module.table.entity.ColumnIndexEntity;
import com.lanting.admin.module.table.entity.TableIndexEntity;
import com.lanting.admin.module.table.mapper.ColumnIndexMapper;
import com.lanting.admin.module.table.mapper.TableIndexMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TableController HTTP 接口端到端集成测试。
 * <p>
 * 覆盖通过 TableController 创建、更新、删除表的全链路，验证磁盘文件、文件索引、表索引、字段索引的一致性。
 *
 * @author wangzhao
 */
@DisplayName("TableController 端到端集成测试")
class TableControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private FileIndexService fileIndexService;

    @Autowired
    private FileIndexMapper fileIndexMapper;

    @Autowired
    private TableIndexMapper tableIndexMapper;

    @Autowired
    private ColumnIndexMapper columnIndexMapper;

    @Autowired
    private WorkspaceService workspaceService;

    private String token;
    private String uniquePath;
    private String tableName;

    @BeforeEach
    void setUp() {
        token = loginAsAdmin();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        uniquePath = "ddl/e2e-" + suffix;
        tableName = "e2e_table_" + suffix;
    }

    /**
     * 每个测试结束后清理本测试产生的文件、文件索引、表索引和字段索引，避免多次运行相互影响。
     */
    @AfterEach
    void tearDown() {
        // 1. 物理删除测试目录下所有文件索引，并级联删除表索引/字段索引
        List<FileIndexEntity> files = fileIndexMapper.selectList(
                new LambdaQueryWrapper<FileIndexEntity>()
                        .eq(FileIndexEntity::getPath, uniquePath)
                        .or()
                        .likeRight(FileIndexEntity::getPath, uniquePath + "/"));
        for (FileIndexEntity file : files) {
            TableIndexEntity table = tableIndexMapper.selectOne(
                    new LambdaQueryWrapper<TableIndexEntity>()
                            .eq(TableIndexEntity::getFileId, file.getId()));
            if (table != null) {
                columnIndexMapper.delete(
                        new LambdaQueryWrapper<ColumnIndexEntity>()
                                .eq(ColumnIndexEntity::getTableId, table.getId()));
                tableIndexMapper.deleteById(table.getId());
            }
        }
        List<Long> fileIds = files.stream().map(FileIndexEntity::getId).toList();
        if (!fileIds.isEmpty()) {
            fileIndexService.deletePhysicallyByIds(fileIds);
        }

        // 2. 删除磁盘目录
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path testDir = root.resolve(uniquePath);
        if (Files.exists(testDir)) {
            try (Stream<Path> walk = Files.walk(testDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                                // 忽略清理失败的目录
                            }
                        });
            } catch (IOException ignored) {
                // 忽略清理失败
            }
        }
    }

    /**
     * 创建表时应同时生成磁盘文件、文件索引、表索引和字段索引。
     */
    @Test
    @DisplayName("创建表：生成文件与索引记录")
    void shouldCreateTableAndIndex() {
        String path = uniquePath + "/" + tableName + ".ddl";
        String ddl = buildDdl(tableName, "kafka", List.of(
                new ColumnDef("order_id", "BIGINT"),
                new ColumnDef("user_id", "STRING")));

        CreateTableRequest request = new CreateTableRequest(path, ddl);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/tables",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(token)),
                JsonNode.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isZero();

        Long fileId = response.getBody().path("data").path("fileId").asLong();
        Long tableId = response.getBody().path("data").path("tableId").asLong();
        assertThat(fileId).isPositive();
        assertThat(tableId).isPositive();

        // 文件索引存在
        FileIndexEntity fileIndex = fileIndexService.getByPath(path);
        assertThat(fileIndex).isNotNull();
        assertThat(fileIndex.getId()).isEqualTo(fileId);
        assertThat(fileIndex.getDeletedAt()).isZero();

        // 表索引存在
        TableIndexEntity tableIndex = tableIndexMapper.selectById(tableId);
        assertThat(tableIndex).isNotNull();
        assertThat(tableIndex.getFileId()).isEqualTo(fileId);
        assertThat(tableIndex.getTableName()).isEqualTo(tableName);
        assertThat(tableIndex.getConnectorType()).isEqualTo("kafka");

        // 字段索引存在
        List<ColumnIndexEntity> columns = columnIndexMapper.selectList(
                new LambdaQueryWrapper<ColumnIndexEntity>().eq(ColumnIndexEntity::getTableId, tableId));
        assertThat(columns).hasSize(2);
        assertThat(columns.get(0).getName()).isEqualTo("order_id");
        assertThat(columns.get(1).getName()).isEqualTo("user_id");
    }

    /**
     * 更新表时应同步更新表索引，并全量替换字段索引。
     */
    @Test
    @DisplayName("更新表：同步更新表索引与字段索引")
    void shouldUpdateTableAndReplaceIndex() {
        String path = uniquePath + "/" + tableName + ".ddl";
        String createDdl = buildDdl(tableName, "kafka", List.of(new ColumnDef("user_id", "STRING")));

        Long tableId = createTable(path, createDdl);

        String updateDdl = buildDdl(tableName, "jdbc", List.of(
                new ColumnDef("user_id", "STRING"),
                new ColumnDef("age", "INT"),
                new ColumnDef("city", "STRING")));

        UpdateTableRequest request = new UpdateTableRequest(updateDdl);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/tables/" + tableId,
                HttpMethod.PUT,
                new HttpEntity<>(request, authHeaders(token)),
                JsonNode.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isZero();

        TableIndexEntity tableIndex = tableIndexMapper.selectById(tableId);
        assertThat(tableIndex).isNotNull();
        assertThat(tableIndex.getConnectorType()).isEqualTo("jdbc");

        List<ColumnIndexEntity> columns = columnIndexMapper.selectList(
                new LambdaQueryWrapper<ColumnIndexEntity>().eq(ColumnIndexEntity::getTableId, tableId));
        assertThat(columns).hasSize(3);
        assertThat(columns).extracting(ColumnIndexEntity::getName)
                .containsExactly("user_id", "age", "city");
    }

    /**
     * 删除表时应软删除文件索引，并物理删除表索引与字段索引。
     */
    @Test
    @DisplayName("删除表：软删除文件索引并物理删除表索引")
    void shouldDeleteTableAndRemoveIndex() {
        String path = uniquePath + "/" + tableName + ".ddl";
        String ddl = buildDdl(tableName, "kafka", List.of(new ColumnDef("item_id", "BIGINT")));

        Long tableId = createTable(path, ddl);
        TableIndexEntity tableIndex = tableIndexMapper.selectById(tableId);
        assertThat(tableIndex).isNotNull();
        Long fileId = tableIndex.getFileId();

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/tables/" + tableId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(token)),
                JsonNode.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isZero();

        // 文件索引软删除
        FileIndexEntity fileIndex = fileIndexService.getById(fileId, FileIndexService.INCLUDE_DELETED);
        assertThat(fileIndex).isNotNull();
        assertThat(fileIndex.getDeletedAt()).isGreaterThan(0L);

        // 表索引与字段索引物理删除
        assertThat(tableIndexMapper.selectById(tableId)).isNull();
        List<ColumnIndexEntity> columns = columnIndexMapper.selectList(
                new LambdaQueryWrapper<ColumnIndexEntity>().eq(ColumnIndexEntity::getTableId, tableId));
        assertThat(columns).isEmpty();
    }

    /**
     * 测试辅助：调用创建表接口并返回 tableId。
     */
    private Long createTable(String path, String ddl) {
        CreateTableRequest request = new CreateTableRequest(path, ddl);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "/api/tables",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(token)),
                JsonNode.class);
        assertThat(Objects.requireNonNull(response.getBody()).path("code").asInt()).isZero();
        return response.getBody().path("data").path("tableId").asLong();
    }

    /**
     * 测试辅助：构造 Flink CREATE TABLE DDL。
     */
    private String buildDdl(String tableName, String connector, List<ColumnDef> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(tableName).append(" (\n");
        for (int i = 0; i < columns.size(); i++) {
            ColumnDef col = columns.get(i);
            sb.append("    ").append(col.name()).append(" ").append(col.type());
            if (i < columns.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(") WITH (\n");
        sb.append("    'connector' = '").append(connector).append("'\n");
        sb.append(")");
        return sb.toString();
    }

    private record ColumnDef(String name, String type) {
    }
}
