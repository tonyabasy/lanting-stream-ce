package com.lanting.admin.module.file.result;

import com.lanting.admin.common.result.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件系统模块业务结果码，码段 30701–30799。
 *
 * @author wangzhao
 */
@Getter
@AllArgsConstructor
public enum FileResultCode implements ResultCode {

    WORKSPACE_NOT_FOUND(30701, "工作空间不存在", 404),
    FILE_NOT_FOUND(30702, "文件不存在", 404),
    FILE_TYPE_NOT_ALLOWED(30703, "文件类型不允许", 400),
    FILE_SIZE_EXCEEDED(30704, "文件大小超过限制（最大 1MB）", 400),
    PATH_ILLEGAL(30705, "路径包含非法字符", 400),
    LANTING_DIR_FORBIDDEN(30706, "不允许操作 .lanting 目录", 403),
    FILE_ALREADY_EXISTS(30707, "文件已存在", 400),
    FILE_OPERATION_FAILED(30708, "文件操作失败", 500),
    FILE_LOCKED(30709, "文件已被锁定", 423),
    PUBLISH_TAG_EXISTS(30710, "发布 tag 已存在", 409),
    ROLLBACK_TARGET_NOT_FOUND(30711, "回滚目标不存在", 404),
    FILES_LOCKED(30712, "回滚或删除文件夹时部分文件被锁定", 423),
    NOTHING_TO_COMMIT(30713, "无可提交的文件", 400),
    FILE_CONTENT_INCONSISTENT(30714, "文件内容与索引不一致", 200);

    private final int code;
    private final String message;
    private final int httpStatus;
}
