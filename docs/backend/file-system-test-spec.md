# 文件模块测试方案设计

> 本文档基于 `temp-unit-test-design.md` 整理，聚焦 file 模块近期重构后的测试策略与临界点。
> 核心原则：**测试是手段不是目的，只写有价值的测试，不追求覆盖率指标。**

---

## 1. 背景与范围

file 模块是近期重构的核心区域，涉及 Git 写操作、磁盘 IO、内存软锁、工作空间配置等。这些部分最容易出现并发问题、状态不一致和边界错误。本测试计划覆盖：

- `FileLockService`（内存软锁，含 stripe 锁互斥）
- `GitFileService`（Git + 磁盘核心服务）
- `WorkspaceService`（工作空间配置与初始化）
- `FileController` / `WorkspaceController`（HTTP 接口）
- `GlobalExceptionHandler`（自定义 message 优先的回归）

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

- `acquire`：首次抢锁、覆盖他人、同持有人刷新时间戳
- `release`：持锁人释放成功、非持锁人释放失败
- `forceRelease`：不校验身份直接释放
- `isHolder` / `getHolder` / `getLockedAt`：正常查询与未锁定情况
- `doIfHolder`：持锁人执行成功、非持锁人执行抛 `FILE_LOCKED`、返回值正确传递
- **stripe 互斥**：A 在 `doIfHolder` 动作中阻塞时，B 对同路径的 `acquire` 必须等待 A 完成后才成功

### 3.2 `GitFileServiceIntegrationTest`（服务集成测试）

使用 `@TempDir` 创建真实 Git 仓库，覆盖主链路：

- `tree`：从磁盘读取文件结构，节点附带锁状态
- `content`：读取磁盘当前内容（含未提交变更）
- `save`：自动保存到磁盘，要求持锁
- `createFolder`：创建目录后提交
- `commit`：提交已锁定的文件，未锁定文件进入 skipped
- `diff`：两个 commit 之间的 unified diff
- `revert`：文件级回滚到指定 commit
- `publish`：对当前 HEAD 打 tag
- `rollbackRelease`：按 tag 回滚所有文件
- `delete`：文件与文件夹删除，含 force 场景

关键断言：
- commit 后 HEAD 前进
- tag 存在且指向正确 commit
- rollback 后磁盘内容回退
- **删除后文件真正从 Git 历史中移除**（用 JGit 验证 HEAD tree 中不存在）
- **删除后新发布的 tag 不包含被删文件**

### 3.3 `WorkspaceServiceTest`（服务集成测试）

- `getDefaultWorkspaceConfig`：数据库为空时返回 `{}`
- `updateDefaultWorkspaceConfig`：写入后再次读取一致
- 默认工作空间初始化：目录创建、git init、初始 commit

### 3.4 `FileControllerTest`（Controller 集成测试）

继承 `BaseIntegrationTest`，覆盖：

- 文件树 / 读取 / 保存 / 创建文件夹 / 删除 / 提交
- 历史记录 / diff / 文件级回滚
- 抢锁 / 释放锁
- 发布 / 发布历史 / 回滚预检 / 回滚
- review 添加与查询
- 未登录 401、无权限 403、参数校验 400、业务错误码

### 3.5 `WorkspaceControllerTest`（Controller 集成测试）

- `GET /api/workspaces/config`：读取当前配置，空配置返回 `{}`
- `PUT /api/workspaces/config`：保存后读取一致

### 3.6 `UserControllerTest` 补充（Controller 集成测试）

- `GET /api/users/me/preferences`：读取偏好，空时返回 `{}`
- `PUT /api/users/me/preferences`：保存后读取一致
- `PUT /api/users/me/profile`：仅更新非空字段

### 3.7 `GlobalExceptionHandler` 回归测试

- `BusinessException(ResultCode, String)` 的自定义 message 优先返回，不被 i18n 默认文案覆盖

---

## 4. 反向测试与临界点

### 4.1 文件锁并发与权限

| 场景 | 反向测试 |
|---|---|
| A 持锁时 B 抢锁同一路径 | `acquireLock` 返回 `previousHolder` 为 A；B 未 acquire 直接 save 抛 `FILE_LOCKED` |
| B 释放 A 持有的锁 | `releaseLock` 返回 403，且锁仍在 A 名下 |
| force delete 文件夹后锁未清理 | force delete 后 `getHolder` 返回 null，且 `tree` 中不再显示被锁节点 |
| 锁状态在 Spring 单例中跨请求残留 | 启动新请求时，不存在的路径无锁 |
| stripe 互斥 | A 在 `doIfHolder` 动作中阻塞时，B 对同路径 `acquire` 等待；A 完成后 B 成功，A 后续 save 失败 |

### 4.2 路径安全与越界

| 场景 | 反向测试 |
|---|---|
| `path = ""` / `"."` / `"./"` | 返回 `PATH_ILLEGAL` |
| 路径含 `..`（如 `a/../b`） | 返回 `PATH_ILLEGAL` |
| 绝对路径 `/etc/passwd` | 返回 `PATH_ILLEGAL` |
| 反斜杠路径 `a\\b` | 返回 `PATH_ILLEGAL` |
| 路径包含 `.lanting` | 返回 `LANTING_DIR_FORBIDDEN` |
| 路径包含 `.git` | 返回 `LANTING_DIR_FORBIDDEN` |
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
| 同一天第 999 个 tag 后再发布 | `generateTagName` 序号耗尽，返回 `PUBLISH_TAG_EXISTS` |
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
| page=0 或负数 | 确认 `PageQuery` 校验或 `history` 内部计算不抛异常 |
| pageSize 超过最大限制 | `PageQuery` 的 `@Max` 返回 400 |
| history 指定 path 只返回该文件的 commit | 提交多个文件后，只查询目标 path 的历史，结果只包含该文件的 commit |
| ⚠️ diff 文件在某一 commit 中不存在 | 确认返回空 diff 还是抛异常，需产品语义确认 |
| `from == to` | 返回空字符串，不抛异常 |
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
| 中文内容 | 按 UTF-8 字节数校验，非字符数 |
| 保存 `.txt` | 返回 `FILE_TYPE_NOT_ALLOWED` |
| 保存 `.exe` | 返回 `FILE_TYPE_NOT_ALLOWED` |
| 保存无扩展名文件 | 返回 `FILE_TYPE_NOT_ALLOWED` |

### 4.9 异常消息与错误码

| 场景 | 反向测试 |
|---|---|
| `BusinessException(code, message)` 自定义消息 | 前端收到自定义消息，不被 i18n 默认文案覆盖 |
| 磁盘 IO 错误 | 返回 `GIT_OPERATION_FAILED`（30708），message 透传异常信息；错误码文案为"文件操作失败" |

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
3. **`FileControllerTest` + `WorkspaceControllerTest`**（接口层完整链路）
4. **`UserControllerTest` 补充**（preferences / profile）
5. **`GlobalExceptionHandler` 回归测试**（防止 custom message 回归）

---

## 7. 高风险待确认点

以下临界点在测试过程中需特别关注：

- [x] **软锁无 IO 级互斥**：已解决。通过 `FileLockService` 的 stripe 锁将"持锁校验 + 写动作"与"抢锁/释放"互斥，避免并发写入导致内容不一致。
- [ ] **非法 commit SHA**：需修代码。`diff` / `revert` 中 `ObjectId.fromString` 抛 `IllegalArgumentException` 时，应转为 `PARAM_INVALID`（10001，400），而不是被全局处理器兜底为 `SYSTEM_ERROR`。
- [ ] **磁盘 IO 错误码**：不新增错误码。将 `30708` 的文案从"Git 操作失败"改为"文件操作失败"，以覆盖 Git 操作和磁盘 IO 两类异常。
- [ ] **diff 文件在某一 commit 中不存在**：需产品确认返回空 diff 还是抛异常。

---

## 8. 严重 bug 回归测试（必须补充）

这些是近期修复的、最容易复发的 bug，文档正文中已分散列出，这里集中强调：

| 补充项 | 对应的修复 | 优先级 |
|---|---|---|
| **删除真正进入 Git** | delete 文件 → commit 后用 JGit 验证 HEAD tree 中该文件不存在；再走 delete → rollbackRelease(更早的 tag) → 文件恢复；delete 后新 publish 的 tag 不含该文件 | 🔴 最高 |
| `history` 指定 path 只返回该文件的 commit | `addPath` 修复 | 🔴 |
| force 删除含他人锁的文件夹后，锁真正被清理 | `forceRelease` 修复 | 🟡 |
| 文件类型白名单反向 | save `.txt`/`.exe`/无扩展名 → `FILE_TYPE_NOT_ALLOWED` | 🟡 |
| `createFolder` 已存在路径 → `FILE_ALREADY_EXISTS` | 正反向都没覆盖 | 🟡 |
| `rollbackRelease` 不删除 tag 之后新建的文件 | 覆盖语义回归 | 🟡 |
| publish 落库失败补偿删 tag | 用 `@SpyBean` 让 `create` 抛异常，断言 tag 不存在 | 🟢 可选 |

