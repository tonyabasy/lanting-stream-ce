package com.lanting.admin.module.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 被锁定文件 VO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "被锁定文件信息")
public class LockedFileVO {

    /** 文件路径 */
    @Schema(description = "文件路径")
    private String path;

    /** 持锁人 username */
    @Schema(description = "持锁人 username")
    private String lockedBy;
}
