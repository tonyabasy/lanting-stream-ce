package com.lanting.admin.module.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 移动文件或文件夹 DTO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "移动文件或文件夹请求")
public class MoveDTO {

    /** 文件或文件夹 ID */
    @NotNull(message = "文件 ID 不能为空")
    @Schema(description = "文件或文件夹 ID", example = "1")
    private Long fileId;

    /** 新路径（完整路径，含名称） */
    @NotBlank(message = "新路径不能为空")
    @Schema(description = "新路径（完整路径）", example = "project/user_count.sql")
    private String newPath;
}
