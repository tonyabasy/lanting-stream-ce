# 文件删除与回滚设计规范

## 背景

当前 `GitFileService.delete()` 会直接删除磁盘文件并从 `lanting_file_index` 索引表中移除记录。这带来两个问题：

1. **删除不可回滚**：索引记录被物理删除后，`fileId` 立即失效，用户无法再通过 `/api/files/history`、`/api/files/diff`、`/api/files/revert` 等接口查看或恢复文件。
2. **删除无法被用户提交**：commit API 内部需要先通过 `fileId` 解析当前路径，索引删除后该接口无法识别文件，导致"删除进入 Git 历史"只能由系统自动完成，与用户希望"自己控制何时提交"的诉求冲突。

本规范引入**软删除（Soft Delete）**机制，让删除操作本身只标记状态并清理工作区，真正的 Git 提交由用户主动触发。

---

## 设计目标

| 目标 | 说明 |
|---|---|
| 删除可回滚 | 删除后 `fileId` 仍有效，用户可从 Git 历史恢复文件 |
| 用户控制提交 | `delete` 不自动产生 Git commit，删除的提交由用户手动触发 |
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
| 软删除（默认） | 用户调用 `delete` | 保留，`deleted_at > 0` | 删除 | 保持不变，直到用户主动 commit |
| 硬删除 / 彻底删除 | 用户在回收站执行 | 物理删除 | 已删 | Git 历史保留（Git 不可逆） |

### 两个关键状态

- **工作区已删除**：磁盘文件不存在，`deleted_at > 0`。
- **Git 已删除**：某次 commit 把该文件从 tree 中移除。

只有用户主动 commit 后，工作区删除才会进入 Git 历史。在此之前的删除只是"暂态"，可通过 restore 恢复。

---

## 数据模型变更

在 `lanting_file_index` 表中增加软删除时间戳，并把 `path` 的唯一约束改为与删除时间戳联合唯一：

```sql
ALTER TABLE lanting_file_index
    ADD COLUMN deleted_at BIGINT DEFAULT 0;

-- 软删除后同名路径需要能再次创建，因此 path 唯一约束必须包含 deleted_at
DROP INDEX IF EXISTS idx_file_index_path_unique;
CREATE UNIQUE INDEX idx_file_index_path_deleted ON lanting_file_index(path, deleted_at);
```

对应 `FileIndexEntity`：

```java
private Long deletedAt;
```

- `deleted_at = 0`：正常文件
- `deleted_at > 0`：已软删除

`deleted_at` 同时作为逻辑删除标志和业务删除时间戳。`lanting_file_index` 不继承 `BasicEntity`，因此不需要项目通用的 `is_delete` 字段。

### 索引调整

所有按 `parent_path` 查询的 tree/list 接口统一增加 `deleted_at = 0` 过滤。可在现有索引上依赖查询条件，不强制新增索引；若回收站查询性能不足，再补充 `(deleted_at)` 索引。

---

## 业务规则

### 1. `delete(fileId, force)`

- 校验文件存在且未被他人锁定（文件夹删除的锁检查逻辑保持不变）。
- 将目标文件/文件夹及其所有子节点的 `deleted_at` 置为当前时间戳。
- 删除磁盘文件/目录。
- 释放相关文件锁。
- **不产生 Git commit**。

### 2. `tree(parentPath)` / `content(fileId)`

- 默认只返回 `deleted_at = 0` 的节点。
- `content(fileId)` 对已删除文件返回 `FILE_NOT_FOUND`（30702），因为磁盘文件已不存在；如需查看历史内容，使用 `history` 或回收站接口。

### 3. `commit(fileIds, message)`

commit 需要支持两类文件：

| 文件状态 | Git 操作 |
|---|---|
| `deleted_at = 0` | `git add <path>`（现有行为） |
| `deleted_at > 0` | `git rm <path>` |

commit 成功后，已删除文件可以选择：

- **保留索引但标记为已提交删除**：便于后续 restore 仍能找到入口。
- **从 DB 删除索引**：删除操作已进 Git，索引可以清理。推荐先保留，restore 时再恢复；彻底删除时清理。

为简化实现，本规范建议 commit 成功后**保留软删除索引**，直至用户在回收站执行"彻底删除"。

### 4. `restore(fileId, commitHash)` 与 `revert(fileId, commitHash)` 的区别

| | `revert` | `restore` |
|---|---|---|
| 文件状态 | 文件仍存在（`deleted_at = 0`） | 文件已软删除（`deleted_at > 0`） |
| 用户意图 | 将当前文件内容**回滚到某个历史版本** | 将已删除文件从 Git 历史中**复活** |
| DB 操作 | 更新 `mtime`/`crc32` | 重置 `deleted_at = 0`，恢复 `mtime`/`crc32` |
| 磁盘操作 | 覆盖已有文件内容 | 从无到有写回文件内容 |

两者在"读取指定 commit 内容写回磁盘"这一段可以复用同一个私有方法，但 Controller/Service 入口保持独立，避免语义混淆。

### 5. 回收站

- 新增 `trash(parentPath)` 接口，返回 `deleted_at > 0` 的文件/文件夹树。
- 支持 `restore(fileId)` 和 `purge(fileId)`（彻底删除）。
- 彻底删除时从 DB 物理删除索引，磁盘已删无需处理。

---

## 状态流转

```
正常文件
   │
   │ delete()
   ▼
软删除（磁盘无，DB 有 deleted_at > 0）
   │
   ├─ restore() ───────────► 正常文件
   │
   ├─ commit() ────────────► Git 已删除（磁盘无，DB 仍保留索引）
   │                            │
   │                            │ purge()
   │                            ▼
   │                         彻底删除（DB 索引移除）
   │
   └─ purge() ─────────────► 彻底删除（未进 Git，不可逆）
```

---

## 与 Git 的交互

### 删除未提交时

- Git HEAD 仍包含文件。
- 工作区文件已被删除，`git status` 显示 `deleted: <path>`。
- publish 的 tag 仍指向包含该文件的 commit（符合"未提交变更不纳入发布"的现有设计）。

### 删除已提交时

- 新 commit 中文件从 tree 移除。
- 历史 commit 仍可通过 `history`/`diff` 查看。
- publish 的 tag 指向新 commit，不再包含该文件。

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
| `DELETE /api/files` | 行为改为软删除，不再自动 commit |
| `POST /api/files/commit` | 支持对软删除文件执行 `git rm` |
| `GET /api/files/tree` | 默认过滤已删除文件 |
| `GET /api/files/content` | 已删除文件返回 30702 |
| `GET /api/files/history` | 对已删除文件仍可查询历史 |

---

## 边界情况

1. **删除未保存的新建文件**：文件从未 commit 过，Git 历史中不存在。软删除后 restore 需要从 HEAD 读取，HEAD 中不存在则 restore 失败，提示"无历史版本可恢复"。
2. **删除文件夹**：递归软删除所有子节点，commit 时收集所有子文件路径统一 `git rm`。
3. **重命名已删除文件**：不允许；重命名只针对正常文件。
4. **恢复时路径冲突**：若原路径已被其他文件占用，restore 失败，提示路径冲突。
5. **并发恢复/删除**：通过 `FileLockService` 的 `doIfHolder` 或 `forceRelease` 保证原子性。

---

## 测试要点

1. 软删除后 `fileId` 仍可用于 `history`/`diff`。
2. 软删除后 `tree()` 不返回该文件。
3. `commit` 对软删除文件执行 `git rm`，新 publish tag 不包含该文件。
4. `restore` 能从 HEAD 恢复文件内容与索引。
5. `purge` 后 `fileId` 彻底失效，`history` 无法再通过该 `fileId` 查询（但 Git 历史本身仍存在）。
6. 删除文件夹时，子文件锁释放与软删除状态一致。

---

## 迁移计划

1. 新增 `deleted_at` 字段，默认 `0`，不影响现有数据；同步把 `path` 唯一约束改为 `(path, deleted_at)` 联合唯一。
2. 逐步改造 `GitFileService.delete()`、`commit()`、`tree()`、`content()`。
3. 新增 `restore()`、`trash()`、`purge()` 服务与 Controller。
4. 调整现有测试，删除"delete 自动进入 Git"的预期，改为"软删除 + 手动 commit"。
