package com.lanting.admin.module.file.service;

import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.common.exception.ContentInconsistentException;
import com.lanting.admin.common.page.PageResult;
import com.lanting.admin.common.result.CommonResultCode;
import com.lanting.admin.common.util.HappyRun;
import com.lanting.admin.module.file.dto.*;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import com.lanting.admin.module.file.event.PathRenamedEvent;
import com.lanting.admin.module.file.result.FileResultCode;
import com.lanting.admin.module.file.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.lanting.admin.common.util.SecurityUtils.currentUser;
import static com.lanting.admin.module.file.entity.FileIndexEntity.FOLDER;

/**
 * 基于 Git 的文件服务，file 模块的核心。
 * <p>
 * 设计要点（详见 docs/backend/file-system-spec.md）：
 * <ul>
 *   <li><b>磁盘 = 工作区</b>：自动保存只写磁盘不进 Git；只有用户主动提交才产生 commit，
 *       因此磁盘内容可能领先于 Git HEAD（存在未提交变更是正常状态）。</li>
 *   <li><b>软删除</b>：delete 只将索引标记为 deleted_at &gt; 0 并删除磁盘文件，自动产生 commit；</li>
 *   <li><b>软锁</b>：写操作（保存/提交/删除/回滚/恢复）要求持有文件锁，但锁可被他人强制接管，
 *       锁的语义由 {@link FileLockService} 维护；读操作不需要锁。</li>
 *   <li><b>并发模型</b>：文件锁保证"同一文件同一时刻只有一人编辑"；工作空间锁
 *       {@link #withWorkspaceLock} 保证 Git 写操作（add/commit/tag）串行执行，
 *       两层锁职责不同，互不替代。</li>
 *   <li><b>发布 = Tag</b>：发布对当前 HEAD 打 tag，未提交的磁盘变更不纳入发布；
 *       回滚采用"读历史内容覆盖当前文件再 commit"的软回滚，不使用 git revert。</li>
 * </ul>
 * <p>
 *
 * @author wangzhao
 */
@Slf4j
@Service
public class GitFileService {

    public static final byte[] EMPTY_CONTENT = new byte[0];

    private final WorkspaceService workspaceService;

    private final FileLockService fileLockService;

    private final FileIndexService fileIndexService;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 允许的文件扩展名白名单，写入时校验（读取不限制，兼容历史遗留文件）
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("sql", "md", "html", "json", "ddl");

    /**
     * 单文件大小上限；文本文件超过此值基本可判定为误传或恶意请求
     */
    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1MB

    /**
     * Git 写操作锁。所有 Git 写操作（add/commit/tag）必须经过此方法串行化，
     * 避免并发 commit 导致对象库状态不一致。
     */
    private final ReentrantLock gitWriteLock = new ReentrantLock();

    public GitFileService(WorkspaceService workspaceService, FileLockService fileLockService,
                          FileIndexService fileIndexService, ApplicationEventPublisher eventPublisher) {
        this.workspaceService = workspaceService;
        this.fileLockService = fileLockService;
        this.fileIndexService = fileIndexService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 在 Git 写锁内执行操作。所有 Git 写操作必须经过此方法串行化。
     * <p>
     * ReentrantLock 可重入，嵌套调用安全。EE 分布式部署时替换此方法实现
     * 为分布式锁即可，调用方无需改动（见 extension-points-watchlist.md）。
     * <p>
     * <b>锁顺序纪律（防死锁）</b>：允许在文件锁临界区内（{@link FileLockService#doIfHolder}）
     * 获取本锁，即 fileId stripe → gitWriteLock；但在本锁内<b>绝不允许</b>再调用
     * {@code doIfHolder}/{@code acquire}/{@code release}/{@code forceRelease}，
     * 反向嵌套会与 save/delete/revert/restore 构成死锁。
     */
    private <T> T withWorkspaceLock(Supplier<T> action) {
        gitWriteLock.lock();
        try {
            return action.get();
        } finally {
            gitWriteLock.unlock();
        }
    }

    /**
     * 根据文件 ID 解析当前路径。包含已删除记录，用于 history/diff/revert/restore。
     */
    private String resolvePathByFileId(Long fileId) {
        FileIndexEntity entity = fileIndexService.getById(fileId, FileIndexService.INCLUDE_DELETED);
        if (entity == null) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
        }
        return entity.getPath();
    }

    /**
     * 文件树。按层级从 DB 索引查询，并附带每个节点的软锁状态。
     * <p>
     * 前端按需懒加载：首次请求根层级（{@code parentPath=""}），展开文件夹时再请求子层级。
     */
    public List<FileTreeNode> tree(String parentPath, String sort) {
        // parentPath 为空字符串表示根目录，允许；非空时按普通路径校验
        if (!StringUtils.isEmpty(parentPath)) {
            validatePath(parentPath);
        }

        parentPath = parentPath == null ? "" : parentPath;
        List<FileIndexEntity> children = fileIndexService.listDirectlyChildren(parentPath);
        List<FileTreeNode> nodes = children.stream()
                .map(entity -> FileTreeNode.of(entity, fileLockService.getHolder(entity.getId()), fileLockService.getLockedAt(entity.getId())))
                .collect(Collectors.toList());
        sortTreeNodes(nodes, sort);
        return nodes;
    }

    /**
     * 排序树节点。
     * <p>
     * {@code mtime}：按索引表中的 mtime 倒序；其他值一律按文件名升序。
     */
    private void sortTreeNodes(List<FileTreeNode> nodes, String sort) {
        if ("mtime".equals(sort)) {
            nodes.sort((a, b) -> {
                Long ta = a.getMtime() == null ? 0L : a.getMtime();
                Long tb = b.getMtime() == null ? 0L : b.getMtime();
                return tb.compareTo(ta);
            });
        } else {
            nodes.sort(Comparator.comparing(FileTreeNode::getName));
        }
    }

    /**
     * 返回给定文件中存在未提交变更的路径。
     * {@code modified}: 已跟踪文件的未暂存修改，{@code untracked}: 从未提交过的新文件。
     */
    public UncommitVO uncommit(List<Long> fileIds) {
        List<FileIndexEntity> entities = fileIndexService.listByIds(fileIds);
        if (entities.isEmpty()) {
            return new UncommitVO();
        }

        List<String> filePaths = entities.stream().map(FileIndexEntity::getPath).toList();
        Status status = gitStatus(filePaths);

        UncommitVO vo = new UncommitVO();
        vo.setModified(new ArrayList<>(status.getModified()));
        vo.setUntracked(new ArrayList<>(status.getUntracked()));
        return vo;
    }

    /**
     * 读取磁盘当前文件内容。读取不需要持锁，返回内容包含自动保存但未提交的数据。
     * <p>
     * 已删除文件返回 FILE_NOT_FOUND（30702），因为磁盘文件已不存在；如需查看历史内容，
     * 使用 history 或回收站接口。
     * <p>
     * 每次读取都会比对 DB 索引中的 CRC32：不一致时仍抛出磁盘真实内容，由全局异常处理器返回
     * {@code code=30714} 但 {@code data=磁盘内容} 的响应。
     */
    public String content(Long fileId) {
        FileIndexEntity entity = fileIndexService.getById(fileId, FileIndexService.INCLUDE_DELETED);
        if (entity == null || entity.getDeletedAt() > 0) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND, String.valueOf(fileId));
        }

        String path = entity.getPath();
        validatePath(path);
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path filePath = root.resolve(path).toAbsolutePath().normalize();
        ensureInsideWorkspace(filePath, root);
        if (!Files.exists(filePath)) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
        }
        // 目录不是可读内容，复用"文件类型不允许"错误码而非 404，让前端能区分两种情况
        if (Files.isDirectory(filePath)) {
            throw new BusinessException(FileResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        try {
            // 最大长度检查
            long fileSize = Files.size(filePath);
            if (fileSize > MAX_FILE_SIZE) {
                throw new BusinessException(FileResultCode.FILE_SIZE_EXCEEDED, path);
            }

            byte[] bytes = Files.readAllBytes(filePath);
            String content = new String(bytes, StandardCharsets.UTF_8);

            long diskCrc32 = FileIndexService.crc32(bytes);
            if (entity.getCrc32() != diskCrc32) {
                throw new ContentInconsistentException(FileResultCode.FILE_CONTENT_INCONSISTENT, "文件内容与索引不一致，请执行索引修复", content);
            }

            return content;
        } catch (IOException e) {
            throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
        }
    }

    /**
     * 自动保存文件到磁盘。要求当前用户已锁定该文件，否则抛 30709 文件已被锁定。
     * <p>
     * 持锁校验与写磁盘通过 {@link FileLockService#doIfHolder} 在同一临界区内原子执行，
     * 保证写入期间锁不会被他人抢走——避免接管者打开文件后，前持锁人的迟到写入仍落盘
     * 造成内容不一致。
     */
    public void save(SaveFileDTO dto) {
        if (StringUtils.isEmpty(dto.getContent())) {
            return;
        }

        FileIndexEntity entity = fileIndexService.getById(dto.getFileId());
        if (entity == null) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
        }
        String path = entity.getPath();

        validatePath(path);
        String username = currentUser();
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path filePath = root.resolve(path).toAbsolutePath().normalize();
        ensureInsideWorkspace(filePath, root);
        validateFileType(filePath);
        if (!Files.exists(filePath)) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
        }

        try {
            fileLockService.doIfHolder(entity.getId(), entity.getPath(), username, () -> {
                byte[] bytes = dto.getContent().getBytes(StandardCharsets.UTF_8);
                // 按 UTF-8 编码后的字节数校验，而非字符数：中文内容编码后体积会膨胀
                if (bytes.length > MAX_FILE_SIZE) {
                    throw new BusinessException(FileResultCode.FILE_SIZE_EXCEEDED);
                }
                // 整体覆盖（自动保存总是全量写入）
                Files.write(filePath, bytes);
                // 磁盘写入成功后更新索引，直接用内存 bytes 计算 CRC32 避免重复读盘
                fileIndexService.indexOnSave(path, root, bytes);
                return null;
            });
        } catch (IOException e) {
            throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 创建空文件。只写磁盘空文件 + DB INSERT，不抢锁、不写入内容、不产生 commit。
     */
    public FileCreatedVO create(CreateFileDTO dto) {
        String path = dto.getPath();
        validatePath(path);
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path filePath = root.resolve(path).toAbsolutePath().normalize();
        ensureInsideWorkspace(filePath, root);
        validateFileType(filePath);

        try {
            if (Files.exists(filePath)) {
                throw new BusinessException(FileResultCode.FILE_ALREADY_EXISTS);
            }
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, EMPTY_CONTENT, StandardOpenOption.CREATE);
            fileIndexService.indexOnSave(path, root, EMPTY_CONTENT);
            FileIndexEntity entity = fileIndexService.getByPath(path);
            // 谁创建，谁持有
            fileLockService.acquire(entity.getId(), entity.getPath(), currentUser());
            return new FileCreatedVO(entity.getId(), path);
        } catch (IOException e) {
            throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
        }
    }

    /**
     * 创建文件夹。新路径无并发冲突风险，不需要 Git 写锁；
     * 目录结构由 {@link FileIndexService} 维护，不再写入 .gitkeep 占位。
     */
    public FileCreatedVO createFolder(CreateFolderDTO dto) {
        String path = dto.getPath();
        validatePath(path);
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path folderPath = root.resolve(path).toAbsolutePath().normalize();
        ensureInsideWorkspace(folderPath, root);

        String currentUser = currentUser();
        fileLockService.acquireFolderLock(dto.getPath(), currentUser);
        try {
            if (Files.exists(folderPath)) {
                throw new BusinessException(FileResultCode.FILE_ALREADY_EXISTS);
            }
            Files.createDirectories(folderPath);
            fileIndexService.indexOnCreate(path, "folder", root);
            FileIndexEntity entity = fileIndexService.getByPath(path);
            return new FileCreatedVO(entity.getId(), path);
        } catch (IOException e) {
            throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
        } finally {
            fileLockService.releaseFolderLock(dto.getPath(), currentUser);
        }
    }

    /**
     * 重命名文件或文件夹。
     * <p>
     * 文件重命名要求当前用户持有该文件锁；文件夹重命名不检查锁，直接递归更新。
     * 不产生 Git commit，成功后会发布 {@link PathRenamedEvent}。
     */
    public PathRenamedVO rename(RenameDTO dto) {
        Long fileId = dto.getFileId();
        FileIndexEntity entity = fileIndexService.getById(fileId);
        if (entity == null) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
        }

        String oldPath = entity.getPath();
        String parentPath = entity.getParentPath();
        String newPath = StringUtils.isEmpty(parentPath) ? dto.getNewName() : parentPath + "/" + dto.getNewName();
        validatePath(newPath);

        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path oldFilePath = root.resolve(oldPath).toAbsolutePath().normalize();
        Path newFilePath = root.resolve(newPath).toAbsolutePath().normalize();

        ensureInsideWorkspace(oldFilePath, root);
        ensureInsideWorkspace(newFilePath, root);

        // 如果rename前后的位置相同，快速返回
        if (oldFilePath.equals(newFilePath)) {
            return new PathRenamedVO(entity.getId(), oldPath, newPath);
        }
        if (Files.exists(newFilePath)) {
            throw new BusinessException(FileResultCode.FILE_ALREADY_EXISTS);
        }

        if (!Files.exists(oldFilePath)) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
        }

        if (entity.isFile()) {
            validateFileType(newFilePath);
        }

        String currentUser = currentUser();
        if (entity.isFile()) {
            // rename file 需要抢锁
            try {
                fileLockService.doIfHolder(entity.getId(), entity.getPath(), currentUser, () -> {
                    Files.move(oldFilePath, newFilePath);
                    fileIndexService.indexOnRename(fileId, newPath);
                    return null;
                });
            } catch (IOException e) {
                throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                fileLockService.acquireFolderLock(oldPath, currentUser);
                fileLockService.acquireFolderLock(newPath, currentUser);
                Files.move(oldFilePath, newFilePath);
                fileIndexService.indexOnFolderRename(oldPath, newPath);
            } catch (IOException e) {
                throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
            } finally {
                fileLockService.releaseFolderLock(newPath, currentUser);
                fileLockService.releaseFolderLock(oldPath, currentUser);
            }
        }

        // 发布 rename 事件
        eventPublisher.publishEvent(new PathRenamedEvent(
                this, fileId, oldPath, newPath, entity.getType(), currentUser));
        return new PathRenamedVO(fileId, oldPath, newPath);
    }

    /**
     * 删除文件或文件夹（软删除 + 自动生成 Git commit）。文件夹会删除整体目录，包括目录中的所有子目录及文件。
     * <p>
     * 目录：先通过 {@link FileLockService#acquireFolderLock} 封门，再清场删除。
     * 文件：通过 {@link FileLockService#doIfHolder} 校验持锁后删除。
     * 删除前自动将未提交改动 commit（auto commit before delete），保证磁盘最新内容进入 Git 历史。
     * <p>
     * 支持容错：若磁盘文件已被外部提前删除，自动清理 DB 元数据索引并视情况补齐 Git 删除 commit，
     * 杜绝幽灵节点。
     */
    public void delete(Long fileId) {
        FileIndexEntity entity = fileIndexService.getById(fileId);
        if (entity == null) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
        }

        // 路径检查
        String path = entity.getPath();
        validatePath(path);
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path absolutePath = root.resolve(path).toAbsolutePath().normalize();
        ensureInsideWorkspace(absolutePath, root);

        String currentUser = currentUser();
        if (entity.isDirectory()) {
            fileLockService.acquireFolderLock(path, currentUser);
            try {
                doDelete(path, absolutePath);
            } catch (Exception e) {
                throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
            } finally {
                fileLockService.releaseFolderLock(path, currentUser);
            }
        } else {
            try {
                fileLockService.doIfHolder(entity.getId(), entity.getPath(), currentUser, () -> {
                    doDelete(path, absolutePath);
                    return null;
                });
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
            }
        }
    }

    private void doDelete(String path, Path absolutePath) {
        // auto commit before delete
        if (Files.exists(absolutePath)) {
            HappyRun.run(() ->
                            gitCommit(Collections.singletonList(path), Collections.emptyList(), "auto commit before delete")
                    , "删除前的自动备份：" + path, NoFilepatternException.class);
            HappyRun.run(() ->
                            FileUtils.forceDelete(absolutePath.toFile())
                    , "从磁盘删除：" + path, FileNotFoundException.class);
        }

        // delete commit
        RevCommit revCommit = HappyRun.run(() ->
                        gitCommit(Collections.emptyList(), Collections.singletonList(path), "delete " + path)
                , null, "删除后自动提交：" + path, NoFilepatternException.class);
        String commitHash = revCommit != null ? revCommit.getName() : null;
        long deleteTimestamp = revCommit != null ? revCommit.getCommitTime() * 1000L : System.currentTimeMillis();
        // DB 软删除
        fileIndexService.indexOnDelete(path, deleteTimestamp, commitHash);
    }

    /**
     * 提交文件。只提交调用方已锁定的文件；提交未锁定的文件抛异常。
     * <p>
     * 已采用多文件分段锁保护，确保从"校验持锁"到"JGit 提交落盘"的整个过程是原子操作，
     * 彻底杜绝提交期间因他人抢锁并保存导致的"脏提交"问题。
     */
    public CommitResultVO commit(List<Long> fileIds, String message) {
        String username = currentUser();
        List<String> filePathsToCommit = new ArrayList<>();
        List<Long> fileIdsToCommit = new ArrayList<>();
        List<FileIndexEntity> committedFileEntities = new ArrayList<>();

        // 乐观筛选：初步找出当前用户持有的文件（此阶段不加锁，减少锁竞争）
        Set<Long> notFoundFileIds = new HashSet<>(fileIds);
        List<FileIndexEntity> list = fileIndexService.listByIds(notFoundFileIds);
        for (FileIndexEntity entity : list) {
            String path = entity.getPath();
            validatePath(path);
            if (fileLockService.isHolder(entity.getId(), username)) {
                committedFileEntities.add(entity);
                fileIdsToCommit.add(entity.getId());
                filePathsToCommit.add(path);
                notFoundFileIds.remove(entity.getId());
            } else {
                // 未锁定的文件不允许提交，提交本质上是上一次变更，需要先获取锁
                throw new BusinessException(FileResultCode.FILE_LOCKED, entity.getPath());
            }
        }

        // 提交的文件请求和真实查询出来的文件数量不一致
        if (!notFoundFileIds.isEmpty()) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND, notFoundFileIds.toString());
        }

        // 多文件原子加锁：按 ID 排序后对分段锁进行排他性锁定并执行二次校验，
        // 保证 gitCommit 期间他人既无法抢走锁，也无法 save 写入新数据，确保 Git 历史纯净。
        return fileLockService.doIfHolder(fileIdsToCommit, filePathsToCommit, username, () -> {
            String hash = gitCommit(filePathsToCommit, Collections.emptyList(), message).getName();
            fileIndexService.updateCommitHashByIds(fileIdsToCommit, hash);

            CommitResultVO commitResult = new CommitResultVO();
            commitResult.setCommitHash(hash);
            commitResult.setCommitted(committedFileEntities);
            return commitResult;
        });
    }

    /**
     * 回收站文件树。返回指定父路径下 deleted_at &gt; 0 的文件/文件夹。
     */
    public List<FileTreeNode> trash(String parentPath) {
        if (!StringUtils.isEmpty(parentPath)) {
            validatePath(parentPath);
        }
        parentPath = parentPath == null ? "" : parentPath;
        List<FileIndexEntity> children = fileIndexService.listDirectlyChildren(parentPath, FileIndexService.ONLY_DELETED);
        List<FileTreeNode> nodes = new ArrayList<>();
        for (FileIndexEntity entity : children) {
            nodes.add(FileTreeNode.of(entity, fileLockService.getHolder(entity.getId()), fileLockService.getLockedAt(entity.getId())));
        }
        sortTreeNodes(nodes, "name");
        return nodes;
    }

    /**
     * 从回收站彻底删除文件/文件夹（purge）。
     * <p>
     * 仅允许删除已软删除（deleted_at &gt; 0）的条目。物理删除 DB 索引，磁盘已删无需处理，
     * Git 历史保留。删除前释放该条目及其子文件锁。
     */
    public void purge(Long fileId) {
        FileIndexEntity entity = fileIndexService.getById(fileId, FileIndexService.ONLY_DELETED);
        if (entity == null) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
        }

        // 路径检查
        String path = entity.getPath();
        validatePath(path);
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path absolutePath = root.resolve(path).toAbsolutePath().normalize();
        ensureInsideWorkspace(absolutePath, root);
        String currentUser = currentUser();
        try {
            if (entity.isDirectory()) {
                // purge folder
                fileLockService.acquireFolderLock(path, currentUser);

                List<FileIndexEntity> children = fileIndexService.listAllChildren(entity.getPath(), FileIndexService.ONLY_DELETED);
                // 先从索引中删除，避免用户操作文件/目录
                fileIndexService.deletePhysicallyByPathRecursively(path);
                if (!children.isEmpty()) {
                    children.forEach(f -> fileLockService.forceRelease(f.getId()));
                }
                FileUtils.forceDelete(absolutePath.toFile());
            } else {
                // purge single file
                // 先从索引中删除，避免用户操作文件/目录
                fileLockService.doIfHolder(entity.getId(), entity.getPath(), currentUser, () -> {
                    fileIndexService.deletePhysicallyByIds(Collections.singleton(fileId));
                    FileUtils.delete(absolutePath.toFile());
                    return null;
                });

            }
        } catch (FileNotFoundException e) {
            log.warn("从磁盘彻底删除文件/目录失败：{}，absolutePath={}", e.getMessage(), absolutePath);
        } catch (Exception e) {
            log.error("从磁盘彻底删除文件/目录失败：{}，absolutePath={}", e.getMessage(), absolutePath);
            throw new RuntimeException(e);
        } finally {
            fileLockService.forceRelease(fileId);
            fileLockService.forceReleaseFolderLock(path);
        }
    }

    /**
     * 恢复已删除文件或文件夹（仅恢复，不提交 Git，提交权在用户手里）。
     * <p>
     * commitHash 为空时从 HEAD 恢复。目录恢复只需在磁盘重建目录并重置 deleted_at；
     * 文件恢复需从指定 commit 读取内容写回磁盘，并更新索引 mtime/crc32。
     */
    public void restore(RestoreFileDTO dto) {
        List<FileIndexEntity> entities = fileIndexService.listByIds(dto.getFileIds(), FileIndexService.INCLUDE_DELETED);
        if (entities == null || entities.isEmpty()) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
        }

        Map<String, FileIndexEntity> restoreFolderPaths = new HashMap<>();
        Map<String, FileIndexEntity> restoreFilePaths = new HashMap<>();
        Path root = workspaceService.getDefaultWorkspaceRoot();
        for (FileIndexEntity entity : entities) {
            // 未删除的文件不能参与恢复
            if (entity.getDeletedAt() == 0) {
                throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, "文件未删除，无需恢复");
            }

            if (entity.isDirectory()) {
                restoreFolderPaths.put(entity.getPath(), entity);
            } else {
                restoreFilePaths.put(entity.getPath(), entity);
            }
        }

        // 从 Git 中恢复文件内容，先恢复文件是因为：如果 commitHash 不合法会立即抛异常，不会创建任何的目录
        // 而如果先恢复目录则会导致恢复了一半发现 commitHash 不合法，也没办法回滚了
        if (!restoreFilePaths.isEmpty()) {
            restoreFiles(root, dto.getCommitHash(), restoreFilePaths);
        }

        // 从回收站中与否目录结构
        if (!restoreFolderPaths.isEmpty()) {
            restoreFolder(root, restoreFolderPaths);
        }
    }

    /**
     * 从回收站中恢复目录
     */
    private void restoreFolder(Path root, Map<String, FileIndexEntity> restoreFolderPaths) {
        String currentUser = currentUser();
        restoreFolderPaths.forEach((path, entity) -> {
            // 路径校验
            validatePath(path);
            Path targetPath = root.resolve(path).toAbsolutePath().normalize();
            ensureInsideWorkspace(targetPath, root);

            try {
                // 恢复目录结构
                fileLockService.acquireFolderLock(path, currentUser);
                Files.createDirectories(targetPath);
                fileIndexService.indexOnCreate(entity.getPath(), FOLDER, root);
            } catch (IOException e) {
                throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
            } finally {
                fileLockService.releaseFolderLock(path, currentUser);
            }
        });
    }

    /**
     * 从回收站中恢复文件（必须持有锁才能操作已删除文件）
     */
    private void restoreFiles(Path root, String commitHash, Map<String, FileIndexEntity> restoreFilePaths) {
        String currentUser = currentUser();
        Map<String, byte[]> restoreFileContents = gitCatFiles(root, commitHash, restoreFilePaths.keySet());
        restoreFileContents.forEach((path, content) -> {
            // 路径校验
            validatePath(path);
            Path targetPath = root.resolve(path).toAbsolutePath().normalize();
            ensureInsideWorkspace(targetPath, root);

            // 恢复文件内容
            FileIndexEntity entity = restoreFilePaths.get(path);
            try {
                fileLockService.doIfHolder(entity.getId(), entity.getPath(), currentUser, () -> {
                    Path parent = targetPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.write(targetPath, content == null ? EMPTY_CONTENT : content);
                    fileIndexService.indexOnSave(entity.getPath(), root, content);
                    return null;
                });
            } catch (IOException e) {
                throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 查询 Git 文件历史记录（游标分页）。
     * <p>
     * 返回的 {@link PageResult 分页结果}中 total 与 totalPages 固定为 -1，
     * 通过 hasMore 判断是否有下一页。
     *
     * @param query 查询参数，path 为空字符串时查询整个仓库历史
     */
    public PageResult<FileHistoryVO> history(HistoryPageQuery query) {
        Long fileId = query.getFileId();
        if (fileId == null) {
            throw new BusinessException(CommonResultCode.PARAM_INVALID, "fileId 不能为空");
        }
        // 路径校验
        String path = resolvePathByFileId(fileId);
        validatePath(path);
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path targetPath = root.resolve(path).toAbsolutePath().normalize();
        ensureInsideWorkspace(targetPath, root);
        try (Git git = Git.open(root.toFile())) {
            LogCommand log = git.log();
            log.addPath(path);

            int page = query.getPageNum();
            int pageSize = query.getPageSize();
            int skip = (page - 1) * pageSize;
            // 多取一条用于判断是否有下一页，避免再查一次总数
            log.setSkip(skip).setMaxCount(pageSize + 1);

            List<FileHistoryVO> fileHistoryVOs = new ArrayList<>();
            boolean hasMore = false;
            for (RevCommit commit : log.call()) {
                if (fileHistoryVOs.size() == pageSize) {
                    hasMore = true;
                    break;
                }
                fileHistoryVOs.add(FileHistoryVO.of(commit));
            }

            return PageResult.ofHasMore(fileHistoryVOs, page, pageSize, hasMore);
        } catch (IOException | GitAPIException e) {
            throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
        }
    }

    /**
     * 计算指定文件在两个 commit 之间的 unified diff。
     * 支持新增文件（from == null）和删除文件（to == null），
     * 使用 JGit EmptyTreeIterator 处理空树场景。
     *
     * @param fileId 文件 ID
     * @param from   起始 Commit SHA，传 null 代表空树（新增文件，全量 ADD）
     * @param to     目标 Commit SHA，传 null 代表空树（删除文件，全量 DELETE）
     */
    public String diff(Long fileId, String from, String to) {
        String path = resolvePathByFileId(fileId);
        validatePath(path);
        if (from == null && to == null) {
            throw new BusinessException(CommonResultCode.PARAM_INVALID, "from 和 to 不能同时为空");
        }
        if (from != null && from.equals(to)) {
            return "";
        }
        Path root = workspaceService.getDefaultWorkspaceRoot();
        try (Git git = Git.open(root.toFile());
             RevWalk walk = new RevWalk(git.getRepository());
             ByteArrayOutputStream out = new ByteArrayOutputStream();
             DiffFormatter formatter = new DiffFormatter(out)) {

            Repository repository = git.getRepository();
            AbstractTreeIterator oldTree = from == null ? new EmptyTreeIterator() : prepareTreeParser(repository, walk, from);
            AbstractTreeIterator newTree = to == null ? new EmptyTreeIterator() : prepareTreeParser(repository, walk, to);

            formatter.setRepository(repository);
            List<DiffEntry> diffs = git.diff()
                    .setOldTree(oldTree)
                    .setNewTree(newTree)
                    .setPathFilter(PathFilter.create(path))
                    .call();

            if (diffs.isEmpty()) {
                boolean existsInFrom = from != null && existsInCommit(repository, walk, from, path);
                boolean existsInTo = to != null && existsInCommit(repository, walk, to, path);
                if (!existsInFrom && !existsInTo) {
                    throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
                }
                return "";
            }
            for (DiffEntry entry : diffs) {
                formatter.format(entry);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (InvalidObjectIdException e) {
            throw new BusinessException(CommonResultCode.PARAM_INVALID,
                    "非法的 commit hash: " + e.getMessage());
        } catch (IOException | GitAPIException e) {
            throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
        }
    }

    /**
     * 将 commit 解析为 Diff 所需的 TreeParser。
     */
    private AbstractTreeIterator prepareTreeParser(Repository repository, RevWalk walk, String commitHash)
            throws IOException {
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        ObjectId commitId = ObjectId.fromString(commitHash);
        RevCommit commit = walk.parseCommit(commitId);
        try (org.eclipse.jgit.lib.ObjectReader reader = repository.newObjectReader()) {
            treeParser.reset(reader, commit.getTree());
        }
        return treeParser;
    }

    /**
     * 检查指定 commit 中是否存在该文件。
     */
    private boolean existsInCommit(Repository repository, RevWalk walk, String commitHash, String path)
            throws IOException {
        RevCommit commit = walk.parseCommit(ObjectId.fromString(commitHash));
        return TreeWalk.forPath(repository, path, commit.getTree()) != null;
    }

    /**
     * 文件级回滚（软回滚）：读取目标 commit 中该文件的内容覆盖当前文件，只写磁盘不 commit。
     * 用户确认后可自行选择是否提交此次变更。
     * 不使用 git revert（多文件/合并场景易冲突，服务端无法交互式解决）。
     * <p>
     * 持锁校验与磁盘写入通过 {@link FileLockService#doIfHolder} 在同一临界区内原子执行。
     * 不需要 Git 写锁（没有 commit 操作）。
     */
    public void revert(RevertFileDTO dto) {
        Long fileId = dto.getFileId();
        FileIndexEntity entity = fileIndexService.getById(fileId);
        if (entity == null) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
        }
        String path = entity.getPath();
        validatePath(path);
        String username = currentUser();
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path filePath = root.resolve(path);
        try {
            fileLockService.doIfHolder(entity.getId(), entity.getPath(), username, () -> {
                byte[] bytes = gitCatFile(root, dto.getCommitHash(), path);
                Files.write(filePath, bytes == null ? EMPTY_CONTENT : bytes);
                // 磁盘写入成功后再更新索引（文件可能已不存在，用 UPSERT）
                fileIndexService.indexOnSave(path, root, bytes);
                return null;
            });
        } catch (InvalidObjectIdException e) {
            throw new BusinessException(CommonResultCode.PARAM_INVALID, e.getMessage());
        } catch (IOException e) {
            throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /* ----------------------------- Git 基础工具方法 ----------------------------- */

    /**
     * 提交指定路径到 Git。在工作空间锁内执行，可被已持锁方法嵌套调用（锁可重入）。
     * <p>
     * 对正常文件执行 {@code git add}，对已删除文件执行 {@code git rm}，
     * 从而支持软删除后通过 commit 将删除纳入 Git 历史。
     */
    private RevCommit gitCommit(List<String> pathsToAdd, List<String> pathsToRemove, String message) {
        Path root = workspaceService.getDefaultWorkspaceRoot();
        return withWorkspaceLock(() -> {
            try (Git git = Git.open(root.toFile())) {
                if (!pathsToAdd.isEmpty()) {
                    AddCommand add = git.add();
                    for (String path : pathsToAdd) {
                        add.addFilepattern(path);
                    }
                    add.call();
                }

                if (!pathsToRemove.isEmpty()) {
                    RmCommand rm = git.rm();
                    for (String path : pathsToRemove) {
                        rm.addFilepattern(path);
                    }
                    rm.call();
                }

                String username = currentUser();
                String email = username + "@lanting.io";
                return git.commit()
                        .setMessage(message)
                        .setAuthor(username, email)
                        .call();
            } catch (IOException | GitAPIException e) {
                throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
            }
        });
    }

    /**
     * 执行 git status，通过 configurer 配置需要检查的文件路径。
     */
    private Status gitStatus(List<String> filePaths) {
        Path root = workspaceService.getDefaultWorkspaceRoot();
        try (Git git = Git.open(root.toFile())) {
            StatusCommand cmd = git.status();
            if (filePaths != null && !filePaths.isEmpty()) {
                filePaths.forEach(cmd::addPath);
            }
            return cmd.call();
        } catch (IOException | GitAPIException e) {
            throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
        }
    }

    /**
     * 从指定 commit 或 HEAD 读取文件内容；文件不存在时抛 30702。
     */
    private Map<String, byte[]> gitCatFiles(Path root, String commitHash, Collection<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> notFoundFiles = new HashSet<>(filePaths);
        Map<String, byte[]> fileContents = new HashMap<>();
        try (Git git = Git.open(root.toFile());
             RevWalk rw = new RevWalk(git.getRepository());
             TreeWalk tw = new TreeWalk(git.getRepository())) {
            RevCommit commit = rw.parseCommit(ObjectId.fromString(commitHash));
            tw.addTree(commit.getTree());
            // 开启递归模式：这样游标遭遇文件夹时会自动深入，只在命中最终的文件(Blob)节点时才停下来
            tw.setRecursive(true);
            // 挂载路径组过滤器，让 TreeWalk 带着明确的目标去扫树
            tw.setFilter(PathFilterGroup.createFromStrings(filePaths));

            while (tw.next()) {
                String filePath = tw.getPathString();

                ObjectId objectId = tw.getObjectId(0);
                ObjectLoader loader = git.getRepository().open(objectId);
                fileContents.put(filePath, loader.getBytes());
                notFoundFiles.remove(filePath);
            }

        } catch (InvalidObjectIdException e) {
            throw new BusinessException(CommonResultCode.PARAM_INVALID, e.getMessage());
        } catch (IOException e) {
            throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED, e.getMessage());
        }

        if (!notFoundFiles.isEmpty()) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND, "没有从 Git History 中找到文件：" + notFoundFiles);
        }
        return fileContents;
    }

    private byte[] gitCatFile(Path root, String commitHash, String filePath) {
        return gitCatFiles(root, commitHash, Collections.singletonList(filePath)).get(filePath);
    }

    /* ----------------------------- File 基础工具方法 ----------------------------- */

    /**
     * 路径合法性校验，所有对外接口的第一道防线。拒绝：空路径、绝对路径、
     * 反斜杠（Windows 风格，统一只收正斜杠）、路径穿越（..）、纯 . 以及 .lanting 目录。
     * <p>
     * 注意此校验是字符串层面的，{@link #ensureInsideWorkspace} 是解析后路径层面的第二道防线，两者缺一不可。
     */
    private void validatePath(String path) {
        if (path == null || path.isBlank()) {
            throw new BusinessException(FileResultCode.PATH_ILLEGAL);
        }
        if (path.startsWith("/") || path.startsWith("\\") || path.contains("..") || path.contains("\\")) {
            throw new BusinessException(FileResultCode.PATH_ILLEGAL);
        }
        if (path.equals(".") || path.equals("./")) {
            throw new BusinessException(FileResultCode.PATH_ILLEGAL);
        }
        if (path.contains(".lanting") || path.contains(".git")) {
            throw new BusinessException(FileResultCode.LANTING_DIR_FORBIDDEN);
        }
    }

    /**
     * 校验解析（normalize）后的绝对路径确实落在工作空间根目录内，
     * 封堵经过编码/拼接绕过字符串校验的路径穿越。
     */
    private void ensureInsideWorkspace(Path target, Path root) {
        if (!target.startsWith(root.toAbsolutePath().normalize())) {
            throw new BusinessException(FileResultCode.PATH_ILLEGAL);
        }
    }

    /**
     * 文件扩展名白名单校验，仅在写入时调用；无扩展名或不在白名单内均拒绝。
     */
    private void validateFileType(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot == -1 || dot == fileName.length() - 1) {
            throw new BusinessException(FileResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(FileResultCode.FILE_TYPE_NOT_ALLOWED);
        }
    }
}
