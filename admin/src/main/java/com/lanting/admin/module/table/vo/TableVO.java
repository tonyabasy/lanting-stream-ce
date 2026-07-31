package com.lanting.admin.module.table.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 表详情 VO。
 *
 * @author wangzhao
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "表详情")
public class TableVO {

    @Schema(description = "表索引 ID")
    private Long tableId;

    @Schema(description = "关联文件 ID")
    private Long fileId;

    @Schema(description = "CREATE TABLE 的表名")
    private String tableName;

    @Schema(description = "连接器类型")
    private String connectorType;

    @Schema(description = "分区字段名，多个以逗号分隔")
    private String partitionField;

    @Schema(description = "字段列表")
    private List<ColumnVO> columns;

    @Schema(description = "创建时间（毫秒时间戳）")
    private Long createTime;

    @Schema(description = "更新时间（毫秒时间戳）")
    private Long updateTime;
}
