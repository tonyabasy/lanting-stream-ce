package com.lanting.admin.module.table.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.common.result.CommonResultCode;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import com.lanting.admin.module.file.service.FileIndexService;
import com.lanting.admin.module.file.service.GitFileService;
import com.lanting.admin.module.file.vo.FileCreatedVO;
import com.lanting.admin.module.table.entity.ColumnIndexEntity;
import com.lanting.admin.module.table.entity.TableIndexEntity;
import com.lanting.admin.module.table.mapper.ColumnIndexMapper;
import com.lanting.admin.module.table.mapper.TableIndexMapper;
import com.lanting.admin.module.table.model.FlinkColumn;
import com.lanting.admin.module.table.model.FlinkTable;
import com.lanting.admin.module.table.util.FlinkSqlParser;
import com.lanting.admin.module.table.vo.ColumnVO;
import com.lanting.admin.module.table.vo.TableCreatedVO;
import com.lanting.admin.module.table.vo.TableVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 表业务服务。
 *
 * <p>负责 Table 的创建、更新、删除、查询，以及 .ddl 文件保存/删除时同步
 * {@code lanting_table_index} 与 {@code lanting_column_index}。
 * 解析失败仅记录 warn，不阻断文件主链路。
 *
 * @author wangzhao
 */
@Slf4j
@Service
public class TableService {

    private final TableIndexMapper tableIndexMapper;
    private final ColumnIndexMapper columnIndexMapper;
    private final GitFileService gitFileService;
    private final FileIndexService fileIndexService;

    public TableService(TableIndexMapper tableIndexMapper, ColumnIndexMapper columnIndexMapper,
                        GitFileService gitFileService, FileIndexService fileIndexService) {
        this.tableIndexMapper = tableIndexMapper;
        this.columnIndexMapper = columnIndexMapper;
        this.gitFileService = gitFileService;
        this.fileIndexService = fileIndexService;
    }

    /**
     * 创建表：创建 .ddl 文件、写入 DDL、同步索引。
     *
     * @param path        文件相对路径
     * @param ddl         完整 Flink SQL DDL 文本
     * @param currentUser 当前用户
     * @return 创建结果
     */
    public TableCreatedVO createTable(String path, String ddl, String currentUser) {
        FileCreatedVO fileCreated = gitFileService.create(path, currentUser, ddl);
        FileIndexEntity fileEntity = fileIndexService.getById(fileCreated.getFileId());
        Long tableId = updateIndex(fileEntity.getId(), ddl);
        return new TableCreatedVO(tableId, fileCreated.getFileId(), path);
    }

    /**
     * 更新表：按 tableId 找到文件，保存 DDL，同步索引。
     *
     * @param tableId     表索引 ID
     * @param ddl         完整 Flink SQL DDL 文本
     * @param currentUser 当前用户
     */
    public void saveTable(Long tableId, String ddl, String currentUser) {
        TableIndexEntity tableIndex = getTableIndexOrThrow(tableId);
        saveTableByFileId(tableIndex.getFileId(), ddl, currentUser);
    }

    public void saveTableByFileId(Long fileId, String ddl, String currentUser) {
        gitFileService.save(fileId, ddl, currentUser);
        updateIndex(fileId, ddl);
    }

    /**
     * 删除表：按 tableId 找到文件，删除文件，级联删除索引。
     *
     * @param tableId     表索引 ID
     * @param currentUser 当前用户
     */
    public void deleteTable(Long tableId, String currentUser) {
        TableIndexEntity tableIndex = getTableIndexOrThrow(tableId);
        deleteTableByFileId(tableIndex.getFileId(), currentUser);
    }

    public void deleteTableByFileId(Long fileId, String currentUser) {
        gitFileService.delete(fileId, currentUser);
        deleteIndexByFileId(fileId);
    }

    /**
     * 查询单表详情。
     *
     * @param tableId 表索引 ID
     * @return 表详情
     */
    public TableVO getTable(Long tableId) {
        TableIndexEntity tableIndex = getTableIndexOrThrow(tableId);
        List<ColumnIndexEntity> columns = columnIndexMapper.selectList(
                new LambdaQueryWrapper<ColumnIndexEntity>().eq(ColumnIndexEntity::getTableId, tableId));
        return toTableVO(tableIndex, columns);
    }

    public TableVO getTableByFileId(Long fileId) {
        TableIndexEntity tableIndex = getTableIndexByFileIdOrThrow(fileId);
        List<ColumnIndexEntity> columns = columnIndexMapper.selectList(
                new LambdaQueryWrapper<ColumnIndexEntity>().eq(ColumnIndexEntity::getTableId, tableIndex.getId()));
        return toTableVO(tableIndex, columns);
    }

    /**
     * 查询全部表列表。
     *
     * @return 表列表
     */
    public List<TableVO> listTables() {
        List<TableIndexEntity> tables = tableIndexMapper.selectList(null);
        if (tables.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> tableIds = tables.stream().map(TableIndexEntity::getId).toList();
        List<ColumnIndexEntity> allColumns = columnIndexMapper.selectList(
                new LambdaQueryWrapper<ColumnIndexEntity>().in(ColumnIndexEntity::getTableId, tableIds));

        return tables.stream()
                .map(table -> toTableVO(table, filterColumns(allColumns, table.getId())))
                .toList();
    }

    /**
     * 按表名或连接器类型关键字搜索表。
     *
     * @param keyword 搜索关键字
     * @return 匹配的表列表
     */
    public List<TableVO> searchTables(String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return listTables();
        }

        LambdaQueryWrapper<TableIndexEntity> wrapper = new LambdaQueryWrapper<TableIndexEntity>()
                .like(TableIndexEntity::getTableName, keyword)
                .or()
                .like(TableIndexEntity::getConnectorType, keyword);
        List<TableIndexEntity> tables = tableIndexMapper.selectList(wrapper);
        if (tables.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> tableIds = tables.stream().map(TableIndexEntity::getId).toList();
        List<ColumnIndexEntity> allColumns = columnIndexMapper.selectList(
                new LambdaQueryWrapper<ColumnIndexEntity>().in(ColumnIndexEntity::getTableId, tableIds));

        return tables.stream()
                .map(table -> toTableVO(table, filterColumns(allColumns, table.getId())))
                .toList();
    }

    private TableIndexEntity getTableIndexOrThrow(Long tableId) {
        TableIndexEntity tableIndex = tableIndexMapper.selectById(tableId);
        if (tableIndex == null) {
            throw new BusinessException(CommonResultCode.PARAM_INVALID, "表不存在");
        }
        return tableIndex;
    }

    private TableIndexEntity getTableIndexByFileIdOrThrow(Long fileId) {
        TableIndexEntity tableIndex = tableIndexMapper.selectOne(new LambdaQueryWrapper<TableIndexEntity>()
                .eq(TableIndexEntity::getFileId, fileId));
        if (tableIndex == null) {
            throw new BusinessException(CommonResultCode.PARAM_INVALID, "表不存在");
        }
        return tableIndex;
    }

    private static List<ColumnIndexEntity> filterColumns(List<ColumnIndexEntity> allColumns, Long tableId) {
        return allColumns.stream()
                .filter(column -> column.getTableId().equals(tableId))
                .sorted((a, b) -> Integer.compare(a.getOrdinal(), b.getOrdinal()))
                .toList();
    }

    private static TableVO toTableVO(TableIndexEntity table, List<ColumnIndexEntity> columns) {
        List<ColumnVO> columnVOs = columns.stream()
                .map(c -> new ColumnVO(c.getName(), c.getType(), c.getComment(), c.getOrdinal()))
                .toList();
        return new TableVO(
                table.getId(),
                table.getFileId(),
                table.getTableName(),
                table.getConnectorType(),
                table.getPartitionField(),
                columnVOs,
                table.getCreateTime(),
                table.getUpdateTime()
        );
    }

    /**
     * 根据 DDL 内容更新表索引。
     *
     * <p>若该文件已存在表索引则更新，否则新建；columns 始终先删除后重新插入。
     *
     * @param fileId    文件索引实体
     * @param content DDL 文本内容
     */
    public Long updateIndex(Long fileId, String content) {
        if (fileId == null || StringUtils.isBlank(content)) {
            return null;
        }

        final FlinkTable table;
        try {
            table = FlinkSqlParser.parseCreateTable(content);
        } catch (Exception e) {
            log.warn("Failed to parse DDL for fileId={}: {}", fileId, e.getMessage());
            return null;
        }

        long now = System.currentTimeMillis();
        TableIndexEntity existing = getByFileId(fileId);

        Long tableId;
        if (existing == null) {
            TableIndexEntity entity = new TableIndexEntity();
            entity.setFileId(fileId);
            entity.setTableName(table.tableName());
            entity.setConnectorType(table.connector());
            entity.setPartitionField(String.join(",", table.partitionKeys()));
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            tableIndexMapper.insert(entity);
            tableId = entity.getId();
        } else {
            tableId = existing.getId();
            existing.setTableName(table.tableName());
            existing.setConnectorType(table.connector());
            existing.setPartitionField(String.join(",", table.partitionKeys()));
            existing.setUpdateTime(now);
            tableIndexMapper.updateById(existing);
        }

        columnIndexMapper.delete(
                new LambdaQueryWrapper<ColumnIndexEntity>().eq(ColumnIndexEntity::getTableId, tableId));

        List<FlinkColumn> columns = table.columns();
        if (columns.isEmpty()) {
            return tableId;
        }

        List<ColumnIndexEntity> columnEntities = createColumnEntities(columns, tableId, now);
        columnIndexMapper.insert(columnEntities);
        return tableId;
    }

    private TableIndexEntity getByFileId(Long fileId) {
        return tableIndexMapper.selectOne(
                new LambdaQueryWrapper<TableIndexEntity>().eq(TableIndexEntity::getFileId, fileId));
    }

    private static @NonNull List<ColumnIndexEntity> createColumnEntities(List<FlinkColumn> columns, Long tableId, long now) {
        List<ColumnIndexEntity> columnEntities = new ArrayList<>(columns.size());
        for (FlinkColumn column : columns) {
            ColumnIndexEntity columnEntity = new ColumnIndexEntity();
            columnEntity.setTableId(tableId);
            columnEntity.setName(column.name());
            columnEntity.setType(column.type());
            columnEntity.setComment(column.comment());
            columnEntity.setOrdinal(column.ordinal());
            columnEntity.setCreateTime(now);
            columnEntity.setUpdateTime(now);
            columnEntities.add(columnEntity);
        }
        return columnEntities;
    }

    /**
     * 根据文件 ID 删除表索引及其字段索引。
     *
     * @param fileId 文件 ID
     */
    public void deleteIndexByFileId(Long fileId) {
        TableIndexEntity existing = getByFileId(fileId);
        if (existing == null) {
            return;
        }
        // 这里之所以没有用 fileId + tableMapper 直接删除是因为 ColumnIndex 的删除需要基于 TableId
        deleteIndexByTableId(existing.getId());
    }

    private void deleteIndexByTableId(Long tableId) {
        columnIndexMapper.delete(
                new LambdaQueryWrapper<ColumnIndexEntity>().eq(ColumnIndexEntity::getTableId, tableId));
        tableIndexMapper.deleteById(tableId);
    }
}
