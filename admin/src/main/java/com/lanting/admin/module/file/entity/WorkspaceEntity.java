package com.lanting.admin.module.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lanting.admin.common.basic.BasicEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 工作空间表。
 *
 * @author wangzhao
 */
@Schema(description = "工作空间")
@Getter
@Setter
@TableName("lanting_workspace")
public class WorkspaceEntity extends BasicEntity {

    /** 工作空间名称 */
    @Schema(description = "工作空间名称")
    private String name;

    /** 工作空间 Git 仓库根目录路径 */
    @Schema(description = "工作空间 Git 仓库根目录路径")
    private String gitPath;

    /** 描述 */
    @Schema(description = "描述")
    private String description;

    /** 工作空间配置，JSON 字符串 */
    @Schema(description = "工作空间配置，JSON 字符串")
    @JsonIgnore
    private String config;

    /** 创建人 */
    @Schema(description = "创建人")
    private String createdBy;
}
