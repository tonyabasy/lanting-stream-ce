package com.lanting.admin.module.file.service;

import com.lanting.admin.common.exception.BusinessException;
import com.lanting.admin.module.file.result.FileResultCode;
import com.lanting.admin.module.file.vo.AcquireLockVO;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
     * path -> 持锁信息。
     */
    private final Map<String, LockInfo> lockMap = new ConcurrentHashMap<>();

    /**
     * 固定大小的 stripe 锁数组。把文件路径按 hash 映射到某一把锁，
     * 在"持锁校验与执行"和"锁操作"之间提供互斥；不同路径可能命中不同 stripe 实现并行，
     * 也可能命中同一 stripe 导致无关串行，但不会影响正确性。
     * <p>
     * 数量为 32，基于 CE 并发编辑文件数通常不超过 5 的估算：5 个文件时碰撞概率约 14%，
     * 内存占用可忽略，且无 OOM 风险。
     */
    private static final int STRIPE_COUNT = 32;
    private final Object[] stripes = new Object[STRIPE_COUNT];

    public FileLockService() {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            stripes[i] = new Object();
        }
    }

    private Object stripeFor(String path) {
        return stripes[Math.floorMod(path.hashCode(), STRIPE_COUNT)];
    }

    /**
     * 持锁信息。
     */
    private record LockInfo(String holder, long lockedAt) {
    }

    /**
     * 原子地校验持锁并执行动作。
     * <p>
     * 临界区与 {@link #acquire} 互斥：动作执行期间他人的抢锁请求会阻塞等待；
     * 若他人先抢到锁，本方法进入临界区后校验失败，抛 30709 文件已被锁定。
     * <p>
     * <b>死锁纪律</b>：action 内允许获取 Git 写锁（path stripe → gitWriteLock 的顺序），
     * 但任何持有 gitWriteLock 的代码<b>不得</b>再调用本方法或锁操作（反向嵌套会死锁），
     * 见 GitFileService#withWorkspaceLock 的说明。
     *
     * @param path     文件相对路径
     * @param username 期望的持锁人
     * @param action   持锁校验通过后执行的动作
     * @return action 的返回值
     */
    public <T> T doIfHolder(String path, String username, Supplier<T> action) {
        synchronized (stripeFor(path)) {
            if (!isHolder(path, username)) {
                throw new BusinessException(FileResultCode.FILE_LOCKED);
            }
            return action.get();
        }
    }

    /**
     * 抢锁。软锁：即使有人持锁也可以强制抢锁成功。
     * <p>
     * 与 {@link #doIfHolder} 互斥：他人的写动作正在执行时，抢锁会等待其完成。
     *
     * @param path   文件相对路径
     * @param holder 持锁人 username
     * @return 抢锁结果
     */
    public AcquireLockVO acquire(String path, String holder) {
        synchronized (stripeFor(path)) {
            AcquireLockVO vo = new AcquireLockVO();
            vo.setAcquired(true);

            LockInfo previous = lockMap.put(path, new LockInfo(holder, System.currentTimeMillis()));
            if (previous != null && !previous.holder.equals(holder)) {
                vo.setPreviousHolder(previous.holder);
                vo.setPreviousHolderAt(previous.lockedAt);
            }
            return vo;
        }
    }

    /**
     * 释放锁。只有当前持锁人自己可以释放。
     *
     * @param path   文件相对路径
     * @param holder 持锁人 username
     * @return 是否释放成功
     */
    public boolean release(String path, String holder) {
        synchronized (stripeFor(path)) {
            LockInfo current = lockMap.get(path);
            if (current == null) {
                return true;
            }
            if (!current.holder.equals(holder)) {
                return false;
            }
            lockMap.remove(path);
            return true;
        }
    }

    /**
     * 强制释放锁，不校验持锁人。
     * <p>
     * 仅供服务端内部使用（如 force 删除文件夹时清理他人持有的锁），
     * 权限校验由调用方在入口处完成，不得直接暴露给 Controller。
     *
     * @param path 文件相对路径
     */
    public void forceRelease(String path) {
        synchronized (stripeFor(path)) {
            lockMap.remove(path);
        }
    }

    /**
     * 当前持锁人是否是指定用户。
     * <p>
     * 此方法读取的是某一时刻的快照，不保证调用后锁状态不变。
     * {@link GitFileService#commit} 使用此方法判断文件是否可提交，是已知的设计取舍
     * （commit 不要求原子性）。
     */
    public boolean isHolder(String path, String holder) {
        LockInfo current = lockMap.get(path);
        return current != null && current.holder.equals(holder);
    }

    /**
     * 获取当前持锁人。
     *
     * @param path 文件相对路径
     * @return 持锁人 username，未锁定返回 null
     */
    public String getHolder(String path) {
        LockInfo current = lockMap.get(path);
        return current == null ? null : current.holder;
    }

    /**
     * 获取持锁时间戳。
     *
     * @param path 文件相对路径
     * @return 持锁时间戳，未锁定返回 null
     */
    public Long getLockedAt(String path) {
        LockInfo current = lockMap.get(path);
        return current == null ? null : current.lockedAt;
    }
}
