# TableFormEditor 重做计划：对照 Gemini 设计目标落地

> 记录目标来源：Gemini 提出的 Flink SQL `CreateTable` 表单设计原则——
> “结构化降低认知负荷、列语义可视化、智能联动、双模态无损切换、焦点稳定、效率舒适”。
> 本计划用于做完后回头验收。

## 0. 现有基线

| 层级 | 现状 | 是否可复用 |
|------|------|-----------|
| 后端解析/序列化 | `FlinkSqlParser` 已支持 physical / metadata / computed / watermark / PK / distribution / partition / comment；`TableController` 已提供 `/utils/deserialize`、`/utils/serialize` | ✅ 直接用 |
| 前端类型 | `web/src/types/table.d.ts` 已定义 `FlinkTableVO`、`FlinkColumnVO` 等 | ✅ 直接用 |
| 文件保存 | `.ddl` 文件保存/删除走 `FileController`；表级保存已有 `saveTable(fileId, ddl)` | ✅ 直接用 |
| 双模态容器 | `TableEditor/index.tsx` 已有 `mode` 和 Segmented 切换，但结构较乱 | 🔄 重构 |
| 表单组件 | `TableFormEditor.tsx` 已基本清空 | 🔄 重写 |

**关键约束（来自项目历史决策）：**

- DDL 语法统一为 **Flink SQL**：`CREATE TABLE (...) WITH (...)`。
- 表单模式**没有自动保存**，必须点击「确定」后序列化写盘。
- 文本模式保留 **1.5s 自动保存**。
- `lanting_table_index` / `lanting_column_index` 只存**表名、连接器类型、字段列表**，不存 `WITH` 属性；属性校验直接读磁盘 DDL 解析。
- Source/Sink 双用途表的三段配置（Common / Source / Sink）全部写入同一个 `WITH` 子句。
- 分区字段 `PARTITIONED BY` 仅对 **Hive / FileSystem** 连接器启用；Kafka / Upsert Kafka / JDBC 不支持。
- `GitFileService` 保持纯净；Table 的创建/更新/删除/查询由 `TableController` + `TableService` 负责。

## 1. 设计目标 → 实施策略 → 验收标准

### 1.1 结构化模块分层（降低认知负荷）

**Gemini 目标：** 按语义拆分为清晰模块，避免冗长堆叠。

**实施策略：**

- 采用 **六段式布局**（比原设计稿更细，符合 Flink 语法顺序）：
  1. 基本信息（表名、连接器、注释）
  2. 字段定义（物理 / 元数据 / 计算列）
  3. PRIMARY KEY
  4. WATERMARK
  5. DISTRIBUTED BY
  6. PARTITIONED BY（仅 Hive / FileSystem）
  7. 连接器属性 WITH（Common / Source / Sink 分组）
- 每段一个独立组件（`BasicInfoSection`、`ColumnsSection`、`PrimaryKeySection`…），各自 `React.memo`。
- Section header 吸顶（sticky），支持折叠，默认展开。
- 长段内部再分组：连接器属性按 `Common / Source / Sink` 三个小表格呈现，对应三段式配置。

**验收标准：**

- 打开任意 DDL，用户能在 3 秒内定位到想编辑的段。
- 段与段之间视觉边界清晰，不使用大量分割线。

### 1.2 列语义与类型的可视化区分

**Gemini 目标：** 用 Badge 区分物理列 / 元数据列 / 计算列；计算列只读引导。

**实施策略：**

- 字段表每行展示 `Badge`：
  - `physical` — 默认无 badge 或灰色
  - `metadata` — 蓝色
  - `computed` — 橙色 + 只读
- `metadata` 行扩展出 `METADATA FROM` 输入和 `VIRTUAL` 开关。
- `computed` 行禁用编辑，hover 提示“计算列请切换文本模式编辑”。
- 行操作：添加、删除、拖拽排序。为控制性能，**不使用重型 DnD 库**，用原生 HTML5 Drag & Drop 或简单的上下箭头。

**验收标准：**

- 含计算列的 DDL 打开后，计算列原文原样展示，表单仍可保存其他部分。
- Badge 显示与后端 `columnType` 一致。
- 删除字段自动处理逗号，无需用户手动检查。

### 1.3 智能联动与预置填充

**Gemini 目标：** 连接器切换自动推荐属性；Watermark / PK / 分区字段选择器只列出已有物理列。

**实施策略：**

- 新增 `web/src/pages/dev/components/TableEditor/constants/connectorTemplates.ts`：
  - 每个连接器定义 `commonKeys`、`sourceKeys`、`sinkKeys`、`defaultProps`。
- 切换连接器时：
  - 若 `properties` 为空，自动填入 `defaultProps` 的 key（value 留空或给出默认值）。
  - 若已有属性，提供「补全推荐属性」按钮，避免覆盖用户已填内容。
- Watermark 字段、PK 字段、Partition 字段选择器使用 `useMemo` 过滤出当前物理列名称。
- `PARTITIONED BY` 段仅在 `connector` 为 `hive`、`filesystem` 时渲染；其他连接器隐藏并清空已选分区。

**验收标准：**

- 选择 `kafka` 后，属性表自动出现 `connector`、`topic`、`format`、`scan.startup.mode`、`sink.partitioner` 等推荐 key。
- 删除一个被 Watermark 引用的字段时，自动清空 Watermark 或给出错误提示。

### 1.4 双模态无缝切换（表单 ↔ 文本）

**Gemini 目标：** 共享容器、无损 AST 双向同步、容错 Warning。

**实施策略：**

- `TableEditor` 维护 `mode: 'form' | 'text'`，**默认 `'form'`**（当前默认 text，需要改）。
- 表单 → 文本：
  - 调用 `createTableToString(formData)` 生成 DDL。
  - 更新 `editorStore` 当前文件内容，交给 `CodeEditor` 接管，触发其 1.5s 自动保存。
  - 未点「确定」的修改也带入文本模式。
- 文本 → 表单：
  - 调用 `stringToCreateTable(currentContent)` 解析。
  - 回填 `formData`；解析 warnings 展示在表单顶部 Warning Banner。
  - 计算列、复杂表达式保留在 `columns` 中但只读。
- 保存路径：
  - 表单模式：点「确定」→ `createTableToString` → `saveTable(fileId, ddl)`。
  - 文本模式：走 CodeEditor 自动保存 → `editor.saveFile`。
- `rawDdlRef` 仅作为初始加载失败时的回退，切换时以当前内存状态为准。

**验收标准：**

- 任意合法 DDL 在表单和文本之间来回切换 5 次，内容逐字符一致。
- 含计算列的 DDL，切换后计算列仍在原位置、原表达式不变。
- 非法/无法解析的 DDL 切换时给出 Warning，不崩溃、不丢失原文。

### 1.5 焦点稳定与渐进式呈现

**Gemini 目标：** Hover 仅改背景、辅助元素弱化、减少视觉噪音。

**实施策略：**

- 字段表行 hover 只改变背景色；删除/排序按钮默认半透明，hover 行时提高对比度。
- 输入框统一 `size="small"`，紧凑对齐。
- 不全局使用 antd `Form` 的自动校验触发，避免整表重渲染；每段内部用受控组件 + 独立 `onBlur` 校验。
- 错误提示 Inline：在字段行尾部或输入框下方显示，避免顶部 Banner 包揽所有错误。

**验收标准：**

- 鼠标在字段表上来回移动时，文字不跳动、不闪动。
- 删除按钮不会因常驻而干扰阅读。

### 1.6 效率不弱于 CodeEditor

**Gemini 目标 + 明确要求：** 表单交互不能比 CodeMirror 慢。

**实施策略：**

- **状态最小化且局部化**：
  - `TableEditor` 持有完整 `formData: FlinkTableVO`。
  - 字段/属性更新时只重建被修改的对象，未变更的行对象引用保持不变。
  - 字段表行使用业务无关的本地 `__id` 作为 `key`（不依赖 index 或 name），保证重命名时 React 不会误卸载。
- **渲染范围控制**：
  - 行组件 `ColumnsTableRow`、`PropRow` 用 `React.memo`。
  - Section 组件用 `React.memo`，未变更段不重新渲染。
- **expensive 操作非阻塞**：
  - 模式切换时的序列化/解析用 `React.startTransition`。
  - 不使用重型拖拽库。
- **性能指标**：
  - 单字段输入：仅当前输入框 + 当前行重渲染。
  - 50 个字段模式切换 < 100ms。
  - 序列化 30 个字段 < 5ms。

**验收标准：**

- React DevTools Profiler：输入字段名时，只有当前 `ColumnsTableRow` 高亮。
- 手动测试 50 字段 DDL，表单 ↔ 文本切换无明显卡顿。

## 2. 组件与文件结构

```
web/src/pages/dev/components/TableEditor/
├── index.tsx                          # 容器：mode、parse/serialize、save、dirty
├── TableFormEditor/
│   ├── index.tsx                      # 表单外壳：sections、warning、footer
│   ├── sections/
│   │   ├── BasicInfoSection.tsx
│   │   ├── ColumnsSection.tsx
│   │   ├── ColumnsTableRow.tsx        # memoized
│   │   ├── PrimaryKeySection.tsx
│   │   ├── WatermarkSection.tsx
│   │   ├── DistributedSection.tsx
│   │   ├── PartitionSection.tsx
│   │   └── ConnectorPropsSection.tsx
│   │   └── PropRow.tsx                # memoized
│   ├── constants/
│   │   └── connectorTemplates.ts
│   └── hooks/
│       └── useTableForm.ts            # 状态更新工具（局部更新 + dirty）
├── tableUtils.ts                      # 已有，保留扩展位
```

## 3. 数据模型

直接复用 `FlinkTableVO`，前端仅增加本地辅助字段：

```ts
type FormColumn = FlinkColumnVO & { __id: string };
type FormData = Omit<FlinkTableVO, 'columns'> & { columns: FormColumn[] };
```

- `__id` 仅用于 React `key` 和行定位，序列化前剔除。
- `properties` 保持 `Record<string, string>`，UI 层转为 `Array<{ __id: string; key: string; value: string }>` 便于表格编辑。

## 4. 实施顺序

| 阶段 | 内容 | 产出 | 验证 |
|------|------|------|------|
| M1 | 空骨架 + 状态层 + 六段占位 + 模式切换 | `TableFormEditor` 骨架 | 能切换模式，dirty 正确传递 |
| M2 | 基本信息 + 字段表（增删改、badge、排序、memo） | `ColumnsSection` | Profiler 验证行级渲染 |
| M3 | PK / WATERMARK / DISTRIBUTED / PARTITION | 高级段组件 | 联动筛选物理列正确 |
| M4 | 连接器属性表 + 模板推荐 + Common/Source/Sink 分组 | `ConnectorPropsSection` | 切换 connector 自动推荐 key |
| M5 | 接入 `/utils/deserialize` + `/utils/serialize` + Warning 展示 | 双模态同步 | 来回切换 5 次内容一致 |
| M6 | 性能优化 + 样式统一 + 验收测试 | 最终代码 | 通过验收清单 |

## 5. 验收清单（做完后用 Gemini 的目标回头验证）

- [ ] 能完整表达本次支持的 DDL 子句，不支持的语法只读展示 + Warning。
- [ ] 表单 ↔ 文本切换零数据丢失，字段顺序、注释、watermark、PK、partition、WITH 属性保持一致。
- [ ] 选择连接器后自动出现该连接器常用属性 key。
- [ ] Watermark / PK / Partition 字段选择器只列出当前物理列。
- [ ] 输入单字段时不触发整表重渲染。
- [ ] 50 字段 DDL 模式切换无明显卡顿。
- [ ] 表单模式点「确定」才写盘；文本模式保留自动保存。
- [ ] 计算列等表单无法编辑的语法不会被静默改写。

## 6. 明确不做

- 不实现 `LIKE` / `AS SELECT`（CTAS / RTAS）。
- 不实现计算列的表达式编辑（只读展示）。
- 不新增后端 parser 能力（现有能力已覆盖本次需求）。
- 不引入 `react-hook-form` 等表单库，保持受控组件 + 自定义状态。
