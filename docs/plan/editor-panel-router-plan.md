# EditorPanel 按文件类型路由改造 — 实施计划

> 2026-07-31 · 对齐主流 IDE：Tab 全局、编辑区按类型路由、模态状态在编辑器内部、只读是编辑器层 prop。
> 前置文档：`docs/plan/table-implementation-plan.md`（CP3/CP4 DDL 编辑器设计）、`docs/backend/editor-tables-design.md`（双模态设计）、`docs/frontend/design/table-editor-design.html`（UI 稿）。

---

## 背景

`panels/EditorPanel.tsx` 当前硬编码渲染 `CodeEditor`，无法按文件类型渲染不同编辑器。
目标：改成「按 activeTab.fileType 路由」的容器，支持 DDL 双模态（Form/Text）、MD 双模态（Preview/Text，占位）。

---

## 设计

### 路由模型

```
EditorPanel（容器：FileTabs + ReadOnlyBanner + 路由）
│
└── activeTab.fileType → 编辑器组件
    ├── 'ddl' → <DdlEditor />          // Form/Text 双模态（本次实现）
    ├── 'md'  → <MarkdownEditor />     // Preview/Text（本次骨架）
    └── 其他  → <CodeEditor />         // 默认文本（现状）
```

### 关键决策（对齐 IDE + 沿用历史设计）

| 决策 | 内容 | 来源 |
|------|------|------|
| Tab 全局 | FileTabs 归属 editor 模块，所有类型共用 | IDE 模式 |
| 路由依据 | `activeTab.fileType`（FileTreeNode 扩展名） | 现状已有 |
| 模态在内部 | DdlEditor 自己管 Form/Text 状态（useState），容器不感知 | IDE 模式 |
| 只读是 prop | 每个编辑器接收 `readonly`，自己实现只读表现 | IDE 模式 |
| 默认模态 | DDL 打开默认表单模式 | table-implementation-plan CP3 |
| 保存 | 表单点「确定」serializeFormToDdl → saveFile；文本 1.5s 自动保存 | 历史设计 |
| 切换同步 | 表单→文本序列化；文本→表单解析（CP4 双向同步，本次先单向） | 历史设计 |

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
1. **Tabs 管理**：FileTabs 渲染所有打开 tab，切换/关闭（全局，所有类型共用）
2. **管理 `readonly` state**：从锁状态计算 `readonly`，作为 prop 传给编辑器；**不负责**告诉编辑器如何渲染只读
3. **内容挂载**：按 `activeTab.fileType` 路由，决定挂哪个 Editor Component；不关心组件内部格式

### Editor Components（各自负责「内容」）
1. 渲染内容
2. 渲染成不同格式（CodeEditor 文本 / DdlFormEditor 表单 / MarkdownPreview 预览）
3. 格式间转换（DdlEditor 内部 Form↔Text 序列化/解析）
4. **根据 `readonly` prop 自行决定只读表现**（CodeEditor → editable=false；DdlFormEditor → 字段禁用；MarkdownPreview → 天然只读）

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
      <FileTabs ... />                          // 职责1：Tabs（不变）
      {activeTab && readonly && <ReadOnlyBanner ... />}  // 职责2：锁提示（不变）
      {openTabs.length === 0 ? <Empty /> : (      // 职责3：挂载（新增路由）
        activeTab?.fileType === 'ddl'
          ? <DdlEditor fileId readonly content />
          : activeTab?.fileType === 'md'
            ? <MarkdownEditor fileId readonly content />
            : <CodeEditor ref={codeEditorRef} ... />   // 默认文本
      )}
    </div>
  );
};
```

- 现状的 FileTabs / ReadOnlyBanner / CodeEditor 组装逻辑**原地保留**（Tabs + 锁管理不变）
- 只把内容区从「固定 CodeEditor」改为「按 fileType 路由」
- `editor/index.tsx` **保持导出 CodeEditor**（默认文本编辑器），不需要新增 EditorContent

### 2. `components/editor/DdlEditor.tsx`（新）— DDL 双模态容器

- 参考 table-implementation-plan CP3.2：`fileId` / `readonly` / `content` props
- `mode: 'form' | 'text'` state，默认 `'form'`
- Segmented `[表单 | 文本]` 切换
- 表单→文本：`serializeFormToDdl(formData)` 写入文本（单向，CP4 再做双向）
- 文本模式复用 CodeEditor

### 4. `components/editor/ddlSerializer.ts`（新）

- `serializeFormToDdl(data: DdlFormData): string`
- 输出 `CREATE TABLE ... (字段) [PARTITIONED BY (...)] WITH (...)`（参考 CP3.4）

### 5. `components/editor/DdlFormEditor.tsx`（新）

- 四段式：基本信息 / 字段定义 / 分区 / 连接器属性（参考 CP3.3）
- `DdlFormData { tableName, connector, fields[], partitionField, properties[] }`
- 「确定」→ serializeFormToDdl → `saveFile(fileId, content)`
- `readonly=true` 字段禁用

### 6. `components/editor/MarkdownEditor.tsx`（新，占位）

- Segmented `[预览 | 文本]` + 占位内容（本次不实现渲染）

---

## 实施步骤

| Step | 内容 | 产出 |
|------|------|------|
| 1 | editor/index.tsx 保持导出 CodeEditor（默认文本编辑器） | index.tsx（无改动） |
| 2 | EditorPanel 内容区改按 fileType 路由 | EditorPanel.tsx |
| 3 | DdlEditor 容器（Segmented + mode state） | DdlEditor.tsx |
| 4 | ddlSerializer | ddlSerializer.ts |
| 5 | DdlFormEditor 四段表单 | DdlFormEditor.tsx |
| 6 | MarkdownEditor 占位 | MarkdownEditor.tsx |
| 7 | tsc + 手动验证 | — |

---

## 明确不做（本次）

- MD Preview 实际渲染（只建骨架）
- DDL 文本→表单解析回填（CP4 双向同步，后续）
- 多 tab 模态状态持久化（按 fileId 记忆 mode，可后续）
- DDL 检查 / Pull from DB（后端占位，前端未接）

---

## 验证

1. 打开 `.sql` 文件 → 行为与现状一致（CodeEditor）
2. 打开 `.ddl` 文件 → DdlEditor 默认表单模式，Segmented 可切文本
3. 表单编辑 → 点确定 → 保存为 DDL 文本；切文本看到序列化结果
4. 打开 `.md` → MarkdownEditor 骨架（Segmented 可用，内容占位）
5. 锁感知：只读时表单字段禁用 / 文本只读
6. `tsc --noEmit` 通过
