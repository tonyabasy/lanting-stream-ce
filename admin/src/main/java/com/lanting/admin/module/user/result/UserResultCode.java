package com.lanting.admin.module.user.result;

import com.lanting.admin.common.result.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户模块业务结果码，码段 30101–30199。
 *
 * @author wangzhao
 */
@Getter
@AllArgsConstructor
public enum UserResultCode implements ResultCode {

    USER_NOT_FOUND(30101, "用户不存在", 404),
    USERNAME_DUPLICATE(30102, "用户名已存在", 400),
    PASSWORD_WRONG(30103, "用户名或密码错误", 400),
    CANNOT_DELETE_SELF(30104, "不能删除自己", 400),
    SUPER_ADMIN_PROTECTED(30106, "不允许操作超级管理员", 403),
    USERNAME_PROTECTED(30107, "不允许操作受保护的用户名", 400);

    private final int code;
    private final String message;
    private final int httpStatus;
}
