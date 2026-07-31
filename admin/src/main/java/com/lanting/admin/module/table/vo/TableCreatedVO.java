package com.lanting.admin.module.table.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表创建结果 VO。
 *
 * @author wangzhao
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "表创建结果")
public class TableCreatedVO {

    @Schema(description = "表索引 ID")
    private Long tableId;

    @Schema(description = "关联文件 ID")
    private Long fileId;

    @Schema(description = "文件相对路径")
    private String path;
}
