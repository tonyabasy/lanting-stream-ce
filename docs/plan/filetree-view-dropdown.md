# FileTree 标题下拉视图切换 — 实施计划（v2）

> 2026-07-30 · 仿 IDEA：文件树面板标题变成下拉菜单，切换 Workspace / Project / Tables 视图。左侧 Tables 图标删除。

---

## 设计决策（已确认）

| 决策 | 内容 |
|------|------|
| 下拉选项 | Workspace / Project / Tables 三个视图（Changes 单独模块，不进来） |
| 左侧栏 | 删除 Tables 图标；上组剩 Project + 变更，下组终端 + Git（共 4 个按钮） |
| 状态分层 | `leftTop` 管「哪个面板」；视图状态存 **fileTree model**，两者分离 |
| 视图类型 | `FileTreeViews` 常量（含 rootPath/title 配置）放 types 目录 |
| 视图切换 | 下拉选中 → `switchTreeView(key)`：查表 + 更新状态 + 拉取对应根路径 |
| **加载职责划分** | `switchTreeView(key)`：视图切换（整体替换 + 重置状态）；`loadTree(path)`：懒加载展开 / CRUD 局部刷新（保留状态）。两个都是加载动作，职责不同，并存 |
| 收起面板 | 行为固定 = 收起整个左栏（`setLeftTop(null)`），**无需 collapseKey 参数** |
| 展开视图记忆 | view 状态在 fileTree model（全局单例），面板收起再展开保持选择 |

---

## 状态模型

```typescript
// types/file.d.ts — 视图配置（对应 Java: interface FileTreeViewProp + Map<String, FileTreeViewProp>）
export interface FileTreeViewProp {
  /** 视图 key（唯一标识，状态存这个） */
  key: string;
  /** 数据源根路径 */
  rootPath: string;
  /** Header 标题 */
  title: string;
}

/** 视图 key 联合类型：'workspace' | 'project' | 'tables' */
export type FileTreeViewKey = keyof typeof FileTreeViews;

/** 视图配置表：key → 视图属性（对应 Java 的 Map<String, FileTreeViewProp>） */
export const FileTreeViews: Record<string, FileTreeViewProp> = {
  workspace: { key: 'workspace', rootPath: '', title: 'Workspace' },
  project:   { key: 'project',   rootPath: 'project/', title: 'Project' },
  tables:    { key: 'tables',    rootPath: 'tables/', title: 'Tables' },
};
```

```typescript
// models/devPanels.ts — 只留面板类型
export type LeftTopTab = 'files' | 'changes' | null;   // 去掉 tables
export type LeftTopKey = NonNullable<LeftTopTab>;
```

```typescript
// models/fileTree.ts — 保存当前视图 key + 切换
// 强制约束：树加载唯一入口是 switchTreeView（视图语义，接收视图 key），不允许按裸路径加载。
const [viewKey, setViewKey] = useState<string>('project');

/** 切换视图：查表 + 更新 key + 拉取对应根路径（树加载唯一入口） */
const switchTreeView = useCallback(async (key: string) => {
  const view = FileTreeViews[key];
  if (!view) return;
  setViewKey(key);
  setLoading(true);
  try {
    const nodes = await tree(view.rootPath);
    setTreeData(nodes.map((node) => toTreeDataNode(node)));
    setExpandedKeys([]);
    setLoadedKeys([]);
    setSelectedNode(null);
    setSearchQuery('');
    setSearchResults([]);
  } finally {
    setLoading(false);
  }
}, []);
```

---

## 改动清单

### 1. `types/file.d.ts`（改）
- 新增 `FileTreeViewProp` 接口 + `FileTreeViews` 配置表（key → rootPath/title）

### 2. `models/devPanels.ts`（改）
- `LeftTopTab` 去掉 `'tables'`（`'files' | 'changes' | null`）

### 3. `models/fileTree.ts`（改）
- 新增 `viewKey` state（默认 `'project'`）+ `switchTreeView(key)` 方法（查表 + 更新 key + 整体加载）
- **`loadTree` 保留**：承担懒加载展开子目录、CRUD 后局部刷新（局部更新指定目录 children，保留展开/选中）
- **职责划分**：`switchTreeView`（视图切换，整体替换 + 重置状态）与 `loadTree`（局部操作，保留状态）并存，互不替代
- return 增加 `viewKey`、`switchTreeView`；`loadTree` 保持返回

### 4. `leftSidebar/LeftSidebar.tsx`（改）
- 删除 Tables 按钮（`IconTable`），清 import
- 上组剩 Project + 变更

### 5. `panels/ProjectPanel.tsx`（改）
```tsx
const ProjectPanel = ({ active }) => {
  if (!active) return <div className="lt-panel-base" />;
  if (active === 'changes') return <ChangesPlaceholder />;  // 变更占位（单独做）
  const { view } = useModel('fileTree');
  return <FileTree view={view} />;
};
```
- 不再传 rootPath/title（FileTree 内部从 FileTreeViews 查）

### 6. `tree/FileTree.tsx`（改）
```tsx
interface FileTreeProps {
  /** 当前视图（下拉选中的值） */
  view: FileTreeViewKey;
}

const FileTree = ({ view }: FileTreeProps) => {
  const { switchTreeView } = useModel('fileTree');
  useEffect(() => { switchTreeView(view); }, [view]);  // 视图变化 → 拉对应根
  const config = FileTreeViews[view];
  return (
    <FileTreeHeader
      view={view}
      title={config.title}
      ...
      onCollapsePanel={() => setLeftTop(null)}   // 收起整个左栏
    />
    ...
  );
};
```
- 删除 `rootPath` / `title` / `collapseKey` props（view 推导）
- 收起按钮改 `setLeftTop(null)`

### 7. `tree/FileTreeHeader.tsx`（改）
```tsx
interface FileTreeHeaderProps {
  /** 当前视图 */
  view: FileTreeViewKey;
  /** 视图下拉选项（Workspace/Project/Tables，带 icon） */
  viewOptions: MenuProps['items'];
  /** 下拉选中视图 */
  onViewChange: (view: FileTreeViewKey) => void;
  onRefresh: () => void;
  onAddFile: () => void;
  onAddFolder: () => void;
  onCollapseAll: () => void;
  onCollapsePanel: () => void;  // 收起整个左栏
}
```
- title → Dropdown（当前视图名 + 箭头，点开三视图带 icon）
- 收起按钮无参数

### 8. `pages/dev/index.tsx`（无需改）
- `ProjectPanel active={leftTop}` 逻辑不变

---

## 实施步骤

### Step 1: types/file.d.ts
- FileTreeViews 常量 + FileTreeViewKey 类型

### Step 2: devPanels
- LeftTopTab 去掉 tables

### Step 3: fileTree model
- viewKey state + switchTreeView

### Step 4: 左侧栏删 Tables 图标

### Step 5: ProjectPanel + FileTree
- ProjectPanel 传 view；FileTree 用 view 推导，去 rootPath/title/collapseKey

### Step 6: Header 下拉
- title → Dropdown 视图切换

### Step 7: 验证
- 下拉切三视图，rootPath/title 正确
- 左侧栏 4 按钮（Project/变更/终端/Git）
- 收起 → 左栏收起；再开 → 视图保持上次选择
- `tsc --noEmit` 通过

---

## 明确不做

- Changes 面板（单独模块，后续做）
- Workspace 与 Project 的隐藏文件过滤差异（当前 rootPath 相同，差异后续定义）
