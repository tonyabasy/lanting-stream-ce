package com.lanting.admin.module.cluster.result;

import com.lanting.admin.common.result.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 集群模块业务结果码，码段 30201–30299。
 *
 * @author wangzhao
 */
@Getter
@AllArgsConstructor
public enum ClusterResultCode implements ResultCode {

    CLUSTER_NOT_FOUND(30201, "集群不存在", 404),
    CLUSTER_NAME_DUPLICATE(30202, "集群名称已存在", 400),
    DEPLOY_TARGET_INVALID(30204, "不支持的部署目标", 400),
    FLINK_VERSION_DETECT_FAILED(30205, "Flink 版本检测失败，请检查 FLINK_HOME 是否正确", 400);

    private final int code;
    private final String message;
    private final int httpStatus;
}
