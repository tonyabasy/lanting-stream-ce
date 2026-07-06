package com.lanting.admin.module.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 发布 VO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "发布记录")
public class PublishVO {

    /** 发布 ID，如 release-20260704-001 */
    @Schema(description = "发布 ID")
    private String tagName;

    /** 可选显示名 */
    @Schema(description = "可选显示名")
    private String displayName;

    /** 对应 commit SHA */
    @Schema(description = "对应 commit SHA")
    private String commitHash;

    /** 发布时间 */
    @Schema(description = "发布时间")
    private Long timestamp;
}
