package com.lanting.admin.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import com.lanting.admin.module.file.mapper.FileIndexMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.CRC32;

import static com.lanting.admin.module.file.entity.FileIndexEntity.FILE;
import static com.lanting.admin.module.file.entity.FileIndexEntity.isDirectory;

/**
 * 文件系统元数据索引服务。
 *
 * @author wangzhao
 */
@Slf4j
@Service
public class FileIndexService {

    @Autowired
    private FileIndexMapper fileIndexMapper;

    /**
     * 按父路径查询直接子节点，仅返回未删除记录。
     *
     * @param parentPath 父路径
     * @return 子节点列表
     */
    public List<FileIndexEntity> listDirectlyChildren(String parentPath) {
        return fileIndexMapper.selectList(
                new LambdaQueryWrapper<FileIndexEntity>()
                        .eq(FileIndexEntity::getParentPath, parentPath)
                        .eq(FileIndexEntity::getDeletedAt, 0L));
    }

    public List<FileIndexEntity> listIncludeDeletedByIds(Collection<Long> ids) {
        return fileIndexMapper.selectByIds(ids);
    }

    public List<FileIndexEntity> listExcludeDeletedByIds(Collection<Long> ids) {
        return fileIndexMapper.selectList(new LambdaQueryWrapper<FileIndexEntity>()
                .in(FileIndexEntity::getId, ids)
                .eq(FileIndexEntity::getDeletedAt, 0L));
    }

    /**
     * 批量更新文件索引中的 lastest_commit_hash
     */
    public void updateCommitHashByIds(List<Long> fileIds, String commitHash) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        fileIndexMapper.update(new LambdaUpdateWrapper<FileIndexEntity>()
                .set(FileIndexEntity::getLatestCommitHash, commitHash)
                .set(FileIndexEntity::getUpdateTime, System.currentTimeMillis())
                .in(FileIndexEntity::getId, fileIds));
    }

    /**
     * 根据路径前缀查找未删除的文件列表。
     *
     * @param pathPrefix 路径前缀
     * @return 子节点列表
     */
    public List<FileIndexEntity> listAllChildren(String pathPrefix) {
        return selectAllChildren(pathPrefix, false);
    }

    /**
     * 根据路径前缀查找所有节点，包含已删除记录。
     *
     * @param pathPrefix 路径前缀
     * @return 子节点列表
     */
    public List<FileIndexEntity> listAllChildrenIncludingDeleted(String pathPrefix) {
        return selectAllChildren(pathPrefix, true);
    }

    private List<FileIndexEntity> selectAllChildren(String pathPrefix, boolean includeDeleted) {
        // 只返回严格子孙节点，不包含 pathPrefix 自身
        LambdaQueryWrapper<FileIndexEntity> wrapper = new LambdaQueryWrapper<FileIndexEntity>()
                .likeRight(FileIndexEntity::getPath, pathPrefix + "/");
        if (!includeDeleted) {
            wrapper.eq(FileIndexEntity::getDeletedAt, 0L);
        }
        return fileIndexMapper.selectList(wrapper);
    }

    /**
     * 按路径查询单个未删除的索引记录。
     *
     * @param path 文件相对路径
     * @return 索引记录，不存在返回 null
     */
    public FileIndexEntity getByPath(String path) {
        return selectByPath(path, false);
    }

    /**
     * 按路径查询单个索引记录，包含已删除记录。
     *
     * @param path 文件相对路径
     * @return 索引记录，不存在返回 null
     */
    public FileIndexEntity getByPathIncludingDeleted(String path) {
        return selectByPath(path, true);
    }

    private FileIndexEntity selectByPath(String path, boolean includeDeleted) {
        LambdaQueryWrapper<FileIndexEntity> wrapper = new LambdaQueryWrapper<FileIndexEntity>()
                .eq(FileIndexEntity::getPath, path);
        if (!includeDeleted) {
            wrapper.eq(FileIndexEntity::getDeletedAt, 0L);
        }
        return fileIndexMapper.selectOne(wrapper.last("LIMIT 1"));
    }

    /**
     * 按主键 ID 查询索引记录，包含已删除记录。
     *
     * @param id 主键 ID
     * @return 索引记录，不存在返回 null
     */
    public FileIndexEntity getIncludeDeletedById(Long id) {
        return fileIndexMapper.selectById(id);
    }

    /**
     * 按主键 ID 查询未删除的索引记录。
     *
     * @param id 主键 ID
     * @return 索引记录，不存在或已删除返回 null
     */
    public FileIndexEntity getExcludeDeletedById(Long id) {
        return fileIndexMapper.selectOne(new LambdaQueryWrapper<FileIndexEntity>()
                .eq(FileIndexEntity::getId, id)
                .eq(FileIndexEntity::getDeletedAt, 0L));
    }

    /**
     * 创建文件或文件夹后建立索引。
     *
     * @param path 文件相对路径
     * @param type file / folder
     * @param root 工作空间根目录
     */
    public FileIndexEntity indexOnCreate(String path, String type, Path root) {
        long now = System.currentTimeMillis();
        long mtime = isDirectory(type) ? lastModifiedTime(root.resolve(path)) : 0L;

        FileIndexEntity existing = getByPath(path);
        if (existing != null) {
            existing.setDeletedAt(0L);
            existing.setType(type);
            existing.setName(extractName(path));
            existing.setParentPath(extractParentPath(path));
            existing.setMtime(mtime);
            existing.setUpdateTime(now);
            fileIndexMapper.updateById(existing);
            return existing;
        }

        FileIndexEntity entity = new FileIndexEntity();
        entity.setPath(path);
        entity.setName(extractName(path));
        entity.setType(type);
        entity.setParentPath(extractParentPath(path));
        entity.setMtime(mtime);
        entity.setCrc32(0L); // 新建文件没有任何数据 CRC32 为 0
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        fileIndexMapper.insert(entity);
        return entity;
    }

    /**
     * 保存文件后更新索引。文件不存在时自动 INSERT，存在时 UPDATE mtime/crc32。
     * <p>
     * 不带 bytes 的重载会重新读取磁盘内容计算 CRC32；正常写路径建议调用带 bytes 版本避免重复读盘。
     *
     * @param path 文件相对路径
     * @param root 工作空间根目录
     */
    public void indexOnSave(String path, Path root) {
        Path filePath = root.resolve(path);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.warn("读取文件内容计算 CRC32 失败：{}，按空内容处理", filePath, e);
            bytes = new byte[0];
        }
        indexOnSave(path, root, bytes);
    }

    /**
     * 保存文件后更新索引。文件不存在时自动 INSERT，存在时 UPDATE mtime/crc32。
     *
     * @param path  文件相对路径
     * @param root  工作空间根目录
     * @param bytes 文件内容字节数组，用于直接计算 CRC32 避免重复读盘
     */
    public void indexOnSave(String path, Path root, byte[] bytes) {
        FileIndexEntity existing = getByPath(path);
        long mtime = lastModifiedTime(root.resolve(path));
        long crc32 = crc32(bytes);
        long now = System.currentTimeMillis();

        if (existing != null) {
            existing.setDeletedAt(0L);
            existing.setMtime(mtime);
            existing.setCrc32(crc32);
            existing.setUpdateTime(now);
            fileIndexMapper.updateById(existing);
            return;
        }

        FileIndexEntity entity = new FileIndexEntity();
        entity.setPath(path);
        entity.setName(extractName(path));
        entity.setType(FILE);
        entity.setParentPath(extractParentPath(path));
        entity.setMtime(mtime);
        entity.setCrc32(crc32);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        fileIndexMapper.insert(entity);
    }

    /**
     * 单条文件 rename 后更新索引的 path/name/parent_path。
     *
     * @param fileId  文件 ID
     * @param newPath 新相对路径
     */
    public void indexOnRename(Long fileId, String newPath) {
        FileIndexEntity entity = fileIndexMapper.selectById(fileId);
        if (entity == null) {
            return;
        }
        long now = System.currentTimeMillis();
        entity.setPath(newPath);
        entity.setName(extractName(newPath));
        entity.setParentPath(extractParentPath(newPath));
        entity.setUpdateTime(now);
        fileIndexMapper.updateById(entity);
    }

    /**
     * 文件夹 rename 后批量更新索引：path 和 parent_path 中匹配 oldPrefix 的前缀替换为 newPrefix。
     *
     * @param oldPrefix 原文件夹路径
     * @param newPrefix 新文件夹路径
     */
    public void indexOnFolderRename(String oldPrefix, String newPrefix) {
        if (oldPrefix.equals(newPrefix)) {
            return;
        }
        fileIndexMapper.updatePathsByPrefix(oldPrefix, newPrefix);
    }

    /**
     * 删除文件或文件夹时软删除索引（递归更新子路径），同时设置 deletedAt 和 latestCommitHash。
     *
     * @param path             文件相对路径
     * @param deletedAt        删除时间戳
     * @param latestCommitHash 本次删除的 Git commit hash
     */
    public void indexOnDelete(String path, long deletedAt, String latestCommitHash) {
        fileIndexMapper.update(
                new LambdaUpdateWrapper<FileIndexEntity>()
                        .eq(FileIndexEntity::getDeletedAt, 0L)
                        .and(w -> w.eq(FileIndexEntity::getPath, path)
                                .or()
                                .likeRight(FileIndexEntity::getPath, path + "/"))
                        .set(FileIndexEntity::getDeletedAt, deletedAt)
                        .set(FileIndexEntity::getLatestCommitHash, latestCommitHash));
    }

    /**
     * 根据 ID 列表批量软删除文件/目录，同时设置 deletedAt 和 latestCommitHash。
     */
    public void indexOnDeleteByIds(Set<Long> fileIds, long deletedAt, String latestCommitHash) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        fileIndexMapper.update(
                new LambdaUpdateWrapper<FileIndexEntity>()
                        .eq(FileIndexEntity::getDeletedAt, 0L)
                        .in(FileIndexEntity::getId, fileIds)
                        .set(FileIndexEntity::getDeletedAt, deletedAt)
                        .set(FileIndexEntity::getLatestCommitHash, latestCommitHash));
    }

    /**
     * 从DB彻底删除文件或文件夹（递归物理删除索引）。
     *
     * @param path 文件相对路径
     */
    public void deletePhysicallyByPathRecursively(String path) {
        fileIndexMapper.delete(new LambdaQueryWrapper<FileIndexEntity>()
                .and(w -> w.eq(FileIndexEntity::getPath, path)
                        .or()
                        .likeRight(FileIndexEntity::getPath, path + "/")));
    }

    public void deletePhysicallyByPath(String path) {
        fileIndexMapper.delete(new LambdaQueryWrapper<FileIndexEntity>()
                .eq(FileIndexEntity::getPath, path));
    }

    /**
     * 从DB彻底删除文件/目录。
     *
     * @param fileIds 文件 IDs
     */
    public void deletePhysicallyByIds(Collection<Long> fileIds) {
        fileIndexMapper.deleteByIds(fileIds);
    }

    /**
     * 恢复已删除文件或文件夹（递归重置 deleted_at）。
     *
     * @param path 文件相对路径
     */
    public void restore(String path) {
        FileIndexEntity entity = getByPathIncludingDeleted(path);
        if (entity == null) {
            return;
        }
        Set<Long> ids = collectIdsRecursively(path);
        if (ids.isEmpty()) {
            return;
        }
        fileIndexMapper.update(
                new LambdaUpdateWrapper<FileIndexEntity>()
                        .in(FileIndexEntity::getId, ids)
                        .set(FileIndexEntity::getDeletedAt, 0L));
    }

    /**
     * 恢复单个已删除文件/目录。
     *
     * @param fileId 文件 ID
     */
    public void restoreById(Long fileId) {
        fileIndexMapper.update(
                new LambdaUpdateWrapper<FileIndexEntity>()
                        .eq(FileIndexEntity::getId, fileId)
                        .set(FileIndexEntity::getDeletedAt, 0L));
    }

    /**
     * 查询回收站中直接子节点。
     *
     * @param parentPath 父路径
     * @return 已删除子节点列表
     */
    public List<FileIndexEntity> listTrash(String parentPath) {
        return fileIndexMapper.selectList(
                new LambdaQueryWrapper<FileIndexEntity>()
                        .eq(FileIndexEntity::getParentPath, parentPath)
                        .gt(FileIndexEntity::getDeletedAt, 0L)
                        .orderByDesc(FileIndexEntity::getDeletedAt));
    }

    /**
     * 递归收集某路径下所有节点 ID（包含自身）。
     */
    private Set<Long> collectIdsRecursively(String path) {
        Set<Long> ids = new HashSet<>();
        FileIndexEntity self = getByPathIncludingDeleted(path);
        if (self == null) {
            return ids;
        }
        ids.add(self.getId());
        List<FileIndexEntity> children = listAllChildrenIncludingDeleted(path);
        for (FileIndexEntity child : children) {
            ids.add(child.getId());
        }
        return ids;
    }

    /**
     * 扫描磁盘并建立/重建索引。
     *
     * @param root 工作空间根目录
     */
    public void reloadIndex(Path root) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String relative = relativePath(dir, root);
                    if (relative.isEmpty() || ".git".equals(relative) || ".lanting".equals(relative)) {
                        return FileVisitResult.CONTINUE;
                    }
                    indexOnCreate(relative, "folder", root);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String relative = relativePath(file, root);
                    if (relative.isEmpty() || relative.startsWith(".git/") || relative.startsWith(".lanting/")) {
                        return FileVisitResult.CONTINUE;
                    }
                    indexOnSave(relative, root);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("扫描磁盘建立索引失败：{}", root, e);
            throw new RuntimeException("扫描磁盘建立索引失败", e);
        }
    }

    /**
     * 一致性校验：扫描磁盘与 DB 索引对比，返回不一致报告（不自动修复/删除）。
     *
     * @param root  工作空间根目录
     * @param scope 扫描范围，仅对该路径前缀下的文件和目录做对比；为 null 或空则扫描全局
     * @return 不一致报告
     */
    public Map<String, Object> reconcile(Path root, String scope) {
        boolean hasScope = scope != null && !scope.isBlank();

        List<String> unindexedFiles = new ArrayList<>();
        List<String> unindexedFolders = new ArrayList<>();
        List<String> staleFiles = new ArrayList<>();
        List<String> staleFolders = new ArrayList<>();
        List<String> mtimeMismatches = new ArrayList<>();
        List<String> checksumMismatches = new ArrayList<>();

        // 加载 DB 中未删除索引记录，按 path 建立 Map
        List<FileIndexEntity> allIndex = hasScope
                ? fileIndexMapper.selectList(new LambdaQueryWrapper<FileIndexEntity>()
                                             .eq(FileIndexEntity::getDeletedAt, 0L)
                                             .and(w -> w.likeRight(FileIndexEntity::getPath, scope + "/")
                                                       .or()
                                                       .eq(FileIndexEntity::getPath, scope)))
                : fileIndexMapper.selectList(new LambdaQueryWrapper<FileIndexEntity>()
                                             .eq(FileIndexEntity::getDeletedAt, 0L));
        Map<String, FileIndexEntity> indexMap = new HashMap<>();
        for (FileIndexEntity entity : allIndex) {
            indexMap.put(entity.getPath(), entity);
        }

        // 遍历磁盘，对比 DB
        Set<String> diskPaths = new HashSet<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String relative = relativePath(dir, root);
                    if (relative.isEmpty() || ".git".equals(relative) || ".lanting".equals(relative)
                            || relative.startsWith(".git/") || relative.startsWith(".lanting/")) {
                        return FileVisitResult.CONTINUE;
                    }
                    // scope 过滤：不在 scope 范围内的目录跳过
                    if (hasScope && !relative.startsWith(scope)) {
                        return FileVisitResult.CONTINUE;
                    }
                    diskPaths.add(relative);
                    FileIndexEntity entity = indexMap.get(relative);
                    if (entity == null) {
                        unindexedFolders.add(relative);
                    } else if (entity.isFile()) {
                        mtimeMismatches.add(relative);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String relative = relativePath(file, root);
                    if (relative.isEmpty() || relative.startsWith(".git/") || relative.startsWith(".lanting/")) {
                        return FileVisitResult.CONTINUE;
                    }
                    // scope 过滤：不在 scope 范围内的文件跳过
                    if (hasScope && !relative.startsWith(scope + "/") && !relative.equals(scope)) {
                        return FileVisitResult.CONTINUE;
                    }
                    diskPaths.add(relative);
                    FileIndexEntity entity = indexMap.get(relative);
                    if (entity == null) {
                        unindexedFiles.add(relative);
                    } else {
                        long diskMtime = lastModifiedTime(file);
                        if (diskMtime != entity.getMtime()) {
                            mtimeMismatches.add(relative);
                        } else {
                            // mtime 一致时才测 CRC32，检测“mtime 被还原但内容已改”的情况
                            long diskCrc32 = crc32(file);
                            if (diskCrc32 != (entity.getCrc32() != null ? entity.getCrc32() : 0L)) {
                                checksumMismatches.add(relative);
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("reconcile 遍历磁盘失败：{}", root, e);
            throw new RuntimeException("reconcile 遍历磁盘失败", e);
        }

        // 遍历 DB，找出磁盘缺失的记录
        for (FileIndexEntity entity : allIndex) {
            String path = entity.getPath();
            if (!diskPaths.contains(path)) {
                if (entity.isDirectory()) {
                    staleFolders.add(path);
                } else {
                    staleFiles.add(path);
                }
            }
        }

        Map<String, Object> report = new HashMap<>();
        report.put("total", allIndex.size());
        report.put("unindexedFiles", unindexedFiles);
        report.put("unindexedFolders", unindexedFolders);
        report.put("staleFiles", staleFiles);
        report.put("staleFolders", staleFolders);
        report.put("mtimeMismatches", mtimeMismatches);
        report.put("checksumMismatches", checksumMismatches);
        return report;
    }

    /**
     * 全局扫描。等价于 {@link #reconcile(Path, String)} 且 scope 为 null。
     */
    public Map<String, Object> reconcile(Path root) {
        return reconcile(root, null);
    }

    /**
     * 查询当前索引状态。
     *
     * @return 状态统计
     */
    public Map<String, Object> status() {
        long total = fileIndexMapper.selectCount(new LambdaQueryWrapper<FileIndexEntity>()
                .eq(FileIndexEntity::getDeletedAt, 0L));
        Map<String, Object> status = new HashMap<>();
        status.put("total", total);
        return status;
    }

    /**
     * 修复模式。
     */
    public enum RepairMode {
        DISK_WINS;

        public static RepairMode of(String mode) {
            if (mode == null || mode.isBlank()) {
                return DISK_WINS;
            }
            if ("disk_wins".equalsIgnoreCase(mode)) {
                return DISK_WINS;
            }
            throw new IllegalArgumentException("Unsupported repair mode: " + mode);
        }
    }

    /**
     * 以磁盘为准修复索引不一致。当前仅支持 DISK_WINS 模式：
     * <ul>
     *   <li>missing：删除 DB 中缺失的索引记录</li>
     *   <li>orphan：按磁盘实际内容建立/更新索引</li>
     *   <li>mismatch：重新读取磁盘内容并更新 mtime/crc32</li>
     * </ul>
     *
     * @param root 工作空间根目录
     * @param mode 修复模式
     * @return 修复结果统计
     */
    public Map<String, Object> repair(Path root, RepairMode mode) {
        return repair(root, mode, null);
    }

    /**
     * 以磁盘为准修复索引不一致。
     *
     * @param root  工作空间根目录
     * @param mode  修复模式
     * @param scope 修复范围，仅对该路径前缀下的文件和目录做对比；为 null 或空则修复全局
     * @return 修复结果统计
     */
    public Map<String, Object> repair(Path root, RepairMode mode, String scope) {
        if (mode != RepairMode.DISK_WINS) {
            throw new IllegalArgumentException("Unsupported repair mode: " + mode);
        }

        Map<String, Object> report = reconcile(root, scope);
        @SuppressWarnings("unchecked")
        List<String> unindexedFiles = (List<String>) report.get("unindexedFiles");
        @SuppressWarnings("unchecked")
        List<String> unindexedFolders = (List<String>) report.get("unindexedFolders");
        @SuppressWarnings("unchecked")
        List<String> staleFiles = (List<String>) report.get("staleFiles");
        @SuppressWarnings("unchecked")
        List<String> staleFolders = (List<String>) report.get("staleFolders");
        @SuppressWarnings("unchecked")
        List<String> checksumMismatches = (List<String>) report.get("checksumMismatches");
        @SuppressWarnings("unchecked")
        List<String> mtimeMismatches = (List<String>) report.get("mtimeMismatches");

        // 修复 missing：从 DB 中彻底删除记录
        for (String path : staleFiles) {
            deletePhysicallyByPath(path);
        }
        for (String path : staleFolders) {
            deletePhysicallyByPathRecursively(path);
        }

        // 修复 orphan：按磁盘内容建立/更新索引
        for (String path : unindexedFiles) {
            try {
                byte[] bytes = Files.readAllBytes(root.resolve(path));
                indexOnSave(path, root, bytes);
            } catch (IOException e) {
                log.warn("修复 orphan 文件读取失败：{}，跳过", path, e);
            }
        }
        for (String path : unindexedFolders) {
            indexOnCreate(path, "folder", root);
        }

        // 修复 checksum/mtime mismatch：以磁盘为准重新计算
        Set<String> toUpdate = new HashSet<>(checksumMismatches);
        toUpdate.addAll(mtimeMismatches);
        for (String path : toUpdate) {
            try {
                byte[] bytes = Files.readAllBytes(root.resolve(path));
                indexOnSave(path, root, bytes);
            } catch (IOException e) {
                log.warn("修复 mismatch 文件读取失败：{}，跳过", path, e);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("repairedstaleFiles", staleFiles);
        result.put("repairedstaleFolders", staleFolders);
        result.put("repairedunindexedFiles", unindexedFiles);
        result.put("repairedunindexedFolders", unindexedFolders);
        result.put("repairedChecksumMismatches", checksumMismatches);
        result.put("repairedMtimeMismatches", mtimeMismatches);
        return result;
    }

    private String relativePath(Path path, Path root) {
        return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private String extractName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash == -1 ? path : path.substring(lastSlash + 1);
    }

    private String extractParentPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash == -1 ? "" : path.substring(0, lastSlash);
    }

    private long lastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            log.warn("读取文件修改时间失败：{}，使用 0", path, e);
            return 0L;
        }
    }

    /**
     * 计算字节数组的 CRC32 校验和。
     */
    public static long crc32(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return 0L;
        }
        CRC32 crc32 = new CRC32();
        crc32.update(bytes);
        return crc32.getValue();
    }

    /**
     * 计算文件内容的 CRC32 校验和。读取失败时返回 0。
     */
    public static long crc32(Path path) {
        try {
            return crc32(Files.readAllBytes(path));
        } catch (IOException e) {
            log.warn("读取文件计算 CRC32 失败：{}，返回 0", path, e);
            return 0L;
        }
    }
}
