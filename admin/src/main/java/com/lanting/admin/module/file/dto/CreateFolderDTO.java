package com.lanting.admin.module.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建文件夹 DTO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "创建文件夹请求")
public class CreateFolderDTO {

    /** 文件夹相对路径 */
    @NotBlank(message = "文件夹路径不能为空")
    @Schema(description = "文件夹相对路径", example = "jobs/2026")
    private String path;
}
