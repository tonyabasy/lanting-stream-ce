package com.lanting.admin.module.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建文件 DTO。
 *
 * @author wangzhao
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "创建文件请求")
public class CreateFileDTO {

    /** 文件相对路径 */
    @NotBlank(message = "文件路径不能为空")
    @Schema(description = "文件相对路径", example = "sql/user_count.sql")
    private String path;
}
