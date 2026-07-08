package com.lanting.admin.common.util;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 安全/隐私工具
 *
 * @author wangzhao
 */
public class SecurityUtils {

    public static String currentUser() {
        return StpUtil.getLoginIdAsString();
    }
}
