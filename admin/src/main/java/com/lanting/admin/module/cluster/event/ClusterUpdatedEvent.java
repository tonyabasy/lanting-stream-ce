package com.lanting.admin.module.cluster.event;

import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

/**
 * 集群更新事件。
 * <p>
 * 在集群编辑成功后发布，携带更新后的集群核心信息，
 * 供其他模块（如监控、调度）联动处理。
 *
 * @author wangzhao
 */
@Getter
@ToString
public class ClusterUpdatedEvent extends ApplicationEvent {

    /** 集群 ID */
    private final Long clusterId;

    /** 集群名称 */
    private final String clusterName;

    /** Flink 版本号 */
    private final String flinkVersion;

    /** 资源类型（YARN / KUBERNETES / LOCAL） */
    private final String resourceType;

    /** 部署目标（SESSION / APPLICATION） */
    private final String deployTarget;

    /** 状态（ACTIVE / INACTIVE） */
    private final String status;

    public ClusterUpdatedEvent(Object source,
                               Long clusterId,
                               String clusterName,
                               String flinkVersion,
                               String resourceType,
                               String deployTarget,
                               String status) {
        super(source);
        this.clusterId = clusterId;
        this.clusterName = clusterName;
        this.flinkVersion = flinkVersion;
        this.resourceType = resourceType;
        this.deployTarget = deployTarget;
        this.status = status;
    }
}
