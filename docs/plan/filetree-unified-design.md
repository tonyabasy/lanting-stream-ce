# FileTree 统一数据源重构 — 概要设计 + 实施计划

> 2026-07-30 · 方向已定：treeData 全局一份，视图 = 根路径 + title，文件类型路由操作。

---

## 一、核心设计

### 1.1 数据流总览

```
后端文件树 API（GET /api/files/tree?parentPath=，含虚拟目录分组）
        │
        ▼
全局 treeModel（models/fileTree.ts 扩展，一份 treeData）
        │
        ├── 视图切换 → 重拉当前视图根路径 → treeData 整体替换
        ├── CRUD 后   → 局部刷新操作节点的父目录（updateTreeData）
        └── 懒加载    → 展开时加载子节点（loadData）
        │
        ▼
FileTreeContent / FileTreeHeader（全局唯一，UI 不变，前端不感知虚拟目录）
```

### 1.2 关键决策（已确认）

| 决策 | 内容 |
|------|------|
| treeData 全局一份 | 值为**当前视图根路径**拉取的数据；切换视图 = 重拉 + 替换 |
| 视图的本质 | 只声明「数据源根路径 + title」两个标量；**无构建逻辑、无 UI 配置** |
| 虚拟目录在后端 | 后端按需构造虚拟目录结构（如 connector 分组）随 tree 返回，前端不构建、不感知 |
| UI 不变 | FileTreeContent / FileTreeHeader / FileTreeSearch 布局保持现状 |
| 操作按文件类型路由 | 文件节点带类型（前端按扩展名推断），操作分发到对应 Controller（ddl → TableController 等） |
| 加载禁用态 | 视图切换进入 loading，Content 禁用交互，数据回来才解锁（无竞态） |
| CRUD 局部刷新 | 删除/重命名/移动后只刷新操作节点的父目录，不整棵重拉 |
| 新增菜单合并 | 所有视图共享同一套「新增」菜单（文件/表/目录），不按视图区分 |
| expandedKeys 不持久化 | 切换视图不保留展开状态，后续有需要再做 |
| **新建统一行为** | 「+ → 选类型 → 弹窗输名称 → 创建 → 自动打开」（表/文件打开编辑器，目录不打开） |

### 1.3 文件类型路由

**文件类型来源**：前端按扩展名推断（`inferFileType`），后端暂不提供字段。

```
文件类型（fileType = 扩展名）
  ├── ddl → TableController API
  ├── sql → FileController（当前）/ SqlFileController（未来）
  └── 其他 → FileController
```

操作分发点：`services/fileTypeOps.ts` 注册表（`opsOf(fileType)`），加新类型只加注册项。

---

## 二、视图定义

| 视图 | 数据源根路径 | title | 说明 |
|------|------------|-------|------|
| Project | `''`（根） | Project | 默认视图 |
| Tables | `ddl/` | Tables | 替换现有 Tables 占位 |
| TableType | `ddl/`（后端按 connector 分组返回虚拟目录） | Tables(Type) | 虚拟目录由后端构造，前端同构渲染 |
| Workspace | `''`（根） | Workspace | 后续 |

**前端不感知虚拟目录**：三个视图都是「拉 rootPath + 显示 title」，完全同构。
`FileTreeNode.isVirtual` 标记（若后端下发）由 `toTreeDataNode` 透传即可，前端无构建逻辑。

---

## 三、要改动的文件

### 后端（后续，本次范围外）

| 文件 | 改动 |
|------|------|
| `FileTreeNode.java` | 可选：增加 `isVirtual` 标记（虚拟目录节点） |
| 树服务 | 可选：按需构造虚拟目录（如 connector 分组） |

### 前端（核心）

| 文件 | 改动 | 类型 |
|------|------|------|
| `models/fileTree.ts` | 扩展为统一 treeModel：支持 rootPath 切换重拉、局部刷新、loading 禁用态 | ✅ 完成 |
| `types/file.d.ts` | `FileTreeNode` 增加 `fileType`；新增 `FileTreeDataNode`（含 data） | ✅ 完成 |
| `services/fileTypeOps.ts` | 操作按 fileType 路由的注册表 | ✅ 完成 |
| `services/table.ts` | Table API（createTable/saveTable/deleteTable 等，fileId 主键） | ✅ 完成 |
| `services/fileTree.ts` | 数据加载/操作 API（已有，无需改） | 无 |
| `pages/dev/tree/FileTree.tsx` | 接收视图配置（rootPath + title），去掉硬编码 | 改 |
| `pages/dev/panels/ProjectPanel.tsx` | 配置表驱动三视图切换 | 改 |

---

## 四、实施计划（Checkpoint）

### CP1: 全局 treeModel 支持视图切换 ✅

- `models/fileTree.ts` 新增 `switchTreeView(rootPath)` —— 重拉 + 替换 treeData + loading 禁用
- 局部刷新保持现有 `updateTreeData` 逻辑
- 类型：`FileTreeDataNode`（含 data 原始节点）
- 验证：Project/Table 手动切换能正确加载不同根

### CP2: 文件类型 + 操作路由 ✅

- `FileTreeNode` 增加 `fileType`（扩展名）
- 新建 `services/fileTypeOps.ts` 注册表 + `services/table.ts`
- `deleteNode` / `createFileNode` 按类型路由
- 验证：ddl 文件删除走 TableController

### CP3: 视图配置化

- `FileTree.tsx` 接收视图配置（rootPath + title）
- `ProjectPanel.tsx` 用配置表驱动三视图切换
- 验证：三视图切换正常，title 正确

### CP4: 清理（原虚拟目录构建任务取消）

- 移除废弃代码（旧的 Tables 占位）
- 全量 `tsc --noEmit` + 手动回归

---

## 五、明确不做（本次范围外）

- expandedKeys 持久化（切换视图不记忆展开状态）
- 虚拟目录前端构建（后端构造，前端不感知）
- WorkspaceView（后续加，配置化后成本极低）
- 多视图 treeData 缓存（切换即重拉，不缓存旧视图数据）
- 血缘视图、Job 视图（未来扩展）

---

## 六、风险与对策

| 风险 | 对策 |
|------|------|
| 视图切换加载间隙白屏 | loading 禁用态 + 首屏 spinner（FileTreeContent 已有） |
| 后端虚拟目录下发延迟 | 前端不受影响（只按 rootPath 拉数据），后端完成后即生效 |
| fileType 前端推断与后端不一致 | 后续需要时后端加字段，前端 `?? 推断` 兼容 |
