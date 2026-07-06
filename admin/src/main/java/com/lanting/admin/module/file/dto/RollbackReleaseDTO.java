package com.lanting.admin.module.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发布级回滚 DTO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "发布级回滚请求")
public class RollbackReleaseDTO {

    /** 发布 Tag 名称 */
    @NotBlank(message = "tagName 不能为空")
    @Schema(description = "发布 Tag 名称")
    private String tagName;
}
