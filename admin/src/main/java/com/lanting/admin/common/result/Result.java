package com.lanting.admin.common.result;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应体。
 * <p>
 * HTTP 状态码由 {@link com.lanting.admin.common.exception.GlobalExceptionHandler}
 * 根据 {@link ResultCode#getHttpStatus()} 设置，body 中的 {@code code} 字段供前端做细粒度判断。
 *
 * @param <T> data 类型
 * @author wangzhao
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Result<T> {

    /** 业务结果码 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    // ========== 静态工厂 ==========

    /** 成功，无返回数据 */
    public static <T> Result<T> success() {
        return new Result<>(CommonResultCode.SUCCESS.getCode(),
                CommonResultCode.SUCCESS.getMessage(), null);
    }

    /** 成功，带返回数据 */
    public static <T> Result<T> success(T data) {
        return new Result<>(CommonResultCode.SUCCESS.getCode(),
                CommonResultCode.SUCCESS.getMessage(), data);
    }

    /** 失败，根据 ResultCode */
    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 失败，覆盖默认提示信息 */
    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }
}
