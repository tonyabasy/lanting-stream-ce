package com.lanting.admin.module.file.event;

import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

/**
 * 文件或文件夹路径重命名事件。
 * <p>
 * 在 rename 操作成功后发布，供 WebSocket 等监听方同步推送前端。
 *
 * @author wangzhao
 */
@Getter
@ToString
public class PathRenamedEvent extends ApplicationEvent {

    /** 文件或文件夹 ID */
    private final long fileId;

    /** 原相对路径 */
    private final String oldPath;

    /** 新相对路径 */
    private final String newPath;

    /** 类型：file 或 folder */
    private final String type;

    /** 重命名操作人 username */
    private final String renamedBy;

    public PathRenamedEvent(Object source, long fileId, String oldPath,
                            String newPath, String type, String renamedBy) {
        super(source);
        this.fileId = fileId;
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.type = type;
        this.renamedBy = renamedBy;
    }
}
