package com.lanting.admin.module.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件重命名结果 VO。
 *
 * @author wangzhao
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件重命名结果")
public class PathRenamedVO {

    @Schema(description = "文件或文件夹 ID")
    private Long fileId;

    @Schema(description = "原相对路径")
    private String oldPath;

    @Schema(description = "新相对路径")
    private String newPath;
}
