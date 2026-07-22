package com.lanting.admin.common.util;

import java.util.UUID;

/**
 * ID 生成工具。
 *
 * @author wangzhao
 */
public final class IdGeneratorUtils {
    
    /**
     * 生成 UUID（36 字符，含横线）。
     */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }
}
