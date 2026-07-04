package com.lanting.admin.common.exception;

import java.util.Arrays;

import com.lanting.admin.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常，携带 {@link ResultCode}，由 {@link GlobalExceptionHandler} 统一处理。
 * <p>
 * 支持动态参数替换 message 中的占位符（如 {@code "用户 {0} 不存在"}）。
 *
 * @author wangzhao
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;
    private final Object[] args;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
        this.args = null;
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
        this.args = null;
    }

    public BusinessException(ResultCode resultCode, Object[] args) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
        this.args = args != null ? Arrays.copyOf(args, args.length) : null;
    }

    /**
     * 工厂方法，支持动态参数。
     */
    public static BusinessException of(ResultCode resultCode, Object... args) {
        return new BusinessException(resultCode, args);
    }

    public Object[] getArgs() {
        return args != null ? Arrays.copyOf(args, args.length) : null;
    }
}
