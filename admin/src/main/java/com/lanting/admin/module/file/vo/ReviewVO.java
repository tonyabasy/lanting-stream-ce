package com.lanting.admin.module.file.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Review VO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "Review 记录")
public class ReviewVO {

    /** 发布 ID */
    @Schema(description = "发布 ID")
    private String tagName;

    /** reviewer username */
    @Schema(description = "reviewer")
    private String reviewer;

    /** 备注 */
    @Schema(description = "备注")
    private String comment;

    /** review 时间 */
    @Schema(description = "review 时间")
    private Long timestamp;
}
