package com.lanting.common.cli;

/**
 * Flink CLI 调用异常。
 *
 * @author wangzhao
 */
public class FlinkCliException extends RuntimeException {

    public FlinkCliException(String message) {
        super(message);
    }

    public FlinkCliException(String message, Throwable cause) {
        super(message, cause);
    }
}
