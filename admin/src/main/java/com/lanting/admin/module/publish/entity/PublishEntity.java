package com.lanting.admin.module.publish.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.lanting.admin.common.util.TimestampDeserializer;
import com.lanting.admin.common.util.TimestampSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 发布批次头表（lanting_publish）。
 * id 使用 UUID/雪花 字符串，不继承 BasicEntity（避免 Long 自增主键冲突）。
 *
 * @author wangzhao
 */
@Getter
@Setter
@TableName("lanting_publish")
public class PublishEntity {

    @Schema(description = "发布批次 ID（UUID）")
    @TableId
    private String id;

    @Schema(description = "批次展示名")
    private String displayName;

    @Schema(description = "发布人")
    private String publishedBy;

    @Schema(description = "创建时间（毫秒时间戳）")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = TimestampSerializer.class)
    @JsonDeserialize(using = TimestampDeserializer.class)
    private Long createTime;

    @Schema(description = "更新时间（毫秒时间戳）")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonSerialize(using = TimestampSerializer.class)
    @JsonDeserialize(using = TimestampDeserializer.class)
    private Long updateTime;
}
