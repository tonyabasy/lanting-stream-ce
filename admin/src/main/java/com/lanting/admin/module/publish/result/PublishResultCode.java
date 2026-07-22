package com.lanting.admin.module.publish.result;

import com.lanting.admin.common.result.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发布模块业务结果码，码段 31001–31099。
 *
 * @author wangzhao
 * @since 2026-07-21
 */
@Getter
@AllArgsConstructor
public enum PublishResultCode implements ResultCode {

    EMPTY_PUBLISH(31001, "请选择要发布的文件", 400),
    NOTHING_TO_COMMIT(31002, "文件未做任何修改无需提交", 400),
    FILE_NOT_COMMITTED(31003, "文件未在待发布列表中", 400),
    PUBLISH_NOT_FOUND(31004, "发布批次不存在", 404),

    REVIEW_COMMIT_STALE(31005, "commit 已过期，请刷新后重新审核", 409),
    REVIEW_NOT_FOUND(31006, "评审记录不存在", 404),
    REVIEW_DEL_FORBIDDEN(31007, "仅可删除自己的评审", 403);

    private final int code;
    private final String message;
    private final int httpStatus;
}
