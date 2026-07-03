package com.lanting.admin.module.cluster.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lanting.admin.common.basic.BasicEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 集群表。
 *
 * @author wangzhao
 */
@Schema(description = "集群信息")
@Getter
@Setter
@TableName("lanting_cluster")
public class ClusterEntity extends BasicEntity {

    /** 集群名称 */
    @Schema(description = "集群名称")
    private String name;

    /** Flink 安装路径 */
    @Schema(description = "Flink 安装路径")
    private String flinkHome;

    /** Flink 版本号（检测后缓存） */
    @Schema(description = "Flink 版本号")
    private String flinkVersion;

    /** 资源类型（YARN / KUBERNETES / LOCAL） */
    @Schema(description = "资源类型：YARN / KUBERNETES / LOCAL")
    private String resourceType;

    /** 部署目标（SESSION / APPLICATION / MINI_CLUSTER） */
    @Schema(description = "部署目标：SESSION / APPLICATION / MINI_CLUSTER")
    private String deployTarget;

    /** 配置信息（JSON 格式） */
    @Schema(description = "配置信息（JSON 格式）")
    private String configurations;

    /** 状态（ACTIVE / INACTIVE） */
    @Schema(description = "状态：ACTIVE / INACTIVE")
    private String status;
}
