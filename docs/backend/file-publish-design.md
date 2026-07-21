# 文件级发布设计

## 背景

当前 `publish()` 的实现是对工作空间 Git 仓库的 HEAD 打 tag（`git tag release-... HEAD`），
将发布建模为「工作空间级快照」。但蓝蜓的文件系统是一个**多文件编辑平台**，
`sql/` 下的查询脚本和 `ddl/` 下的表定义具有独立的开发节奏和发布周期，
工作空间级发布存在以下问题：

1. **粒度错配**：一次 `publish()` 会把 HEAD 上**所有文件**的当前状态打包，
   即使只改了 2 个文件、只提交了 2 个文件，另外 98 个未修改的文件也被迫"发布"。
   更严重的是——如果某个文件有未完成的草稿 commit，它也会被意外发布。

2. **Git tag 无增量价值**：`commit()` 已经返回了不可变的 `commitHash`，
   通过 `git show <hash>:<path>` 随时能取出该文件在该版本的内容。
   再打一个 tag 只是给 commit 起了个别名，没有增加任何新的语义保证。

3. **Review 粒度模糊**：`lanting_file_review` 关联的是 `tag_name`，
   一个 tag 下包含了仓库所有文件的快照，reviewer 实际上不知道自己在 review 哪些文件。

因此，将发布从**工作空间级（Git tag）**改为**文件级（DB 记录）**。

---

## 核心决策

| 决策项 | 旧设计 | 新设计 |
|---|---|---|
| 发布粒度 | 工作空间（全仓库） | **单文件** |
| 实现方式 | `git tag` 打在 HEAD 上 | DB 记录 `(file_id, commit_hash)` |
| Git 影响 | 产生 tag，污染 Git 引用空间 | 不操作 Git |
| 部署依据 | tag → commit → 全仓库内容 | `file_id + commit_hash` → 单文件内容 |
| Review 粒度 | 一次发布（N 个文件混在一起） | 一个文件的某个版本 |
| 发布历史可见性 | Git tag + DB | 纯 DB（仓库 clone 后不可见） |

---

## 运作方式

### 模型

```
lanting_publish（一次发布操作）           lanting_file_index
┌──────────────────────────┐           ┌──────────────────────┐
│ id           INTEGER PK  │           │ id        INTEGER    │
│ display_name VARCHAR     │           │ path      VARCHAR    │
│ published_by VARCHAR     │           │ latest_commit_hash   │──┐
│ is_delete    INTEGER     │           │ ...                  │  │
│ create_time  BIGINT      │           └──────────────────────┘  │
└────────┬─────────────────┘                                      │
         │ 1:N                                                    │
         ▼                                                        │
lanting_file_publish（每个文件的版本快照）                          │
┌──────────────────────────┐                                      │
│ id           INTEGER PK  │                                      │
│ publish_id   INTEGER ────┼──→ lanting_publish.id                │
│ file_id      INTEGER ────┼──→ lanting_file_index.id             │
│ commit_hash  VARCHAR(40) │◄── lanting_file_index.latest_commit  │
└──────────────────────────┘                                      │
         │
         │ 1:N
         ▼
lanting_file_review
┌──────────────────────────┐
│ id           INTEGER PK  │
│ publish_id   INTEGER ────┼──→ lanting_publish.id
│ reviewer     VARCHAR     │
│ comment      VARCHAR     │
│ ...                      │
└──────────────────────────┘
```

核心关系：
- **一次发布操作**（`lanting_publish`）包含多个文件的版本快照（`lanting_file_publish`）
- 同一批发布的文件共享一个 `publish_id`，通过 `display_name` 和 `published_by` 标识这次发布的含义
- Review 关联到 `publish_id`，review 的是「这次发布操作整体」，而不是单个文件版本

### 流程

```
用户选中文件 → 点击「发布」
        │
        ▼
┌───────────────────────────────────────────────┐
│ publish(fileIds, displayName)                 │
│                                               │
│  1. 创建发布批次（status=PUBLISHING）          │
│     INSERT lanting_publish(display_name,       │
│            published_by, status) → publishId    │
│                                                │
│  2. for each fileId:                           │
│     a. entity = index.getById(fileId)           │
│        → 取 latest_commit_hash                  │
│        → 如果为 null：文件从未提交，回滚批次     │
│     b. INSERT lanting_file_publish              │
│        (publish_id, file_id, commit_hash)       │
│                                                │
│  3. 全部文件写入成功                            │
│     UPDATE lanting_publish SET status=PUBLISHED │
│                                                │
│  4. 返回：{ publishId, status, files[], ... }   │
└───────────────────────────────────────────────┘
```

### 关键行为说明

**Q：发布的是哪个版本？**

发布的是 `lanting_file_index.latest_commit_hash`——即该文件最近一次 `commit()` 产生的版本。
这个字段由 `commit()` 方法在 `gitCommit` 成功后写入（见 `GitFileService.commit()` L508）。

**Q：磁盘上未提交的自动保存内容会怎样？**

不会纳入发布。`latest_commit_hash` 只在 `commit()` 成功后更新，自动保存（`save()`）只写磁盘和更新 mtime/crc32，不更新 `latest_commit_hash`。
这与旧设计「未提交的磁盘变更静默忽略」语义一致。

**Q：同一个文件能在同一批次中重复出现吗？**

不能。`(publish_id, file_id)` 联合唯一，一次发布操作中每个文件最多一条记录。

**Q：同一个版本（同一个 commit）能在不同批次中重复发布吗？**

能。例如 `sql/query.sql` 在 commit `abc123` 时发布过一次（`publish_id=7`），
后来即使文件没有新 commit，也可以再次发布到新批次（`publish_id=42`）。
这在「重新打发布包」的场景下是有意义的——文件内容没变，但作为新发布的一部分重新标记。

如果业务上需要「同一个 commit 不能重复发布」，限制应加在应用层而非数据库约束。

**Q：文件从未 commit 过能发布吗？**

不能。`latest_commit_hash` 为 null 表示文件自创建以来从未被提交到 Git，
此时调用 `publish()` 返回错误「文件从未提交，请先提交后再发布」。

**Q：发布后文件继续修改，已发布的版本还在吗？**

在。发布记录是不可变的——它指向的是 `commit_hash`，不是文件的当前状态。
后续的 `save()` 和 `commit()` 修改的是磁盘和新的 commit，不影响已发布的发布记录。
这是文件级发布的核心价值：**已发布的版本可以随时回溯和部署，不受后续修改影响。**

**Q：如果发布过程中服务崩溃了怎么办？**

批次以 `status = 'PUBLISHING'` 创建，子表写入中途崩溃时，该批次会永远留在 `PUBLISHING` 状态。
系统定时任务（或下次查询时）扫描 `status = 'PUBLISHING'` 且超过合理时间的批次，
将其标记为失败或直接删除，避免脏数据残留。对外部调用方来说，崩溃的批次不可部署、不可 review。

---

## 数据模型变更

### `lanting_publish`（新增：发布批次）

```sql
CREATE TABLE lanting_publish (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    display_name VARCHAR(200),               -- 可选显示名，如 "v1.2.0 线上版本"
    published_by VARCHAR(100) NOT NULL,      -- 发布人 username
    status       VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHING',
                                             -- PUBLISHING / PUBLISHED / REVOKED
    is_delete    INTEGER      NOT NULL DEFAULT 0,
    create_time  BIGINT       NOT NULL DEFAULT 0,
    update_time  BIGINT       NOT NULL DEFAULT 0
);
```

一次 `publish()` 调用创建一条 `lanting_publish` 记录，作为这批文件发布的「批次」。

### 发布状态机

```
                    publish()
                       │
                       ▼
                 ┌───────────┐
                 │ PUBLISHING │ ← 创建批次，逐文件写入快照
                 └─────┬─────┘
                       │ 全部文件写入成功
                       ▼
                 ┌───────────┐
                 │ PUBLISHED  │ ← 可部署、可 review
                 └─────┬─────┘
                       │ revoke()
                       ▼
                 ┌───────────┐
                 │ REVOKED    │ ← 被撤回，不可部署，记录保留
                 └───────────┘
```

| 状态 | 含义 | 可部署 | 可 Review | 触发操作 |
|---|---|---|---|---|
| `PUBLISHING` | 批次创建中，子表尚未全部写入 | 否 | 否 | `publish()` 入口 |
| `PUBLISHED` | 发布成功，所有文件快照已就绪 | 是 | 是 | 全部文件写入后自动转换 |
| `REVOKED` | 被主动撤回，记录保留供审计 | 否 | 否 | `revoke(publishId)` |

**`PUBLISHING` 状态的必要性：**

同步发布流程中，`PUBLISHING` 只是一个瞬态（先 INSERT 批次 → 逐文件 INSERT 子表 → UPDATE 为 `PUBLISHED`）。
但在以下场景它提供安全保障：
- 若进程在子表写入中途崩溃，重启后可通过 `status = 'PUBLISHING'` 找到未完成的批次做清理
- 若未来发布改为异步（如需要远端部署确认），`PUBLISHING` 就是「等待部署结果」的中间态

**`REVOKED` 与 `is_delete` 的区别：**

- `REVOKED`：发布批次被撤回，是**业务操作**——"这个版本不发布了"，但记录保留在发布列表中
- `is_delete`：用户把记录扔进了回收站，是**数据操作**——"这条记录我不想看到了"

### `lanting_file_publish`（重构：发布批次中的每个文件）

```sql
-- 旧表（废弃）
CREATE TABLE lanting_file_publish (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    tag_name     VARCHAR(100) NOT NULL,       -- 删除：不再使用 Git tag
    display_name VARCHAR(200),
    commit_hash  VARCHAR(100) NOT NULL,       -- 保留：文件版本引用
    created_by   VARCHAR(100) NOT NULL,
    is_delete    INTEGER NOT NULL DEFAULT 0,
    create_time  BIGINT  NOT NULL DEFAULT 0,
    update_time  BIGINT  NOT NULL DEFAULT 0,
    UNIQUE (tag_name, is_delete)              -- 删除
);

-- 新表
CREATE TABLE lanting_file_publish (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    publish_id   INTEGER      NOT NULL,      -- 新增：关联 lanting_publish.id
    file_id      INTEGER      NOT NULL,      -- 新增：关联 lanting_file_index.id
    commit_hash  VARCHAR(40)  NOT NULL,      -- 该文件的 commit 版本
    create_time  BIGINT       NOT NULL DEFAULT 0,
    UNIQUE (publish_id, file_id)             -- 同一批次同一文件不重复
);

CREATE INDEX IF NOT EXISTS idx_file_publish_publish_id
    ON lanting_file_publish(publish_id);
CREATE INDEX IF NOT EXISTS idx_file_publish_file_id
    ON lanting_file_publish(file_id);
```

### `lanting_file_review`

```sql
-- 旧表
CREATE TABLE lanting_file_review (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    tag_name    VARCHAR(100) NOT NULL,       -- 删除：改为 publish_id
    reviewer    VARCHAR(100) NOT NULL,
    comment     VARCHAR(500),
    ...
);

-- 新表
CREATE TABLE lanting_file_review (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    publish_id  INTEGER      NOT NULL,      -- 改：关联 lanting_publish.id
    reviewer    VARCHAR(100) NOT NULL,
    comment     VARCHAR(500),
    is_delete   INTEGER      NOT NULL DEFAULT 0,
    create_time BIGINT       NOT NULL DEFAULT 0,
    update_time BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_review_publish_id
    ON lanting_file_review(publish_id, is_delete);
```

---

## API 设计

### 发布

```
POST /api/files/publish
```

请求：

```json
{
  "fileIds": [1, 2, 3],
  "displayName": "v1.2.0 线上版本"
}
```

响应：

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "publishId": 42,
    "status": "PUBLISHED",
    "displayName": "v1.2.0 线上版本",
    "publishedBy": "zhangsan",
    "publishedAt": 1721404800000,
    "files": [
      {
        "fileId": 1,
        "path": "sql/user_count.sql",
        "commitHash": "abc123def456..."
      },
      {
        "fileId": 2,
        "path": "ddl/user_table.ddl",
        "commitHash": "def456abc123..."
      }
    ],
    "skipped": [
      {
        "fileId": 3,
        "path": "sql/report.sql",
        "reason": "SAME_VERSION_ALREADY_PUBLISHED"
      }
    ]
  }
}
```

### 查询发布批次详情

```
GET /api/publishes/{publishId}
```

响应：

```json
{
  "code": 0,
  "data": {
    "publishId": 42,
    "status": "PUBLISHED",
    "displayName": "v1.2.0 线上版本",
    "publishedBy": "zhangsan",
    "publishedAt": 1721404800000,
    "files": [
      {
        "fileId": 1,
        "path": "sql/user_count.sql",
        "commitHash": "abc123..."
      },
      {
        "fileId": 2,
        "path": "ddl/user_table.ddl",
        "commitHash": "def456..."
      }
    ]
  }
}
```

### 查询文件的发布历史

```
GET /api/files/{fileId}/publishes
```

返回该文件参与过的所有发布批次：

```json
{
  "code": 0,
  "data": [
    {
      "publishId": 42,
      "status": "PUBLISHED",
      "displayName": "v1.2.0 线上版本",
      "publishedBy": "zhangsan",
      "commitHash": "abc123...",
      "publishedAt": 1721404800000
    },
    {
      "publishId": 7,
      "displayName": "v1.1.0",
      "publishedBy": "zhangsan",
      "commitHash": "789def...",
      "publishedAt": 1721000000000
    }
  ]
}
```

### 撤销发布

```
POST /api/publishes/{publishId}/revoke
```

将 `lanting_publish.status` 从 `PUBLISHED` 更新为 `REVOKED`。
仅 `PUBLISHED` 状态的批次可撤销；`PUBLISHING` 或已 `REVOKED` 的批次拒绝操作。

响应：

```json
{
  "code": 0,
  "data": {
    "publishId": 42,
    "status": "REVOKED",
    "revokedAt": 1721500000000
  }
}
```

### 删除发布（软删除）

```
DELETE /api/publishes/{publishId}
```

标记 `lanting_publish.is_delete = 1`，级联标记其下所有 `lanting_file_publish` 记录。已删除的发布可在回收站中查看或恢复。

> **`revoke` 和 `delete` 的区别**：`revoke` 是业务操作——撤回一个已发布的版本，记录仍保留在发布列表中（status=REVOKED）；`delete` 是数据操作——把记录扔进回收站（is_delete=1），用户不想再看到它。

---

## 与旧设计的兼容

### 废弃但保留

- `deleteTag(String tagName)` 方法保留，用于清理旧的 Git tag（如果历史数据中存在）
- `PublishVO.tagName` 字段废弃，改为 `publishId`
- `generateTagName()` 方法移除

### 迁移

旧 `lanting_file_publish` 表中的数据无法直接映射到新模型（缺少 `file_id`）。
如果表中已有数据：

1. 通过 `commit_hash` 查找 `lanting_file_index.latest_commit_hash` 匹配的记录来反推 `file_id`（不精确，一个 commit 可能包含多个文件）
2. 或者直接清理旧数据，从新设计重新开始发布

社区版目前无生产数据，建议直接清空重建表。

---

## 待讨论

- [ ] Review 表是否需要同时支持 `publish_id`（review 整个批次）和 `file_id + commit_hash`（直接 review 某个文件版本）两种模式？当前设计为 review 关联批次
- [ ] 发布通知/事件：`FilePublishedEvent` 是否需要在本次实现，还是等后续有订阅者时再加？
- [ ] 发布列表的分页/筛选需求（按时间、按发布人、按文件路径搜索）