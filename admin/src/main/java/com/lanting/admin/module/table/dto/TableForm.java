package com.lanting.admin.module.table.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 表结构表单。
 *
 * <p>用于前端表单模式编辑表结构，最终由后端序列化为 Flink SQL DDL 文本。
 *
 * @author wangzhao
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "表结构表单")
public class TableForm {

    @NotBlank(message = "表名不能为空")
    @Schema(description = "CREATE TABLE 的表名")
    private String tableName;

    @NotBlank(message = "连接器类型不能为空")
    @Schema(description = "连接器类型，如 kafka / jdbc / hive / doris")
    private String connectorType;

    @Schema(description = "WITH 属性集合")
    private Map<String, String> properties;

    @Schema(description = "分区字段名列表，仅 Hive / FileSystem 连接器有效")
    private List<String> partitionKeys;

    @NotEmpty(message = "字段列表不能为空")
    @Valid
    @Schema(description = "字段列表")
    private List<ColumnForm> columns;
}
