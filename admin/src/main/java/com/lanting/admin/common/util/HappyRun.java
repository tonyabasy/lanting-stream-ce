package com.lanting.admin.common.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 快乐的执行，包含：
 * 1. 重试执行
 * 2. 忽略报错执行
 *
 * @author wangzhao
 */
@Slf4j
public class HappyRun {

    @FunctionalInterface
    public interface HappyAction<T> {
        T run() throws Throwable;
    }

    @FunctionalInterface
    public interface VoidHappyAction {
        void run() throws Throwable;
    }

    // ==================== 1. 忽略报错执行 (Happy Run) ====================

    /**
     * 快乐执行（无返回值）：执行动作，若发生任何异常直接忽略，仅记录 Warn 日志
     *
     * @param action     待执行的动作
     * @param actionDesc 动作描述，用于日志排查
     */
    @SafeVarargs
    public static void run(VoidHappyAction action, String actionDesc, Class<? extends Throwable>... ignoreTypes) {
        try {
            action.run();
        } catch (Throwable t) {
            if (shouldIgnore(t, ignoreTypes)) {
                log.warn("HappyRun 捕获并忽略了异常 | 动作: {} | 异常原因: {}", actionDesc, t.getMessage());
            } else {
                sneakyThrow(t);
            }
        }
    }

    /**
     * 快乐执行（有返回值）：执行动作，若发生任何异常则忽略，并返回指定的默认值
     *
     * @param action       待执行的动作
     * @param defaultValue 发生异常时的降级默认值
     * @param actionDesc   动作描述，用于日志排查
     */
    @SafeVarargs
    public static <T> T run(HappyAction<T> action, T defaultValue, String actionDesc, Class<? extends Throwable>... ignoreTypes) {
        try {
            return action.run();
        } catch (Throwable t) {
            if (shouldIgnore(t, ignoreTypes)) {
                log.warn("HappyRun 捕获并忽略了异常 | 动作: {} | 异常原因: {}", actionDesc, t.getMessage());
            } else {
                sneakyThrow(t);
            }
        }
        return defaultValue;
    }

    // ==================== 2. 失败重试执行 (Retry Run) ====================

    /**
     * 失败重试执行（无返回值）
     *
     * @param action      待执行的动作
     * @param maxAttempts 最大尝试次数（必须 >= 1）
     * @param delayMs     重试间隔毫秒数
     * @param actionDesc  动作描述，用于日志排查
     */
    public static void retry(VoidHappyAction action, int maxAttempts, long delayMs, String actionDesc) {
        retry(() -> {
            action.run();
            return null;
        }, maxAttempts, delayMs, actionDesc);
    }

    /**
     * 失败重试执行（有返回值）
     *
     * @param action      待执行的动作
     * @param maxAttempts 最大尝试次数（必须 >= 1）
     * @param delayMs     重试间隔毫秒数
     * @param actionDesc  动作描述，用于日志排查
     */
    public static <T> T retry(HappyAction<T> action, int maxAttempts, long delayMs, String actionDesc) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return action.run();
            } catch (Throwable t) {
                if (attempts >= maxAttempts) {
                    log.error("HappyRun 重试彻底失败 | 动作: {} | 已尝试次数: {} | 最终抛出异常", actionDesc, attempts, t);
                    throw new RuntimeException("HappyRun 重试执行失败: " + actionDesc, t);
                }
                log.warn("HappyRun 执行遭遇失败，准备进行第 {}/{} 次重试 | 动作: {} | 异常信息: {}",
                        attempts, maxAttempts, actionDesc, t.getMessage());
                if (delayMs > 0) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("HappyRun 重试被中断 | 动作: " + actionDesc, ie);
                    }
                }
            }
        }
    }


    /**
     * 判断当前异常是否在忽略名单中
     */
    private static boolean shouldIgnore(Throwable t, Class<? extends Throwable>[] ignoreTypes) {
        // 如果没有传入指定的 ignoreTypes，默认忽略全部异常
        if (ignoreTypes == null || ignoreTypes.length == 0) {
            return true;
        }
        for (Class<? extends Throwable> ignoreType : ignoreTypes) {
            // 如果抛出的异常是 ignoreType 的实例或子类，则忽略
            if (ignoreType.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 欺骗编译器的“悄悄抛出”黑魔法。
     */
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneakyThrow(Throwable t) throws E {
        throw (E) t;
    }
}