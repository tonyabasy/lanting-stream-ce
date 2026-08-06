# 破坏者计划（Sabotage Plan）— 系统韧性验证

> 2026-08-05 · 前置：`docs/backend/exception-and-logging-spec.md`（异常体系与日志规范）、`docs/backend/result-code-spec.md`（统一响应码）、`docs/plan/table-parse-backend-plan.md`（DDL parse 链路）。
> 本计划**只验证和记录，不修复**。发现的缺陷记入「六、结果记录」，修复走独立 plan。

---

## 一、目标

通过构造破坏性输入 / 并发竞争 / 故障注入，系统性验证系统六项能力：

| 能力维度 | 定义 | 验证方式示例 |
|---|---|---|
| 风险防控 | 阻止或限制破坏性操作 | 路径穿越、越权、锁被接管后的写入 |
| 边界处理 | 非法/极端输入的健壮性 | 空值、超长、特殊字符、超大并发 |
| 异常处理 | 异常分类、抛出点记录、兜底 | 错误 DDL、git 故障、磁盘故障 |
| 错误展示 | 用户可理解的错误提示，不泄漏内部细节 | 前端 Alert / toast 文案、HTTP 状态码语义 |
| 问题排查 | 可关联日志定位问题 | traceId 透传、日志含业务上下文 |
| 恢复能力 | 故障后可恢复且数据一致 | 服务重启、索引与磁盘不一致、回收站恢复 |

**验收口径**：系统"不崩溃、不静默丢数据、错误可理解、可排查、可恢复"。

---

## 二、执行环境与通用方法

- 后端：`admin/`，`mvn spring-boot:run`（本地开发环境，使用临时 workspace）。
- 前端：`web/`，`pnpm dev`（umi + antd）。
- 验证手段：
  - API 层：`curl -H "lanting-token: <token>" ...` 直打接口，观察 HTTP status + body。
  - 前端层：浏览器实际操作 + DevTools Network/Console。
  - 可复现场景：补充集成测试（如 `TableControllerIntegrationTest` 模式）。
- **安全前提**：只在本地/开发环境执行，不碰生产；破坏操作使用测试文件/临时目录，不真实删除用户数据。

---

## 三、场景 1 静态分析结论（错误 DDL → 前端展示与解析）

> 作为首个破坏场景先跑，静态链路已确认（代码证据如下），执行验证时重点核对。

**链路**：打开 `.ddl` → `TableEditor` form 模式 → `TableFormEditor` → `useTableForm` → `POST /api/tables/utils/deserialize`（`web/src/services/table.ts:5`）→ `FlinkSqlParser.parseCreateTable` 抛 `IllegalArgumentException` → `GlobalExceptionHandler.handleException` 兜底 → 500 + 「系统内部错误」→ 前端 `ApiError(50001)` → 降级为空表单 + warning Alert。

| # | 静态发现 | 证据 | 能力维度 |
|---|---|---|---|
| P1 | 非法 DDL（用户可修正的输入错误）落到 **HTTP 500 / 50001「系统内部错误」**，语义错配 | `GlobalExceptionHandler.java:119-124`（`Exception` 兜底统一 500） | 错误展示 / 异常处理 |
| P2 | 解析失败**静默降级为空表单**，用户可能在不注意 Alert 的情况下保存 → 残缺 DDL 覆盖原文件 | `useTableForm.ts:60-64`；`TableFormEditor/index.tsx:43-48`（save 链路已通） | 风险防控 |
| P3 | 保存坏 DDL 时 `gitFileService.save` 写盘成功，`updateIndex` 解析失败仅 warn 返回 null，**旧索引残留**，磁盘与索引不一致且用户零感知 | `TableService.java:226-231` | 数据一致性 / 恢复能力 |
| P4 | Calcite 解析错误详情（含行列号）只进 ERROR 日志，前端只见「系统内部错误」 | `FlinkSqlParser.java:58`；`GlobalExceptionHandler.java:122` | 问题排查 |
| P5 | 规范设计的 traceId 透传**未落地**，`Result` 无 traceId 字段 | `Result.java:23-35`（仅 code/message/data） | 问题排查 |
| P6 | `ParseTableRequest` 无长度上限；Calcite 对重复列名/未知类型宽容放行，前端表单不校验 | `ParseTableRequest.java:20-22`；`FlinkSqlParser.java:91-115` | 边界处理 |
| P7 | 前端 `autoSave` catch **静默 return false**，网络/服务器错误时用户不知数据未保存 | `web/src/models/editor.ts:150-152` | 错误展示 |

---

## 四、破坏场景清单

> 每场景：操作 → 预期现象（依据代码静态分析）→ 能力维度 → 通过判据（系统应…）。执行时按批次逐条实测，记录「实际 vs 预期」。

### A. DDL / 表模块

| ID | 场景 | 操作 | 预期现象（代码依据） | 能力维度 | 通过判据 |
|---|---|---|---|---|---|
| A1 | 语法错误 DDL | `POST /api/tables/utils/deserialize` body `{"ddl":"CREATE TABLE t ("}` | 500 + 50001「系统内部错误」；前端空表单 + Alert（P1-P7 全触发） | 错误展示/异常处理 | 应返回 4xx 业务码 + 具体行列提示 |
| A2 | 空/纯空白 DDL | `{"ddl":"   "}` | `IllegalArgumentException("DDL content is empty")` → 同 A1 | 边界处理 | 返回明确业务错误而非 500 |
| A3 | 非 CREATE TABLE | `DROP TABLE t;` / `SELECT 1;` / 注释开头 | `"Only CREATE TABLE DDL is supported..."`（`FlinkSqlParser.java:49-52`）→ 同 A1 | 边界处理 | 明确提示"仅支持 CREATE TABLE" |
| A4 | 语义非法语法合法 | `WATERMARK FOR x`（x 不存在）、`PRIMARY KEY (y)`（y 不存在）、重复列名、未知类型 `FOO` | Calcite 宽容解析成功，前端不校验 | 风险防控/边界处理 | 前端应警告非法引用/重复列 |
| A5 | 空表单/残缺表单保存 | 解析失败后直接点确定 | `serialize` → `createTableToString`（`FlinkSqlParser.java:170-240`）生成残缺 DDL 写盘 | 风险防控 | 无合法内容时禁止覆盖保存 |
| A6 | 特殊字符 | 列名/comment/expr 含 `'`、反引号、换行、null 字节 | `escapeSql` 仅转义单引号（`:271-273`）；`stripBackticks` 仅去反引号（`:264-266`）；反序列化端 Calcite 行为待实测 | 边界处理 | 往返不丢字符、不产生非法 DDL |
| A7 | 超大 DDL | 1MB+ DDL | 无大小限制（`ParseTableRequest`）；前端 15s 超时（`request.ts:39`）→ 10002 请求超时 | 边界处理 | 有长度上限与友好超时提示 |
| A8 | 文本↔表单往返 | 含 metadata/computed/watermark/PK 的 DDL 反复往返 | 字段提取（`FlinkSqlParser.java:91-115`）与序列化（`:245-259`）对称性 | 边界处理 | 往返信息不丢失 |

### B. 文件系统模块

| ID | 场景 | 操作 | 预期现象（代码依据） | 能力维度 | 通过判据 |
|---|---|---|---|---|---|
| B1 | 路径穿越变体 | create/move 传 `..%2f`、`%2e%2e%2f`、`.../`、Unicode 全角 `．．`、`a/../b` | 现有校验 `startsWith("/") || contains("..") || contains("\\")`（`GitFileService.java:965`）——只挡字面 `..` 和 `\`，编码绕过待实测 | 风险防控 | 所有变体均无法越出工作区根 |
| B2 | 重名冲突 | 创建已存在路径 / 文件与文件夹同名 / rename 撞名 / move 覆盖目标 | 存在性检查待核对 `GitFileService.create/rename/move` | 边界处理 | 明确报"已存在"，不静默覆盖 |
| B3 | 删除边界 | 删非空文件夹 / 删被他人锁文件 / 并发删除同一文件 | delete 走软删除 + git commit，锁处理（forceRelease）待核对 | 风险防控 | 不误删他人数据、不删根目录 |
| B4 | 移动死循环 | 把文件夹 move 进自己子目录 | 祖先关系校验待核对 | 边界处理 | 拒绝该操作 |
| B5 | 超大/二进制内容 | save 大文件（>10MB）或二进制内容 | save 无大小限制 | 边界处理 | 有大小限制或明确报错 |
| B6 | 回收站 | purge 未软删除条目 / restore 到已占用路径 / restore 不存在的 commitHash | 文档声称 purge 仅允许已软删除条目，实测绕过 | 风险防控/恢复能力 | 非法操作被拒 |

### C. 锁模块

| ID | 场景 | 操作 | 预期现象（代码依据） | 能力维度 | 通过判据 |
|---|---|---|---|---|---|
| C1 | 抢锁风暴 + 被接管后写入 | A 编辑中 B 抢锁，A 继续编辑保存 | `acquire` 永远成功（`FileLockService.java:209-230`）；`doIfHolder` 校验失败 → 30709（`:123-137`）；前端标记 `<lost>` 只读（`editor.ts:96-111`） | 风险防控 | 被接管者写盘被拒，前端真正只读 |
| C2 | 释放他人锁 | A 释放 B 的锁 | `release` 校验 holder → false → FORBIDDEN（`FileLockService.java:244-261`；`FileController.java:222-225`） | 风险防控 | 非持锁人不能释放 |
| C3 | 服务重启 | 编辑中重启后端 | 内存锁全清（`FileLockService.java:32` 会话级设计）→ 磁盘未提交内容无锁可抢 | 恢复能力 | 重启后不丢数据、状态可感知 |
| C4 | 并发抢锁 | 同文件高并发 acquire/save | segment tryLock 1s 超时 → `FILE_OPERATION_BUSY`（`:92-105`） | 边界处理 | 超时错误可理解，不死锁 |
| C5 | 目录锁边界 | 根目录、path 相等、祖先/子孙边界 | `ensureFolderLocksSafety` 用 `startsWith(lockedPath + "/")`（`:339,344`）——根串/相等路径边界待实测 | 边界处理 | 目录锁语义正确 |

### D. 认证/权限

| ID | 场景 | 操作 | 预期现象（代码依据） | 能力维度 | 通过判据 |
|---|---|---|---|---|---|
| D1 | 暴力破解 | 连续错误密码登录 | `UserService.login` 无验证码/限流待核对 | 风险防控 | 有防爆破机制 |
| D2 | token 生命周期 | 登出后重放旧 token / 过期 token | Sa-Token 拦截（`SaTokenConfig.java`）→ 401 | 边界处理 | 旧 token 失效 |
| D3 | 越权 | A 用户 save/delete B 用户的文件（B 未持锁） | 锁是软校验，文件归属是否校验待核对 | 风险防控 | 无锁/非本人操作被拒 |
| D4 | admin 接口 | 普通用户调 admin 接口 | `StpInterfaceImpl` 权限分级 | 风险防控 | 按权限拒绝 |

### E. Git / 版本模块

| ID | 场景 | 操作 | 预期现象（代码依据） | 能力维度 | 通过判据 |
|---|---|---|---|---|---|
| E1 | 并发 commit+save | 多文件同时 commit 与 save | gitWriteLock 顺序（`GitFileService.withWorkspaceLock`） | 边界处理 | 无数据错乱 |
| E2 | diff/revert 非法参数 | from/to/commitHash 传 `--` 开头、不存在 hash、注入字符 | 参数直接进 git 命令（`GitFileService.java:188-193`） | 风险防控 | 无命令注入、报错友好 |
| E3 | revert 边界 | revert 已删除文件 / 不存在的 commit | revert 逻辑待核对 | 边界处理 | 明确报错不破坏现状 |
| E4 | git 仓库损坏 | 破坏 `.git` 目录内容后操作 | git 异常路径（`SystemException` 设计） | 恢复能力 | 报"系统内部错误"+ 日志可查，不崩溃 |
| E5 | history 分页边界 | pageSize 0/负数/超大 | `PageQuery` 统一分页校验 | 边界处理 | 参数被正确 clamp/拒绝 |

### F. 前端模块

| ID | 场景 | 操作 | 预期现象（代码依据） | 能力维度 | 通过判据 |
|---|---|---|---|---|---|
| F1 | 超大/二进制文件 | 打开 >5MB 或乱码文件 | CodeMirror 行为 | 边界处理 | 不卡死、可关闭 |
| F2 | 网络断开自动保存 | 断网后编辑 | `autoSave` catch 静默 `return false`（`editor.ts:150-152`） | 错误展示 | 应有"保存失败"提示 |
| F3 | 强制关闭 | beforeunload 拦截后强关 | dirty 数据丢失（`editor.ts:181-190`） | 恢复能力 | 有提示或恢复手段 |

---

## 五、执行批次（每批跑完停一下 review）

| 批次 | 范围 | 方式 | 验收 |
|---|---|---|---|
| 1 | A1-A8（DDL/表，含场景 1 全量核对 P1-P7） | curl + 浏览器 + 补测试 | 记录实际现象 vs 预期 |
| 2 | B1-B6 + C1-C5（文件系统 + 锁） | curl 并发 + 浏览器 | 同上 |
| 3 | D1-D4 + E1-E5（认证 + Git） | curl + 故障注入 | 同上 |
| 4 | F1-F3 + 综合恢复演练（重启、索引修复） | 浏览器 + 运维操作 | 同上 |

---

## 六、结果记录与缺陷追踪

每场景实测后记录：

```
### A1 语法错误 DDL
- 实际现象：
- 预期 vs 实际：□一致 □不一致（差异：）
- 能力维度结论：通过 / 缺陷
- 缺陷编号：SAB-xxx（仅记录，不修复）
- 修复建议（供后续 plan 引用）：
```

发现缺陷按严重度排序汇总，修复统一走独立 plan（如 `sabotage-fix-{topic}-plan.md`）。

---

## 七、明确不做（本次）

- **不修改业务代码**：本计划只做破坏性验证与记录，不顺手修复。
- 不碰生产/共享环境：所有破坏操作限本地开发环境与测试数据。
- 不真实删除用户数据：删除类场景使用测试文件，结束后恢复 workspace。
- 不引入真实故障到基础设施：磁盘满、DB 停机等用模拟/测试替身验证。
- 缺陷修复不在此计划内，另行排期。
