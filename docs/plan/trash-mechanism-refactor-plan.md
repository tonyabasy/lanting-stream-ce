# Trash 机制重构计划

> 状态：待 review  
> 创建时间：2026-07-15  
> 基于 2026-07-15 的讨论整理，先敲定容易达成一致的部分，再进入复杂逻辑改造。

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

---

## 二、分阶段修改计划

### 阶段 1：数据模型与 FileIndexService 基础调整（低风险，可先做）

**改动点**：

1. ~~`FileIndexEntity` 增加字段：~~（待后续一起加，见下方未划掉项）
   ```java
   private String latestCommitHash;
   ```

2. ~~建表 SQL（`docs/sql` 或 `admin/src/main/resources` 中对应的 `lanting_file_index` 建表语句）增加字段：~~
   ```sql
   latest_commit_hash VARCHAR(40)
   ```
   不采用 `ALTER TABLE`，直接改 `CREATE TABLE`。

3. ~~`FileIndexService.indexOnCreate` / `indexOnSave` 不再调用 `findDeletedByPath`，同名路径再创建时直接 insert 新记录。~~（已确认）

4. ~~`FileIndexService` 中 `findDeletedByPath` 方法若已无用，可删除。~~（已删除）

5. `FileIndexEntity` 增加字段：
   ```java
   private String latestCommitHash;
   ```

6. 建表 SQL（`docs/sql` 或 `admin/src/main/resources` 中对应的 `lanting_file_index` 建表语句）增加字段：
   ```sql
   latest_commit_hash VARCHAR(40)
   ```
   不采用 `ALTER TABLE`，直接改 `CREATE TABLE`。

7. `FileIndexService.indexOnDelete(String path)`：
   - 方法签名改为 `indexOnDelete(String path, long deletedAt, String latestCommitHash)`；
   - 更新命中记录的 `deleted_at` 和 `latest_commit_hash`；
   - 只更新 `deleted_at = 0` 的记录，避免唯一约束冲突。

8. `FileIndexService.indexOnDeleteByIds(Set<Long> fileIds)`：
   - 方法签名改为 `indexOnDeleteByIds(Set<Long> fileIds, long deletedAt, String latestCommitHash)`；
   - 更新命中记录的 `deleted_at` 和 `latest_commit_hash`；
   - 只更新 `deleted_at = 0` 的记录。

9. `FileIndexService.listTrash(String parentPath)`：
   - 在查询条件基础上增加 `.orderByDesc(FileIndexEntity::getDeletedAt)`。

**验收标准**：
- 数据模型编译通过；
- 现有 `FileIndexServiceTest` 通过（或同步更新测试）。

---

### 阶段 2：GitFileService.delete 改造（高风险，已确定流程）

删除流程（统一处理 file/folder，按 path 前缀匹配）：

1. **path 前缀匹配**：根据目标 path 找出所有 `deleted_at = 0` 的节点。
   - 文件：匹配自身；
   - 目录：匹配自身及所有子节点。

2. **抢锁**：先获取**路径锁**锁住整个目录范围，再为匹配到的所有文件节点抢**文件锁**。
   - 路径锁是范围锁，操作完成后必须释放；
   - 文件锁长期持有，不主动释放；
   - 若目标正在变更中，等待完成后再获得锁；
   - 同一文件并发竞争时，只有一个请求能持锁，其他请求进入等待或后续成功。

3. **auto commit before delete**：检查待删除节点是否有未提交改动。
   - 若有，先提交一次，message 为 `auto commit before delete`；
   - 保证删除前最新内容进入 Git，方便用户恢复。

4. **FS 删除**：使用 `forceDelete` 删除磁盘文件/目录。

5. **DB 软删除**：按 path 前缀批量更新索引。
   - 设置 `deleted_at` 为本次 `delete` commit 秒级时间戳 × 1000；
   - 设置 `latest_commit_hash` 为本次 `delete` commit 的 hash。

6. **delete commit**：在工作空间锁内生成删除 commit。
   - 文件：`git rm <path>`；
   - 目录：`git rm -r <path>`；
   - message：`delete <path>`。

**边界情况**：
- 删除未 commit 过的新建文件：先通过 `auto commit before delete` 将其提交到 Git，再生成 `delete <path>` commit 删除，共两个 commit；索引进入回收站。
- 删除目录：子节点同步软删除，目录本身也更新 `deleted_at` / `latest_commit_hash`。
- 删除完成后**不主动释放锁**，由下一个操作者（restore/purge）抢锁时自动覆盖。原持锁人再次操作已删除文件时会因锁失效失败。

**接口变更**：
- `GitFileService.delete(Long fileId)` 去掉 `force` 参数；
- `FileController.delete` 去掉 `force` 参数；
- 删除失败直接抛异常，不再返回 `DeleteLockedVO`。

---

### 阶段 3：GitFileService.commit 调整（中风险，需讨论用户体验）

**核心问题**：`commit` 是否允许提交没有锁定的文件？

**候选方案**：

| 方案 | 说明 |
|---|---|
| A：保持现有锁语义 | 只提交当前用户已锁定的文件；他人锁定文件进入 skipped。 |
| B：允许提交任意改动 | 只要磁盘内容/索引状态与 HEAD 有差异，就可以 commit，无需锁。 |

**推荐方案 A（保持现有语义）**：
- 减少并发冲突；
- 与现有 `save`/`lock` 流程保持一致。

**改动点**：
1. `commit` 只处理 `deleted_at = 0` 的文件；
2. `deleted_at > 0` 的文件直接加入 `skipped`（因为 delete 已自动 commit）；
3. 正常文件 commit 成功后，批量更新这些索引的 `latest_commit_hash` 为本次 commit hash；
4. `GitFileService` 类注释更新：删除已自动 commit，commit 不再处理软删除文件。

**待确认**：选择方案 A 还是方案 B。

---

### 阶段 4：GitFileService.restore 改造（中高风险，需单独设计）

**核心方向**：

1. 单文件恢复：
   - 从 `latest_commit_hash` 对应的删除 commit 取父 commit，读取文件内容；
   - 检查目标路径是否存在；
   - 不存在：直接写回磁盘，重置原 `fileId` 的 `deleted_at = 0`，更新 `latest_commit_hash` 为新生成的 restore commit；
   - 存在：返回冲突信息，用户选择覆盖/取消/强制重建；
   - 覆盖：先提交当前新文件内容（`save <path> before restore`），再用旧内容覆盖，生成 `restore <path>` commit，物理删除旧 `fileId`。

2. 目录恢复：
   - 检查目录是否存在，不存在则重建（DB + FS），存在则复用；
   - 根据 `latest_commit_hash` + path 前缀获取该目录下所有文件/目录列表；
   - 检查每个待恢复子文件/子目录是否与当前路径冲突；
   - 有冲突：统一告知用户，提供"强制重建"选项；
   - 无冲突：直接重建目录树和文件内容；
   - 生成 `restore <path>` commit；
   - 只恢复该目录本身，子文件/子目录按需恢复，不递归恢复所有曾存在内容（避免意外大量恢复）。

**待确认**：
- 目录恢复时，是否默认恢复该目录下所有直接子文件/子目录？还是只恢复目录本身，子文件让用户单独恢复？
- 强制重建时，如果当前目录下已有同名新文件，是直接覆盖还是先提交再覆盖？

---

### 阶段 5：回收站接口完善（低风险）

**改动点**：

1. `GET /api/files/trash`：
   - 已存在，按 `deleted_at` 倒序展示；
   - 同一路径多版本时并列显示。

2. `POST /api/files/trash/restore`：
   - 已存在，需支持返回"路径冲突"状态；
   - 增加 `force` 参数或增加独立 `forceRestore` 接口，用于用户确认强制重建。

3. `DELETE /api/files/trash/purge`：
   - 已存在，保持现状：物理删除该 `fileId` 索引，释放锁。

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

## 三、待讨论 / 待确认事项

1. **阶段 2 中 `delete` 的详细设计**：尤其是目录删除的锁处理、commit 生成时机、与现有 `FileController.delete` 返回值的配合。
2. **阶段 3 中 `commit` 的锁语义**：是否允许 commit 未锁定的文件？
3. **阶段 4 中目录恢复范围**：是恢复目录本身，还是递归恢复目录下所有内容？
4. **阶段 4 中强制重建的具体行为**：覆盖前是否必须先提交当前新文件内容？

---

## 四、建议执行顺序

1. 先 review 并敲定阶段 1（数据模型 + FileIndexService 基础）；
2. 同时讨论阶段 2 和阶段 3（delete 与 commit 设计）；
3. 阶段 2/3 敲定后进入阶段 4（restore）；
4. 最后阶段 5/6（接口完善与测试）。
