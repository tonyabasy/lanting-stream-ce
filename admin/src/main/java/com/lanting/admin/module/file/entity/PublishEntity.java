package com.lanting.admin.module.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lanting.admin.common.basic.BasicEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 发布记录表。
 *
 * @author wangzhao
 */
@Schema(description = "发布记录")
@Getter
@Setter
@TableName("lanting_file_publish")
public class PublishEntity extends BasicEntity {

    /** 发布 Tag 名称 */
    @Schema(description = "发布 Tag 名称")
    private String tagName;

    /** 显示名 */
    @Schema(description = "显示名")
    private String displayName;

    /** 对应 commit SHA */
    @Schema(description = "对应 commit SHA")
    private String commitHash;

    /** 发布人 */
    @Schema(description = "发布人")
    private String createdBy;
}
