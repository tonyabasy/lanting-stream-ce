package com.lanting.admin.module.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件创建结果 VO。
 *
 * @author wangzhao
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件创建结果")
public class FileCreatedVO {

    @Schema(description = "文件 ID")
    private Long fileId;

    @Schema(description = "文件相对路径")
    private String path;
}
