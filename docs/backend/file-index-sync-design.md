# 文件索引同步设计方案

> 版本：v1  
> 更新时间：2026-07-24

## 1. 设计目标

- 检测磁盘与 DB 索引（`lanting_file_index`）之间的不一致，并在 DB 中标记或修复。
- 支持两种触发方式：手动触发（用户操作）和定时触发（后台任务）。
- 对「索引有、磁盘无」的记录，**标记**而非删除，保留给用户审视和决策。
- 对「索引无、磁盘有」的记录，按磁盘内容自动补全索引。

## 2. 核心概念

### 2.1 不一致类型

| 类型 | 含义 | 当前处理 | 目标处理 |
|---|---|---|---|
| `unindexedFiles` / `unindexedFolders` | 磁盘有、索引无 | `repair()` 中建立/更新索引 | **不变** |
| `staleFiles` / `staleFolders` | 索引有、磁盘无 | `repair()` 中物理删除 | **改为标记** `missing_on_disk = 1` |
| `mtimeMismatches` | mtime 不一致 | `repair()` 中重新读盘更新 | **不变** |
| `checksumMismatches` | CRC32 不一致 | `repair()` 中重新读盘更新 | **不变** |

### 2.2 标记字段：`missing_on_disk`

在 `lanting_file_index` 表新增列：

```sql
missing_on_disk INTEGER NOT NULL DEFAULT 0
```

- `0`：磁盘文件存在，索引正常。
- `1`：索引记录存在但对应磁盘文件缺失（文件可能被外部删除或意外丢失）。

该字段与 `deleted_at` 正交：
- `deleted_at = 0, missing_on_disk = 0` → 正常文件
- `deleted_at = 0, missing_on_disk = 1` → 索引有记录但磁盘文件丢失（异常）
- `deleted_at > 0` → 已软删除（回收站），此时 `missing_on_disk` 无意义

### 2.3 触发方式

| 方式 | 说明 |
|---|---|
| **手动触发** | 通过 API 接口调用 `reconcile`（只读检查）或 `repair`（检查 + 修复） |
| **定时触发** | 后台定时任务，通过配置开关控制，默认关闭 |

## 3. 数据流

### 3.1 reconcile 流程（只读检查）

```
用户请求 / 定时任务触发
        │
        ▼
  加载 DB 索引（deleted_at = 0 的全部或 scope 范围内记录）
        │
        ▼
  遍历磁盘（跳过 .git / .lanting / scope 外路径）
        │
        ├─ 磁盘路径不在索引中 → unindexed
        ├─ 索引路径不在磁盘中 → stale
        ├─ mtime 不一致 → mtimeMismatch
        └─ mtime 一致但 CRC32 不同 → checksumMismatch
        │
        ▼
  返回报告（不修改任何数据）
```

### 3.2 repair 流程（检查 + 修复，DISK_WINS 模式）

```
reconcile 报告
        │
        ├─ unindexedFiles   → indexOnSave() 建立索引
        ├─ unindexedFolders → indexOnCreate() 建立索引
        ├─ staleFiles       → UPDATE missing_on_disk = 1 （标记，不删除）
        ├─ staleFolders     → UPDATE missing_on_disk = 1 递归标记子节点
        ├─ mtimeMismatches  → indexOnSave() 重新读盘更新
        └─ checksumMismatches → indexOnSave() 重新读盘更新
        │
        ▼
  返回修复结果统计
```

### 3.3 `missing_on_disk` 标记的自动清除

以下场景自动清除标记：

| 场景 | 触发点 |
|---|---|
| 文件被 save | `indexOnSave()` 中检测 `missingOnDisk = 1`，写入成功后置 0 |
| 文件被 create | `indexOnCreate()` 中检测之前标记，创建成功后置 0 |
| 文件夹被恢复 | `restore()` 中的 `indexOnCreate()` |
| reconcile 发现磁盘文件重新出现 | `repair()` 中 unindexed 路径如果之前被标记 stale，清除标记而非新建 |

## 4. API 设计

### 4.1 一致性检查（只读）

```
POST /api/files/index/reconcile
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `scope` | String | 否 | 扫描范围路径前缀，为空则全局扫描 |

响应示例：

```json
{
  "code": 0,
  "data": {
    "total": 45,
    "unindexedFiles": ["sql/new_file.sql"],
    "unindexedFolders": [],
    "staleFiles": ["docs/old_readme.md"],
    "staleFolders": [],
    "mtimeMismatches": ["ddl/user_table.ddl"],
    "checksumMismatches": []
  }
}
```

### 4.2 索引修复

```
POST /api/files/index/repair
```

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `scope` | String | 否 | 修复范围路径前缀，为空则全局修复 |

响应示例：

```json
{
  "code": 0,
  "data": {
    "repairedStaleFiles": ["docs/old_readme.md"],
    "repairedStaleFolders": [],
    "repairedUnindexedFiles": ["sql/new_file.sql"],
    "repairedUnindexedFolders": [],
    "repairedMtimeMismatches": ["ddl/user_table.ddl"],
    "repairedChecksumMismatches": []
  }
}
```

### 4.3 索引状态查询

```
GET /api/files/index/status
```

返回当前索引的总数统计和异常计数：

```json
{
  "code": 0,
  "data": {
    "total": 45,
    "missingOnDisk": 3,
    "deleted": 12
  }
}
```

## 5. 定时任务

### 5.1 配置

```yaml
lanting:
  file:
    index:
      auto-repair:
        enabled: false              # 默认关闭
        cron: "0 0 */2 * * ?"       # 默认每 2 小时执行一次
```

### 5.2 实现

新增 `FileIndexSyncScheduler`：

```java
@Component
@ConditionalOnProperty(name = "lanting.file.index.auto-repair.enabled", havingValue = "true")
public class FileIndexSyncScheduler {

    @Scheduled(cron = "${lanting.file.index.auto-repair.cron:0 0 */2 * * ?}")
    public void autoRepair() {
        // 1. reconcile(root)
        // 2. repair(root, DISK_WINS)
        // 3. log 修复结果
    }
}
```

- 任务执行期间不阻塞用户请求（`reconcile` 和 `repair` 操作的是 DB，不涉及 Git 写锁）。
- 失败时仅记录日志，不抛异常影响调度线程。

## 6. 对现有代码的修改

### 6.1 `FileIndexEntity`

新增字段：

```java
/** 磁盘文件是否缺失，0-正常 1-缺失 */
private Integer missingOnDisk;
```

### 6.2 `FileIndexService`

| 方法 | 改动 |
|---|---|
| `repair()` | stale 条目改为 `markMissingOnDisk()` 而非 `deletePhysicallyByPath` |
| 新增 `markMissingOnDisk(String path)` | 递归设置 `missing_on_disk = 1` |
| 新增 `clearMissingOnDisk(String path)` | 清除标记 |
| `indexOnSave()` | 写入成功后清除 `missingOnDisk` |
| `indexOnCreate()` | 创建成功后清除 `missingOnDisk`（如果之前有标记） |
| `status()` | 增加 `missingOnDisk` 计数 |

### 6.3 `FileController`

新增三个接口：

```java
@PostMapping("/index/reconcile")
public Result<Map<String, Object>> reconcile(@RequestParam(required = false) String scope)

@PostMapping("/index/repair")
public Result<Map<String, Object>> repair(@RequestParam(required = false) String scope)

@GetMapping("/index/status")
public Result<Map<String, Object>> status()
```

### 6.4 `FileResultCode`

新增错误码：

```java
FILE_MISSING_ON_DISK(30716, "磁盘文件缺失", 404)
```

### 6.5 DDL 迁移

新增 `V2__add_missing_on_disk.sql`：

```sql
ALTER TABLE lanting_file_index ADD COLUMN missing_on_disk INTEGER NOT NULL DEFAULT 0;
```

### 6.6 新增 `FileIndexSyncScheduler.java`

定时任务组件，通过 `@ConditionalOnProperty` 控制开关。

## 7. 待确认事项

- [ ] 定时任务是否默认开启？建议默认关闭，由运维按需开启。
- [ ] 定时任务的默认 cron（每 2 小时）是否合理？
- [ ] `content()` 读取接口遇到 `missing_on_disk = 1` 时，是否需要返回特定错误码？当前文件不存在时会走到 `FileNotFoundException`，是否改用新错误码 `30716`？
- [ ] 前端是否需要展示 `missing_on_disk` 状态（如文件树中图标变灰）？如果是，`FileTreeNode` 需要增加对应字段。
- [ ] 事件驱动方案：后续是否需要文件变更后发布事件、异步触发增量 reconcile？
