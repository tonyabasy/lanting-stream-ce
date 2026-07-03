package com.lanting.admin.module.cluster.event;

import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

/**
 * 集群删除事件。
 * <p>
 * 在集群删除成功后发布，供其他模块清理关联资源。
 *
 * @author wangzhao
 */
@Getter
@ToString
public class ClusterDeletedEvent extends ApplicationEvent {

    /** 集群 ID */
    private final Long clusterId;

    /** 集群名称 */
    private final String clusterName;

    public ClusterDeletedEvent(Object source, Long clusterId, String clusterName) {
        super(source);
        this.clusterId = clusterId;
        this.clusterName = clusterName;
    }
}
