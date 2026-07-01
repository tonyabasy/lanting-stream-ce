package com.lanting.admin.common.exception;

import com.lanting.admin.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常，携带 {@link ResultCode}，由 {@link GlobalExceptionHandler} 统一处理。
 *
 * @author wangzhao
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
