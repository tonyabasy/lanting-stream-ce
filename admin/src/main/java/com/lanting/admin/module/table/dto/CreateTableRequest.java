package com.lanting.admin.module.table.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建表请求。
 *
 * @author wangzhao
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建表请求")
public class CreateTableRequest {

    /** 文件相对路径，建议以 .ddl 结尾 */
    @NotBlank(message = "文件路径不能为空")
    @Schema(description = "文件相对路径", example = "ddl/ods_order.ddl")
    private String path;

    /** 完整 Flink SQL DDL 文本 */
    @NotBlank(message = "DDL 内容不能为空")
    @Schema(description = "完整 Flink SQL DDL 文本")
    private String ddl;
}
