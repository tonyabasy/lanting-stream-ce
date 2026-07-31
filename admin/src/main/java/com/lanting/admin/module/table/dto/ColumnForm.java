package com.lanting.admin.module.table.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表字段表单。
 *
 * @author wangzhao
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "表字段表单")
public class ColumnForm {

    @NotBlank(message = "字段名不能为空")
    @Schema(description = "字段名")
    private String name;

    @NotBlank(message = "字段类型不能为空")
    @Schema(description = "字段类型，如 STRING / BIGINT / TIMESTAMP(3)")
    private String type;

    @Schema(description = "字段注释")
    private String comment;

    @Schema(description = "字段顺序（0-based）")
    private Integer ordinal;
}
