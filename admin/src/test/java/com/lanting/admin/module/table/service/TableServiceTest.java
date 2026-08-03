package com.lanting.admin.module.table.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import com.lanting.admin.module.file.service.FileIndexService;
import com.lanting.admin.module.file.service.GitFileService;
import com.lanting.admin.module.file.vo.FileCreatedVO;
import com.lanting.admin.module.table.entity.ColumnIndexEntity;
import com.lanting.admin.module.table.entity.TableIndexEntity;
import com.lanting.admin.module.table.mapper.ColumnIndexMapper;
import com.lanting.admin.module.table.mapper.TableIndexMapper;
import com.lanting.admin.module.table.vo.TableCreatedVO;
import com.lanting.admin.module.table.vo.TableVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TableService 单元测试。
 *
 * @author wangzhao
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TableService 单元测试")
class TableServiceTest {

    @Mock
    private TableIndexMapper tableIndexMapper;

    @Mock
    private ColumnIndexMapper columnIndexMapper;

    @Mock
    private GitFileService gitFileService;

    @Mock
    private FileIndexService fileIndexService;

    @InjectMocks
    private TableService tableService;

    // ==================== 索引同步 ====================

    /**
     * 当表索引不存在时，updateIndex 应解析 DDL 后插入新的表记录，并全量替换字段记录。
     */
    @Test
    @DisplayName("索引不存在时：插入表索引并全量替换字段索引")
    void shouldInsertTableIndexAndColumnsWhenNotExists() {
        FileIndexEntity file = new FileIndexEntity();
        file.setId(1L);
        file.setPath("ddl/user_log.ddl");

        String ddl = """
                CREATE TABLE user_log (
                    user_id STRING,
                    event_time TIMESTAMP(3)
                ) WITH (
                    'connector' = 'kafka',
                    'topic' = 'user_log_topic'
                )
                """;

        when(tableIndexMapper.selectOne(any())).thenReturn(null);
        when(tableIndexMapper.insert(any(TableIndexEntity.class))).thenAnswer(invocation -> {
            TableIndexEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        });

        tableService.updateIndex(file.getId(), ddl);

        ArgumentCaptor<TableIndexEntity> tableCaptor = ArgumentCaptor.forClass(TableIndexEntity.class);
        verify(tableIndexMapper).insert(tableCaptor.capture());
        TableIndexEntity table = tableCaptor.getValue();
        assertThat(table.getFileId()).isEqualTo(1L);
        assertThat(table.getTableName()).isEqualTo("user_log");
        assertThat(table.getConnectorType()).isEqualTo("kafka");
        assertThat(table.getPartitionField()).isEmpty();

        verify(columnIndexMapper).delete(any());
        ArgumentCaptor<List<ColumnIndexEntity>> columnCaptor = ArgumentCaptor.forClass(List.class);
        verify(columnIndexMapper).insert(columnCaptor.capture());
        List<ColumnIndexEntity> columns = columnCaptor.getValue();
        assertThat(columns).hasSize(2);
        assertThat(columns.get(0).getTableId()).isEqualTo(100L);
        assertThat(columns.get(0).getName()).isEqualTo("user_id");
        assertThat(columns.get(0).getOrdinal()).isZero();
        assertThat(columns.get(1).getName()).isEqualTo("event_time");
    }

    /**
     * 当表索引已存在时，updateIndex 应更新现有表元数据，并删除旧字段后插入新字段。
     */
    @Test
    @DisplayName("索引已存在时：更新表元数据并替换字段索引")
    void shouldUpdateTableIndexAndReplaceColumnsWhenExists() {
        FileIndexEntity file = new FileIndexEntity();
        file.setId(1L);

        TableIndexEntity existing = new TableIndexEntity();
        existing.setId(100L);
        existing.setFileId(1L);
        existing.setTableName("old_name");
        existing.setConnectorType("old_connector");

        String ddl = """
                CREATE TABLE new_table (
                    id BIGINT
                ) WITH (
                    'connector' = 'jdbc'
                )
                """;

        when(tableIndexMapper.selectOne(any())).thenReturn(existing);

        tableService.updateIndex(file.getId(), ddl);

        ArgumentCaptor<TableIndexEntity> tableCaptor = ArgumentCaptor.forClass(TableIndexEntity.class);
        verify(tableIndexMapper).updateById(tableCaptor.capture());
        TableIndexEntity table = tableCaptor.getValue();
        assertThat(table.getId()).isEqualTo(100L);
        assertThat(table.getTableName()).isEqualTo("new_table");
        assertThat(table.getConnectorType()).isEqualTo("jdbc");

        verify(columnIndexMapper).delete(any());
        ArgumentCaptor<List<ColumnIndexEntity>> columnCaptor = ArgumentCaptor.forClass(List.class);
        verify(columnIndexMapper).insert(columnCaptor.capture());
        List<ColumnIndexEntity> columns = columnCaptor.getValue();
        assertThat(columns).hasSize(1);
        assertThat(columns.get(0).getTableId()).isEqualTo(100L);
        assertThat(columns.get(0).getName()).isEqualTo("id");
    }

    /**
     * Hive DDL 包含 PARTITIONED BY 子句，应正确解析并保存分区字段。
     */
    @Test
    @DisplayName("Hive DDL：解析并保存分区字段")
    void shouldParsePartitionKeysWhenHiveDdl() {
        FileIndexEntity file = new FileIndexEntity();
        file.setId(2L);

        String ddl = """
                CREATE TABLE IF NOT EXISTS orders (
                    order_id BIGINT,
                    dt STRING
                ) PARTITIONED BY (dt)
                WITH (
                    'connector' = 'hive'
                )
                """;

        when(tableIndexMapper.selectOne(any())).thenReturn(null);
        when(tableIndexMapper.insert(any(TableIndexEntity.class))).thenAnswer(invocation -> {
            TableIndexEntity entity = invocation.getArgument(0);
            entity.setId(200L);
            return 1;
        });

        tableService.updateIndex(file.getId(), ddl);

        ArgumentCaptor<TableIndexEntity> tableCaptor = ArgumentCaptor.forClass(TableIndexEntity.class);
        verify(tableIndexMapper).insert(tableCaptor.capture());
        assertThat(tableCaptor.getValue().getTableName()).isEqualTo("orders");
        assertThat(tableCaptor.getValue().getPartitionField()).isEqualTo("dt");
    }

    /**
     * DDL 解析失败时不应抛异常，也不应操作索引表。
     */
    @Test
    @DisplayName("DDL 解析失败：跳过索引更新且不抛异常")
    void shouldSkipUpdateWhenDdlParseFails() {
        FileIndexEntity file = new FileIndexEntity();
        file.setId(1L);

        tableService.updateIndex(file.getId(), "not a ddl");

        verify(tableIndexMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(tableIndexMapper, never()).insert(any(TableIndexEntity.class));
        verify(tableIndexMapper, never()).updateById(any(TableIndexEntity.class));
        verify(columnIndexMapper, never()).insert(any(ColumnIndexEntity.class));
        verify(columnIndexMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    /**
     * deleteByFileId 应级联删除表索引和关联的字段索引。
     */
    @Test
    @DisplayName("按 fileId 删除：级联删除表索引和字段索引")
    void shouldDeleteTableAndColumnsByFileId() {
        TableIndexEntity table = new TableIndexEntity();
        table.setId(100L);
        table.setFileId(1L);

        when(tableIndexMapper.selectOne(any())).thenReturn(table);

        tableService.deleteIndexByFileId(1L);

        verify(columnIndexMapper).delete(any());
        verify(tableIndexMapper).deleteById(100L);
    }

    /**
     * 当按 fileId 找不到表索引时，deleteByFileId 应静默跳过。
     */
    @Test
    @DisplayName("按 fileId 删除：表索引不存在时静默跳过")
    void shouldSkipDeleteWhenTableIndexNotExists() {
        when(tableIndexMapper.selectOne(any())).thenReturn(null);

        tableService.deleteIndexByFileId(1L);

        verify(columnIndexMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(tableIndexMapper, never()).deleteById(any(Long.class));
    }

    // ==================== CRUD ====================

    /**
     * createTable 应调用 GitFileService 创建文件，随后同步写入表索引，并返回 tableId 与 fileId。
     */
    @Test
    @DisplayName("创建表：创建文件并同步写入表索引")
    void shouldCreateTableFileAndIndex() {
        String path = "ddl/user_log.ddl";
        String ddl = """
                CREATE TABLE user_log (
                    user_id STRING
                ) WITH (
                    'connector' = 'kafka'
                )
                """;

        FileCreatedVO fileCreated = new FileCreatedVO(10L, path);
        FileIndexEntity fileEntity = new FileIndexEntity();
        fileEntity.setId(10L);
        fileEntity.setPath(path);

        TableIndexEntity tableIndex = new TableIndexEntity();
        tableIndex.setId(100L);
        tableIndex.setFileId(10L);
        tableIndex.setTableName("user_log");

        when(gitFileService.create(path, "user", ddl)).thenReturn(fileCreated);
        when(fileIndexService.getById(10L)).thenReturn(fileEntity);
        when(tableIndexMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(tableIndex);

        TableCreatedVO result = tableService.createTable(path, ddl, "user");

        assertThat(result.getFileId()).isEqualTo(10L);
        assertThat(result.getTableId()).isEqualTo(100L);
        assertThat(result.getPath()).isEqualTo(path);
        verify(gitFileService).create(path, "user", ddl);
        verify(gitFileService, never()).save(any(), any(), any());
    }

    /**
     * updateTable 应通过 tableId 反查 fileId，再调用 GitFileService 保存文件并同步索引。
     */
    @Test
    @DisplayName("更新表：通过 tableId 更新文件并同步索引")
    void shouldUpdateTableByTableId() {
        Long tableId = 100L;
        Long fileId = 10L;
        String ddl = """
                CREATE TABLE user_log (
                    user_id STRING
                ) WITH (
                    'connector' = 'kafka'
                )
                """;

        TableIndexEntity tableIndex = new TableIndexEntity();
        tableIndex.setId(tableId);
        tableIndex.setFileId(fileId);

        FileIndexEntity fileEntity = new FileIndexEntity();
        fileEntity.setId(fileId);
        fileEntity.setPath("ddl/user_log.ddl");

        when(tableIndexMapper.selectById(tableId)).thenReturn(tableIndex);

        tableService.saveTable(tableId, ddl, "user");

        verify(gitFileService).save(fileId, ddl, "user");
        verify(tableIndexMapper).selectOne(any(LambdaQueryWrapper.class));
    }

    /**
     * deleteTable 应通过 tableId 反查 fileId，删除文件后级联删除表索引。
     */
    @Test
    @DisplayName("删除表：通过 tableId 删除文件并级联删除索引")
    void shouldDeleteTableByTableId() {
        Long tableId = 100L;
        Long fileId = 10L;

        TableIndexEntity tableIndex = new TableIndexEntity();
        tableIndex.setId(tableId);
        tableIndex.setFileId(fileId);

        when(tableIndexMapper.selectById(tableId)).thenReturn(tableIndex);

        tableService.deleteTable(tableId, "user");

        verify(gitFileService).delete(fileId, "user");
        verify(columnIndexMapper).delete(any(LambdaQueryWrapper.class));
        verify(tableIndexMapper).deleteById(tableId);
    }

    /**
     * getTable 应返回表元数据以及按 ordinal 排序后的字段列表。
     */
    @Test
    @DisplayName("查询表详情：返回表元数据和字段列表")
    void shouldGetTableWithColumns() {
        Long tableId = 100L;

        TableIndexEntity tableIndex = new TableIndexEntity();
        tableIndex.setId(tableId);
        tableIndex.setFileId(10L);
        tableIndex.setTableName("user_log");
        tableIndex.setConnectorType("kafka");
        tableIndex.setCreateTime(1L);
        tableIndex.setUpdateTime(2L);

        ColumnIndexEntity column = new ColumnIndexEntity();
        column.setTableId(tableId);
        column.setName("user_id");
        column.setType("STRING");
        column.setOrdinal(0);

        when(tableIndexMapper.selectById(tableId)).thenReturn(tableIndex);
        when(columnIndexMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(column));

        TableVO result = tableService.getTable(tableId);

        assertThat(result.getTableId()).isEqualTo(tableId);
        assertThat(result.getTableName()).isEqualTo("user_log");
        assertThat(result.getColumns()).hasSize(1);
        assertThat(result.getColumns().get(0).getName()).isEqualTo("user_id");
    }

    /**
     * listTables 应返回所有表索引列表，并按需求加载字段信息。
     */
    @Test
    @DisplayName("查询表列表：返回所有表索引")
    void shouldListTables() {
        TableIndexEntity t1 = new TableIndexEntity();
        t1.setId(100L);
        t1.setFileId(10L);
        t1.setTableName("t1");
        t1.setConnectorType("kafka");

        TableIndexEntity t2 = new TableIndexEntity();
        t2.setId(101L);
        t2.setFileId(11L);
        t2.setTableName("t2");
        t2.setConnectorType("jdbc");

        when(tableIndexMapper.selectList(null)).thenReturn(List.of(t1, t2));
        when(columnIndexMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<TableVO> result = tableService.listTables();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTableName()).isEqualTo("t1");
        assertThat(result.get(1).getTableName()).isEqualTo("t2");
    }

    /**
     * searchTables 应按关键字过滤表名并返回匹配的表列表。
     */
    @Test
    @DisplayName("搜索表：按关键字过滤表名")
    void shouldSearchTablesByKeyword() {
        TableIndexEntity t1 = new TableIndexEntity();
        t1.setId(100L);
        t1.setFileId(10L);
        t1.setTableName("user_log");
        t1.setConnectorType("kafka");

        when(tableIndexMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(t1));
        when(columnIndexMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<TableVO> result = tableService.searchTables("user");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTableName()).isEqualTo("user_log");
    }
}
