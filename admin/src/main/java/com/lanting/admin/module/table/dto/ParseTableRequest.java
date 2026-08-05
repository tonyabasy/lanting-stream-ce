package com.lanting.admin.module.table.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解析 DDL 请求。
 *
 * @author wangzhao
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "解析 DDL 请求")
public class ParseTableRequest {

    @NotBlank(message = "DDL 内容不能为空")
    @Schema(description = "完整 Flink SQL DDL 文本", example = "CREATE TABLE ...")
    private String ddl;
}
