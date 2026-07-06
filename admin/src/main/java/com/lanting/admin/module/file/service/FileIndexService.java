package com.lanting.admin.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * 按父路径查询直接子节点。
     *
     * @param parentPath 父路径
     * @return 子节点列表
     */
    public List<FileIndexEntity> listByParentPath(String parentPath) {
        return fileIndexMapper.selectByParentPath(parentPath);
    }

    /**
     * 按路径查询单个文件索引。
     *
     * @param path 文件相对路径
     * @return 索引记录，不存在返回 null
     */
    public FileIndexEntity getByPath(String path) {
        LambdaQueryWrapper<FileIndexEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileIndexEntity::getPath, path);
        return fileIndexMapper.selectOne(wrapper);
    }

    /**
     * 创建文件或文件夹后建立索引。
     *
     * @param path 文件相对路径
     * @param type file / folder
     * @param root 工作空间根目录
     */
    public void indexOnCreate(String path, String type, Path root) {
        FileIndexEntity existing = getByPath(path);
        long now = System.currentTimeMillis();
        long mtime = "folder".equals(type) ? lastModifiedTime(root.resolve(path)) : 0L;

        if (existing != null) {
            existing.setType(type);
            existing.setName(extractName(path));
            existing.setParentPath(extractParentPath(path));
            existing.setMtime(mtime);
            existing.setUpdateTime(now);
            fileIndexMapper.updateById(existing);
            return;
        }

        FileIndexEntity entity = new FileIndexEntity();
        entity.setPath(path);
        entity.setName(extractName(path));
        entity.setType(type);
        entity.setParentPath(extractParentPath(path));
        entity.setMtime(mtime);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        fileIndexMapper.insert(entity);
    }

    /**
     * 删除文件或文件夹前删除索引（递归删除子路径）。
     *
     * @param path 文件相对路径
     */
    public void indexOnDelete(String path) {
        // 删除自身
        LambdaQueryWrapper<FileIndexEntity> self = new LambdaQueryWrapper<>();
        self.eq(FileIndexEntity::getPath, path);
        fileIndexMapper.delete(self);

        // 递归删除以该路径为前缀的子节点
        LambdaQueryWrapper<FileIndexEntity> children = new LambdaQueryWrapper<>();
        children.likeRight(FileIndexEntity::getPath, path + "/");
        fileIndexMapper.delete(children);
    }

    /**
     * 保存文件后更新索引。文件不存在时自动 INSERT，存在时 UPDATE mtime。
     *
     * @param path 文件相对路径
     * @param root 工作空间根目录
     */
    public void indexOnSave(String path, Path root) {
        FileIndexEntity existing = getByPath(path);
        long mtime = lastModifiedTime(root.resolve(path));
        long now = System.currentTimeMillis();

        if (existing != null) {
            existing.setMtime(mtime);
            existing.setUpdateTime(now);
            fileIndexMapper.updateById(existing);
            return;
        }

        FileIndexEntity entity = new FileIndexEntity();
        entity.setPath(path);
        entity.setName(extractName(path));
        entity.setType("file");
        entity.setParentPath(extractParentPath(path));
        entity.setMtime(mtime);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        fileIndexMapper.insert(entity);
    }

    /**
     * 批量保存文件后更新索引。
     *
     * @param paths 文件相对路径列表
     * @param root  工作空间根目录
     */
    public void indexOnSaveBatch(List<String> paths, Path root) {
        for (String path : paths) {
            indexOnSave(path, root);
        }
    }

    /**
     * 扫描磁盘并建立/重建索引。用于工作空间初始化或 reconcile。
     *
     * @param root 工作空间根目录
     */
    public void scanAndIndex(Path root) {
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
     * @param root 工作空间根目录
     * @return 不一致报告
     */
    public Map<String, Object> reconcile(Path root) {
        List<String> orphanFiles = new ArrayList<>();
        List<String> orphanFolders = new ArrayList<>();
        List<String> missingFiles = new ArrayList<>();
        List<String> missingFolders = new ArrayList<>();
        List<String> mtimeMismatches = new ArrayList<>();

        // 加载 DB 中所有索引记录，按 path 建立 Map
        Map<String, FileIndexEntity> indexMap = new HashMap<>();
        List<FileIndexEntity> allIndex = fileIndexMapper.selectList(null);
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
                    diskPaths.add(relative);
                    FileIndexEntity entity = indexMap.get(relative);
                    if (entity == null) {
                        orphanFolders.add(relative);
                    } else if (!"folder".equals(entity.getType())) {
                        // 磁盘是目录但 DB 记录为文件
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
                    diskPaths.add(relative);
                    FileIndexEntity entity = indexMap.get(relative);
                    if (entity == null) {
                        orphanFiles.add(relative);
                    } else {
                        long diskMtime = lastModifiedTime(file);
                        if (diskMtime != entity.getMtime()) {
                            mtimeMismatches.add(relative);
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
                if ("folder".equals(entity.getType())) {
                    missingFolders.add(path);
                } else {
                    missingFiles.add(path);
                }
            }
        }

        Map<String, Object> report = new HashMap<>();
        report.put("total", allIndex.size());
        report.put("orphanFiles", orphanFiles);
        report.put("orphanFolders", orphanFolders);
        report.put("missingFiles", missingFiles);
        report.put("missingFolders", missingFolders);
        report.put("mtimeMismatches", mtimeMismatches);
        return report;
    }

    /**
     * 查询当前索引状态。
     *
     * @return 状态统计
     */
    public Map<String, Object> status() {
        long total = fileIndexMapper.selectCount(null);
        Map<String, Object> status = new HashMap<>();
        status.put("total", total);
        return status;
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
}
