package com.lanting.admin.module.file.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lanting.admin.common.result.Result;
import com.lanting.admin.module.file.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private WorkspaceService workspaceService;

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
    @PutMapping("/config")
    public Result<Void> saveWorkspaceConfig(@RequestBody JsonNode body) {
        workspaceService.updateDefaultWorkspaceConfig(body.toString());
        return Result.success();
    }
}
