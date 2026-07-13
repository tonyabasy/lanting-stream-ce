package com.lanting.admin.module.file.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.JsonNode;
import com.lanting.admin.common.result.Result;
import com.lanting.admin.module.file.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 工作空间接口。
 *
 * @author wangzhao
 */
@Tag(name = "工作空间")
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /**
     * 获取当前用户空间根目录。
     */
    @Operation(summary = "获取工作空间根目录")
    @GetMapping("/root")
    public Result<String> getDefaultWorkspaceRoot() {
        String path = workspaceService.getDefaultWorkspaceRoot().toString();
        return Result.success(path);
    }

    /**
     * 读取当前工作空间配置。
     */
    @Operation(summary = "读取工作空间配置")
    @GetMapping("/config")
    public Result<String> getWorkspaceConfig() {
        return Result.success(workspaceService.getDefaultWorkspaceConfig());
    }

    /**
     * 保存当前工作空间配置。
     */
    @Operation(summary = "保存工作空间配置")
    @SaCheckPermission("file:admin")
    @PutMapping("/config")
    public Result<Void> saveWorkspaceConfig(@RequestBody JsonNode body) {
        workspaceService.updateDefaultWorkspaceConfig(body.toString());
        return Result.success();
    }
}
