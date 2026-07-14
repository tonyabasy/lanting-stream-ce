package com.lanting.admin.module.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文件锁 DTO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "文件锁请求")
public class LockDTO {

    /** 文件 ID */
    @NotNull(message = "文件 ID 不能为空")
    @Schema(description = "文件 ID", example = "1")
    private Long fileId;
}
