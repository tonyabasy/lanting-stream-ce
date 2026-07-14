package com.lanting.admin.module.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 重命名文件或文件夹 DTO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "重命名文件或文件夹请求")
public class RenameDTO {

    /** 文件或文件夹 ID */
    @NotNull(message = "文件 ID 不能为空")
    @Schema(description = "文件或文件夹 ID", example = "1")
    private Long fileId;

    /** 新名称（仅名称，不含路径） */
    @NotBlank(message = "新名称不能为空")
    @Schema(description = "新名称（不含路径）", example = "user_count.sql")
    private String newName;
}
