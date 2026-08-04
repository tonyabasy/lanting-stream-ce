# Job 设计交接近况

> 时间：2026-07-28 12:28 起

## 已决策

### 1. ProjectPanel 多视图

四种视图共享同一 Tree 组件：

| 视图 | 内容 | 数据源 | 频率 |
|---|---|---|---|
| Project | SQL 文件 | `GET /files/tree` 过滤 | 高，默认 |
| Tables | DDL 文件 | `GET /files/tree` 过滤 `tables/` | 高 |
| Changed | 未提交变更 | `GET /files/uncommit` | 中 |
| Workspace | 全量文件 | `GET /files/tree` | 低 |

### 2. Table 设计（DDL 文件）

- 存储在 `$workspace/tables/`，`.ddl` 后缀，内容为 Flink SQL DDL
- DB 索引层（`FileIndexService` 扩展）解析结构化元信息（表名、连接器、字段列表）
- 前端双模态编辑：表格表单（快速建表） ↔ CodeMirror 文本（精细控制），编辑同一文件

### 3. Config 存储方案（已敲定）

```
$workspace/.flink/conf/{fileId}.json
```

- 用 SQL 文件的 `fileId` 命名 Config 文件
- 不受 SQL 文件重命名/移动影响（fileId 不变）
- `.flink/` 文件夹树所有视图隐藏，用户不感知
- 首次打开 ConfigPanel 时才创建空 `{}`
- Config 和 SQL 文件物理分离，不用新 DB 表

### 4. 保护目录

后端 `validatePath()` 追加保护：

| 目录 | 保护 |
|---|---|
| `project/` | 禁止删除/重命名/移动 |
| `tables/` | 禁止删除/重命名/移动 |
| `.flink/` | 禁止删除/重命名/移动 |

同现有的 `.lanting`、`.git` 保护逻辑。

### 5. 场景1：用户创建 DDL

用户从 Kafka topic 读数据 → 每分钟统计订单量 → 输出到 Doris。需要创建两个 Table 定义：

- Kafka Source：`order_no`/`order_time`/`user_id`/`city_id`/`pay_amt`
- Doris Sink：`dt`/`stat_time`/`pay_ord_cnt`

### 6. 已记录文档

- `docs/backend/editor-tables-design.md` — Table 场景 + 设计决策
- `docs/backend/editor-config-design.md` — Config 设计（待讨论）
- `docs/backend/job-config-design-question.md` — Config 方案对比

## 当前实现状态

- 文件树：`web/src/pages/dev/tree/` — 搜索、CRUD、懒加载、三视图切换基础设施就绪
- 编辑器：CodeMirror 6，多 tab，自动保存（1.5s），dirty 标记，saveGeneration 竞态控制
- 锁系统：读/写锁感知，只读编辑器，抢锁 UI
- 右侧面板：ConfigPanel 占位（`dev/panels/ConfigPanel.tsx`）
- 后端：`FileController`、`FileIndexService`、`FileLockService`、`GitFileService` 成熟运行
