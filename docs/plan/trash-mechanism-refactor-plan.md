# Trash 机制重构计划

> 状态：阶段 1-3 已完成，阶段 4 待设计  
> 创建时间：2026-07-15  
> 最后更新：2026-07-17

---

## 一、已敲定的设计决策

| 决策点 | 结论 |
|---|---|
| 删除是否自动 commit | 是。所有 `delete` 操作默认生成一个 Git `delete` commit。 |
| `deleted_at` 取值 | 删除 commit 的秒级时间戳 × 1000（毫秒），与 Git commit 时间一致。 |
| `latest_commit_hash` 语义 | 该文件/目录**最后一次进入 Git 的 commit hash**。删除时更新为本次 `delete` commit 的 hash；恢复时从该 commit 取父 commit，读取删除前的内容。 |
| 恢复时读取内容来源 | 从 `latest_commit_hash` 对应的删除 commit 出发，读其父 commit 中该文件的内容。 |
| 同名路径再创建 | 不复活旧 `fileId`，直接插入新记录。同一路径在 trash 中可存在多个历史版本。 |
| 恢复路径冲突 | 只提供"覆盖 / 取消 / 强制重建"三个选项；覆盖前自动先提交当前新文件内容。 |
| 恢复后 fileId 处理 | 恢复谁，物理删除谁的 `fileId`；当前活跃 `fileId` 保留。 |
| 回收站展示 | 按 `deleted_at` 倒序排列，同一路径多版本并列展示。 |
| 目录恢复范围 | 递归恢复该目录及其所有子文件/子目录（与 spec 场景 1 一致）。恢复前预览冲突列表让用户确认。 |
| 轮询轮播文案展示 | 一期：屏蔽 @ 轮播组展示；二期：看板增加跑马灯。 |
| commit 锁语义 | 方案 A：只提交当前用户已锁定的文件，保持与 save/lock 一致。 |
| delete 目录锁 | 使用 `FileLockService.acquireFolderLock`（先 compute 封门再逐文件 acquire 清场），操作完成 `finally` 释放。 |
| 双 commit 策略 | `auto commit before delete`（备份未提交内容）+ `delete commit`（git rm），最大程度保证数据不丢失。 |

---

## 二、分阶段修改计划

### 阶段 1：数据模型与 FileIndexService 基础调整 ✅

**改动点**：

1. `FileIndexEntity` 增加字段：
   ```java
   private String latestCommitHash;
   ```

2. 建表 SQL（`docs/sql` 中对应的 `lanting_file_index` 建表语句）增加字段：
   ```sql
   latest_commit_hash VARCHAR(40)
   ```
   不采用 `ALTER TABLE`，直接改 `CREATE TABLE`。

3. `FileIndexService.indexOnDelete(String path)`：
   - 方法签名改为 `indexOnDelete(String path, long deletedAt, String latestCommitHash)`；
   - 更新命中记录的 `deleted_at` 和 `latest_commit_hash`；
   - 只更新 `deleted_at = 0` 的记录，避免唯一约束冲突。

4. `FileIndexService.indexOnDeleteByIds(Set<Long> fileIds)`：
   - 方法签名改为 `indexOnDeleteByIds(Set<Long> fileIds, long deletedAt, String latestCommitHash)`；
   - 更新命中记录的 `deleted_at` 和 `latest_commit_hash`；
   - 只更新 `deleted_at = 0` 的记录。

5. `FileIndexService.listTrash(String parentPath)`：
   - 在查询条件基础上增加 `.orderByDesc(FileIndexEntity::getDeletedAt)`。

**验收标准**：
- 数据模型编译通过；
- 现有 `FileIndexServiceTest` 通过（或同步更新测试）。

---

### 阶段 2：GitFileService.delete 改造 ✅

**实现架构 — 分层容错删除**：

```
delete()
  ├─ 锁管理层：acquireFolderLock(目录) / doIfHolder(文件)
  │    ├─ try { doDelete() }
  │    └─ finally { release }
  │
  └─ doDelete() — 文件和目录共享同一套删除逻辑
       ├─ diskExists? → ACBD(git add + commit) + forceDelete
       │                  ↑ 仅磁盘文件存在时执行，避免空 commit
       └─ 始终执行 → git rm commit + indexOnDelete(DB 软删除)
```

**三个关键设计**：

1. **ACBD 受 `Files.exists` 保护** — 磁盘文件已被外部删除时，`git add` 只会暂存"删除"而非备份内容。所以仅在文件存在时执行 ACBD。

2. **HappyRun 逐步容错** — 三步各被 HappyRun 包裹，每步失败不阻断下一步：
   - ACBD → 忽略 `NoFilepatternException`
   - forceDelete → 忽略 `FileNotFoundException`（TOCTOU 防御）
   - git rm commit → 忽略 `NoFilepatternException`（文件从未进入 Git）
   - 最终 DB 软删除一定执行，保证幽灵节点被清理

3. **锁在外，逻辑在内** — 锁的获取和释放写在 `delete()`，`doDelete()` 不碰锁。调用方负责隔离，被调方负责执行。

**接口变更**：
- `GitFileService.delete(Long fileId)` 去掉 `force` 参数
- `FileController.delete` 去掉 `force` 参数
- 删除失败直接抛异常，不再返回 `DeleteLockedVO`

**新增工具**：
- `HappyRun`（`admin/src/main/java/com/lanting/admin/common/util/HappyRun.java`）：容错执行工具，`run()` 忽略指定异常继续执行，`retry()` 失败重试
- `FileIndexService` 新增 `updateCommitHashByIds` 等方法

**容错场景**（ACBD = auto commit before delete）：

| case | 磁盘状态 | ACBD | FS删除 | git rm commit | DB 软删除 |
|------|----------|------|--------|---------------|-----------|
| 1 | 存在 | ✓ | ✓ | ✓ | ✓ |
| 2 | 存在，ACBD 后被外部删 | ✓ | ✗(忽略) | ✓ | ✓ |
| 3 | 存在，ACBD 后被外部删+commit | ✓ | ✗(忽略) | ✗(忽略) | ✓ |
| 4 | 不存在，有 Git 历史 | ✗(跳过) | ✗(跳过) | ✓ | ✓ |
| 5 | 不存在，已 commit 过 | ✗(跳过) | ✗(跳过) | ✗(忽略) | ✓ |

---

### 阶段 3：GitFileService.commit 调整 ✅

**决策**：方案 A — 只提交当前用户已锁定的文件，保持与 `save`/`lock` 一致。

**实现**：
1. 乐观筛选：遍历待提交文件，`isHolder` 快照判断 → 持锁者进入 `commited`，否则进入 `skipped`
2. `doIfLocked` 原子复核 + 执行：按 segment 排序加锁 → 二次校验 `isHolder` → `git add + commit` → 批量更新 `latestCommitHash`
3. `deleted_at > 0` 的文件直接 skipped（delete 已自动 commit）
4. 使用 `listByIds` 批量查询，消除 N+1

**为什么必须用 `doIfLocked`**：`isHolder` 快照检查存在 TOCTOU 窗口——check 后、git add 前，他人可能抢锁并写入。脏提交会污染 Git 历史和 blame。`doIfLocked` 将"校验 + 提交"原子化，利用 256 segment 分段锁（5-20ms 持有时间，阻塞概率 <4%），代价极低。

---

### 阶段 4：GitFileService.restore 改造（中高风险）

**单文件恢复**：

1. 从 `latest_commit_hash` 对应的删除 commit 取父 commit，读取文件内容；
2. 检查目标路径是否存在：
   - 不存在：直接写回磁盘，重置原 `fileId` 的 `deleted_at = 0`，更新 `latest_commit_hash` 为新生成的 restore commit；
   - 存在：返回冲突信息，用户选择覆盖/取消/强制重建；
3. 覆盖：先提交当前新文件内容（`save <path> before restore`），再用旧内容覆盖，生成 `restore <path>` commit，物理删除旧 `fileId`。

**目录恢复**（递归恢复子文件）：

1. 对目标目录调用 `fileLockService.acquireFolderLock(path, currentUser)` 隔离；
2. 检查目录是否存在，不存在则重建（DB + FS），存在则复用；
3. 根据 `latest_commit_hash` + path 前缀获取该目录下所有文件/目录列表（即删除时一起标记的节点）；
4. 检查每个待恢复子文件/子目录是否与当前路径冲突；
5. 有冲突：统一告知用户，提供"强制重建"选项；
6. 无冲突：直接重建目录树和文件内容；
7. 生成 `restore <path>` commit；
8. `finally` 中 `releaseFolderLock`。

---

### 阶段 5：回收站接口完善（低风险）

1. `GET /api/files/trash`：已存在，按 `deleted_at` 倒序展示；同一路径多版本并列显示。
2. `POST /api/files/trash/restore`：已存在，需支持返回"路径冲突"状态；增加 `force` 参数用于用户确认强制重建。
3. `DELETE /api/files/trash/purge`：已存在，物理删除该 `fileId` 索引，释放锁。

---

### 阶段 6：测试与文档（低风险）

**测试补充**：
1. `delete` 后检查 `latest_commit_hash` 和 `deleted_at`；
2. `delete` 后检查 publish tag 是否指向新 commit；
3. `commit` 对软删除文件返回 skipped；
4. 恢复路径冲突：覆盖/取消/强制重建；
5. 同路径多次删除，回收站展示多版本；
6. 恢复后旧 `fileId` 物理删除；
7. 目录删除后锁释放完整；
8. 删除从未 commit 过的新建文件的行为。

**文档同步**：
- 根据最终实现微调 `docs/backend/file-system-deletion-spec.md`。

---

## 三、执行进度

- [x] 阶段 1：数据模型 + FileIndexService 基础
- [x] 阶段 2：GitFileService.delete 改造（分层容错删除 + HappyRun）
- [x] 阶段 3：GitFileService.commit 调整（doIfLocked 原子保护）
- [ ] 阶段 4：restore 改造
- [ ] 阶段 5：回收站接口完善
- [ ] 阶段 6：测试与文档
