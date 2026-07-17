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
import jakarta.validation.constraints.NotNull;
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
    public Result<String> content(@RequestParam @NotNull Long fileId) {
        return Result.success(gitFileService.content(fileId));
    }

    /**
     * 创建空文件。只写磁盘空文件 + DB INSERT，不抢锁、不写入内容、不产生 commit。
     */
    @Operation(summary = "创建文件")
    @PostMapping("/create")
    public Result<FileCreatedVO> create(@Valid @RequestBody CreateFileDTO dto) {
        return Result.success(gitFileService.create(dto));
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
     * 创建文件夹。目录结构由 DB 索引维护，创建成功后返回文件夹 ID。
     */
    @Operation(summary = "创建文件夹")
    @PostMapping("/folder")
    public Result<FileCreatedVO> folder(@Valid @RequestBody CreateFolderDTO dto) {
        return Result.success(gitFileService.createFolder(dto));
    }

    /**
     * 重命名文件或文件夹。文件重命名需持锁，文件夹重命名不检查锁，不产生 commit。
     */
    @Operation(summary = "重命名文件或文件夹")
    @PostMapping("/rename")
    public Result<PathRenamedVO> rename(@Valid @RequestBody RenameDTO dto) {
        return Result.success(gitFileService.rename(dto));
    }

    /**
     * 删除文件或文件夹（软删除 + 自动生成 Git delete commit）。
     */
    @Operation(summary = "删除文件或文件夹")
    @DeleteMapping
    public Result<Void> delete(@RequestParam @NotNull Long fileId) {
        gitFileService.delete(fileId);
        return Result.success();
    }

    /**
     * 回收站文件树。返回指定父路径下已软删除的文件/文件夹。
     */
    @Operation(summary = "回收站文件树")
    @GetMapping("/trash")
    public Result<List<FileTreeNode>> trash(@RequestParam(defaultValue = "") String parentPath) {
        return Result.success(gitFileService.trash(parentPath));
    }

    /**
     * 从回收站恢复文件/文件夹。commitHash 为空时从 HEAD 恢复。
     */
    @Operation(summary = "恢复文件或文件夹")
    @PostMapping("/trash/restore")
    public Result<Void> restore(@Valid @RequestBody RestoreFileDTO dto) {
        gitFileService.restore(dto);
        return Result.success();
    }

    /**
     * 从回收站彻底删除文件/文件夹。仅允许删除已软删除（deleted_at > 0）的条目。
     */
    @Operation(summary = "彻底删除文件或文件夹")
    @DeleteMapping("/trash/purge")
    public Result<Void> purge(@RequestParam @NotNull Long fileId) {
        gitFileService.purge(fileId);
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
    public Result<String> diff(@RequestParam @NotNull Long fileId,
                               @RequestParam @NotBlank String from,
                               @RequestParam @NotBlank String to) {
        return Result.success(gitFileService.diff(fileId, from, to));
    }

    /**
     * 文件级回滚。读取目标 commit 中该文件的内容覆盖当前文件，不产生新 commit。
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
        return Result.success(fileLockService.acquire(dto.getFileId(), currentUser()));
    }

    /**
     * 释放锁。只有当前持锁人自己可以释放。
     */
    @Operation(summary = "释放锁")
    @PostMapping("/lock/release")
    public Result<Void> releaseLock(@Valid @RequestBody LockDTO dto) {
        boolean released = fileLockService.release(dto.getFileId(), currentUser());
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
