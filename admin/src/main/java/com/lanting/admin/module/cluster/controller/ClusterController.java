package com.lanting.admin.module.cluster.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lanting.admin.common.result.Result;
import com.lanting.admin.module.cluster.dto.CreateClusterDTO;
import com.lanting.admin.module.cluster.dto.UpdateClusterDTO;
import com.lanting.admin.module.cluster.entity.ClusterEntity;
import com.lanting.admin.module.cluster.service.ClusterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 集群管理接口。
 * <p>
 * 列表和详情接口所有登录用户可访问；
 * 新建、编辑、删除、状态切换需 {@code cluster:admin} 权限。
 *
 * @author wangzhao
 */
@Tag(name = "集群管理")
@Validated
@RestController
@RequestMapping("/api/clusters")
public class ClusterController {

    @Autowired
    private ClusterService clusterService;

    // ==================== 查询接口 ====================

    /**
     * 获取集群列表。
     */
    @Operation(summary = "获取集群列表")
    @GetMapping
    public Result<List<ClusterEntity>> list() {
        return Result.success(clusterService.listAll());
    }

    /**
     * 获取集群详情。
     */
    @Operation(summary = "获取集群详情")
    @GetMapping("/{id}")
    public Result<ClusterEntity> get(@PathVariable @NotNull Long id) {
        return Result.success(clusterService.getClusterById(id));
    }

    // ==================== 管理接口 ====================

    /**
     * 新建集群。
     */
    @Operation(summary = "新建集群")
    @SaCheckPermission("cluster:admin")
    @PostMapping
    public Result<ClusterEntity> create(@Valid @RequestBody CreateClusterDTO dto) {
        return Result.success(clusterService.create(dto));
    }

    /**
     * 编辑集群。
     */
    @Operation(summary = "编辑集群")
    @SaCheckPermission("cluster:admin")
    @PutMapping("/{id}")
    public Result<ClusterEntity> update(@PathVariable Long id,
                                        @Valid @RequestBody UpdateClusterDTO dto) {
        dto.setId(id);
        return Result.success(clusterService.update(dto));
    }

    /**
     * 删除集群（逻辑删除）。
     */
    @Operation(summary = "删除集群")
    @SaCheckPermission("cluster:admin")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @NotNull Long id) {
        clusterService.delete(id);
        return Result.success();
    }

    /**
     * 切换集群启用/禁用状态。
     */
    @Operation(summary = "切换集群启用/禁用状态")
    @SaCheckPermission("cluster:admin")
    @PutMapping("/{id}/status")
    public Result<ClusterEntity> toggleStatus(@PathVariable @NotNull Long id) {
        return Result.success(clusterService.toggleStatus(id));
    }

    // ==================== 工具接口 ====================

    /**
     * 检测 Flink 版本号。
     */
    @Operation(summary = "检测 Flink 版本号")
    @SaCheckPermission("cluster:admin")
    @GetMapping("/check-version")
    public Result<String> detectVersion(@RequestParam @NotBlank String flinkHome) {
        String version = clusterService.checkFlinkVersion(flinkHome);
        return Result.success(version);
    }
}
