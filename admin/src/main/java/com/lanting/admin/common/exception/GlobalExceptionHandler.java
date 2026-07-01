package com.lanting.admin.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.lanting.admin.common.result.CommonResultCode;
import com.lanting.admin.common.result.Result;
import com.lanting.admin.common.result.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * <p>
 * 捕获 {@link BusinessException} 及未预期的异常，根据 {@link ResultCode#getHttpStatus()}
 * 设置真实的 HTTP 状态码，同时返回统一的 {@link Result}。
 *
 * @author wangzhao
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 未登录异常：Sa-Token 拦截器或 @SaCheckLogin 触发。
     */
    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLoginException(NotLoginException e, HttpServletResponse response) {
        response.setStatus(CommonResultCode.UNAUTHORIZED.getHttpStatus());
        log.warn("未登录访问: {}", e.getMessage());
        return Result.error(CommonResultCode.UNAUTHORIZED);
    }

    /**
     * 无权限异常：@SaCheckPermission 校验失败时触发。
     */
    @ExceptionHandler(NotPermissionException.class)
    public Result<Void> handleNotPermissionException(NotPermissionException e, HttpServletResponse response) {
        response.setStatus(CommonResultCode.FORBIDDEN.getHttpStatus());
        log.warn("无权限访问: {}", e.getMessage());
        return Result.error(CommonResultCode.FORBIDDEN);
    }

    /**
     * 业务异常：由 Service 层主动抛出，携带具体的 ResultCode。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletResponse response) {
        ResultCode rc = e.getResultCode();
        response.setStatus(rc.getHttpStatus());
        log.warn("业务异常: code={}, message={}", rc.getCode(), e.getMessage());
        return Result.error(rc, e.getMessage());
    }

    /**
     * 参数校验异常：@RequestBody + @Valid 校验失败时抛出。
     * 将所有字段错误拼接成一条提示信息返回，HTTP 状态码 400。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e, HttpServletResponse response) {
        response.setStatus(CommonResultCode.PARAM_INVALID.getHttpStatus());
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.error(CommonResultCode.PARAM_INVALID, message);
    }

    /**
     * 兜底异常：未被上述处理器捕获的所有异常，统一返回系统内部错误。
     * 注意：不向前端暴露异常详情，避免信息泄露。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletResponse response) {
        response.setStatus(CommonResultCode.SYSTEM_ERROR.getHttpStatus());
        log.error("系统异常", e);
        return Result.error(CommonResultCode.SYSTEM_ERROR);
    }
}
