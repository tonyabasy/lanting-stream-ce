package com.lanting.admin.common.result;

/**
 * 业务结果码接口，同时承载成功码和错误码。
 * <p>
 * {@code code} 本身可作为国际化 key，后续 {@code GlobalExceptionHandler} 可通过
 * {@code code} 查找 MessageSource 中的多语言文案，查不到再 fallback 到 {@link #getMessage()}。
 *
 * @author wangzhao
 */
public interface ResultCode {

    /**
     * 业务结果码
     */
    int getCode();

    /**
     * 默认提示信息（中文硬编码）
     */
    String getMessage();

    /**
     * 对应的 HTTP 状态码
     */
    int getHttpStatus();
}
