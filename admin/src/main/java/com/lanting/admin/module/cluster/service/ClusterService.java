package com.lanting.admin.module.cluster.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lanting.admin.common.basic.BasicServiceImpl;
import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.module.cluster.dto.CreateClusterDTO;
import com.lanting.admin.module.cluster.dto.UpdateClusterDTO;
import com.lanting.admin.module.cluster.entity.ClusterEntity;
import com.lanting.admin.module.cluster.entity.DeployTarget;
import com.lanting.admin.module.cluster.mapper.ClusterMapper;
import com.lanting.admin.module.cluster.result.ClusterResultCode;
import com.lanting.admin.module.cluster.event.ClusterDeletedEvent;
import com.lanting.admin.module.cluster.event.ClusterUpdatedEvent;
import com.lanting.common.cli.FlinkCliException;
import com.lanting.common.cli.FlinkCliUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 集群管理服务。
 * <p>
 * Service 层只抛 {@link BusinessException}，不返回 {@link com.lanting.admin.common.result.Result}。
 * 参数格式校验由上层的 DTO {@code @Valid} 注解负责。
 *
 * @author wangzhao
 */
@Service
public class ClusterService extends BasicServiceImpl<ClusterMapper, ClusterEntity> {

    // ==================== 查询 ====================

    /**
     * 获取所有集群列表。
     */
    public List<ClusterEntity> listAll() {
        return list(new LambdaQueryWrapper<ClusterEntity>()
                .orderByDesc(ClusterEntity::getCreateTime));
    }

    /**
     * 根据 ID 获取集群，不存在时抛异常。
     */
    public ClusterEntity getClusterById(Long id) {
        ClusterEntity cluster = getById(id);
        if (cluster == null) {
            throw new BusinessException(ClusterResultCode.CLUSTER_NOT_FOUND);
        }
        return cluster;
    }

    /**
     * 根据名称查询集群（用于唯一性校验）。
     */
    private ClusterEntity getByName(String name) {
        return getOne(new LambdaQueryWrapper<ClusterEntity>()
                .eq(ClusterEntity::getName, name));
    }

    // ==================== 创建 ====================

    /**
     * 创建集群。
     * <p>
     * 校验：名称唯一；部署目标合法性；资源类型从 Enum 派生。
     */
    public ClusterEntity create(CreateClusterDTO dto) {
        // 1. 名称唯一性检查
        if (getByName(dto.getName()) != null) {
            throw new BusinessException(ClusterResultCode.CLUSTER_NAME_DUPLICATE);
        }

        // 2. 部署目标校验 + 派生资源类型
        DeployTarget target = resolveDeployTarget(dto.getDeployTarget());

        // 3. 检测 Flink 版本
        String flinkVersion = checkFlinkVersion(dto.getFlinkHome());

        ClusterEntity cluster = new ClusterEntity();
        cluster.setName(dto.getName());
        cluster.setFlinkHome(dto.getFlinkHome());
        cluster.setFlinkVersion(flinkVersion);
        cluster.setResourceType(target.getResourceType());
        cluster.setDeployTarget(target.getValue());
        cluster.setConfigurations(dto.getConfigurations());
        cluster.setStatus("ACTIVE");
        save(cluster);
        return cluster;
    }

    // ==================== 编辑 ====================

    /**
     * 编辑集群。
     * <p>
     * 名称唯一性检查排除自身；部署目标变更时重新检测版本。
     */
    public ClusterEntity update(UpdateClusterDTO dto) {
        ClusterEntity exist = getClusterById(dto.getId());

        // 名称唯一性检查（排除自身）
        ClusterEntity conflict = getByName(dto.getName());
        if (conflict != null && !conflict.getId().equals(dto.getId())) {
            throw new BusinessException(ClusterResultCode.CLUSTER_NAME_DUPLICATE);
        }

        // 部署目标校验 + 派生资源类型
        DeployTarget target = resolveDeployTarget(dto.getDeployTarget());

        // FLINK_HOME 或部署目标变更时重新检测版本
        String flinkVersion = exist.getFlinkVersion();
        if (!dto.getFlinkHome().equals(exist.getFlinkHome())
                || !target.getValue().equals(exist.getDeployTarget())) {
            flinkVersion = checkFlinkVersion(dto.getFlinkHome());
        }

        exist.setName(dto.getName());
        exist.setFlinkHome(dto.getFlinkHome());
        exist.setFlinkVersion(flinkVersion);
        exist.setResourceType(target.getResourceType());
        exist.setDeployTarget(target.getValue());
        exist.setConfigurations(dto.getConfigurations());
        updateById(exist);

        publishEventSafely(new ClusterUpdatedEvent(this,
                exist.getId(), exist.getName(), exist.getFlinkVersion(),
                exist.getResourceType(), exist.getDeployTarget(), exist.getStatus()));

        return exist;
    }

    // ==================== 删除 ====================

    /**
     * 删除集群（逻辑删除）。
     */
    public void delete(Long id) {
        ClusterEntity cluster = getClusterById(id); // 确保存在并拿到实体
        removeById(id);
        publishEventSafely(new ClusterDeletedEvent(this, cluster.getId(), cluster.getName()));
    }

    // ==================== 状态切换 ====================

    /**
     * 切换集群启用/禁用状态。
     */
    public ClusterEntity toggleStatus(Long id) {
        ClusterEntity cluster = getClusterById(id);
        cluster.setStatus("ACTIVE".equals(cluster.getStatus()) ? "INACTIVE" : "ACTIVE");
        updateById(cluster);
        return cluster;
    }

    // ==================== 工具方法 ====================

    /**
     * 解析部署目标字符串，校验合法性并返回对应的枚举。
     */
    private DeployTarget resolveDeployTarget(String deployTarget) {
        try {
            return DeployTarget.fromValue(deployTarget);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ClusterResultCode.DEPLOY_TARGET_INVALID);
        }
    }

    /**
     * 检测 Flink 版本号。
     * <p>
     * 通过执行 {@code {flinkHome}/bin/flink --version} 解析版本号，
     * 实际逻辑委托给 {@link FlinkCliUtil#checkVersion(String)}。
     */
    public String checkFlinkVersion(String flinkHome) {
        try {
            return FlinkCliUtil.checkVersion(flinkHome);
        } catch (FlinkCliException e) {
            throw new BusinessException(ClusterResultCode.FLINK_VERSION_DETECT_FAILED);
        }
    }
}
