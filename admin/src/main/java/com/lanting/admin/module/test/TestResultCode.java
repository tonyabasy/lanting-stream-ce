package com.lanting.admin.module.test;

import com.lanting.admin.common.result.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 测试专用业务结果码，码段 30901–30999。
 *
 * @author wangzhao
 */
@Getter
@AllArgsConstructor
public enum TestResultCode implements ResultCode {

    TEST_MESSAGE_FORMAT(99901, "测试参数 {0} 和 {1}", 400);

    private final int code;
    private final String message;
    private final int httpStatus;
}
