# 文件系统元数据索引

## 背景

`GitFileService.tree()` 当前实现是每次请求全量递归遍历磁盘，在机械硬盘（HDD）环境下性能严重不足：

| 文件数 | HDD 冷读估算 |
|---|---|
| 50 | 550ms |
| 100 | 1050ms |
| 500 | 5 秒 |
| 1000 | 10 秒 |
| 10000 | > 80 秒 |

LantingFS 通过引入 `lanting_file_index` 索引表，将 `tree()` 从磁盘遍历变为 DB 查询（< 1ms），同时建立完整的一致性保障机制，确保在 10000 文件规模下仍有流畅的用户体验。

---

## 核心设计原则

**DB 是 source of truth，磁盘是 DataNode，没有例外。**

类比 HDFS：Namenode（DB）持有元数据，DataNode（磁盘）存储实际文件内容。Namenode 没有的文件，DataNode 上也不应存在。

| DB 状态 | 磁盘状态 | 处理方式 |
|---|---|---|
| 存在 | 存在 | 正常 |
| 存在 | 不存在 | 磁盘文件丢失，reconcile 时记录 missing，用户可手动从 Git 历史恢复 |
| 不存在 | 存在 | 孤儿文件，reconcile 时记录 orphan，由用户决定是否删除 |

用户通过管理接口触发 reconcile。reconcile **只扫描、不自动删除**，生成不一致报告。
DB 为空且磁盘有文件时（典型场景：首次安装、DB 损坏、迁移失败），**不自动删除磁盘文件**，
而是把全部磁盘文件标记为 orphan，由管理接口展示给用户，由用户手动处理。

---

## 索引表结构

```sql
CREATE TABLE IF NOT EXISTS lanting_file_index (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    path        VARCHAR(1000) NOT NULL UNIQUE,
    name        VARCHAR(200)  NOT NULL,
    type        VARCHAR(10)   NOT NULL,             -- file / folder
    parent_path VARCHAR(1000) NOT NULL DEFAULT '',  -- 根目录子节点为空字符串
    mtime       INTEGER       NOT NULL DEFAULT 0,   -- 磁盘文件最后修改时间（毫秒）
    create_time INTEGER       NOT NULL DEFAULT 0,
    update_time INTEGER       NOT NULL DEFAULT 0
);

-- tree() 按 parent_path 分组查询，此索引是核心
CREATE INDEX IF NOT EXISTS idx_file_index_parent ON lanting_file_index(parent_path);
```

---

## 写操作协议

不使用多段式状态机，写操作按"操作磁盘 → 成功后更新 DB"或"先更新 DB → 再操作磁盘"
两种方式组织，不一致情况全部留给 reconcile 在用户手动触发时处理，不在写操作热路径上做实时补偿。

### 创建文件 / 创建文件夹

```
1. 磁盘操作（Files.write / createDirectories）
2. 成功 → DB INSERT
3. 失败 → 抛出异常（磁盘未写成功，DB 不更新，无不一致）
```

极端情况：磁盘成功、DB INSERT 失败 → 产生孤儿文件，reconcile 时记录。

### 删除文件 / 删除文件夹

```
1. DB DELETE（递归删除子节点）
2. 磁盘删除
3. 磁盘失败 → 抛出异常，产生磁盘残留文件，reconcile 时记录
```

先删 DB 保证 DB 是 source of truth：DB 删成功后即使磁盘删失败，
用户视角文件已不存在（tree() 查 DB），残留文件由 reconcile 记录。

### 更新文件内容（save）

```
1. 磁盘写入（Files.write TRUNCATE_EXISTING）
2. 成功 → DB UPDATE mtime
3. 失败 → 抛出异常（磁盘未写成功，DB 不更新，无不一致）
```

### 回滚操作（revert / rollbackRelease）

```
1. 从 Git 读取历史内容，写入磁盘
2. gitCommit
3. 成功 → DB UPSERT（存在则 UPDATE mtime，不存在则 INSERT；批量）
4. 失败 → 抛出异常
```

> 注意：rollbackRelease 可能恢复当前 DB 中不存在的文件（如被删除后回滚），因此 `indexOnSave`
> 必须是 UPSERT，不能只做 UPDATE。

---

## 一致性校验（reconcile）

reconcile 不由应用启动时自动执行，而是通过管理接口由用户手动触发。
触发后同步执行并返回完整不一致报告。

### 执行顺序

reconcile **只扫描、不自动修复/删除**，输出不一致报告，由用户通过管理接口决定后续动作。

```
Step 1：处理孤儿文件（磁盘有，DB 无）
  遍历磁盘，找出 DB 中不存在的文件 → 记录 orphan
  跳过：.git / .lanting 目录

Step 2：处理缺失文件（DB 有，磁盘无）
  type=file → 记录 missing（用户可手动从 Git 历史恢复）
  type=folder → 记录 missingFolder

Step 3：处理 mtime 不一致（DB 有，磁盘有，但 mtime 不同）
  记录 mtimeMismatch

Step 4：校验完成，记录日志并输出报告
  { total, orphanFiles, missingFiles, missingFolders, mtimeMismatches }
```

### reconcile 的幂等性

reconcile 可以多次安全执行，每次执行后系统状态趋向一致。
运维遇到文件树异常时可通过管理接口手动触发。

---

## tree() 查询改造

改造前（全量磁盘遍历，HDD 10000 文件 > 80 秒）：

```java
// 递归遍历磁盘，每个节点一次磁盘 IO
public List<FileTreeNode> tree(String sort) {
    Path root = workspaceService.getDefaultWorkspaceRoot();
    // ...
}
```

改造后（按层级 DB 查询，< 1ms）：

```java
public List<FileTreeNode> tree(String parentPath, String sort) {
    // 查询指定父路径下的直接子节点
    List<FileIndexEntity> children = fileIndexService.listByParentPath(parentPath);

    // 直接返回子节点
    return children.stream()
        .map(e -> toTreeNode(e))
        .peek(node -> fillLockStatus(node))   // 实时填充锁状态
        .sorted(buildComparator(sort))
        .collect(Collectors.toList());
}
```

- 前端按需懒加载：首次请求根层级（`parentPath=""`），展开文件夹时再请求子层级
- `lockedBy` / `lockedAt` 在组装节点时实时从 `FileLockService` 填充（内存操作，无磁盘 IO）
- `mtime` 排序直接使用索引表中的 `mtime` 字段，无需读磁盘

---

## FileIndexService 接口

```java
// 查询
List<FileIndexEntity> listByParentPath(String parentPath);
FileIndexEntity getByPath(String path);

// 写操作（磁盘操作成功后调用；删除操作先删 DB 再删磁盘）
void indexOnCreate(String path, String type, Path root);  // INSERT
void indexOnDelete(String path);                          // DELETE（递归删除子路径）
void indexOnSave(String path, Path root);                 // UPSERT：存在 UPDATE mtime，不存在 INSERT
```

简洁：只有三个写方法，每个对应一类磁盘操作，磁盘成功后调用，失败时不调用。

---

## 管理接口

```
POST /api/admin/fs/reconcile
     手动触发一致性校验（同步执行）
     响应：{ "total": 1024, "orphanFiles": 0, "missingFiles": 0,
             "missingFolders": 0, "mtimeMismatches": 0 }

GET  /api/admin/fs/status
     查询当前索引状态
     响应：{ "total": 1024, "orphanFiles": 0, "missingFiles": 0 }
```

---

## GitFileService 改动点

核心逻辑（Git 操作、锁机制、路径校验、并发控制）**完全不变**，
只在原有锁保护的临界区内注入 `FileIndexService` 的调用：

- `save/delete/revert` 的索引操作在 `FileLockService.doIfHolder` 的 stripe 临界区内执行
- `createFolder/rollbackRelease/deleteInternal` 的索引操作在 `withWorkspaceLock` 工作空间锁内执行
- 保证"DB 操作 + 磁盘操作"对外原子可见，避免并发读取到中间状态

| 方法 | 改动位置 | 调用 |
|---|---|---|
| `tree(String parentPath, String sort)` | 整体替换 | `fileIndexService.listByParentPath(parentPath)` + 内存组装 |
| `save()` | 磁盘写入成功后，在 `doIfHolder` 内 | `indexOnSave` |
| `createFolder()` | 磁盘创建成功后，在 `withWorkspaceLock` 内 | `indexOnCreate` |
| `deleteInternal()` | 磁盘删除之前，在 `withWorkspaceLock` 内 | `indexOnDelete` |
| `revert()` | 磁盘写入成功后，在 `doIfHolder` 内 | `indexOnSave` |
| `rollbackRelease()` | 磁盘写入成功后，在 `withWorkspaceLock` 内 | 批量 `indexOnSave` |

---

## 待讨论 / 后续事项

- [ ] reconcile 同步执行超时控制（文件数 > 10000 时是否设置最大执行时间，避免长期占用请求线程）

> 注：管理接口的一键删除/恢复 orphan 或 missing 文件、MySQL 迁移、前端 tree() 懒加载交互等不在本次版本范围内，后续版本按需补充。
