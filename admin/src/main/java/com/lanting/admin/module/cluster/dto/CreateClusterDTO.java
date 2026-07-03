package com.lanting.admin.module.cluster.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新建集群请求 DTO。
 *
 * @author wangzhao
 */
@Schema(description = "创建集群请求")
@Data
public class CreateClusterDTO {

    /** 集群名称 */
    @Schema(description = "集群名称")
    @NotBlank(message = "集群名称不能为空")
    private String name;

    /** Flink 安装路径 */
    @Schema(description = "Flink 安装路径")
    @NotBlank(message = "FLINK_HOME 不能为空")
    private String flinkHome;

    /** 部署目标（yarn-session / kubernetes-application / local 等），DeployTarget.fromValue() 解析并校验 */
    @Schema(description = "部署目标：yarn-session / kubernetes-application / local")
    @NotBlank(message = "部署目标不能为空")
    private String deployTarget;

    /** 配置信息（JSON 格式） */
    @Schema(description = "配置信息（JSON 格式）")
    private String configurations;
}
