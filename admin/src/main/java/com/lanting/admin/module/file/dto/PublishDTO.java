package com.lanting.admin.module.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 发布 DTO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "发布请求")
public class PublishDTO {

    /** 可选显示名 */
    @Schema(description = "可选显示名")
    private String displayName;
}
