package com.lanting.admin.module.table.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 表字段索引。
 *
 * @author wangzhao
 */
@Schema(description = "表字段索引")
@Getter
@Setter
@TableName("lanting_column_index")
public class ColumnIndexEntity {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "关联表索引 ID")
    private Long tableId;

    @Schema(description = "字段名")
    private String name;

    @Schema(description = "字段类型，如 STRING / BIGINT / DOUBLE / DATE / DATETIME / TIMESTAMP")
    private String type;

    @Schema(description = "字段注释")
    private String comment;

    @Schema(description = "字段顺序（0-based）")
    private Integer ordinal;

    @Schema(description = "创建时间（毫秒时间戳）")
    private Long createTime;

    @Schema(description = "更新时间（毫秒时间戳）")
    private Long updateTime;
}
