package com.lanting.admin.module.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lanting.admin.common.basic.BasicEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Review 记录表。
 *
 * @author wangzhao
 */
@Schema(description = "Review 记录")
@Getter
@Setter
@TableName("lanting_file_review")
public class ReviewEntity extends BasicEntity {

    /** 发布 Tag 名称 */
    @Schema(description = "发布 Tag 名称")
    private String tagName;

    /** reviewer username */
    @Schema(description = "reviewer")
    private String reviewer;

    /** 备注 */
    @Schema(description = "备注")
    private String comment;
}
