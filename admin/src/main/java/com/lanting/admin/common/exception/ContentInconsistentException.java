package com.lanting.admin.common.exception;

import com.lanting.admin.common.result.ResultCode;
import lombok.Getter;

/**
 * 文件内容与索引不一致异常。
 * <p>
 * 与 {@link BusinessException} 不同之处在于：本异常必须携带磁盘真实内容（{@code data}），
 * 由 {@link com.lanting.admin.common.config.GlobalExceptionHandler} 统一转换为
 * {@code code != 0} 但 {@code data} 为磁盘内容的响应，保证前端在提示用户的同时仍能展示内容。
 *
 * @author wangzhao
 */
@Getter
public class ContentInconsistentException extends RuntimeException {

    private final ResultCode resultCode;
    private final String customMessage;
    private final Object data;

    public ContentInconsistentException(ResultCode resultCode, String message, Object data) {
        super(message);
        this.resultCode = resultCode;
        this.customMessage = message;
        this.data = data;
    }

    public ResultCode getResultCode() {
        return resultCode;
    }

    public String getCustomMessage() {
        return customMessage;
    }
}
