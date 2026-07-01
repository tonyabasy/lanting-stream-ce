package com.lanting.admin.common.result;


import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 跨模块通用的业务结果码：成功、参数校验、认证鉴权、系统内部错误。
 *
 * @author wangzhao
 */
@Getter
@AllArgsConstructor
public enum CommonResultCode implements ResultCode {

    SUCCESS(0, "成功", 200),

    // ========== 1xxxx 参数 / 客户端错误 ==========
    PARAM_INVALID(10001, "参数校验失败", 400),

    // ========== 2xxxx 认证鉴权 ==========
    UNAUTHORIZED(20001, "未登录或登录已过期", 401),
    FORBIDDEN(20002, "无权限访问", 403),

    // ========== 5xxxx 系统内部错误 ==========
    SYSTEM_ERROR(50001, "系统内部错误", 500);

    private final int code;
    private final String message;
    private final int httpStatus;
}
