package com.lanting.admin.module.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 自动保存文件 DTO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "自动保存文件请求")
public class SaveFileDTO {

    /** 文件相对路径 */
    @NotBlank(message = "文件路径不能为空")
    @Schema(description = "文件相对路径", example = "jobs/user_count.sql")
    private String path;

    /** 文件内容 */
    @Schema(description = "文件内容")
    private String content;
}
