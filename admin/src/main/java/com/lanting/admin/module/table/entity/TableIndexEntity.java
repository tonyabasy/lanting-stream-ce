package com.lanting.admin.module.table.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 表索引：DDL 文件的结构化缓存。
 *
 * <p>数据权威始终在磁盘 DDL 文件，Index 仅用于快速查询表名、连接器类型和字段列表。
 *
 * @author wangzhao
 */
@Schema(description = "表索引")
@Getter
@Setter
@TableName("lanting_table_index")
public class TableIndexEntity {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "关联文件 ID")
    private Long fileId;

    @Schema(description = "CREATE TABLE 的表名")
    private String tableName;

    @Schema(description = "连接器类型，如 Kafka / UpsertKafka / JDBC / FileSystem / Hive / Doris / HUDI")
    private String connectorType;

    @Schema(description = "分区字段名，多个以逗号分隔；仅 Hive / FileSystem 连接器使用")
    private String partitionField;

    @Schema(description = "创建时间（毫秒时间戳）")
    private Long createTime;

    @Schema(description = "更新时间（毫秒时间戳）")
    private Long updateTime;
}
