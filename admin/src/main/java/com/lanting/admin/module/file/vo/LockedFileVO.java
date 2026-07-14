package com.lanting.admin.module.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 被锁定文件 VO。
 *
 * @author wangzhao
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "被锁定文件信息")
public class LockedFileVO {

    @Schema(description = "文件ID")
    private Long id;

    @Schema(description = "文件路径")
    private String path;

    @Schema(description = "持锁人 username")
    private String lockedBy;
}
