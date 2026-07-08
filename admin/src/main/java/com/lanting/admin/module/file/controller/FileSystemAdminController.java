package com.lanting.admin.module.file.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lanting.admin.common.result.Result;
import com.lanting.admin.module.file.service.FileIndexService;
import com.lanting.admin.module.file.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;

/**
 * 文件系统管理接口。
 *
 * @author wangzhao
 */
@Tag(name = "文件系统管理")
@RestController
@RequestMapping("/api/admin/fs")
public class FileSystemAdminController {

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private FileIndexService fileIndexService;

    /**
     * 手动触发一致性校验（同步执行）。
     */
    @Operation(summary = "手动触发一致性校验")
    @SaCheckPermission("file:admin")
    @PostMapping("/reconcile")
    public Result<Map<String, Object>> reconcile(
            @RequestParam(required = false) String scope) {
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Map<String, Object> report = fileIndexService.reconcile(root, scope);
        return Result.success(report);
    }

    @Operation(summary = "手动触发一致性修复（DB重建部分索引）")
    @SaCheckPermission("file:admin")
    @PostMapping("/repair")
    public Result<Map<String, Object>> repair(
            @RequestParam(required = false) String scope) {
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Map<String, Object> report = fileIndexService.repair(
                root, FileIndexService.RepairMode.DISK_WINS, scope);
        return Result.success(report);
    }

    /**
     * 查询当前索引状态。
     */
    @Operation(summary = "查询文件索引状态")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> status = fileIndexService.status();
        return Result.success(status);
    }
}
