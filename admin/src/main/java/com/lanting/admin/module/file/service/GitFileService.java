package com.lanting.admin.module.file.service;

import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.common.exception.ContentInconsistentException;
import com.lanting.admin.common.page.PageResult;
import com.lanting.admin.common.result.CommonResultCode;
import com.lanting.admin.module.file.dto.*;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import com.lanting.admin.module.file.result.FileResultCode;
import com.lanting.admin.module.file.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static com.lanting.admin.common.util.SecurityUtils.currentUser;

/**
 * 基于 Git 的文件服务，file 模块的核心。
 * <p>
 * 设计要点（详见 docs/backend/file-system-spec.md）：
 * <ul>
 *   <li><b>磁盘 = 工作区</b>：自动保存只写磁盘不进 Git；只有用户主动提交才产生 commit，
 *       因此磁盘内容可能领先于 Git HEAD（存在未提交变更是正常状态）。</li>
 *   <li><b>软锁</b>：写操作（保存/提交/删除/回滚）要求持有文件锁，但锁可被他人强制接管，
 *       锁的语义由 {@link FileLockService} 维护；读操作不需要锁。</li>
 *   <li><b>并发模型</b>：文件锁保证“同一文件同一时刻只有一人编辑”；工作空间锁
 *       {@link #withWorkspaceLock} 保证 Git 写操作（add/commit/tag）串行执行，
 *       两层锁职责不同，互不替代。</li>
 *   <li><b>发布 = Tag</b>：发布对当前 HEAD 打 tag，未提交的磁盘变更不纳入发布；
 *       回滚采用“读历史内容覆盖当前文件再 commit”的软回滚，不使用 git revert。</li>
 * </ul>
 *
 * @author wangzhao
 */
@Slf4j
@Service
public class GitFileService {

    private final WorkspaceService workspaceService;

    private final FileLockService fileLockService;

    private final FileIndexService fileIndexService;

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

    public GitFileService(WorkspaceService workspaceService, FileLockService fileLockService, FileIndexService fileIndexService) {
        this.workspaceService = workspaceService;
        this.fileLockService = fileLockService;
        this.fileIndexService = fileIndexService;
    }

    /**
     * 在 Git 写锁内执行操作。所有 Git 写操作必须经过此方法串行化。
     * <p>
     * ReentrantLock 可重入，嵌套调用安全。EE 分布式部署时替换此方法实现
     * 为分布式锁即可，调用方无需改动（见 extension-points-watchlist.md）。
     * <p>
     * <b>锁顺序纪律（防死锁）</b>：允许在文件锁临界区内（{@link FileLockService#doIfHolder}）
     * 获取本锁，即 path stripe → gitWriteLock；但在本锁内<b>绝不允许</b>再调用
     * {@code doIfHolder}/{@code acquire}/{@code release}/{@code forceRelease}，
     * 反向嵌套会与 save/delete/revert 构成死锁。
     */
    private <T> T withWorkspaceLock(Supplier<T> action) {
        gitWriteLock.lock();
        try {
            return action.get();
        } finally {
            gitWriteLock.unlock();
        }
    }

    // ==================== 文件树 ====================

    /**
     * 文件树。按层级从 DB 索引查询，并附带每个节点的软锁状态。
     * <p>
     * 前端按需懒加载：首次请求根层级（{@code parentPath=""}），展开文件夹时再请求子层级。
     */
    public List<FileTreeNode> tree(String parentPath, String sort) {
        // parentPath 为空字符串表示根目录，允许；非空时按普通路径校验
        if (parentPath != null && !parentPath.isEmpty()) {
            validatePath(parentPath);
        }
        if (parentPath == null) {
            parentPath = "";
        }
        List<FileIndexEntity> children = fileIndexService.listByParentPath(parentPath);
        List<FileTreeNode> nodes = new ArrayList<>();
        for (FileIndexEntity entity : children) {
            FileTreeNode node = new FileTreeNode();
            node.setName(entity.getName());
            node.setPath(entity.getPath());
            node.setType(entity.getType());
            node.setMtime(entity.getMtime());
            node.setLockedBy(fileLockService.getHolder(entity.getPath()));
            node.setLockedAt(fileLockService.getLockedAt(entity.getPath()));
            nodes.add(node);
        }
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

    // ==================== 读取内容 ====================

    /**
     * 读取磁盘当前文件内容。读取不需要持锁，返回内容包含自动保存但未提交的数据。
     * <p>
     * 每次读取都会比对 DB 索引中的 CRC32：不一致时仍抛出磁盘真实内容，由全局异常处理器返回
     * {@code code=30714} 但 {@code data=磁盘内容} 的响应。
     */
    public String content(String path) {
        validatePath(path);
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path filePath = root.resolve(path).toAbsolutePath().normalize();
        ensureInsideWorkspace(filePath, root);
        if (!Files.exists(filePath)) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
        }
        // 目录不是可读内容，复用“文件类型不允许”错误码而非 404，让前端能区分两种情况
        if (Files.isDirectory(filePath)) {
            throw new BusinessException(FileResultCode.FILE_TYPE_NOT_ALLOWED);
        }
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            String content = new String(bytes, StandardCharsets.UTF_8);
            FileIndexEntity index = fileIndexService.getByPath(path);

            // 索引不存在或缺少 CRC32：返回内容并提示不一致
            if (index == null || index.getCrc32() == null) {
                throw new ContentInconsistentException(FileResultCode.FILE_CONTENT_INCONSISTENT, "文件索引不存在", content);
            }

            long diskCrc32 = FileIndexService.crc32(bytes);
            if (index.getCrc32() != diskCrc32) {
                throw new ContentInconsistentException(FileResultCode.FILE_CONTENT_INCONSISTENT, "文件内容与索引不一致，请执行索引修复", content);
            }

            return content;
        } catch (IOException e) {
            throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
        }
    }

    // ==================== 自动保存 ====================

    /**
     * 自动保存文件到磁盘。要求当前用户已锁定该文件，否则抛 30709 文件已被锁定。
     * <p>
     * 持锁校验与写磁盘通过 {@link FileLockService#doIfHolder} 在同一临界区内原子执行，
     * 保证写入期间锁不会被他人抢走——避免接管者打开文件后，前持锁人的迟到写入仍落盘
     * 造成内容不一致。
     */
    public void save(SaveFileDTO dto) {
        String path = dto.getPath();
        validatePath(path);
        String username = currentUser();
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path filePath = root.resolve(path).toAbsolutePath().normalize();
        ensureInsideWorkspace(filePath, root);
        validateFileType(filePath);

        fileLockService.doIfHolder(path, username, () -> {
            try {
                // 新文件的父目录可能不存在（先抢锁后 save 创建新文件的流程），需要先建目录
                Files.createDirectories(filePath.getParent());
                byte[] bytes = dto.getContent() != null ? dto.getContent().getBytes(StandardCharsets.UTF_8) : new byte[0];
                // 按 UTF-8 编码后的字节数校验，而非字符数：中文内容编码后体积会膨胀
                if (bytes.length > MAX_FILE_SIZE) {
                    throw new BusinessException(FileResultCode.FILE_SIZE_EXCEEDED);
                }
                // CREATE + TRUNCATE：不存在则创建，存在则整体覆盖（自动保存总是全量写入）
                Files.write(filePath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                // 磁盘写入成功后更新索引，直接用内存 bytes 计算 CRC32 避免重复读盘
                fileIndexService.indexOnSave(path, root, bytes);
                return null;
            } catch (IOException e) {
                throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
            }
        });
    }

    // ==================== 创建文件夹 ====================

    /**
     * 创建文件夹。新路径无并发冲突风险，不需要 Git 写锁；
     * 目录结构由 {@link FileIndexService} 维护，不再写入 .gitkeep 占位。
     */
    public void createFolder(CreateFolderDTO dto) {
        String path = dto.getPath();
        validatePath(path);
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path folderPath = root.resolve(path).toAbsolutePath().normalize();
        ensureInsideWorkspace(folderPath, root);
        try {
            if (Files.exists(folderPath)) {
                throw new BusinessException(FileResultCode.FILE_ALREADY_EXISTS);
            }
            Files.createDirectories(folderPath);
            fileIndexService.indexOnCreate(path, "folder", root);
        } catch (IOException e) {
            throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
        }
    }

    // ==================== 删除文件/文件夹 ====================

    /**
     * 删除文件或文件夹。
     * <p>
     * 文件：必须持有该文件的锁才能删除。
     * 文件夹：不递归抢锁，而是预检目录下是否有<b>他人</b>持锁的文件——
     * 有且未指定 force 时返回被锁文件清单（Controller 转 30712）供前端弹确认框，
     * force=true 时踢掉所有持锁人强制删除。
     *
     * @return 被他人锁定的文件清单；删除成功时返回 null
     */
    public DeleteLockedVO delete(String path, boolean force) {
        validatePath(path);
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path targetPath = root.resolve(path).toAbsolutePath().normalize();
        ensureInsideWorkspace(targetPath, root);

        if (!Files.exists(targetPath)) {
            throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
        }

        String username = currentUser();
        // 删除文件
        if (Files.isRegularFile(targetPath)) {
            // 持锁校验与删除在同一临界区内原子执行；单文件删除时 deleteInternal 内的 forceRelease
            // 与入口 doIfHolder 是同线程重入同一 path stripe，目录删除时子文件命中不同 stripe，
            // gitCommit 是 path stripe → workspaceLock，均符合锁顺序纪律
            fileLockService.doIfHolder(path, username, () -> {
                deleteInternal(targetPath, root);
                return null;
            });
            return null;
        }

        // 删除目录
        List<LockedFileVO> lockedFiles = findLockedFilesUnder(targetPath, root, username);
        if (!lockedFiles.isEmpty() && !force) {
            // 1）非强制模式：禁止用户删除未获得锁权限的目录/文件
            DeleteLockedVO vo = new DeleteLockedVO();
            vo.setLockedFiles(lockedFiles);
            return vo;
        }
        // 2）强制模式：允许用户强制删除未获得锁权限的目录/文件
        deleteInternal(targetPath, root);
        return null;
    }

    /**
     * 遍历目录，找出被“指定用户之外的人”持锁的文件（自己持锁的文件可随目录一起删，不算阻碍）。
     */
    private List<LockedFileVO> findLockedFilesUnder(Path folder, Path root, String username) {
        List<LockedFileVO> lockedFiles = new ArrayList<>();
        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                @NonNull
                public FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) {
                    String relative = relativePath(file, root);
                    String holder = fileLockService.getHolder(relative);
                    if (holder != null && !holder.equals(username)) {
                        LockedFileVO vo = new LockedFileVO();
                        vo.setPath(relative);
                        vo.setLockedBy(holder);
                        lockedFiles.add(vo);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
        }
        return lockedFiles;
    }

    /**
     * 执行实际删除：删磁盘 → 清锁 → commit。
     * <p>
     * 权限校验（持锁/force 确认）已在 {@link #delete} 入口完成，本方法不再重复校验。
     */
    private void deleteInternal(Path targetPath, Path root) {
        String relative = relativePath(targetPath, root);
        withWorkspaceLock(() -> {
            // 先删 DB 索引，保证 DB 是 source of truth
            fileIndexService.indexOnDelete(relative);
            try {
                if (Files.isDirectory(targetPath)) {
                    Files.walkFileTree(targetPath, new SimpleFileVisitor<>() {
                        @Override
                        @NonNull
                        public FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            String r = relativePath(file, root);
                            // force 删除可能涉及他人持有的锁，无条件清除，避免已删除文件的锁残留
                            fileLockService.forceRelease(r);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        @NonNull
                        public FileVisitResult postVisitDirectory(@NonNull Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } else {
                    Files.delete(targetPath);
                    fileLockService.forceRelease(relative);
                }
                gitCommit(List.of(relative), "delete: " + relative);
            } catch (IOException e) {
                throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
            }
            return null;
        });
    }

    // ==================== 提交 ====================

    /**
     * 提交文件。只提交调用方已锁定的文件；他人锁定文件进入 skipped。
     * 若 committed 列表为空，Controller 层返回 30713 无可提交的文件。
     */
    public CommitResultVO commit(CommitFileDTO dto) {
        String username = currentUser();
        List<String> committed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (String path : dto.getPaths()) {
            validatePath(path);
            if (fileLockService.isHolder(path, username)) {
                committed.add(path);
            } else {
                skipped.add(path);
            }
        }

        CommitResultVO result = new CommitResultVO();
        result.setCommitted(committed);
        result.setSkipped(skipped);

        if (committed.isEmpty()) {
            result.setCommitHash(null);
            return result;
        }

        String hash = gitCommit(committed, dto.getMessage());
        result.setCommitHash(hash);
        return result;
    }

    // ==================== 历史记录 ====================

    /**
     * 查询 Git 文件历史记录（游标分页）。
     * <p>
     * 返回的 {@link PageResult 分页结果}中 total 与 totalPages 固定为 -1，
     * 通过 hasMore 判断是否有下一页。
     *
     * @param query 查询参数，path 为空字符串时查询整个仓库历史
     */
    public PageResult<FileHistoryVO> history(HistoryPageQuery query) {
        String path = query.getPath();
        if (path == null || path.isBlank()) {
            throw new BusinessException(CommonResultCode.PARAM_INVALID, "path 不能为空");
        }
        Path root = workspaceService.getDefaultWorkspaceRoot();
        try (Git git = Git.open(root.toFile())) {
            LogCommand log = git.log();
            log.addPath(path);

            int page = query.getPageNum();
            int pageSize = query.getPageSize();
            int skip = (page - 1) * pageSize;
            // 多取一条用于判断是否有下一页，避免再查一次总数
            log.setSkip(skip).setMaxCount(pageSize + 1);

            List<RevCommit> commits = new ArrayList<>(pageSize + 1);
            for (RevCommit commit : log.call()) {
                commits.add(commit);
            }

            boolean hasMore = commits.size() > pageSize;
            List<FileHistoryVO> list = getFileHistoryVOs(pageSize, commits);
            return PageResult.ofHasMore(list, page, pageSize, hasMore);
        } catch (IOException | GitAPIException e) {
            throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
        }
    }

    private static @NonNull List<FileHistoryVO> getFileHistoryVOs(int pageSize, List<RevCommit> commits) {
        int limit = Math.min(pageSize, commits.size());
        List<FileHistoryVO> list = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            RevCommit commit = commits.get(i);
            FileHistoryVO vo = new FileHistoryVO();
            vo.setCommitHash(commit.getName());
            vo.setMessage(commit.getFullMessage());
            vo.setAuthor(commit.getAuthorIdent().getName());
            // JGit 的 commitTime 是秒级时间戳，项目统一使用毫秒，需 *1000
            vo.setTimestamp(commit.getCommitTime() * 1000L);
            list.add(vo);
        }
        return list;
    }

    // ==================== Diff ====================

    /**
     * 计算指定文件在两个 commit 之间的 unified diff。from/to 均为完整 commit SHA，
     * 由 Controller 层 @NotBlank 保证非空；非法 SHA 转 PARAM_INVALID（10001）；
     * 文件在两边 commit 中均不存在时抛 FILE_NOT_FOUND（30702）。
     */
    public String diff(String path, String from, String to) {
        validatePath(path);
        if (from == null || to == null) {
            throw new BusinessException(CommonResultCode.PARAM_INVALID, "from 和 to 不能为空");
        }
        if (from.equals(to)) {
            return "";
        }
        Path root = workspaceService.getDefaultWorkspaceRoot();
        try (Git git = Git.open(root.toFile());
             RevWalk walk = new RevWalk(git.getRepository());
             ByteArrayOutputStream out = new ByteArrayOutputStream();
             DiffFormatter formatter = new DiffFormatter(out)) {
            ObjectId fromId = ObjectId.fromString(from);
            ObjectId toId = ObjectId.fromString(to);
            RevCommit fromCommit = walk.parseCommit(fromId);
            RevCommit toCommit = walk.parseCommit(toId);
            CanonicalTreeParser oldTree = prepareTreeParser(git.getRepository(), fromCommit);
            CanonicalTreeParser newTree = prepareTreeParser(git.getRepository(), toCommit);
            formatter.setRepository(git.getRepository());
            List<DiffEntry> diffs = git.diff()
                    .setOldTree(oldTree)
                    .setNewTree(newTree)
                    .setPathFilter(PathFilter.create(path))
                    .call();
            if (diffs.isEmpty()) {
                // 两边都不存在 → FILE_NOT_FOUND；内容没变化 → 返回空字符串
                boolean existsInFrom = TreeWalk.forPath(
                        git.getRepository(), path, fromCommit.getTree()) != null;
                boolean existsInTo = TreeWalk.forPath(
                        git.getRepository(), path, toCommit.getTree()) != null;
                if (!existsInFrom && !existsInTo) {
                    throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
                }
                return "";
            }
            for (DiffEntry entry : diffs) {
                formatter.format(entry);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (org.eclipse.jgit.errors.InvalidObjectIdException e) {
            throw new BusinessException(CommonResultCode.PARAM_INVALID,
                    "非法的 commit hash: " + e.getMessage());
        } catch (IOException | GitAPIException e) {
            throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
        }
    }

    /**
     * 将 commit 的树包装为 diff 命令需要的 TreeParser（JGit diff API 的样板代码）。
     */
    private CanonicalTreeParser prepareTreeParser(Repository repository, RevCommit commit) throws IOException {
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        try (org.eclipse.jgit.lib.ObjectReader reader = repository.newObjectReader()) {
            treeParser.reset(reader, commit.getTree());
        }
        return treeParser;
    }

    // ==================== 文件级回滚 ====================

    /**
     * 文件级回滚（软回滚）：读取目标 commit 中该文件的内容覆盖当前文件，只写磁盘不 commit。
     * 用户确认后可自行选择是否提交此次变更。
     * 不使用 git revert（多文件/合并场景易冲突，服务端无法交互式解决）。
     * <p>
     * 持锁校验与磁盘写入通过 {@link FileLockService#doIfHolder} 在同一临界区内原子执行。
     * 不需要 Git 写锁（没有 commit 操作）。
     */
    public void revert(RevertFileDTO dto) {
        String path = dto.getPath();
        validatePath(path);
        String username = currentUser();
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path filePath = root.resolve(path);
        fileLockService.doIfHolder(path, username, () -> {
            try (Git git = Git.open(root.toFile());
                 RevWalk walk = new RevWalk(git.getRepository())) {
                ObjectId commitId = ObjectId.fromString(dto.getCommitHash());
                RevCommit commit = walk.parseCommit(commitId);
                String content = readFileFromCommit(git.getRepository(), commit, path);
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                Files.write(filePath, bytes);
                // gitCommit 成功后再更新索引（文件可能已不存在，用 UPSERT）
                fileIndexService.indexOnSave(path, root, bytes);
            } catch (InvalidObjectIdException e) {
                throw new BusinessException(CommonResultCode.PARAM_INVALID, e.getMessage());
            } catch (IOException e) {
                throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
            }
            return null;
        });
    }

    // ==================== 发布 ====================

    /**
     * 发布当前 HEAD。自动生成 release-YYYYMMDDHHmmss-abcdef（时间戳 + 短 hash）。
     * <p>
     * commitHash 是 HEAD 的 SHA；磁盘上未提交的变更不影响发布内容（静默忽略，见 spec）。
     */
    public PublishVO publish(PublishDTO dto) {
        Path root = workspaceService.getDefaultWorkspaceRoot();
        return withWorkspaceLock(() -> {
            try (Git git = Git.open(root.toFile());
                 RevWalk walk = new RevWalk(git.getRepository())) {
                ObjectId headId = git.getRepository().resolve(Constants.HEAD);
                if (headId == null) {
                    throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, "HEAD 不存在");
                }
                RevCommit headCommit = walk.parseCommit(headId);
                String tagName = generateTagName(git);
                String message = "release: " + tagName;
                git.tag().setObjectId(headCommit).setName(tagName).setMessage(message).call();
                PublishVO vo = new PublishVO();
                vo.setTagName(tagName);
                vo.setDisplayName(dto.getDisplayName());
                vo.setCommitHash(headId.getName());
                vo.setTimestamp(System.currentTimeMillis());
                return vo;
            } catch (IOException | GitAPIException e) {
                throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
            }
        });
    }

    /**
     * 删除 tag。用于发布记录落库失败时的补偿，删除失败仅记录日志不再抛出，
     * 避免掩盖原始异常。
     */
    public void deleteTag(String tagName) {
        Path root = workspaceService.getDefaultWorkspaceRoot();
        withWorkspaceLock(() -> {
            try (Git git = Git.open(root.toFile())) {
                git.tagDelete().setTags(tagName).call();
            } catch (IOException | GitAPIException e) {
                log.error("补偿删除 tag 失败：{}", tagName, e);
            }
            return null;
        });
    }

    /**
     * 生成 tag 名：release-YYYYMMDDHHmmss-abcdef。
     * <p>
     * 时间戳精确到秒 + 短 commit hash（前 6 位），天然唯一，无需递增序号。
     * 即使同秒内并发（工作空间锁预防），不同 HEAD 产生不同 commit hash 也能区分。
     * 调用方需持有工作空间锁。
     */
    private String generateTagName(Git git) throws IOException {
        String prefix = "release-";

        // 格式：release-20260704153012-abc3f1
        // 时间戳到秒级保证基本唯一，短 commit hash 兜底极端并发冲突
        String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        ObjectId headId = git.getRepository().resolve(Constants.HEAD);
        String shortHash = headId != null
                ? headId.abbreviate(6).name()
                : "000000";
        return prefix + timestamp + "-" + shortHash;
    }

    // ==================== 内部工具方法 ====================

    /**
     * 提交指定路径到 Git。在工作空间锁内执行，可被已持锁方法嵌套调用（锁可重入）。
     * <p>
     * 分两遍 stage：第一遍 add 处理新增和修改；第二遍 {@code setUpdate(true)}
     * 处理已删除的 tracked 文件——JGit 的 AddCommand 默认不会 stage 删除操作，
     * 缺少第二遍会导致删除永远不进入 Git 历史。
     */
    private String gitCommit(List<String> paths, String message) {
        Path root = workspaceService.getDefaultWorkspaceRoot();
        return withWorkspaceLock(() -> {
            try (Git git = Git.open(root.toFile())) {
                AddCommand add = git.add();
                for (String path : paths) {
                    add.addFilepattern(path);
                }
                add.call();

                AddCommand update = git.add().setUpdate(true);
                for (String path : paths) {
                    update.addFilepattern(path);
                }
                update.call();

                String username = currentUser();
                String email = username + "@lanting.io";
                RevCommit commit = git.commit()
                        .setMessage(message)
                        .setAuthor(username, email)
                        .call();
                return commit.getName();
            } catch (IOException | GitAPIException e) {
                throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
            }
        });
    }

    /**
     * 从指定 commit 的文件树中读取单个文件内容；文件在该版本中不存在时抛 30702。
     */
    private String readFileFromCommit(Repository repository, RevCommit commit, String path) throws IOException {
        try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, commit.getTree())) {
            if (treeWalk == null) {
                throw new BusinessException(FileResultCode.FILE_NOT_FOUND);
            }
            ObjectId objectId = treeWalk.getObjectId(0);
            ObjectLoader loader = repository.open(objectId);
            return new String(loader.getBytes(), StandardCharsets.UTF_8);
        }
    }

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

    /**
     * 计算相对于工作空间根目录的路径，统一作为锁、Git、VO 中的文件标识。
     */
    private String relativePath(Path path, Path root) {
        return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }
}
