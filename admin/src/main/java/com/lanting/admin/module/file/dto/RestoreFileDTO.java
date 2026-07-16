package com.lanting.admin.module.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 从回收站恢复文件请求 DTO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "恢复文件请求")
public class RestoreFileDTO {

    /** 文件 ID */
    @NotNull(message = "文件 ID 不能为空")
    @Schema(description = "文件 ID", example = "1")
    private Long fileId;

    /** 目标 commit SHA，为空时从 HEAD 恢复 */
    @Schema(description = "目标 commit SHA，为空时从 HEAD 恢复")
    private String commitHash;
}
