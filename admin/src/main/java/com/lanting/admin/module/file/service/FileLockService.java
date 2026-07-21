package com.lanting.admin.module.file.service;

import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.module.file.entity.FileIndexEntity;
import com.lanting.admin.module.file.result.FileResultCode;
import com.lanting.admin.module.file.vo.AcquireLockVO;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 文件锁服务（内存实现）。
 * <p>
 * 软锁语义：锁记录"谁在编辑"，可被他人强制接管（{@link #acquire} 永远成功），
 * 被接管者的后续写操作会因 {@link #doIfHolder} 校验失败而被拒绝。
 * <p>
 * 原子性保证：{@code doIfHolder} 将"校验持锁 + 执行动作"放在与
 * {@code acquire}/{@code release}/{@code forceRelease} 互斥的临界区内，
 * 确保写磁盘等动作执行期间锁不会被抢走——避免"B 已抢锁打开文件，
 * A 的迟到写入仍落盘"导致的内容不一致。
 * <p>
 * 锁状态存内存，服务重启即清空（会话级状态，符合设计预期，见 file-system-spec.md）。
 * 互斥锁采用固定大小的 stripe 数组，避免无界内存占用。
 *
 * @author wangzhao
 */
@Service
public class FileLockService {

    /**
     * fileId -> 持锁信息。
     */
    private final Map<String, LockInfo> fileSoftLocks = new ConcurrentHashMap<>();

    /**
     * 文件夹硬锁：path -> LockInfo，10s TTL 惰性清理。
     */
    private final Map<String, LockInfo> folderHardLocks = new ConcurrentHashMap<>();
    private static final long FOLDER_LOCK_TTL_MS = 10_000;

    /**
     * 固定大小的 segments 锁数组。把文件 ID 按 hash 映射到某一把锁，
     * 在"持锁校验与执行"和"锁操作"之间提供互斥；不同文件可能命中不同 segments 实现并行，
     * 也可能命中同一 segments 导致无关串行，但不会影响正确性。
     */
    private static final int SEGMENT_COUNT = 256;
    private final ReentrantLock[] segments = new ReentrantLock[SEGMENT_COUNT];

    private final FileIndexService fileIndexService;

    public FileLockService(FileIndexService fileIndexService) {
        this.fileIndexService = fileIndexService;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segments[i] = new ReentrantLock();
        }
    }

    private String key(Long fileId) {
        return fileId == null ? "" : fileId.toString();
    }

    private ReentrantLock segmentFor(Long fileId) {
        long theFileId = fileId == null ? 0 : fileId;
        return segments[Math.floorMod(theFileId, SEGMENT_COUNT)];
    }

    /**
     * 持锁信息。
     */
    private record LockInfo(String holder, long lockedAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - lockedAt > FOLDER_LOCK_TTL_MS;
        }

        public static LockInfo of(String holder) {
            return new LockInfo(holder, 0);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LockInfo lockInfo = (LockInfo) o;
            return Objects.equals(holder, lockInfo.holder);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(holder);
        }
    }

    /**
     * 原子地校验持锁并执行动作。
     * <p>
     * 临界区与 {@link #acquire} 互斥：动作执行期间他人的抢锁请求会阻塞等待；
     * 若他人先抢到锁，本方法进入临界区后校验失败，抛 30709 文件已被锁定。
     * <p>
     * <b>死锁纪律</b>：action 内允许获取 Git 写锁（fileId segments → gitWriteLock 的顺序），
     * 但任何持有 gitWriteLock 的代码<b>不得</b>再调用本方法或锁操作（反向嵌套会死锁），
     * 见 GitFileService#withWorkspaceLock 的说明。
     *
     * @param fileId   文件 ID
     * @param filePath 文件路径，用于目录锁隔离检查
     * @param username 期望的持锁人
     * @param action   持锁校验通过后执行的动作
     * @return action 的返回值
     */
    public <T> T doIfHolder(Long fileId, String filePath, String username, Callable<T> action) throws Exception {
        ReentrantLock lock = segmentFor(fileId);
        lock.lock();
        try {
            // 目录硬锁隔离检查
            if (!folderHardLocks.isEmpty()) {
                ensureFolderLocksSafety(filePath, username);
            }
            if (!isHolder(fileId, username)) {
                throw new BusinessException(FileResultCode.FILE_LOCKED);
            }
            return action.call();
        } finally {
            lock.unlock();
        }
    }

    public <T> T doIfHolder(Long fileId, String username, Callable<T> action) throws Exception {
        FileIndexEntity fileIndexEntity = fileIndexService.getById(fileId, FileIndexService.INCLUDE_DELETED);
        return doIfHolder(fileId, fileIndexEntity.getPath(), username, action);
    }

    /**
     * 多文件持锁校验与执行，原子操作。
     * <p>
     * 将所有文件的 segment 去重后按索引排序加锁，避免死锁；校验所有文件均被
     * username 持有且不与目录硬锁冲突后，执行 action；异常或无异常均释放锁。
     * <p>
     * fileIds 与 filePaths 需一一对应，长度必须一致。
     */
    public <T> T doIfHolder(List<Long> fileIds, List<String> filePaths, String username, Supplier<T> action) {
        if (fileIds == null || fileIds.isEmpty() || filePaths == null || filePaths.isEmpty()) {
            throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED,
                    "fileIds 和 filePaths 不能为空");
        }
        if (fileIds.size() != filePaths.size()) {
            throw new BusinessException(FileResultCode.FILE_OPERATION_FAILED,
                    "fileIds 和 filePaths 数量不一致");
        }

        // 收集去重后的 segment 索引，排序后按序加锁，防止死锁
        int[] indices = fileIds.stream()
                .mapToInt(id -> id == null ? 0 : Math.floorMod(id, SEGMENT_COUNT))
                .distinct()
                .sorted()
                .toArray();

        Arrays.stream(indices).forEach(i -> segments[i].lock());
        try {
            // 目录硬锁隔离检查
            if (!folderHardLocks.isEmpty()) {
                for (String path : filePaths) {
                    ensureFolderLocksSafety(path, username);
                }
            }

            // 校验所有文件均被当前用户持有
            for (Long fileId : fileIds) {
                if (!isHolder(fileId, username)) {
                    throw new BusinessException(FileResultCode.FILE_LOCKED, fileId.toString());
                }
            }

            return action.get();
        } finally {
            // 逆序释放，减少锁竞争峰值
            for (int i = indices.length - 1; i >= 0; i--) {
                segments[indices[i]].unlock();
            }
        }
    }

    public <T> T doIfHolder(List<Long> fileIds, String username, Supplier<T> action) {
        List<String> filePaths = fileIndexService.listByIds(fileIds, FileIndexService.INCLUDE_DELETED).stream().map(FileIndexEntity::getPath).toList();
        return doIfHolder(fileIds, filePaths, username, action);
    }

    /**
     * 抢锁。软锁：即使有人持锁也可以强制抢锁成功。
     * <p>
     * 与 {@link #doIfHolder} 互斥：他人的写动作正在执行时，抢锁会等待其完成。
     *
     * @param fileId   文件 ID
     * @param filePath 文件路径，用于目录锁隔离检查
     * @param holder   持锁人 username
     * @return 抢锁结果
     */
    public AcquireLockVO acquire(Long fileId, String filePath, String holder) {
        if (!folderHardLocks.isEmpty()) {
            ensureFolderLocksSafety(filePath, holder);
        }
        String key = key(fileId);
        ReentrantLock lock = segmentFor(fileId);
        lock.lock();
        try {
            AcquireLockVO vo = new AcquireLockVO();
            vo.setAcquired(true);

            LockInfo previous = fileSoftLocks.put(key, new LockInfo(holder, System.currentTimeMillis()));
            if (previous != null && !previous.holder.equals(holder)) {
                vo.setPreviousHolder(previous.holder);
                vo.setPreviousHolderAt(previous.lockedAt);
            }
            return vo;
        } finally {
            lock.unlock();
        }
    }

    public AcquireLockVO acquire(Long fileId, String holder) {
        FileIndexEntity fileIndexEntity = fileIndexService.getById(fileId, FileIndexService.INCLUDE_DELETED);
        return acquire(fileId, fileIndexEntity.getPath(), holder);
    }

    /**
     * 释放锁。只有当前持锁人自己可以释放。
     *
     * @param fileId 文件 ID
     * @param holder 持锁人 username
     * @return 是否释放成功
     */
    public boolean release(Long fileId, String holder) {
        String key = key(fileId);
        ReentrantLock lock = segmentFor(fileId);
        lock.lock();
        try {
            LockInfo current = fileSoftLocks.get(key);
            if (current == null) {
                return true;
            }
            if (!current.holder.equals(holder)) {
                return false;
            }
            fileSoftLocks.remove(key);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 强制释放锁，不校验持锁人。
     * <p>
     * 仅供服务端内部使用（如 force 删除文件夹时清理他人持有的锁），
     * 权限校验由调用方在入口处完成，不得直接暴露给 Controller。
     *
     * @param fileId 文件 ID
     */
    public void forceRelease(Long fileId) {
        String key = key(fileId);
        ReentrantLock lock = segmentFor(fileId);
        lock.lock();
        try {
            fileSoftLocks.remove(key);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 当前持锁人是否是指定用户。
     * <p>
     * 此方法读取的是某一时刻的快照，不保证调用后锁状态不变。
     * {@link GitFileService#commit} 使用此方法判断文件是否可提交，是已知的设计取舍
     * （commit 不要求原子性）。
     */
    public boolean isHolder(Long fileId, String holder) {
        LockInfo current = fileSoftLocks.get(key(fileId));
        return current != null && current.holder.equals(holder);
    }

    /**
     * 获取当前持锁人。
     *
     * @param fileId 文件 ID
     * @return 持锁人 username，未锁定返回 null
     */
    public String getHolder(Long fileId) {
        LockInfo current = fileSoftLocks.get(key(fileId));
        return current == null ? null : current.holder;
    }

    /**
     * 获取持锁时间戳。
     *
     * @param fileId 文件 ID
     * @return 持锁时间戳，未锁定返回 null
     */
    public Long getLockedAt(Long fileId) {
        LockInfo current = fileSoftLocks.get(key(fileId));
        return current == null ? null : current.lockedAt;
    }

    // ==================== 目录锁（硬锁） ====================

    /**
     * 确保指定路径不与任何活跃目录锁冲突（祖先 + 子孙双向检查）。
     * <p>
     * 遍历 {@code folderHardLocks} 做全表匹配，常年为空或仅 1-2 条，纳秒级。
     * 同时惰性清理过期锁。
     */
    private void ensureFolderLocksSafety(String path, String holder) {
        for (var entry : folderHardLocks.entrySet()) {
            String lockedPath = entry.getKey();
            LockInfo lock = entry.getValue();

            // TTL 过期：惰性清理
            if (lock.isExpired()) {
                folderHardLocks.remove(lockedPath, lock);
                continue;
            }
            // 同一持锁人放行
            if (lock.holder().equals(holder)) {
                continue;
            }
            // lockedPath 是 path 的祖先？
            if (path.startsWith(lockedPath + "/")) {
                throw new BusinessException(FileResultCode.FILE_LOCKED,
                        "父目录 " + lockedPath + " 正在被操作");
            }
            // path 是 lockedPath 的祖先？
            if (lockedPath.startsWith(path + "/")) {
                throw new BusinessException(FileResultCode.FILE_LOCKED,
                        "子目录 " + lockedPath + " 正在被操作");
            }
        }
    }

    /**
     * 获取目录锁。
     * <p>
     * 先封门（注册目录锁），再清场（抢子文件锁）。抢子文件锁失败则自动释放目录锁。
     */
    public void acquireFolderLock(String path, String holder) {
        // 1. 祖先+子孙双向检查
        ensureFolderLocksSafety(path, holder);

        // 2. 先封门 — 注册目录锁
        folderHardLocks.compute(path, (k, existing) -> {
            if (existing == null || existing.isExpired()) {
                return new LockInfo(holder, System.currentTimeMillis());
            }
            if (existing.holder().equals(holder)) {
                return existing;
            }
            throw new BusinessException(FileResultCode.FILE_LOCKED,
                    "目录 " + path + " 正在被 " + existing.holder() + " 操作");
        });

        // 3. 再清场 — 抢子文件锁
        try {
            List<FileIndexEntity> children = fileIndexService.listAllChildren(path);
            for (FileIndexEntity child : children) {
                if (child.isFile()) {
                    acquire(child.getId(), child.getPath(), holder);
                }
            }
        } catch (Exception e) {
            // 4. 抢子文件锁失败则释放目录锁
            releaseFolderLock(path, holder);
            throw e;
        }
    }

    /**
     * 释放目录锁（CAS 安全）。
     */
    public void releaseFolderLock(String path, String holder) {
        folderHardLocks.remove(path, LockInfo.of(holder));
    }

    public void forceReleaseFolderLock(String path) {
        folderHardLocks.remove(path);
    }
}
