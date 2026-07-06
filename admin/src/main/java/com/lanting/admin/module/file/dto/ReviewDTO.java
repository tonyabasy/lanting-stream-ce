package com.lanting.admin.module.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Review DTO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "Review 请求")
public class ReviewDTO {

    /** 发布 Tag 名称 */
    @NotBlank(message = "tagName 不能为空")
    @Schema(description = "发布 Tag 名称")
    private String tagName;

    /** 备注 */
    @Schema(description = "备注")
    private String comment;
}
