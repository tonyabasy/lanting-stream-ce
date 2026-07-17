# 文件删除与回滚设计规范

## 背景

当前 `GitFileService.delete()` 会直接删除磁盘文件并从 `lanting_file_index` 索引表中移除记录。这带来两个问题：

1. **删除不可回滚**：索引记录被物理删除后，`fileId` 立即失效，用户无法再通过 `/api/files/history`、`/api/files/diff` 等接口查看文件历史。
2. **删除无法被用户感知**：用户希望删除和恢复都是明确的操作，并能从回收站中查看、选择并恢复历史删除版本。

本规范引入**软删除（Soft Delete）**机制：删除时只标记索引状态、删除磁盘文件，并自动生成一个 Git `delete` commit；被删除的文件进入回收站，用户可从中恢复或彻底删除。

---

## 设计目标

| 目标 | 说明 |
|---|---|
| 删除可回滚 | 删除后 `fileId` 仍有效，用户可从回收站恢复文件 |
| 删除可追踪 | `delete` 自动生成 Git commit，删除操作进入历史 |
| 前端无感知 | 正常文件树/内容查询默认过滤已删除文件 |
| 不破坏现有接口 | 已有 create/save/commit/history/diff/revert 接口行为基本不变 |

---

## 核心概念

### 路径依赖以 DB 索引为准

文件在 Git 工作区和业务接口中的路径统一由 `lanting_file_index` 索引表解析，而不是以客户端传入的 path 或本地缓存为准。这样设计的原因：

1. **路径变更全局一致**：重命名、移动文件夹等操作只需修改 DB 索引，所有用户、所有接口看到的文件路径立刻同步。
2. **并发冲突更容易解决**：多个用户同时修改同一个文件路径或文件名时，以 DB 索引的最终落盘结果作为唯一真实路径，避免"客户端各自持有不同旧路径"导致的冲突。
3. **删除后仍保留入口**：软删除只清理磁盘，不清理索引，所以 `fileId` 仍然是有效入口，可用于查历史、diff、revert/restore。
4. **锁与文件实体绑定**：文件锁基于 `fileId` 而非路径，即使文件被重命名，锁定的对象仍然是同一个文件。

简言之，DB 索引是文件系统的"真相来源"，所有路径相关操作都围绕它展开，这样多用户并发修改文件名、路径、内容时，系统有一个统一、稳定的参照点来发现和处理冲突。

### 软删除 vs 硬删除

| 类型 | 行为 | DB 索引 | 磁盘文件 | Git 历史 |
|---|---|---|---|---|
| 软删除（默认） | 用户调用 `delete` | 保留，`deleted_at > 0` | 删除 | 自动生成 `delete` commit |
| 硬删除 / 彻底删除 | 用户在回收站执行 | 物理删除 | 已删 | 保留（Git 不可逆） |

### 两个关键状态

- **工作区已删除**：磁盘文件不存在，`deleted_at > 0`。
- **Git 已删除**：`delete` 操作已经自动生成 commit，文件从 Git tree 中移除。

删除发生后，两个状态同时成立。用户可通过 `restore` 恢复，或通过 `purge` 彻底删除。

---

## 数据模型变更

在 `lanting_file_index` 表中增加软删除时间戳和最新提交 hash，并把 `path` 的唯一约束改为与删除时间戳联合唯一：

```sql
ALTER TABLE lanting_file_index
    ADD COLUMN deleted_at BIGINT DEFAULT 0,
    ADD COLUMN latest_commit_hash VARCHAR(40);

-- 软删除后同名路径需要能再次创建，因此 path 唯一约束必须包含 deleted_at
DROP INDEX IF EXISTS idx_file_index_path_unique;
CREATE UNIQUE INDEX idx_file_index_path_deleted ON lanting_file_index(path, deleted_at);
```

对应 `FileIndexEntity`：

```java
private Long deletedAt;
private String latestCommitHash;
```

- `deleted_at = 0`：正常文件
- `deleted_at > 0`：已软删除，取值为删除 commit 的秒级时间戳 × 1000（毫秒），确保与 Git commit 时间一致
- `latest_commit_hash`：该文件最后一次进入 Git 的 commit hash（创建/保存/删除/恢复时都会更新）

`deleted_at` 同时作为逻辑删除标志和业务删除时间戳。`lanting_file_index` 不继承 `BasicEntity`，因此不需要项目通用的 `is_delete` 字段。

### 索引调整

所有按 `parent_path` 查询的 tree/list 接口统一增加 `deleted_at = 0` 过滤。可在现有索引上依赖查询条件，不强制新增索引；若回收站查询性能不足，再补充 `(deleted_at)` 索引。

---

## 业务规则

### 1. `delete(fileId)`

**核心设计 — 双 commit 策略 + 分层容错**：

删除流程同时保证数据安全（备份未提交内容）和操作容错（磁盘文件已被外部删除）：

```
delete()
  ├─ 锁管理层：acquireFolderLock(目录) / doIfHolder(文件)
  │
  └─ doDelete() — 统一逻辑
       ├─ diskExists? → ACBD(备份未提交内容) + forceDelete
       └─ 始终执行 → delete commit(git rm) + DB 软删除
```

**双 commit**：
- `auto commit before delete`（ACBD）：删除前先 `git add + commit`，把磁盘上未提交的改动备份进 Git。仅在磁盘文件存在时执行。
- `delete <path>`：`git rm + commit`，将文件从 Git tree 中移除。

**HappyRun 逐步容错**：每一步被 HappyRun 包裹，单步失败不阻断后续：
- ACBD → 忽略 `NoFilepatternException`（文件不存在）
- forceDelete → 忽略 `FileNotFoundException`（TOCTOU 防御）
- git rm commit → 忽略 `NoFilepatternException`（文件从未进入 Git）
- DB 软删除始终执行，保证幽灵节点被清理

**容错矩阵**：

| case | 磁盘状态 | ACBD | FS删除 | git rm | DB 软删除 |
|------|----------|------|--------|--------|-----------|
| 1 | 存在 | ✓ | ✓ | ✓ | ✓ |
| 2 | 存在，ACBD后被外部删 | ✓ | ✗(忽略) | ✓ | ✓ |
| 3 | 存在，ACBD后被外部删+commit | ✓ | ✗(忽略) | ✗(忽略) | ✓ |
| 4 | 不存在，有 Git 历史 | ✗(跳过) | ✗(跳过) | ✓ | ✓ |
| 5 | 不存在，已 commit | ✗(跳过) | ✗(跳过) | ✗(忽略) | ✓ |

**接口变更**：去掉 `force` 参数（目录锁设计下不再需要），删除失败直接抛异常。


### 2. `tree(parentPath)` / `content(fileId)`

- 默认只返回 `deleted_at = 0` 的节点。
- `content(fileId)` 对已删除文件返回 `FILE_NOT_FOUND`（30702），因为磁盘文件已不存在；如需查看历史内容，使用 `history` 或回收站接口。

### 3. `commit(fileIds, message)`

commit 只处理 `deleted_at = 0` 的文件：

| 文件状态 | Git 操作 |
|---|---|
| `deleted_at = 0` | `git add <path>`（现有行为） |
| `deleted_at > 0` | skipped；删除已在 `delete()` 时自动 commit |

**原子保护**：使用 `FileLockService.doIfLocked` 将"校验持锁 → git commit"原子化。乐观筛选（`isHolder` 快照）后，按 segment 排序加锁二次复核，杜绝提交期间他人抢锁写入导致的"脏提交"。256 segment 分段锁，持有时间仅 5-20ms。

commit 成功后，批量更新 `latest_commit_hash` 为本次 commit hash。

### 4. `restore(fileId, commitHash)` 与 `revert(fileId, commitHash)` 的区别

| | `revert` | `restore` |
|---|---|---|
| 文件状态 | 文件仍存在（`deleted_at = 0`） | 文件已软删除（`deleted_at > 0`） |
| 用户意图 | 将当前文件内容**回滚到某个历史版本** | 将已删除文件从 Git 历史中**复活** |
| DB 操作 | 更新 `mtime`/`crc32`/`latest_commit_hash` | 若目标路径为空，重置 `deleted_at = 0` 并沿用原 `fileId`；若目标路径已被新文件占用，保留新 `fileId` 并覆盖内容，旧 `fileId` 物理删除 |
| 磁盘操作 | 覆盖已有文件内容 | 从无到有写回文件内容，或覆盖当前文件内容 |
| 路径冲突 | 无冲突（文件已存在） | 目录已存在则复用；文件已存在则提示"覆盖/取消"，覆盖前自动先提交新文件当前内容 |

两者在"读取指定 commit 内容写回磁盘"这一段可以复用同一个私有方法，但 Controller/Service 入口保持独立，避免语义混淆。

### 5. 回收站

- 新增 `trash(parentPath)` 接口，返回 `deleted_at > 0` 的文件/文件夹树，按 `deleted_at` 倒序排列。
- 支持 `restore(fileId)` 和 `purge(fileId)`（彻底删除）。
- 同一路径在回收站中可存在多个已删除版本；展示时以删除时间（`deleted_at`）和 `latest_commit_hash` 区分。
- 恢复时若目标路径被新文件占用，提示"覆盖/取消"；覆盖前自动先提交新文件当前内容（`save <path> before restore`），再用旧内容覆盖并生成 `restore <path>` commit。
- 恢复谁，就物理删除谁的索引记录；未被恢复的其他已删除版本继续留在回收站。
- 彻底删除时从 DB 物理删除索引，磁盘已删无需处理。

---

## 状态流转

```
正常文件
   │
   │ delete()  (自动 commit)
   ▼
软删除 + Git 已删除（磁盘无，DB 有 deleted_at > 0）
   │
   ├─ restore() ───────────► 正常文件（自动生成 restore commit）
   │
   └─ purge() ─────────────► 彻底删除（DB 索引移除，Git 历史仍保留）
```

## 典型场景

### 场景 1：删除目录后全部恢复

用户删除目录 A，A 中包含文件 A1、A2、A3。删除后用户发现误删，希望从回收站 **全部恢复**。

| 阶段 | DB 索引 | Git 仓库 | 磁盘 | 锁 |
|---|---|---|---|---|
| 初始 | A/A1/A2/A3 正常，`deleted_at=0` | A/A1/A2/A3 在 HEAD 中 | A/A1/A2/A3 存在 | 无 |
| 删除 A | A/A1/A2/A3 标记 `deleted_at=T` | 自动生成 `delete A` commit，A 从 tree 移除 | A 目录被删除 | 释放 A 及所有子文件锁 |
| 回收站 | 以目录树形式展示 A（含 A1/A2/A3） | 无 | 无 | 无 |
| 恢复 A | A/A1/A2/A3 重置 `deleted_at=0`，沿用原 `fileId` | 从 `delete A` 的父 commit 读取内容，自动生成 `restore A` commit | 重建 A 目录及 A1/A2/A3 | 恢复时按需抢锁/提示覆盖 |

关键点：
- 恢复后的 A1 仍能看到删除前的历史提交，因为 `git log -- A/A1` 仍包含所有历史 commit。
- 恢复时如果目录 A 已存在，则复用现有目录；如果文件 A1 已存在，提示用户是否覆盖，覆盖时自动抢锁写入。

### 场景 2：恢复已删除文件时路径被新文件占用

用户删除文件 A1，随后创建了新文件 A1 并写入内容，现在希望恢复旧 A1。

| 阶段 | DB 索引 | Git 仓库 | 磁盘 | 锁 |
|---|---|---|---|---|
| 初始 | `fileId_old` 正常 | A1 在 HEAD | A1 存在 | 无 |
| 删除旧 A1 | `fileId_old` 标记 `deleted_at=T1`，`latest_commit_hash=C1` | 自动生成 `delete A1` commit | A1 删除 | 释放 |
| 创建新 A1 | `fileId_new` 正常（`fileId_old` 在 trash） | 自动生成 `add A1` commit | A1 重新创建 | 无 |
| 保存新 A1 | `fileId_new` 更新 mtime/crc32 | 未自动 commit | A1 内容更新 | 当前用户持有锁 |
| 恢复前 | — | 系统自动生成 `save A1 before restore` commit | — | 无 |
| 选择覆盖 | — | 用户确认覆盖 | — | 无 |
| 恢复旧 A1 | `fileId_new` 保持活跃；`fileId_old` 物理删除 | 生成 `restore A1` commit，内容来自 `C1` 的父 commit | A1 内容被旧内容覆盖 | 自动抢锁写入 |

关键点：
- 路径冲突时只提供“覆盖”和“取消”两个选项。
- 覆盖前系统自动提交新 A1 当前内容，方便用户后续从 Git 历史恢复新内容。
- 恢复后保留当前 `fileId_new`，旧 `fileId_old` 从 DB 物理删除。

### 场景 3：反复创建、删除后恢复某个历史版本

用户反复创建、删除文件 A1，最终想从第一次删除（opt-del-1）恢复。

| 阶段 | DB 索引 | Git 仓库 | 磁盘 | 锁 |
|---|---|---|---|---|
| 创建 A1 v1 | `fileId_1` 正常 | A1 在 HEAD | A1 存在 | 无 |
| opt-del-1 | `fileId_1` 标记 `deleted_at=T1`，`latest_commit_hash=C1` | 生成 `delete A1` commit | A1 删除 | 释放 |
| 创建 A1 v2 | `fileId_2` 正常（`fileId_1` 在 trash） | 生成 `add A1` commit | A1 重新创建 | 无 |
| opt-del-2 | `fileId_2` 标记 `deleted_at=T2`，`latest_commit_hash=C2` | 生成 `delete A1` commit | A1 删除 | 释放 |
| 创建 A1 v3 | `fileId_3` 正常（`fileId_1`、`fileId_2` 在 trash） | 生成 `add A1` commit | A1 重新创建 | 无 |
| 从 opt-del-1 恢复 | `fileId_3` 保持活跃；`fileId_1` 物理删除；`fileId_2` 仍在 trash | 从 `C1` 父 commit 读取内容，生成 `restore A1` commit | A1 内容回到 v1 | 恢复时抢锁 |

关键点：
- 同一路径在 trash 中可存在多个已删除版本，按 `deleted_at`（即 commit time × 1000）排序展示。
- 恢复谁，就物理删除谁的 `fileId`；其他未恢复的已删除版本继续留在 trash。
- `deleted_at` 统一使用删除 commit 的秒级时间戳 × 1000，确保与 Git commit 时间一致。

---

## 与 Git 的交互

`delete()` 会自动生成一个 Git commit，因此不存在"删除未提交"的状态。

### 删除后

- Git HEAD 指向 `delete <path>` commit，文件已从 tree 移除。
- 历史 commit 仍可通过 `history`/`diff` 查看。
- publish 的 tag 指向最新 commit，不再包含该文件。

### 恢复后

- Git HEAD 指向 `restore <path>` commit，文件内容来自删除 commit 的父 commit。
- 后续 `git log -- <path>` 仍能看到删除前的所有历史 commit，以及删除、恢复两个 commit。

---

## 接口设计（建议）

### 新增接口

```java
// 回收站列表
@GetMapping("/trash")
Result<List<FileTreeNode>> trash(@RequestParam(defaultValue = "") String parentPath);

// 恢复文件
@PostMapping("/trash/restore")
Result<Void> restore(@Valid @RequestBody RestoreFileDTO dto);

// 彻底删除
@DeleteMapping("/trash/purge")
Result<Void> purge(@RequestParam @NotNull Long fileId);
```

### DTO

```java
public class RestoreFileDTO {
    @NotNull
    private Long fileId;
    // 可选，为空时从 HEAD 恢复
    private String commitHash;
}
```

### 现有接口影响

| 接口 | 变更 |
|---|---|
| `DELETE /api/files` | 去掉 `force` 参数；改为双 commit（ACBD + delete）软删除 + 容错 |
| `POST /api/files/commit` | 仅支持 `deleted_at = 0` 的文件，不再处理软删除文件 |
| `GET /api/files/tree` | 默认过滤已删除文件 |
| `GET /api/files/content` | 已删除文件返回 30702 |
| `GET /api/files/history` | 对已删除文件仍可查询历史 |

---

## 边界情况

1. **删除未保存的新建文件**：文件从未 commit 过，Git 历史中不存在。ACBD 跳过（磁盘无文件），git rm 忽略 `NoFilepatternException`，DB 进入回收站。restore 时因无历史版本无法恢复。
2. **磁盘文件已被外部删除（有 Git 历史）**：ACBD 跳过，forceDelete 忽略 `FileNotFoundException`，git rm 成功生成 delete commit，DB 进入回收站。restore 可从 Git 历史恢复。见容错矩阵 case 4。
3. **删除文件夹**：通过 `acquireFolderLock` 封门隔离后，递归软删除所有子节点，自动生成 `delete` commit。
4. **并发写入中删除**：`acquireFolderLock` 先封门，`acquire` 子文件锁时在 `synchronized(segment)` 上等待写入完成。写入完整落盘后才接管，保证不丢数据。
5. **重命名已删除文件**：不允许；重命名只针对正常文件。
6. **恢复时路径冲突**：目录已存在则复用；文件已存在则提示"覆盖/取消"，覆盖前自动提交当前文件内容。
7. **同一路径多次删除**：回收站中可存在多个已删除版本，按 `deleted_at` 排序展示。

---

## 测试要点

1. 软删除后 `fileId` 仍可用于 `history`/`diff`。
2. 软删除后 `tree()` 不返回该文件。
3. `delete` 自动生成 `delete` commit，publish tag 指向新 commit。
4. `restore` 能从指定 commit 恢复文件内容与索引，路径冲突时支持覆盖/取消。
5. 同一路径多次删除后，回收站展示多个版本，恢复其中一个版本时只删除该版本的 `fileId`。
6. `purge` 后 `fileId` 彻底失效，`history` 无法再通过该 `fileId` 查询（但 Git 历史本身仍存在）。
7. 删除文件夹时，子文件锁释放与软删除状态一致。

---

## 迁移计划

1. 新增 `deleted_at` 和 `latest_commit_hash` 字段，默认 `deleted_at = 0`；同步把 `path` 唯一约束改为 `(path, deleted_at)` 联合唯一。
2. 改造 `GitFileService.delete()`：删除磁盘文件/目录、标记 `deleted_at`、更新 `latest_commit_hash`、自动生成 `delete` commit。
3. 改造 `GitFileService.commit()`：不再处理 `deleted_at > 0` 的文件，正常文件 commit 后更新 `latest_commit_hash`。
4. 新增 `restore()`、`trash()`、`purge()` 服务与 Controller。
5. 调整 `tree()`、`content()` 默认过滤已删除文件。
6. 调整现有测试，改为"`delete` 自动生成 commit"的预期，并补充恢复路径冲突、同路径多版本等测试。
