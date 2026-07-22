package com.lanting.admin.module.publish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 发布请求 DTO（入池 / 提交 / 发布共用）。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "发布请求")
public class PublishDTO {

    @NotEmpty(message = "fileIds 不能为空")
    @Schema(description = "待发布文件 ID 列表")
    private List<Long> fileIds;

    @Schema(description = "批次展示名（发布时使用）")
    private String displayName;

    @Schema(description = "commit message（提交时使用）")
    private String message;
}
