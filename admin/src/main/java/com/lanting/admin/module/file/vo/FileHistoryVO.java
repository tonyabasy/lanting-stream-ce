package com.lanting.admin.module.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件历史 VO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "文件历史记录")
public class FileHistoryVO {

    /** commit SHA */
    @Schema(description = "commit SHA")
    private String commitHash;

    /** commit message */
    @Schema(description = "commit message")
    private String message;

    /** 操作人 username */
    @Schema(description = "操作人 username")
    private String author;

    /** commit 时间（毫秒时间戳） */
    @Schema(description = "commit 时间（毫秒时间戳）")
    private Long timestamp;
}
