package com.lanting.admin.module.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文件级回滚 DTO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "文件级回滚请求")
public class RevertFileDTO {

    /** 文件 ID */
    @NotNull(message = "文件 ID 不能为空")
    @Schema(description = "文件 ID", example = "1")
    private Long fileId;

    /** 目标 commit SHA */
    @NotBlank(message = "commitHash 不能为空")
    @Schema(description = "目标 commit SHA")
    private String commitHash;
}
