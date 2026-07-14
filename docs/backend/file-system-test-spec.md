# 文件模块测试方案设计

> 核心原则：**测试是手段不是目的，只写有价值的测试，不追求覆盖率指标。**

---

## 1. 背景与范围

file 模块是近期重构的核心区域，涉及 Git 写操作、磁盘 IO、内存软锁、工作空间配置等。这些部分最容易出现并发问题、状态不一致和边界错误。本测试计划覆盖：

- `FileLockService`（内存软锁，doIfHolder 语义）
- `GitFileService`（Git + 磁盘核心服务）
- `FileIndexService`（元数据索引，reconcile 一致性）
- `FileController` / `FileSystemAdminController`（HTTP 接口）

---

## 2. 测试分层

沿用项目已有测试规范，分三层：

| 测试层 | 工具 | 典型场景 | 启动速度 |
|---|---|---|---|
| 单元测试 | Mockito | `FileLockService`、路径校验、业务分支 | 毫秒级 |
| 服务集成 | `@SpringBootTest` + `@TempDir` | `GitFileService` + 真实 Git 仓库 | 数秒 |
| Controller 集成 | `BaseIntegrationTest` + `TestRestTemplate` | 完整 HTTP 链路、鉴权、错误码 | 数秒 |

---

## 3. 正向测试覆盖

### 3.1 `FileLockServiceTest`（单元测试）

- `doIfHolder`：持锁人执行成功、非持锁人执行抛 `FILE_LOCKED`

### 3.2 `GitFileServiceIntegrationTest`（服务集成测试）

使用 `@TempDir` 创建真实 Git 仓库，覆盖主链路：

- 完整正向流程：创建目录 → 保存文件 → 查看文件树 → 读取内容 → 提交文件 → 发布，验证每一步成功
- `revert`：文件级回滚到指定 commit
- `diff`：两个 commit 之间的 unified diff
- `rollbackRelease`：按 tag 回滚所有文件
- `delete`：文件与文件夹删除，含 force 场景

关键断言：
- commit 后 HEAD 前进
- tag 存在且指向正确 commit
- rollback 后磁盘内容回退
- **删除后文件从磁盘和 DB 索引中移除**（软删除，`deleted_at > 0`）
- **删除后用户手动 commit，新发布的 tag 不包含被删文件**
- **force 删除文件夹后，所有子文件锁被清理**

### 3.3 `FileControllerTest`（Controller 集成测试）

继承 `BaseIntegrationTest`，覆盖：

- review 添加与查询（HTTP 接口验证）
- 未登录 401、无权限 403、参数校验 400、业务错误码

### 3.4 `FileSystemAdminControllerTest`（Controller 集成测试）

继承 `BaseIntegrationTest`，覆盖：

- `POST /api/admin/fs/reconcile`：手动触发一致性校验，返回完整报告（total / orphan / missing / mtimeMismatch）
- `GET /api/admin/fs/status`：查询索引总记录数

### 3.5 `FileIndexServiceTest`（服务集成测试）

使用 `@TempDir` 创建真实文件系统。

**写操作回归（6 条）：**

- `createFolder` → DB 有对应记录，`parent_path` 正确
- `save` 新文件 → DB INSERT 成功
- `save` 已有文件 → DB UPDATE mtime（不重复 INSERT）
- `delete` 文件 → DB 记录删除
- `delete` 文件夹 → 子节点递归删除
- `rollbackRelease` 恢复被删文件 → DB UPSERT 正确（不存在则 INSERT，不抛异常）

**reconcile 数据一致性扫描：**

- **正常一致**：空目录 → reconcile，orphan / missing / mtimeMismatch 均为 0
- **未索引文件**：手动在磁盘创建文件/目录但 DB 无记录 → reconcile 后 unindexedFiles / unindexedFolders 正确列出
- **缺失文件**：DB 有记录但磁盘上删除 → reconcile 后 staleFiles / staleFolders 正确列出
- **mtime 不一致**：修改磁盘文件 mtime 但 DB 未更新 → reconcile 后 mtimeMismatches 正确列出
- **跳过 .git / .lanting**：在磁盘 `.git` 或 `.lanting` 目录下创建文件 → reconcile 不报告为 orphan

**tree() 查 DB 验证：**

- DB 有记录但磁盘文件已删除 → `tree()` 仍然返回该文件节点（证明查的是 DB 不是磁盘）

---

## 4. 反向测试与临界点

### 4.1 文件锁并发与权限

| 场景 | 反向测试 |
|---|---|
| A 持锁时 B 抢锁同一文件 | `acquireLock` 返回 `previousHolder` 为 A；B 未 acquire 直接 save 抛 `FILE_LOCKED` |
| B 释放 A 持有的锁 | `releaseLock` 返回 403，且锁仍在 A 名下 |
| force delete 文件夹后锁未清理 | force delete 后 `getHolder` 返回 null，且 `tree` 中不再显示被锁节点 |

### 4.2 路径安全与越界

| 场景 | 反向测试 |
|---|---|
| `path = ""` / `"."` / `"./"` | 返回 `PATH_ILLEGAL` |
| 路径含 `..`（如 `a/../b`） | 返回 `PATH_ILLEGAL` |
| 绝对路径 `/etc/passwd` | 返回 `PATH_ILLEGAL` |
| 反斜杠路径 `a\\b` | 返回 `PATH_ILLEGAL` |
| 路径以 `/` 开头 | 返回 `PATH_ILLEGAL` |

### 4.3 Git 写操作并发与状态

| 场景 | 反向测试 |
|---|---|
| 两个请求同时 commit | 工作空间锁串行化，HEAD 连续递增，对象库无损坏 |
| 无锁用户尝试 save | 返回 `FILE_LOCKED` |
| 提交全为他人锁定文件 | 返回 `NOTHING_TO_COMMIT`（30713），`committed` 为空 |
| 删除文件时未持锁 | 返回 `FILE_LOCKED` |
| 空仓库调用 publish | 解析 HEAD 为 null，抛 `GIT_OPERATION_FAILED` |
| 非法 commit SHA 调用 diff | 返回 `PARAM_INVALID`（10001，400），message 包含"非法的 commit hash" |
| 非法 commit SHA 调用 revert | 返回 `PARAM_INVALID`（10001，400），message 包含"非法的 commit hash" |

### 4.4 发布与回滚边界

| 场景 | 反向测试 |
|---|---|
| 发布时磁盘有未提交变更 | tag 仍指向最近 commit，不包含磁盘未提交内容 |
| 同一 HEAD 连续发布两次 | 生成两个不同 tag，都指向同一 commit |
| 回滚到不存在的 tag | 返回 `ROLLBACK_TARGET_NOT_FOUND` |
| 回滚 tag 中文件被他人锁定 | `rollbackCheck` 列出被他人锁定文件；`rollbackRelease` 后锁归回滚者 |
| 回滚 tag 中文件在当前仓库已不存在 | 先建父目录再写回，确认不报错 |
| tag 中文件被当前用户自己锁定 | `rollbackCheck` 不提示自己锁定的文件 |
| 回滚后不删除 tag 之后新建的文件 | `rollbackRelease` 后，tag 之后新建的文件仍保留在磁盘和 Git 历史中 |

### 4.5 删除边界

| 场景 | 反向测试 |
|---|---|
| 删除文件夹 force=false 但含他人锁定文件 | 返回 `FILES_LOCKED`（30712） |
| 删除文件夹 force=true | 强制删除并清除所有锁（含他人锁） |
| 删除不存在的文件 | 返回 `FILE_NOT_FOUND` |
| 删除空文件夹 | 返回 `FOLDER_NOT_FOUND` 或直接成功（按当前语义） |

### 4.6 历史与 Diff 边界

| 场景 | 反向测试 |
|---|---|
| 历史记录为空 | 返回 `total=0`，`records` 为空 |
| history 指定 fileId 只返回该文件的 commit | 提交多个文件后，只查询目标 fileId 的历史，结果只包含该文件的 commit |
| ⚠️ diff 文件在某一 commit 中不存在 | 确认返回空 diff 还是抛异常，需产品语义确认 |
| `from == to` | 返回空字符串，不抛异常（⚠️ 需确认代码已实现） |
| tree 根层级返回文件夹与文件 | 仅返回 DB 索引中 parent_path='' 的节点 |

### 4.7 配置与偏好边界

| 场景 | 反向测试 |
|---|---|
| workspace config 为 `null` / `""` | `getWorkspaceConfig` 返回 `{}` |
| `WorkspaceEntity.config` 序列化 | 确认不在任何接口响应中暴露 |

> 注：用户偏好为非法 JSON、并发更新 config / preferences 属于透明存储和 last-write-wins 语义，不在服务端测试范围内，已在 `file-system-spec.md` 文档化。

### 4.8 文件大小、类型与空内容

| 场景 | 反向测试 |
|---|---|
| 文件大小正好等于 1MB | 校验是 `bytes.length > MAX_FILE_SIZE`，等于 1MB 应通过 |
| 1MB + 1 byte | 返回 `FILE_SIZE_EXCEEDED` |
| 空内容 `null` / `""` | 写入 0 字节，不抛异常 |
| 保存 `.txt` | 返回 `FILE_TYPE_NOT_ALLOWED` |
| 保存 `.exe` | 返回 `FILE_TYPE_NOT_ALLOWED` |
| 保存无扩展名文件 | 返回 `FILE_TYPE_NOT_ALLOWED` |

### 4.9 异常消息与错误码

| 场景 | 反向测试 |
|---|---|
| `BusinessException(code, message)` 自定义消息 | 前端收到自定义消息，不被 i18n 默认文案覆盖 |

---

## 5. 测试基础设施

- **`@TempDir`**：为每个测试创建独立临时目录，避免污染 `../.data`
- **`application-test.yml` 改造**：将 `lanting.data.workspace-dir` 指向测试临时目录
- **锁状态清理**：`FileLockService` 是 Spring 单例，每个测试后清理 `lockMap`
- **真实 Git 仓库**：`GitFileService` 集成测试使用 JGit 真实仓库，不 mock JGit 行为
- **测试数据隔离**：每个测试使用独立用户名、文件路径，避免交叉污染

---

## 6. 落地顺序

按风险从高到低执行：

1. **`FileLockServiceTest`**（最快建立信心，覆盖软锁核心语义）
2. **`GitFileServiceIntegrationTest`**（file 模块核心，含并发与边界）
3. **`FileIndexServiceTest`**（写操作回归 + reconcile 一致性扫描 + tree() DB 验证）
4. **`FileControllerTest`**（鉴权、参数校验、错误码）
5. **`FileSystemAdminControllerTest`**（reconcile / status 管理接口）

---

## 7. 高风险待确认点

以下临界点在测试过程中需特别关注：

- [x] **软锁无 IO 级互斥**：已解决。通过 `FileLockService` 的 stripe 锁将"持锁校验 + 写动作"与"抢锁/释放"互斥，避免并发写入导致内容不一致。
- [x] **非法 commit SHA**：需修代码。`diff` / `revert` 中 `ObjectId.fromString` 抛 `IllegalArgumentException` 时，应转为 `PARAM_INVALID`（10001，400），而不是被全局处理器兜底为 `SYSTEM_ERROR`。
- [ ] **磁盘 IO 错误码**：不新增错误码。将 `30708` 的文案从"Git 操作失败"改为"文件操作失败"，以覆盖 Git 操作和磁盘 IO 两类异常。
- [ ] **diff 文件在某一 commit 中不存在**：需产品确认返回空 diff 还是抛异常。
