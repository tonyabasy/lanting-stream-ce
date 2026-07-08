# 文件系统元数据索引（LantingFS）

## 背景

`GitFileService.tree()` 原实现每次请求全量递归遍历磁盘，在机械硬盘（HDD）环境下性能严重不足：

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

**磁盘是 source of truth，DB 索引是加速用的派生数据。**

所有写操作通过 API 发生，API 写磁盘后同步更新 DB 索引，保证正常路径下 DB 与磁盘一致。
不一致情况（如进程崩溃、极端异常）通过手动触发 reconcile/repair 修复。

| 磁盘状态 | DB 状态 | 含义 | reconcile 报告 |
|---|---|---|---|
| 存在 | 存在，mtime/CRC32 一致 | 正常 | — |
| 存在 | 存在，mtime 不一致 | 文件已更新但索引未刷新 | `mtimeMismatches` |
| 存在 | 存在，mtime 一致但 CRC32 不一致 | 内容被改但 mtime 被还原 | `checksumMismatches` |
| 存在 | 不存在 | 未索引文件（孤儿文件） | `unindexedFiles/Folders` |
| 不存在 | 存在 | 索引记录过时（磁盘文件已删） | `staleFiles/Folders` |

---

## 索引表结构

```sql
CREATE TABLE IF NOT EXISTS lanting_file_index (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    path        VARCHAR(1000) NOT NULL UNIQUE,
    name        VARCHAR(200)  NOT NULL,
    type        VARCHAR(10)   NOT NULL,             -- file / folder
    parent_path VARCHAR(1000) NOT NULL DEFAULT '',  -- 根目录子节点为空字符串
    mtime       BIGINT        NOT NULL DEFAULT 0,   -- 磁盘文件最后修改时间（毫秒）
    crc32       BIGINT        NOT NULL DEFAULT 0,   -- 文件内容 CRC32 校验和；folder 固定为 0
    create_time BIGINT        NOT NULL DEFAULT 0,
    update_time BIGINT        NOT NULL DEFAULT 0
);

-- tree() 按 parent_path 分组查询，此索引是核心
CREATE INDEX IF NOT EXISTS idx_file_index_parent ON lanting_file_index(parent_path);
```

### CRC32 字段说明

- 使用 Java 标准库 `java.util.zip.CRC32` 计算，无需引入额外依赖
- **写路径优化**：`save()` 时文件内容已在内存中（`dto.getContent().getBytes()`），直接在内存计算 CRC32，不再读磁盘，零额外 IO
- `indexOnSave(path, root, bytes)` 带 bytes 参数的重载用于写路径（内存计算）；`indexOnSave(path, root)` 无参重载用于 repair 路径（读磁盘计算）
- folder 节点 CRC32 固定为 0

---

## 写操作协议

**先写磁盘，成功后更新 DB。** 磁盘是权威，DB 跟着走。不使用多段式状态机。

### 创建文件 / 创建文件夹

```
1. 磁盘操作（Files.write / createDirectories）
2. 成功 → DB INSERT（含 mtime 和 crc32）
3. 失败 → 抛异常，DB 不更新，无不一致
```

极端情况：磁盘成功、DB INSERT 失败 → 产生未索引文件，reconcile 时记录为 `unindexedFiles`。

### 删除文件 / 删除文件夹

```
1. DB DELETE（递归删除子节点）
2. 磁盘删除
3. 磁盘失败 → 抛异常，产生磁盘残留文件，reconcile 时记录为 unindexed
```

先删 DB：DB 删成功后即使磁盘删失败，用户视角文件已不存在（`tree()` 查 DB），残留文件由 reconcile 清理。

### 更新文件内容（save）

```
1. 磁盘写入（Files.write TRUNCATE_EXISTING）
2. 成功 → DB UPSERT：存在则 UPDATE mtime+crc32，不存在则 INSERT
3. 失败 → 抛异常，DB 不更新
```

### 回滚操作（revert）

```
1. 从 Git 读取历史内容，写入磁盘
2. 成功 → DB UPSERT mtime+crc32（revert 不 commit，只写磁盘）
3. 失败 → 抛异常
```

---

## 一致性校验（reconcile）

reconcile 由用户通过管理接口手动触发，**只扫描、不自动修复**，生成不一致报告供用户决策。

### 扫描逻辑

```
Step 1：遍历磁盘（跳过 .git / .lanting 目录）
  - 磁盘有、DB 无 → unindexedFiles / unindexedFolders
  - 磁盘有、DB 有、mtime 不一致 → mtimeMismatches
  - 磁盘有、DB 有、mtime 一致但 CRC32 不一致 → checksumMismatches（检测 mtime 被还原但内容已改的情况）

Step 2：遍历 DB
  - DB 有、磁盘无 → staleFiles / staleFolders

Step 3：返回报告
  { total, unindexedFiles, unindexedFolders, staleFiles, staleFolders,
    mtimeMismatches, checksumMismatches }
```

### scope 参数

reconcile 和 repair 均支持可选的 `scope` 参数，只对该路径前缀下的文件和 DB 记录做对比。
不传则扫描全局。主要用于测试隔离和局部修复场景。

### 幂等性

reconcile 可多次安全执行，每次执行结果反映当前状态。

---

## 修复（repair）

repair 基于 reconcile 的结果，以磁盘为准执行修复，当前仅支持 `disk_wins` 模式：

| 不一致类型 | 修复动作 |
|---|---|
| `unindexedFiles/Folders` | INSERT 进 DB（补录索引） |
| `staleFiles/Folders` | DELETE DB 记录（删除过时索引） |
| `mtimeMismatches` | UPDATE DB mtime+crc32 |
| `checksumMismatches` | UPDATE DB crc32 |

repair 同样支持 `scope` 参数，只修复指定路径下的不一致。

---

## FileIndexService 接口

```java
// 查询
List<FileIndexEntity> listByParentPath(String parentPath);
FileIndexEntity getByPath(String path);

// 写操作（磁盘操作成功后调用；删除操作先删 DB 再删磁盘）
void indexOnCreate(String path, String type, Path root);
void indexOnDelete(String path);                              // 递归删除子路径
void indexOnSave(String path, Path root);                     // 读磁盘计算 CRC32（repair/scan 用）
void indexOnSave(String path, Path root, byte[] bytes);       // 内存计算 CRC32（写路径用，避免重复读盘）

// 运维工具（手动触发）
Map<String, Object> reconcile(Path root);                     // 全局扫描
Map<String, Object> reconcile(Path root, String scope);       // 范围扫描
Map<String, Object> repair(Path root, RepairMode mode);       // 全局修复
Map<String, Object> repair(Path root, RepairMode mode, String scope); // 范围修复
void reloadIndex(Path root);                                  // 全量重建索引（首次安装/索引丢失时用）

// CRC32 工具方法（static）
static long crc32(byte[] bytes);
static long crc32(Path path);
```

---

## 管理接口

```
POST /api/admin/fs/reconcile?scope={path}
     手动触发一致性校验（只扫描，不修复）
     scope 可选，不传则全局扫描
     权限：file:admin
     响应：{ total, unindexedFiles, unindexedFolders, staleFiles, staleFolders,
             mtimeMismatches, checksumMismatches }

POST /api/admin/fs/repair?scope={path}
     手动触发修复（以磁盘为准，disk_wins 模式）
     scope 可选，不传则全局修复
     权限：file:admin
     响应：{ repairedunindexedFiles, repairedunindexedFolders, repairedstaleFiles,
             repairedstaleFolders, repairedChecksumMismatches, repairedMtimeMismatches }

GET  /api/admin/fs/status
     查询当前索引状态
     响应：{ total }
```

---

## 启动时行为

应用启动时（`WorkspaceService.@PostConstruct`）**不做任何扫描或 reconcile**。

索引由正常写操作（save、createFolder、delete、revert 等）自然维护，不需要启动时重建。
如需修复索引（如 DB 损坏恢复、首次迁移），通过管理接口手动触发：

- `POST /api/admin/fs/repair`：以磁盘为准修复，适合大多数场景
- `reloadIndex(root)`（内部方法）：清空 DB 索引后全量重建，适合 DB 完全丢失的极端情况

---

## GitFileService 改动点

核心逻辑（Git 操作、锁机制、路径校验、并发控制）**完全不变**，只在写操作的合适位置注入 `FileIndexService` 调用。

| 方法 | 改动位置 | 调用 |
|---|---|---|
| `tree(parentPath, sort)` | 整体替换 | `fileIndexService.listByParentPath(parentPath)` + 内存组装 |
| `save()` | 磁盘写入成功后，`doIfHolder` 内 | `indexOnSave(path, root, bytes)`（内存计算 CRC32）|
| `createFolder()` | 磁盘创建成功后 | `indexOnCreate` |
| `deleteInternal()` | 磁盘删除之前（先删 DB） | `indexOnDelete` |
| `revert()` | 磁盘写入成功后，`doIfHolder` 内 | `indexOnSave(path, root, bytes)` |

---

## 待讨论 / 后续事项

- [ ] reconcile/repair 同步执行超时控制（文件数 > 10000 时是否设置最大执行时间）
- [ ] 管理接口返回的修复报告 key 命名规范化（当前 `repaireddunindexedFiles` 拼写不统一）
- [ ] 当数据库切换为 MySQL（物理隔离）时，source of truth 语义天然增强，reconcile 策略无需调整
