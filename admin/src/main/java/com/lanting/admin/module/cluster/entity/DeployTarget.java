package com.lanting.admin.module.cluster.entity;

import java.util.Arrays;

/**
 * Flink 部署目标枚举。
 * <p>
 * 值与 {@code flink run --target} 参数一致，
 * 每个目标绑定了对应的资源类型（YARN / Kubernetes / Local / Remote）。
 *
 * @author wangzhao
 */
public enum DeployTarget {

    REMOTE("remote", null),
    LOCAL("local", "local"),
    KUBERNETES_SESSION("kubernetes-session", "kubernetes"),
    KUBERNETES_APPLICATION("kubernetes-application", "kubernetes"),
    YARN_SESSION("yarn-session", "yarn"),
    YARN_APPLICATION("yarn-application", "yarn"),
    /** @deprecated Flink 已弃用 */ @Deprecated
    YARN_PER_JOB("yarn-per-job", "yarn");

    private final String value;
    private final String resourceType;

    DeployTarget(String value, String resourceType) {
        this.value = value;
        this.resourceType = resourceType;
    }

    public String getValue() {
        return value;
    }

    public String getResourceType() {
        return resourceType;
    }

    /**
     * 根据字符串值查找枚举，不区分大小写。
     *
     * @param target 部署目标字符串
     * @return 对应的枚举常量
     * @throws IllegalArgumentException 未找到匹配值时
     */
    public static DeployTarget fromValue(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("部署目标不能为空");
        }
        return Arrays.stream(values())
                .filter(dt -> dt.value.equalsIgnoreCase(target.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的部署目标: " + target));
    }

    /**
     * 判断字符串是否为合法部署目标。
     */
    public static boolean isValid(String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(dt -> dt.value.equalsIgnoreCase(target.trim()));
    }
}
