# 文件锁设计方案

> 版本：v3  
> 更新时间：2026-07-16

## 1. 设计目标

- 保证同一文件在同一时刻只有一个用户执行变更操作。
- 保证目录级批量操作（删除、恢复、重命名、移动）期间，目录下的所有文件被隔离，外部无法写入或抢锁。
- 获取目录锁前必须先抢到所有子文件的文件锁，保证正在写入的文件不会被打断。
- 获取目录锁前检查祖先和子孙不存在活跃目录锁，保证父子目录操作互斥。
- 锁状态仅存于内存，服务重启后清空。

## 2. 核心概念

### 2.1 文件锁（File Soft Lock，已有）

- Key：`fileId`
- 存储：`fileSoftLocks`（`ConcurrentHashMap`），32 stripe 锁保护原子操作
- 语义：软锁，可被他人抢锁覆盖。谁抢到谁负责，长期持有不主动释放
- 适用场景：编辑、保存、删除单文件、提交、回滚、purge 等单文件变更

### 2.2 目录锁（Folder Hard Lock，新增）

- Key：目录 `path`（如 `sql/etl`）
- 存储：`folderHardLocks`（`ConcurrentHashMap`），10s TTL 惰性清理
- 语义：硬锁，目录批量操作期间注册，操作完成后必须释放。注册时自动清空所有子文件的文件锁
- 适用场景：删除目录、恢复目录、重命名目录、移动目录

## 3. 数据结构

```java
// 已有：文件软锁
private final Map<String, LockInfo> fileSoftLocks = new ConcurrentHashMap<>();

// 新增：文件夹硬锁，10s TTL
private final Map<String, LockInfo> folderHardLocks = new ConcurrentHashMap<>();
private static final long FOLDER_LOCK_TTL_MS = 10_000;

// LockInfo 记录持锁人和时间
private record LockInfo(String holder, long lockedAt) {
    boolean isExpired() {
        return System.currentTimeMillis() - lockedAt > FOLDER_LOCK_TTL_MS;
    }
}
```

## 4. 核心流程

### 4.1 文件操作（含隔离检查）

```java
public AcquireLockVO acquire(Long fileId, String filePath, String holder) {
    // 快速通道：无活跃目录锁时跳过
    if (!folderHardLocks.isEmpty()) {
        ensureFolderLocksSafety(filePath, holder);
    }
    synchronized (stripeFor(fileId)) {
        // 原抢锁逻辑不变
    }
}

public <T> T doIfHolder(Long fileId, String filePath, String username, Supplier<T> action) {
    synchronized (stripeFor(fileId)) {
        if (!folderHardLocks.isEmpty()) {
            ensureFolderLocksSafety(filePath, username);
        }
        if (!isHolder(fileId, username)) {
            throw new BusinessException(FileResultCode.FILE_LOCKED);
        }
        return action.get();
    }
}
```

### 4.2 祖先+子孙双向检查（遍历 folderHardLocks，0 DB）

```java
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
        // 例: lockedPath="sql", path="sql/etl/init.sql"
        if (path.startsWith(lockedPath + "/")) {
            throw new BusinessException(FileResultCode.FILE_LOCKED,
                    "父目录 " + lockedPath + " 正在被操作");
        }
        // path 是 lockedPath 的祖先？
        // 例: path="sql", lockedPath="sql/etl"
        if (lockedPath.startsWith(path + "/")) {
            throw new BusinessException(FileResultCode.FILE_LOCKED,
                    "子目录 " + lockedPath + " 正在被操作");
        }
    }
}
```

`folderHardLocks` 常年为空或仅 1-2 条，全表遍历纳秒级。<br>
双向 `startsWith` 覆盖了祖先和子孙两种冲突，不需要 DB 参与。

### 4.3 目录批量操作

```java
public void acquireFolderLock(String path, String holder) {
    // 1. 祖先+子孙双向检查（遍历 folderHardLocks，0 DB）
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

    // 3. 再清场 — 抢子文件锁。此后任何新文件操作被 ensureFolderLocksSafety 拦截
    try {
        List<FileIndexEntity> children = fileIndexService.listAllChildren(path);
        for (FileIndexEntity child : children) {
            if (child.isFile()) {
                acquire(child.getId(), child.getPath(), holder);
            }
        }
    } catch (Exception e) {
        // 4. 抢子文件锁失败则释放目录锁，不残留
        releaseFolderLock(path, holder);
        throw e;
    }
}

public void releaseFolderLock(String path, String holder) {
    folderHardLocks.remove(path, holder);
}
```

**关键顺序：先封门，再清场。** 注册目录锁后，任何新的文件操作都被 `ensureFolderLocksSafety` 直接拦截；`acquire` 只需等待已在 `synchronized(stripe)` 内的写入线程完成即可。抢子文件锁失败自动释放目录锁，不残留。

**祖先 + 子孙双向检查：** 同一祖先链上的两个目录锁不会共存。操作 `/sql/etl` 的人会拒绝操作 `/sql`，反之亦然。

## 5. 释放规则

| 锁类型 | 释放策略 |
|--------|----------|
| 文件锁 | 不主动释放，下次操作者抢锁覆盖 |
| 目录锁 | `finally` 中释放；10s TTL 惰性清理兜底 |

## 6. 死锁分析

| 线程 | 锁获取顺序 |
|------|-----------|
| 文件 save/acquire | 读 `folderHardLocks`（无锁）→ `synchronized(stripe)` |
| 目录 acquireFolderLock | `ensureFolderLocksSafety`（读 folderHardLocks）→ `synchronized(stripe)` 逐文件抢锁 → `folderHardLocks.compute`（写） |

`folderHardLocks.compute` 是最末步，此前所有读写均在 ConcurrentHashMap 无锁语义下完成。

两条路径无环，无死锁。

## 7. TTL 惰性清理

硬锁极端情况下（异常穿透 finally）可能残留。`ensureFolderLocksSafety` 中对过期锁执行惰性清理。

关键细节：用 `ConcurrentHashMap.remove(key, value)` 而非 `remove(key)`。过期锁被读到 `lock1` 时，若别线程恰好 release 了旧锁并 acquire 新锁 `lock2`（`lockedAt` 不同），`remove(key, lock1)` 因 value 不匹配而失败，不会误删新锁。

## 8. 性能

| 场景 | 频率 | 开销 |
|------|------|------|
| 正常编辑/保存 | 99.9% | `folderHardLocks.isEmpty()` → true，纳秒级跳过 |
| 有活跃目录操作时保存 | 极低 | 纯字符串查找，深度 ≤10，纳秒级 |
| 大目录删除 | 低频 | 1 次 DB 查询 + O(n) 纯内存清锁 |
