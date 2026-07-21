package com.lanting.admin.module.file.vo;

import com.lanting.admin.module.file.entity.FileIndexEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 文件树节点 VO。
 *
 * @author wangzhao
 */
@Data
@Schema(description = "文件树节点")
public class FileTreeNode {

    /** 文件/文件夹 ID */
    @Schema(description = "文件/文件夹 ID")
    private Long fileId;

    /** 文件/文件夹名 */
    @Schema(description = "文件/文件夹名")
    private String name;

    /** 相对路径 */
    @Schema(description = "相对路径")
    private String path;

    /** 类型：file / folder */
    @Schema(description = "类型：file / folder")
    private String type;

    /** 当前持锁人 username，null 表示未被锁定 */
    @Schema(description = "当前持锁人 username")
    private String lockedBy;

    /** 抢锁时间戳（毫秒） */
    @Schema(description = "抢锁时间戳（毫秒）")
    private Long lockedAt;

    /** 文件最后修改时间（毫秒），用于 mtime 排序 */
    @Schema(description = "文件最后修改时间（毫秒）")
    private Long mtime;

    /** 子节点，folder 时有值，file 时为 null */
    @Schema(description = "子节点")
    private List<FileTreeNode> children;

    public static FileTreeNode of(FileIndexEntity entity, String holder, Long lockedAt) {
        FileTreeNode node = new FileTreeNode();
        node.setFileId(entity.getId());
        node.setName(entity.getName());
        node.setPath(entity.getPath());
        node.setType(entity.getType());
        node.setMtime(entity.getMtime());
        node.setLockedBy(holder);
        node.setLockedAt(lockedAt);

        return node;
    }
}
