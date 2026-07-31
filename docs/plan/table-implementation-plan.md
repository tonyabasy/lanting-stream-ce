# Table 实现计划

> 2026-07-30 · 从 `docs/backend/editor-tables-design.md` + `docs/frontend/design/table-editor-design.html` 推导

---

## Checkpoint 总览

```
CP1: 后端基础设施 ──► CP2: Tables 树可用 ──► CP3: 表单编辑器 ──► CP4: 双向同步
  保护目录 + DB表         ProjectPanel接入      DdlFormEditor       ddlParser
  DDL解析 + API          .ddl 双击打开          Segmented切换       parseDdlToFormData
```

每个 CP 完成后暂停，等待 review。

---

## 进度总览

| CP | 状态 | 说明 |
|---|---|---|
| CP1-1 保护目录 | ✅ 已完成 | `GitFileService` 已封禁 `.flink`，`ddl/` / `sql/` 目录不可删/移/重命名 |
| CP1-2 DB 表 + Entity + Mapper | ✅ 已完成 | 建表语句追加到 `V1__init.sql`（未新建 V2），实体/Mapper 已就位 |
| CP1-3 DDL 解析器 | ✅ 已完成 | `FlinkSqlParser` + `FlinkSqlParserTest` 已就绪 |
| CP1-4 TableService 索引同步 | ✅ 已完成 | `updateIndex` / `deleteByFileId` 已实现并测试 |
| CP1-5 TableController 与表单模型 | ✅ 已完成 | Controller、Service CRUD、DTO/VO/表单模型、单测已完成 |
| CP1-6 GitFileService 去耦合与端到端验证 | 🔄 部分完成 | GitFileService 已去耦合；端到端验证记录待补 |
| CP2 Tables 树可用 | ⏳ 未开始 | |
| CP3 表单编辑器 | ⏳ 未开始 | |
| CP4 双向同步 | ⏳ 未开始 | |

---

## CP1: 后端基础设施

CP1 拆成 6 个小步，每步完成后暂停 review。

---

### CP1-1: 保护目录 ✅

#### 目标
`ddl/`、`sql/` 目录本身不可删除/重命名/移动，`.flink` 扩展名完全封禁。

#### 任务
- **文件**: `admin/.../GitFileService.java`
- `validatePath()` 追加 `.flink` 封禁（和 `.lanting`、`.git` 同级）
- `delete()` 目录分支：若 `entity.getPath()` 精确等于 `ddl` 或 `sql`，抛 `PROTECTED_DIRECTORY`
- `move()`（rename 也会进入）：若旧路径或新路径精确等于 `ddl` 或 `sql`，抛 `PROTECTED_DIRECTORY`
- `FileResultCode` 新增 `PROTECTED_DIRECTORY(30716, "受保护目录不可操作", 403)`
- 单测覆盖：删 `ddl/`、重命名 `sql/`、移动 `ddl/xxx` 到 `other/xxx`（应通过）

#### 产出
- `GitFileService.java`
- `FileResultCode.java`
- 保护目录单测

---

### CP1-2: DB 表 + Entity + Mapper ✅

#### 目标
建立 `lanting_table_index`、`lanting_column_index` 两张表及对应实体/Mapper。

#### 任务
- **Flyway**: 新建 `V2__add_table_index.sql`
  - `lanting_table_index`: `id`, `file_id`(UNIQUE), `table_name`(UNIQUE), `connector_type`, `partition_field`, `create_time`, `update_time`
  - `lanting_column_index`: `id`, `table_id`, `name`, `type`, `comment`, `ordinal`, `create_time`, `update_time`
  - 两个表都物理删除，不继承 `BasicEntity`
- **Entity**: `TableIndexEntity`、`ColumnIndexEntity`
- **Mapper**: `TableIndexMapper`、`ColumnIndexMapper`（继承 BaseMapper）

#### 产出
- `V2__add_table_index.sql`
- `TableIndexEntity.java`、`ColumnIndexEntity.java`
- `TableIndexMapper.java`、`ColumnIndexMapper.java`

---

### CP1-3: DDL 解析器 ✅

#### 目标
能稳定从 Flink DDL 中提取表名、连接器、字段列表、分区字段。

#### 任务
- **文件**: `admin/.../module/table/util/FlinkSqlParser.java`
- 使用 Flink SQL Parser 解析 `CREATE TABLE` 语句
- `parseCreateTable(String ddl) → FlinkSqlParser.Table`
- `Table` record 包含：`tableName`、`ifNotExists`、`properties`（完整 `WITH` 属性）、`connector`（便捷字段）、`partitionKeys`、`columns`
- `Column` record 包含：`name`、`type`、`comment`、`ordinal`
- 单元测试覆盖：Kafka / Doris / HUDI / Hive / FileSystem 样例 DDL

#### 产出
- `FlinkSqlParser.java`
- `FlinkSqlParserTest.java`

---

### CP1-4: TableService 业务层与索引同步 ✅

#### 目标
Table 作为独立业务域，由 `TableService` 负责索引维护；`GitFileService` 保持纯净，不耦合 Table 业务。

#### 任务
- `TableService` 提供内部索引方法：
  - `updateIndex(FileIndexEntity file, String content)`：upsert Table + 先删后插 Columns
  - `deleteByFileId(Long fileId)`：物理删除 Table + 级联 Columns
- 解析失败打 warn 不抛异常，不阻断上层调用
- 单测：mock Mapper 验证 updateIndex/deleteByFileId 调用逻辑

#### 产出
- `TableService.java`（索引同步部分）
- `TableServiceTest.java`（同步逻辑单测）

---

### CP1-5: TableController 与表单模型 ✅

#### 目标
提供 Table 的完整 CRUD 接口和表单模型；Table 创建/更新/删除由 `TableService` 负责，`GitFileService` 仅作为基础文件服务被其引用。

#### 任务
- **文件**: `admin/.../module/table/controller/TableController.java`
- `TableService` 新增 Table CRUD 方法：
  - `createTable(String path, String ddl, String currentUser)`：调用 `GitFileService` 创建 `.ddl` 文件并写索引
  - `updateTable(Long tableId, String ddl, String currentUser)`：按 `tableId` 反查 `fileId`，调用 `GitFileService` 保存 `.ddl` 文件并更新索引
  - `deleteTable(Long tableId, String currentUser)`：按 `tableId` 反查 `fileId`，调用 `GitFileService` 删除文件并级联删除索引
  - `getTable(Long tableId)` / `listTables(...)`：查询表索引
- Controller 接口：
  - `POST /api/tables` → 创建表
  - `PUT /api/tables/{tableId}` → 更新表
  - `DELETE /api/tables/{tableId}` → 删除表
  - `GET /api/tables` → 列表
  - `GET /api/tables/{tableId}` → 详情（含 columns）
  - `GET /api/tables/search?q={keyword}` → 搜索
  - `POST /api/tables/{tableId}/check` → 占位返回空
  - `POST /api/tables/pull` → 占位返回空
- 表单模型：`TableForm`、`ColumnForm`
- VO：`TableVO`、`ColumnVO`、`TableSearchVO`

#### 产出
- `TableController.java`
- `TableService.java`（完整 CRUD + 索引同步）
- `TableForm.java`、`ColumnForm.java`
- `TableVO.java`、`ColumnVO.java`、`TableSearchVO.java`
- 对应单元测试

---

### CP1-6: GitFileService 去耦合与端到端验证 🔄

#### 目标
明确 `GitFileService` 只保留基础文件系统能力；Table 生命周期由 `TableController` + `TableService` 完成，完成 CP1 闭环。

#### 任务
- `GitFileService` 不再注入 `TableService`
- `GitFileService` 只负责：创建、保存、删除、锁定、commit、revert、history、diff 等基础文件操作
- `.ddl` 后缀的特殊业务处理由 `TableService` 承担
- 端到端验证（通过 TableController）：
  1. `POST /api/tables` 创建 `ddl/ods_order.ddl` → DB 出现 Table Index 记录
  2. `PUT /api/tables/{tableId}` 修改内容保存 → Table / Column 记录更新
  3. `DELETE /api/tables/{tableId}` 删除文件 → Table / Column 记录物理删除

#### 产出
- `GitFileService.java`（保持纯净，无 Table 相关调用）
- 端到端验证记录
- CP1 完成，等待 review

---

## CP2: Tables 树可用 ⏳

### 目标
左侧栏「表」图标点击后在 ProjectPanel 中展示 Tables 视图，能浏览 `ddl/` 下的表定义文件，双击 `.ddl` 文件可在编辑器区打开。

### 前置依赖
- `LeftSidebar.tsx` 已存在 Tables 按钮，点击后通过 `devPanels.toggleLeftTop('tables')` 将 `leftTop` 设为 `'tables'`
- `ProjectPanel.tsx` 已接收 `active: LeftTopTab` prop，当前 `active === 'tables'` 只有占位文字
- `FileTree` 组件内部写死了 Header 标题 `"Project"` 和关闭面板回调 `toggleLeftTop('files')`
- 后端 `GET /api/files/tree?parentPath=` 只支持按父路径分层加载，**不支持按根目录 `ddl/` 过滤**

### 当前已知限制
- `FileTree` 依赖全局 `fileTree` model（`treeData`、`expandedKeys`、`selectedNode` 等），Tables 视图与 Files 视图共享同一份状态。
  - 切换两个视图时会保留/覆盖彼此的展开、选中状态。
  - CP2 先接受这一限制，CP4 或后续专项再考虑拆分独立的 `tableTree` model。
- Tables 视图暂时展示**完整文件树**，不只是 `ddl/` 目录；真正的 `ddl/` 根路径过滤需要后端 `tree` 接口增加 `rootPath` 参数，放到 CP2 之后单独做。

### 任务

#### 2.1 参数化 `FileTree`
- **文件**: `web/src/pages/dev/tree/FileTree.tsx`
- 新增 props：
  - `title?: string` — Header 显示标题，默认 `"Project"`
  - `collapseKey?: LeftTopKey` — 关闭面板时调用 `toggleLeftTop(collapseKey)`，默认 `"files"`
- 保持默认行为不变，确保现有 Files 视图不受影响

#### 2.2 新建 `TableTree` 组件
- **文件**: `web/src/pages/dev/tree/TableTree.tsx`
- 复用 `FileTree`，仅传入：
  - `title="Tables"`
  - `collapseKey="tables"`
- 不引入新的 model，先共用 `fileTree`

#### 2.3 `ProjectPanel` 接入 Tables 视图
- **文件**: `web/src/pages/dev/panels/ProjectPanel.tsx`
- `active === 'tables'` 时渲染 `<TableTree />`
- 移除原来的占位 `<div>ProjectPanel（模型区）</div>`

#### 2.4 双击 .ddl 打开编辑器
- 现有 `FileTreeContent` 双击文件调用 `openFile(found)` → `editor.loadFile(fileId)` → `CodeEditor`
- `.ddl` 文件打开后会先走普通文本编辑器；表单编辑器在 CP3 实现，**CP2 不改动 EditorPanel**

### 验收标准
1. 启动前端 dev server，点击左侧「Tables」图标，ProjectPanel 显示标题为 "Tables" 的文件树
2. 可展开 `ddl/` 目录，看到 `.ddl` 文件列表
3. 双击 `.ddl` 文件，能在中央编辑区打开并显示 DDL 文本
4. Tables 视图右上角的「收起面板」按钮能正确关闭 tables 面板
5. 切换回 Files 视图，Files 视图的标题、关闭面板行为保持原样

### 明确不做
- 后端 `tree` 接口增加 `rootPath` / `ddl/` 过滤参数
- 拆分独立的 `tableTree` model
- `.ddl` 文件打开后渲染表单编辑器（CP3 做）

### 产出
- `web/src/pages/dev/tree/FileTree.tsx` — 支持 `title` / `collapseKey` 参数
- `web/src/pages/dev/tree/TableTree.tsx` — Tables 视图专用包装组件
- `web/src/pages/dev/panels/ProjectPanel.tsx` — tables 视图接入

---

## CP3: 表单编辑器（单向：表单 → 文本） ⏳

### 目标
打开 `.ddl` 时默认表单模式，Segmented 可切换到文本模式。

### 任务

#### 3.1 EditorPanel 按扩展名渲染
- **文件**: `web/src/pages/dev/panels/EditorPanel.tsx`
- `activeTab.name.endsWith('.ddl')` → 渲染 `<DdlEditor>`，否则渲染 `<CodeEditor>`

#### 3.2 DdlEditor 容器
- **新建**: `web/src/pages/dev/editor/DdlEditor.tsx`
- Props: `fileId`、`editable`、`initialContent`（DDL 文本）
- 内部 `mode` 状态，默认 `'form'`
- 渲染 Segmented + 对应子组件
- 维护 `rawDdlRef`：文件原始 DDL 文本

#### 3.3 DdlFormEditor 表单组件
- **新建**: `web/src/pages/dev/editor/DdlFormEditor.tsx`
- 四段式布局（基本信息 / 字段定义 / 分区 / 连接器属性）
- 所有样式 `theme.useToken()`
- 表单数据 `useState<DdlFormData>`
- 字段和属性表格可增删行
- 分区字段输入：仅当 connector 为 Hive / FileSystem 时启用；Kafka / Upsert Kafka / JDBC 禁用或隐藏
- 表单底部固定「确定」按钮，点击后调用 `serializeFormToDdl()` 并通过 `editor.saveFile(fileId, content)` 写盘

#### 3.4 DDL 序列化器
- **新建**: `web/src/pages/dev/editor/ddlSerializer.ts`
- `serializeFormToDdl(data: DdlFormData): string`
- 输出 `CREATE TABLE ... (字段) [PARTITIONED BY (...)] WITH (...)` 格式
- 仅 Hive / FileSystem 连接器输出 `PARTITIONED BY` 子句

#### 3.5 保存逻辑
- 表单模式：点击「确定」→ `serializeFormToDdl()` → `editor.saveFile(fileId, content)`
- 文本模式：复用 CodeEditor 1.5s 自动保存
- 表单 → 文本切换：序列化当前 `DdlFormData` → 写入 CodeMirror

### 产出
- `EditorPanel.tsx` — 条件渲染
- `DdlEditor.tsx` — 容器 + Segmented
- `DdlFormEditor.tsx` — 四段式表单
- `ddlSerializer.ts` — 序列化器

---

## CP4: 双向同步（文本 → 表单） ⏳

### 目标
文本模式切换到表单模式时解析 DDL 回填，不丢数据。

### 任务

#### 4.1 DDL 解析器
- **新建**: `web/src/pages/dev/editor/ddlParser.ts`
- `parseDdlToFormData(ddl: string): { data: DdlFormData; warnings: string[] }`
- 正则提取：表名 / 字段列表 / connector / 分区字段 / WITH 属性
- 无法解析部分 → `warnings`

#### 4.2 双向同步
- **修改**: `DdlEditor.tsx`
- 文本 → 表单：取当前 CodeMirror 文本内容 → `parseDdlToFormData(currentContent)` → 回填表单
- 表单 → 文本：`serializeFormToDdl(formData)` → 覆盖 CodeMirror
- `rawDdlRef` 仅作为首次加载的原始内容备份，切换时以当前编辑器内容为准

#### 4.3 冲突提示
- **修改**: `DdlFormEditor.tsx`
- `warnings` 非空时，顶部展示 warning banner

### 产出
- `ddlParser.ts` — DDL 解析器
- `DdlEditor.tsx` — 双向同步逻辑
- `DdlFormEditor.tsx` — warning 展示
