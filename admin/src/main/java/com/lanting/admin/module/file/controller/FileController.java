package com.lanting.admin.module.file.controller;

import com.lanting.admin.common.page.PageQuery;
import com.lanting.admin.common.page.PageResult;
import com.lanting.admin.common.result.Result;
import com.lanting.admin.module.file.dto.*;
import com.lanting.admin.module.file.result.FileResultCode;
import com.lanting.admin.module.file.service.FileLockService;
import com.lanting.admin.module.file.service.GitFileService;
import com.lanting.admin.module.file.service.PublishService;
import com.lanting.admin.module.file.service.ReviewService;
import com.lanting.admin.module.file.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.lanting.admin.common.util.SecurityUtils.currentUser;

/**
 * 文件系统接口。
 *
 * @author wangzhao
 */
@Tag(name = "文件系统")
@Validated
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final GitFileService gitFileService;

    private final FileLockService fileLockService;

    private final PublishService publishService;

    private final ReviewService reviewService;

    public FileController(GitFileService gitFileService, FileLockService fileLockService, PublishService publishService, ReviewService reviewService) {
        this.gitFileService = gitFileService;
        this.fileLockService = fileLockService;
        this.publishService = publishService;
        this.reviewService = reviewService;
    }

    // ==================== 通用文件操作 ====================

    /**
     * 获取文件树。按层级从 DB 索引查询，节点附带软锁状态。
     * <p>
     * parentPath 为空字符串表示根层级；展开文件夹时传入文件夹路径获取子层级。
     */
    @Operation(summary = "获取文件树")
    @GetMapping("/tree")
    public Result<List<FileTreeNode>> tree(@RequestParam(defaultValue = "") String parentPath,
                                           @RequestParam(defaultValue = "name") String sort) {
        return Result.success(gitFileService.tree(parentPath, sort));
    }

    /**
     * 读取文件内容。返回磁盘当前内容，包含自动保存但未提交的数据。
     */
    @Operation(summary = "读取文件内容")
    @GetMapping("/content")
    public Result<String> content(@RequestParam @NotBlank String path) {
        return Result.success(gitFileService.content(path));
    }

    /**
     * 自动保存文件到磁盘。要求当前用户已锁定该文件。
     */
    @Operation(summary = "自动保存文件")
    @PostMapping("/save")
    public Result<Void> save(@Valid @RequestBody SaveFileDTO dto) {
        gitFileService.save(dto);
        return Result.success();
    }

    /**
     * 创建文件夹。目录结构由 DB 索引维护，创建成功后自动提交。
     */
    @Operation(summary = "创建文件夹")
    @PostMapping("/folder")
    public Result<Void> folder(@Valid @RequestBody CreateFolderDTO dto) {
        gitFileService.createFolder(dto);
        return Result.success();
    }

    /**
     * 删除文件或文件夹。文件夹删除时若存在他人锁定文件且未 force，返回 30712 部分文件被锁定。
     */
    @Operation(summary = "删除文件或文件夹")
    @DeleteMapping
    public Result<DeleteLockedVO> delete(@RequestParam @NotBlank String path,
                                         @RequestParam(defaultValue = "false") boolean force) {
        DeleteLockedVO locked = gitFileService.delete(path, force);
        if (locked != null) {
            return Result.error(FileResultCode.FILES_LOCKED, locked);
        }
        return Result.success();
    }

    /**
     * 提交文件。committed 为空时返回 30713 无可提交的文件。
     */
    @Operation(summary = "提交文件")
    @PostMapping("/commit")
    public Result<CommitResultVO> commit(@Valid @RequestBody CommitFileDTO dto) {
        CommitResultVO result = gitFileService.commit(dto);
        if (result.getCommitted().isEmpty()) {
            return Result.error(FileResultCode.NOTHING_TO_COMMIT, result);
        }
        return Result.success(result);
    }

    /**
     * 查询历史记录。已使用 HistoryPageQuery 统一分页校验，pageSize 默认与 PageQuery 一致为 10。
     */
    @Operation(summary = "查询历史记录")
    @GetMapping("/history")
    public Result<PageResult<FileHistoryVO>> history(@Valid HistoryPageQuery query) {
        return Result.success(gitFileService.history(query));
    }

    /**
     * 文件 diff。返回指定文件在两个 commit 之间的 unified diff 文本。
     */
    @Operation(summary = "文件 diff")
    @GetMapping("/diff")
    public Result<String> diff(@RequestParam @NotBlank String path,
                               @RequestParam @NotBlank String from,
                               @RequestParam @NotBlank String to) {
        return Result.success(gitFileService.diff(path, from, to));
    }

    /**
     * 文件级回滚。读取目标 commit 中该文件的内容覆盖当前文件，并产生一次新 commit。
     */
    @Operation(summary = "文件级回滚")
    @PostMapping("/revert")
    public Result<Void> revert(@Valid @RequestBody RevertFileDTO dto) {
        gitFileService.revert(dto);
        return Result.success();
    }

    // ==================== 文件锁 ====================

    /**
     * 抢锁。软锁允许强制抢占他人持有的锁，返回前持锁人信息。
     */
    @Operation(summary = "抢锁")
    @PostMapping("/lock/acquire")
    public Result<AcquireLockVO> acquireLock(@Valid @RequestBody LockDTO dto) {
        return Result.success(fileLockService.acquire(dto.getPath(), currentUser()));
    }

    /**
     * 释放锁。只有当前持锁人自己可以释放。
     */
    @Operation(summary = "释放锁")
    @PostMapping("/lock/release")
    public Result<Void> releaseLock(@Valid @RequestBody LockDTO dto) {
        boolean released = fileLockService.release(dto.getPath(), currentUser());
        if (!released) {
            return Result.error(com.lanting.admin.common.result.CommonResultCode.FORBIDDEN);
        }
        return Result.success();
    }

    // ==================== 发布与回滚 ====================

    /**
     * 发布。对当前 HEAD 打 tag，磁盘上未提交的变更不影响发布内容。
     */
    @Operation(summary = "发布")
    @PostMapping("/publish")
    public Result<PublishVO> publish(@Valid @RequestBody PublishDTO dto) {
        return Result.success(publishService.publish(dto, currentUser()));
    }

    /**
     * 查询发布历史。已使用 PublishPageQuery 统一分页校验，pageSize 默认与 PageQuery 一致为 10。
     */
    @Operation(summary = "查询发布历史")
    @GetMapping("/publish")
    public Result<PageResult<PublishVO>> publishList(@Valid PageQuery query) {
        return Result.success(publishService.list(query));
    }

//    /**
//     * 发布级回滚预检。列出目标 tag 中当前被他人锁定的文件，供前端二次确认。
//     */
//    @Operation(summary = "发布级回滚预检")
//    @PostMapping("/rollback-release/check")
//    public Result<RollbackCheckVO> rollbackCheck(@Valid @RequestBody RollbackReleaseDTO dto) {
//        return Result.success(gitFileService.rollbackCheck(dto.getTagName()));
//    }
//
//    /**
//     * 发布级回滚。将目标 tag 中所有文件覆盖回当前工作空间，并产生一次新 commit。
//     */
//    @Operation(summary = "发布级回滚")
//    @PostMapping("/rollback-release")
//    public Result<PublishVO> rollbackRelease(@Valid @RequestBody RollbackReleaseDTO dto) {
//        return Result.success(gitFileService.rollbackRelease(dto.getTagName()));
//    }

    // ==================== Review ====================

    /**
     * 添加 review。社区版 review 为轻量标记，不触发审批流程。
     */
    @Operation(summary = "添加 review")
    @PostMapping("/review")
    public Result<Void> review(@Valid @RequestBody ReviewDTO dto) {
        reviewService.add(dto.getTagName(), dto.getComment(), currentUser());
        return Result.success();
    }

    /**
     * 查询某个发布的 review 列表。
     */
    @Operation(summary = "查询 review 列表")
    @GetMapping("/review")
    public Result<List<ReviewVO>> reviewList(@RequestParam @NotBlank String tagName) {
        return Result.success(reviewService.list(tagName));
    }
}
