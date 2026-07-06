package com.lanting.admin.module.file.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.common.page.PageResult;
import com.lanting.admin.common.result.CommonResultCode;
import com.lanting.admin.module.file.dto.*;
import com.lanting.admin.module.file.result.FileResultCode;
import com.lanting.admin.module.file.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

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

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private FileLockService fileLockService;

    /** 允许的文件扩展名白名单，写入时校验（读取不限制，兼容历史遗留文件） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("sql", "md", "html");

    /** 单文件大小上限；文本文件超过此值基本可判定为误传或恶意请求 */
    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1MB

    /** 所有文件遍历均跳过的目录：.lanting 是系统配置，.git 是仓库内部数据 */
    private static final List<String> IGNORED_DIRS = List.of(".lanting", ".git");

    /** 目录占位文件名：Git 不追踪空目录，建文件夹时写入此文件占位；对用户不可见 */
    private static final String GITKEEP = ".gitkeep";

    /**
     * 工作空间级别锁。社区版仅支持一个默认工作空间，因此使用单锁即可。
     */
    private final ReentrantLock workspaceLock = new ReentrantLock();

    /**
     * 在工作空间锁内执行操作。所有 Git 写操作必须经过此方法串行化。
     * <p>
     * ReentrantLock 可重入，嵌套调用安全；Git 写操作（add/commit/tag）在工作空间内串行执行，
     * 避免并发 commit 导致对象库状态不一致。
     * <p>
     * <b>锁顺序纪律（防死锁）</b>：允许在文件锁临界区内（{@link FileLockService#doIfHolder}）
     * 获取本锁，即 path stripe → workspaceLock；但在本锁内<b>绝不允许</b>再调用
     * {@code doIfHolder}/{@code acquire}/{@code release}/{@code forceRelease}，
     * 反向嵌套会与 save/delete/revert 构成死锁。
     * 删除目录时，deleteInternal 内的 forceRelease 会清理子文件锁，这些子文件路径可能命中不同 stripe，
     * 但 lockMap 是 ConcurrentHashMap，清锁操作线程安全，不会阻塞无关路径的写操作。
     */
    private <T> T withWorkspaceLock(Supplier<T> action) {
        workspaceLock.lock();
        try {
            return action.get();
        } finally {
            workspaceLock.unlock();
        }
    }

    // ==================== 文件树 ====================

    /**
     * 文件树。从磁盘读取当前文件结构，并附带每个节点的软锁状态。
     */
    public List<FileTreeNode> tree(String sort) {
        Path root = workspaceService.getDefaultWorkspaceRoot();
        List<FileTreeNode> nodes = new ArrayList<>();
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(p -> !IGNORED_DIRS.contains(p.getFileName().toString()))
                    .filter(p -> !GITKEEP.equals(p.getFileName().toString()))
                    .forEach(p -> nodes.add(buildTreeNode(p, root)));
        } catch (IOException e) {
            throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
        }
        sortTreeNodes(nodes, sort);
        return nodes;
    }

    /**
     * 递归构建单个树节点，附带该路径的软锁状态（前端据此展示“谁在编辑”）。
     */
    private FileTreeNode buildTreeNode(Path path, Path root) {
        FileTreeNode node = new FileTreeNode();
        node.setName(path.getFileName().toString());
        node.setPath(relativePath(path, root));
        boolean isFolder = Files.isDirectory(path);
        node.setType(isFolder ? "folder" : "file");
        node.setLockedBy(fileLockService.getHolder(node.getPath()));
        node.setLockedAt(fileLockService.getLockedAt(node.getPath()));
        if (isFolder) {
            List<FileTreeNode> children = new ArrayList<>();
            try (Stream<Path> stream = Files.list(path)) {
                stream.filter(p -> !IGNORED_DIRS.contains(p.getFileName().toString()))
                        .filter(p -> !GITKEEP.equals(p.getFileName().toString()))
                        .forEach(p -> children.add(buildTreeNode(p, root)));
            } catch (IOException e) {
                throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
            }
            node.setChildren(children);
        }
        return node;
    }

    /**
     * 递归排序树节点。
     * <p>
     * {@code mtime}：按磁盘文件最后修改时间倒序（含自动保存，非 commit 时间）；
     * 其他值一律按文件名升序。
     */
    private void sortTreeNodes(List<FileTreeNode> nodes, String sort) {
        if ("mtime".equals(sort)) {
            nodes.sort((a, b) -> {
                Long ta = lastModifiedTime(a);
                Long tb = lastModifiedTime(b);
                return tb.compareTo(ta);
            });
        } else {
            nodes.sort(Comparator.comparing(FileTreeNode::getName));
        }
        for (FileTreeNode node : nodes) {
            if (node.getChildren() != null) {
                sortTreeNodes(node.getChildren(), sort);
            }
        }
    }

    /**
     * 读取磁盘文件的最后修改时间（毫秒）；读失败时返回 0 而非报错，
     * 避免排序过程中个别文件被并发删除导致整棵树拉取失败。
     */
    private Long lastModifiedTime(FileTreeNode node) {
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path path = root.resolve(node.getPath());
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    // ==================== 读取内容 ====================

    /**
     * 读取磁盘当前文件内容。读取不需要持锁，返回内容包含自动保存但未提交的数据。
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
            return Files.readString(filePath, StandardCharsets.UTF_8);
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
        String username = currentUsername();
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
                return null;
            } catch (IOException e) {
                throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
            }
        });
    }

    // ==================== 创建文件夹 ====================

    /**
     * 创建文件夹。新路径无并发冲突风险，不需要抢锁；
     * 创建后立即 commit（占位文件 .gitkeep），保证目录结构进入版本历史。
     */
    public void createFolder(CreateFolderDTO dto) {
        String path = dto.getPath();
        validatePath(path);
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path folderPath = root.resolve(path).toAbsolutePath().normalize();
        ensureInsideWorkspace(folderPath, root);

        if (Files.exists(folderPath)) {
            throw new BusinessException(FileResultCode.FILE_ALREADY_EXISTS);
        }
        try {
            Files.createDirectories(folderPath);
            // Git 不追踪空目录，写入 .gitkeep 占位才能让目录进入 commit
            Path gitkeep = folderPath.resolve(GITKEEP);
            Files.createFile(gitkeep);
            gitCommit(List.of(relativePath(gitkeep, root)), "mkdir: " + path);
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

        String username = currentUsername();
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
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
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
        try {
            if (Files.isDirectory(targetPath)) {
                Files.walkFileTree(targetPath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        String r = relativePath(file, root);
                        // force 删除可能涉及他人持有的锁，无条件清除，避免已删除文件的锁残留
                        fileLockService.forceRelease(r);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
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
    }

    // ==================== 提交 ====================

    /**
     * 提交文件。只提交调用方已锁定的文件；他人锁定文件进入 skipped。
     * 若 committed 列表为空，Controller 层返回 30713 无可提交的文件。
     */
    public CommitResultVO commit(CommitFileDTO dto) {
        String username = currentUsername();
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
     * 查询文件历史记录。已接入 HistoryPageQuery 统一分页校验，并按 path 过滤（path 为空时查整个仓库）。
     */
    public PageResult<FileHistoryVO> history(HistoryPageQuery query) {
        Path root = workspaceService.getDefaultWorkspaceRoot();
        try (Git git = Git.open(root.toFile())) {
            List<RevCommit> commits = new ArrayList<>();
            LogCommand log = git.log();
            if (query.getPath() == null || query.getPath().isBlank()) {
                throw new BusinessException(CommonResultCode.PARAM_INVALID, "path 不能为空");
            }
            log.call().forEach(commits::add);
            int total = commits.size();
            int page = query.getPageNum();
            int pageSize = query.getPageSize();
            // 内存分页：JGit log 无法直接拿 total，必须先遍历全部 commit 再截取。
            // 单仓库 commit 量级有限（万级以内），当前规模下可接受
            int skip = (page - 1) * pageSize;
            // limit 取“页大小”与“剩余条数”的较小值，越界页码（skip ≥ total）时 limit 为 0，返回空页而非报错
            int limit = Math.min(pageSize, Math.max(0, total - skip));

            List<FileHistoryVO> list = new ArrayList<>();
            for (int i = skip; i < skip + limit && i < total; i++) {
                RevCommit commit = commits.get(i);
                FileHistoryVO vo = new FileHistoryVO();
                vo.setCommitHash(commit.getName());
                vo.setMessage(commit.getFullMessage());
                vo.setAuthor(commit.getAuthorIdent().getName());
                // JGit 的 commitTime 是秒级时间戳，项目统一使用毫秒，需 *1000
                vo.setTimestamp(commit.getCommitTime() * 1000L);
                list.add(vo);
            }
            Page<FileHistoryVO> pageResult = new Page<>(page, pageSize);
            pageResult.setRecords(list);
            pageResult.setTotal(total);
            return PageResult.of(pageResult);
        } catch (IOException | GitAPIException e) {
            throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
        }
    }

    // ==================== Diff ====================

    /**
     * 计算指定文件在两个 commit 之间的 unified diff。from/to 均为完整 commit SHA，
     * 由 Controller 层 @NotBlank 保证非空；非法 SHA 由 ObjectId.fromString 抛出异常转 30708。
     */
    public String diff(String path, String from, String to) {
        validatePath(path);
        Path root = workspaceService.getDefaultWorkspaceRoot();
        try (Git git = Git.open(root.toFile());
             ByteArrayOutputStream out = new ByteArrayOutputStream();
             DiffFormatter formatter = new DiffFormatter(out);
             RevWalk walk = new RevWalk(git.getRepository())) {
            formatter.setRepository(git.getRepository());
            ObjectId fromId = ObjectId.fromString(from);
            ObjectId toId = ObjectId.fromString(to);
            RevCommit fromCommit = walk.parseCommit(fromId);
            RevCommit toCommit = walk.parseCommit(toId);
            CanonicalTreeParser oldTree = prepareTreeParser(git.getRepository(), fromCommit);
            CanonicalTreeParser newTree = prepareTreeParser(git.getRepository(), toCommit);
            List<DiffEntry> diffs = git.diff()
                    .setOldTree(oldTree)
                    .setNewTree(newTree)
                    .setPathFilter(PathFilter.create(path))
                    .call();
            for (DiffEntry entry : diffs) {
                formatter.format(entry);
            }
            return out.toString(StandardCharsets.UTF_8);
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
     * 文件级回滚（软回滚）：读取目标 commit 中该文件的内容覆盖当前文件，自动产生一次新 commit。
     * 不使用 git revert（多文件/合并场景易冲突，服务端无法交互式解决）。
     * <p>
     * 持锁校验与“读历史 + 覆盖 + commit”通过 doIfHolder 原子执行；
     * path stripe → workspaceLock，符合锁顺序纪律。
     */
    public String revert(RevertFileDTO dto) {
        String path = dto.getPath();
        validatePath(path);
        String username = currentUsername();
        Path root = workspaceService.getDefaultWorkspaceRoot();
        Path filePath = root.resolve(path);
        return fileLockService.doIfHolder(path, username, () -> withWorkspaceLock(() -> {
            try (Git git = Git.open(root.toFile());
                 RevWalk walk = new RevWalk(git.getRepository())) {
                ObjectId commitId = ObjectId.fromString(dto.getCommitHash());
                RevCommit commit = walk.parseCommit(commitId);
                String content = readFileFromCommit(git.getRepository(), commit, path);
                Files.writeString(filePath, content, StandardCharsets.UTF_8);
                return gitCommit(List.of(path), "revert: " + path + " to " + dto.getCommitHash());
            } catch (IOException e) {
                throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
            }
        }));
    }

    // ==================== 发布级回滚 ====================

    /**
     * 发布级回滚预检。列出目标 tag 中当前被他人锁定的文件，供前端二次确认。
     */
    public RollbackCheckVO rollbackCheck(String tagName) {
        Path root = workspaceService.getDefaultWorkspaceRoot();
        String username = currentUsername();
        try (Git git = Git.open(root.toFile())) {
            List<String> files = listFilesInTag(git.getRepository(), tagName);
            List<LockedFileVO> lockedFiles = new ArrayList<>();
            for (String file : files) {
                String holder = fileLockService.getHolder(file);
                if (holder != null && !holder.equals(username)) {
                    LockedFileVO vo = new LockedFileVO();
                    vo.setPath(file);
                    vo.setLockedBy(holder);
                    lockedFiles.add(vo);
                }
            }
            RollbackCheckVO vo = new RollbackCheckVO();
            vo.setLockedFiles(lockedFiles);
            return vo;
        } catch (IOException e) {
            throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
        }
    }

    /**
     * 发布级回滚。服务端将目标 tag 中所有文件的锁直接交给回滚者，回滚完成后锁归回滚者持有。
     */
    public String rollbackRelease(String tagName) {
        Path root = workspaceService.getDefaultWorkspaceRoot();
        String username = currentUsername();

        // 抢锁必须在获取工作空间锁之前完成（锁顺序纪律：持有 workspaceLock 时不得操作文件锁，
        // 否则与 save/delete/revert 的 path stripe → workspaceLock 顺序相反，会死锁）。
        // 读 tag 文件列表是 Git 读操作，无需工作空间锁。
        List<String> files;
        try (Git git = Git.open(root.toFile())) {
            files = listFilesInTag(git.getRepository(), tagName);
        } catch (IOException e) {
            throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
        }

        // 设计决策：回滚者接管 tag 内所有文件的锁（软锁可强制抢占），回滚完成后锁保留在回滚者名下。
        // 抢锁后、进入工作空间锁前存在他人再次强抢的极小窗口——强制接管后果自负，符合软锁语义
        for (String file : files) {
            fileLockService.acquire(file, username);
        }

        return withWorkspaceLock(() -> {
            try (Git git = Git.open(root.toFile())) {
                ObjectId tagCommit = git.getRepository().resolve(Constants.R_TAGS + tagName);
                if (tagCommit == null) {
                    throw new BusinessException(FileResultCode.ROLLBACK_TARGET_NOT_FOUND);
                }

                try (RevWalk walk = new RevWalk(git.getRepository())) {
                    RevCommit commit = walk.parseCommit(tagCommit);
                    for (String file : files) {
                        String content = readFileFromCommit(git.getRepository(), commit, file);
                        Path filePath = root.resolve(file);
                        // tag 之后可能有目录被删除，写回前需确保父目录存在
                        Files.createDirectories(filePath.getParent());
                        Files.writeString(filePath, content, StandardCharsets.UTF_8);
                    }
                }
                // 注意：只覆盖 tag 中存在的文件，tag 之后新建的文件不会被删除（设计如此，见 spec）
                return gitCommit(files, "revert to " + tagName);
            } catch (IOException e) {
                throw new BusinessException(FileResultCode.GIT_OPERATION_FAILED, e.getMessage());
            }
        });
    }

    // ==================== 发布 ====================

    /**
     * 发布当前 HEAD。自动生成 release-yyyymmdd-xxx 格式 tag；当日序号超过 999 时报错。
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
                git.tag().setObjectId(headCommit).setName(tagName).setMessage("release: " + tagName).call();
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
     * 生成当日递增的 tag 名：release-YYYYMMDD-NNN。
     * <p>
     * 以 Git 中已存在的 tag 为准递增序号（而非 DB），因此即使发布记录被软删除，
     * tag 名也不会被复用，保证发布 ID 全局唯一。调用方需持有工作空间锁以避免并发取号冲突。
     */
    private String generateTagName(Git git) throws IOException, GitAPIException {
        String prefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int seq = 1;
        while (true) {
            String tagName = "release-" + prefix + "-" + String.format("%03d", seq);
            if (git.getRepository().resolve(Constants.R_TAGS + tagName) == null) {
                return tagName;
            }
            seq++;
            if (seq > 999) {
                throw new BusinessException(FileResultCode.PUBLISH_TAG_EXISTS, "当日 tag 序号已耗尽");
            }
        }
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
                org.eclipse.jgit.api.AddCommand add = git.add();
                for (String path : paths) {
                    add.addFilepattern(path);
                }
                add.call();

                org.eclipse.jgit.api.AddCommand update = git.add().setUpdate(true);
                for (String path : paths) {
                    update.addFilepattern(path);
                }
                update.call();

                RevCommit commit = git.commit()
                        .setMessage(message)
                        .setAuthor(currentUsername(), currentUsername() + "@lanting.io")
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
     * 列出 tag 对应 commit 中的全部文件路径（递归，跳过 .gitkeep）。
     * tag 不存在时抛 30711 回滚目标不存在。
     */
    private List<String> listFilesInTag(Repository repository, String tagName) throws IOException {
        ObjectId tagCommit = repository.resolve(Constants.R_TAGS + tagName);
        if (tagCommit == null) {
            throw new BusinessException(FileResultCode.ROLLBACK_TARGET_NOT_FOUND);
        }
        List<String> files = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(tagCommit);
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    String p = treeWalk.getPathString();
                    String name = p.substring(p.lastIndexOf('/') + 1);
                    // .gitkeep 是目录占位文件，不参与回滚覆盖与锁定
                    if (GITKEEP.equals(name)) {
                        continue;
                    }
                    files.add(p);
                }
            }
        }
        return files;
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

    /**
     * 从 Sa-Token 会话获取当前登录用户名，用作锁持有人与 Git author。
     */
    private String currentUsername() {
        return StpUtil.getLoginIdAsString();
    }
}
