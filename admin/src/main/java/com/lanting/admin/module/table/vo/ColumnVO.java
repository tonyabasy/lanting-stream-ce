package com.lanting.admin.module.table.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表字段 VO。
 *
 * @author wangzhao
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "表字段信息")
public class ColumnVO {

    @Schema(description = "字段名")
    private String name;

    @Schema(description = "字段类型，如 STRING / BIGINT / TIMESTAMP")
    private String type;

    @Schema(description = "字段注释")
    private String comment;

    @Schema(description = "字段顺序（0-based）")
    private Integer ordinal;
}
