package com.lanting.admin.module.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 文件系统元数据索引表。
 *
 * @author wangzhao
 */
@Schema(description = "文件系统元数据索引")
@Getter
@Setter
@TableName("lanting_file_index")
public class FileIndexEntity {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "文件相对路径")
    private String path;

    @Schema(description = "文件或目录名")
    private String name;

    @Schema(description = "类型：file / folder")
    private String type;

    @Schema(description = "父目录相对路径，根目录子节点为空字符串")
    private String parentPath;

    @Schema(description = "磁盘文件最后修改时间（毫秒）")
    private Long mtime;

    @Schema(description = "创建时间（毫秒时间戳）")
    private Long createTime;

    @Schema(description = "更新时间（毫秒时间戳）")
    private Long updateTime;
}
