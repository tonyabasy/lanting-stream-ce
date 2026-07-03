package com.lanting.admin.common.basic;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.lanting.admin.common.util.LongToStringSerializer;
import com.lanting.admin.common.util.TimestampDeserializer;
import com.lanting.admin.common.util.TimestampSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 所有持久化实体共有的基础实体。
 *
 * @author wangzhao
 * @since 2025-12-23
 */
@Schema(description = "基础实体字段")
@Getter
@Setter
public abstract class BasicEntity {

    /**
     * 主键（自增）。
     */
    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = LongToStringSerializer.class)
    private Long id;

    /**
     * 软删除标记：{@code 0} 未删除，非零已删除。
     */
    @Schema(description = "软删除标记，0 未删除，非零已删除")
    @TableLogic(delval = "id")
    private Long isDelete;

    /**
     * 创建时间（毫秒时间戳）。
     */
    @Schema(description = "创建时间（毫秒时间戳）")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = TimestampSerializer.class)
    @JsonDeserialize(using = TimestampDeserializer.class)
    private Long createTime;

    /**
     * 更新时间（毫秒时间戳）。
     */
    @Schema(description = "更新时间（毫秒时间戳）")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonSerialize(using = TimestampSerializer.class)
    @JsonDeserialize(using = TimestampDeserializer.class)
    private Long updateTime;
}
