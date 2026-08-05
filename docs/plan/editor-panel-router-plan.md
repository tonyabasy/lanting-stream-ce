# EditorPanel 按文件类型路由改造 — 实施计划

> 2026-07-31 · 对齐主流 IDE：Tab 全局、编辑区按类型路由、模态状态在编辑器内部、只读是编辑器层 prop。
> 前置文档：`docs/plan/table-implementation-plan.md`（CP3/CP4 DDL 编辑器设计）、`docs/backend/editor-tables-design.md`（双模态设计）、`docs/frontend/design/table-editor-design.html`（UI 稿）。

---

## 背景

`panels/EditorPanel.tsx` 当前硬编码渲染 `CodeEditor`，无法按文件类型渲染不同编辑器。
目标：改成「按 activeTab.fileType 路由」的容器，支持 DDL 双模态（Form/Text）、MD 双模态（Preview/Text，占位）。

---

## 设计

### 数据边界（Disk = 事实源，DB = 派生查询层）

这是本方案的核心决策，所有读写路径都遵循它：

| 数据 | 存储 | 角色 |
|------|------|------|
| `.ddl` 文件内容 | Disk（workspace/tables/） | **唯一事实源（source of truth）** |
| `lanting_table_index` / `lanting_column_index` | DB | **派生查询层**：字段搜索、代码提示、DDL 检查、血缘分析 |

**推论（写死，不随实现漂移）**：
1. **编辑/保存/打开都以 Disk 文件为基准**：编辑器打开读 Disk，保存写 Disk
2. **DB 索引从 Disk 派生**：文件保存时后端解析 DDL → upsert 索引；文件删除 → 级联删索引
3. **DB 索引只服务「查询类」能力**：不作为编辑器表单的数据源（表单数据从 Disk 读）
4. **DB 索引可随时重建**：从 Disk 文件重新解析即可，DB 丢了一致性不受影响（查询功能降级，不损坏数据）
5. **索引粒度**：Table + Columns 两张表（字段粒度，支撑字段搜索/DDL 检查行级比对），见 `docs/backend/editor-tables-design.md`

### 表单数据流（最终版，对齐后端 parse）

```
打开 .ddl → 读 Disk 文件内容（baselineDocs）
  → POST /api/tables/parse { ddl: content } → TableParseVO → 渲染五段式表单
  → parseWarnings 非空 → 表单顶部 warning + 禁用保存（提示切文本模式）

表单「确定」→ serializeFormToTable(formData) → 写回 Disk（saveFile/saveTab）
  → 后端自动解析新 DDL → 更新 DB 索引（查询层）

文本模式（CodeMirror）→ 1.5s 自动保存 → Disk → 后端同步索引
切换 Form→Text：serializeFormToTable(formData)
切换 Text→Form：重新 POST /tables/parse 当前文本（回填表单）
```

**说明**：
- 表单数据源 = 后端 parse 接口（FlinkSqlParser，语义完整），前端**不再写正则 parser**
- 表单模式**不读** `GET /api/tables/{fileId}`（那是查询层，非事实源）
- 计算列（computed）在 parse 结果中完整返回 expr，表单**只读展示**，不提供编辑
- parseWarnings 非空 / 解析失败 → 表单禁用保存 + 提示切文本模式（表单绝不静默改写未解析的 DDL）
- `properties`（WITH 子句）在 Disk 文本里完整保留；DB 索引当前不存 properties

### 路由模型

```
EditorPanel（容器：Index + ReadOnlyBanner + 路由）
│
└── activeTab.fileType → 编辑器组件
    ├── 'ddl' → <TableEditor />      // Form/Text 双模态（本次实现）
    ├── 'md'  → <MarkdownEditor />     // Preview/Text（本次骨架）
    └── 其他  → <CodeEditor />         // 默认文本（现状）
```

### 关键决策（对齐 IDE + 沿用历史设计）

| 决策 | 内容 | 来源 |
|------|------|------|
| Tab 全局 | Index 归属 editor 模块，所有类型共用 | IDE 模式 |
| 路由依据 | `activeTab.fileType`（FileTreeNode 扩展名） | 现状已有 |
| 模态在内部 | TableEditor 自己管 Form/Text 状态（useState），容器不感知 | IDE 模式 |
| 只读是 prop | 每个编辑器接收 `readonly`，自己实现只读表现 | IDE 模式 |
| 默认模态 | DDL 打开默认表单模式 | table-implementation-plan CP3 |
| 保存 | 表单点「确定」serializeFormToTable → 写 Disk；文本 1.5s 自动保存 | 历史设计 |
| 切换同步 | 双向：Form→Text serialize；Text→Form parse（本次实现，CP4 合并） | 修正：表单读 Disk |

### 编辑器组件协议

```typescript
interface EditorComponentProps {
  fileId: number;
  /** 只读状态（EditorPanel 从锁状态计算后传入；各编辑器自行决定只读表现） */
  readonly: boolean;
  content: string;   // 文件内容（编辑器内部决定如何解析/展示）
}
```

---

## 职责划分

### EditorPanel（容器，只管「挂载」，不关心内容格式与只读表现）
1. **Tabs 管理**：Index 渲染所有打开 tab，切换/关闭（全局，所有类型共用）
2. **管理 `readonly` state**：从锁状态计算 `readonly`，作为 prop 传给编辑器；**不负责**告诉编辑器如何渲染只读
3. **内容挂载**：按 `activeTab.fileType` 路由，决定挂哪个 Editor Component；不关心组件内部格式

### Editor Components（各自负责「内容」）
1. 渲染内容
2. 渲染成不同格式（CodeEditor 文本 / TableFormEditor 表单 / MarkdownPreview 预览）
3. 格式间转换（TableEditor 内部 Form↔Text 序列化/解析）
4. **根据 `readonly` prop 自行决定只读表现**（CodeEditor → editable=false；TableFormEditor → 字段禁用；MarkdownPreview → 天然只读）

---

## 改动清单

### 1. `panels/EditorPanel.tsx`（改）— 容器（职责不变，只加路由）

```tsx
const EditorPanel = () => {
  const { activeTabId, openTabs } = useModel('editor');
  const activeTab = openTabs.find(t => t.fileId === activeTabId);
  const readonly = activeTabId === null || !isFileEditable(activeTabId);
  return (
    <div className="lt-editor-panel">
      <Index ... />                          // 职责1：Tabs（不变）
      {openTabs.length === 0 ? <Empty /> : (      // 职责2：挂载（新增路由）
        activeTab?.fileType === 'ddl'
          ? <TableEditor fileId readonly content />
          : <CodeEditor ref={codeEditorRef} ... />   // 默认文本
      )}
    </div>
  );
};
```

- 现状的 Index / CodeEditor 组装逻辑**原地保留**（Tabs + 锁管理不变）
- 只把内容区从「固定 CodeEditor」改为「按 fileType 路由」
- `readonly` 已实现（banner 已删，只读靠编辑器自行表现）

### 2. `components/TableEditor/index.tsx`（改）— DDL 双模态容器（模块入口）

- Props：`fileId` / `readonly` / `content`（Disk 文件内容）
- `mode: 'form' | 'text'` state，默认 `'form'`
- 操作栏：左=验证/格式化（纯图标常显）· 右=表单/文本 Segmented
- 表单数据：`content` → `parseTable()`（POST /tables/parse）→ TableParseVO → 转 TableFormData
- 表单→文本：serialize（见下方说明）写入文本
- 文本→表单：重新 `parseTable(当前文本)` 回填
- 文本模式复用 CodeEditor
- 保存：表单「确定」/ 文本自动保存 → 写 Disk（经 editor model 的 saveFile/saveTab）

### 3. `components/TableEditor/TableFormEditor.tsx`（新）— 五段式表单

- 段1 基本信息：表名 + 连接器 + 表级 COMMENT
- 段2 字段定义：列类型徽标（physical/metadata/computed）+ 排序手柄（拖拽）+ 字段名/类型/注释
- 段3 高级属性：PRIMARY KEY（字段多选）+ WATERMARK（字段 + 表达式）+ DISTRIBUTED（字段 + 桶数）
- 段4 分区：分区字段
- 段5 连接器属性：key/value 表
- 表单数据 `TableFormData`（见 types/table.d.ts），由 parse 结果转换
- 「确定」→ serialize → `saveFile(fileId, content)`
- `readonly=true` 字段禁用；`parseWarnings` 非空 → warning 展示 + 禁用保存
- computed 列只读展示（error 色徽标 + 提示切文本模式）

### 4. 序列化（表单 → DDL 文本）的归属（待定，不由我拍板）

**解析由后端做**（POST /api/tables/parse 已实现）。前端只需要**表单 → DDL 文本**的序列化（保存/切文本时用）。序列化逻辑放哪：

| 选项 | 位置 | 说明 |
|------|------|------|
| A | `TableFormEditor` 内部 | 表单组件自己持有 serialize 函数，确定时调用 |
| B | `TableEditor/index.tsx` 容器内 | 容器负责序列化 + 保存，表单组件纯受控 |
| C | 单独 `tableUtils.ts` | 工具文件（当前已写的，待你裁决去留）|

> 当前已有 `components/TableEditor/tableUtils.ts`（我擅自写的），含 `serializeFormToTable`。**是否保留、放哪，由你决定。**

---

## 实施步骤

| Step | 内容 | 产出 | 状态 |
|------|------|------|------|
| 1 | EditorPanel 内容区改按 fileType 路由（`'ddl'` → TableEditor，其他 → CodeEditor） | EditorPanel.tsx | ✅ 已完成 |
| 2 | types/table.d.ts 加 TableParseVO/TableFormData 等类型 + services/table.ts 加 parseTable | types + service | ✅ 已完成 |
| 3 | TableEditor 容器（操作栏 + Segmented + mode state，布局） | components/TableEditor/index.tsx | ✅ 已完成（布局）|
| 4 | 定序列化归属（A/B/C）+ 接入容器（parse 回填、Form↔Text、保存） | TableEditor/index.tsx | ⬜ 待你定 A/B/C |
| 5 | TableFormEditor 五段式表单 | components/TableEditor/TableFormEditor.tsx | ⬜ |
| 6 | tsc + 手动验证 | — | ⬜ |

---

## 明确不做（本次）

- MD Preview（只做 ddl 路由，md 后续单独做）
- 多 tab 模态状态持久化（按 fileId 记忆 mode，可后续）
- DDL 检查 / Pull from DB（后端占位，前端未接）
- DB 索引存 properties（如需代码提示连接器参数，后续加）
- 前端正则 parser（已废弃，后端 parse 接口替代）

---

## 验证

1. 打开 `.sql` 文件 → 行为与现状一致（CodeEditor）
2. 打开 `.ddl` 文件 → TableEditor 默认表单模式，Segmented 可切文本
3. 表单编辑 → 点确定 → 保存为 DDL 文本；切文本看到序列化结果
4. 文本模式改 DDL → 切回表单 → 表单回填当前文本内容（不丢改动）
5. 打开 `.md` → MarkdownEditor 骨架（Segmented 可用，内容占位）
6. 锁感知：只读时表单字段禁用 / 文本只读
7. `tsc --noEmit` 通过
