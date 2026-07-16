# 文件锁重构执行计划

> 状态：待执行  
> 创建时间：2026-07-16  
> 对应设计文档：`docs/backend/file-lock-design.md`

---

## 变更清单总览

| 文件 | 操作 | 说明 |
|------|------|------|
| `FileLockService.java` | 修改 | 新增目录锁相关字段和方法；`acquire`/`doIfHolder` 加 `filePath` 参数 |
| `FileLockServiceTest.java` | 修改 | 新增 2 个测试用例 |

---

## 一、FileLockService.java

### 1.1 新增字段

```java
private final Map<String, LockInfo> folderHardLocks = new ConcurrentHashMap<>();
private static final long FOLDER_LOCK_TTL_MS = 10_000;
```

### 1.2 新增依赖注入

```java
private final FileIndexService fileIndexService;
```

构造函数加 `fileIndexService` 参数。

### 1.3 新增方法

| 方法 | 可见性 | 要点 |
|------|--------|------|
| `checkAncestorFolderLock(filePath, holder)` | private | 遍历 `folderHardLocks`，双向 `startsWith` 检查祖先和子孙；过期锁 `remove(key, value)` CAS 清理；命中则抛 `FILE_LOCKED` |
| `acquireFolderLock(path, holder)` | public | ① `ensureFolderLocksSafety` ② `compute` 注册目录锁 ③ `listAllChildren` + 逐文件 `acquire` 抢子文件锁 ④ 步骤③失败则 `releaseFolderLock` |
| `releaseFolderLock(path, holder)` | public | `folderHardLocks.remove(path, holder)` |

### 1.4 签名变更

| 方法 | 旧签名 | 新签名 |
|------|--------|--------|
| `acquire` | `(Long fileId, String holder)` | `(Long fileId, String filePath, String holder)` |
| `doIfHolder` | `(Long fileId, String username, Supplier<T>)` | `(Long fileId, String filePath, String username, Supplier<T>)` |

两个方法内均加快速通道：`if (!folderHardLocks.isEmpty()) checkAncestorFolderLock(filePath, holder);`

---

## 二、FileLockServiceTest.java

在现有测试类中追加 2 个用例：

### 用例 1：远端祖先被他人锁定

```
given: bob 执行 acquireFolderLock("sql", "bob")
when:  alice 执行 acquire(1L, "sql/etl/daily/init.sql", "alice")
then:  抛 FILE_LOCKED
```

### 用例 2：TTL 过期自动清理

```
given: bob 执行 acquireFolderLock("sql", "bob")，lockedAt 被设为 11 秒前
when:  alice 执行 acquire(1L, "sql/etl/init.sql", "alice")
then:  无异常，且 folderHardLocks 中 "sql" 已被清理
```

测试辅助方法：通过反射暴露 `setLockedAt` 和 `getFolderHardLockCount`（仅测试用）。

---

## 三、执行顺序

1. `FileLockService` — 加字段、加方法、改签名
2. `FileLockServiceTest` — 写 2 个测试，跑绿
3. `GitFileService` 暂不动，后续单独适配
