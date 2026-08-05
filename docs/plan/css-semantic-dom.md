# CSS Semantic DOM 规范化计划

## 背景

antd v6 引入 Semantic DOM，组件内部结构通过 `classNames` 和 `styles` 精确覆盖，避免依赖 `.ant-*` 内部类名随版本变化而破坏。

## 检查范围

4 个 CSS 文件，共 ~390 行，涉及 antd 组件：

| CSS 文件 | 涉及的 antd 组件 |
|---|---|
| `dev/index.css` | Button |
| `dev/tree/index.css` | Tree, Input, Button, Dropdown |
| `dev/editor/index.css` | Tabs |
| `dev/status/index.css` | —（纯自定义） |

> 注：`dev/index.css` 中的 `display: flex` 是 CSS 属性，不是 antd `Flex` 组件。

---

## 需优化的项

### 1. Input（FileTreeSearch）— P1

**当前**：CSS 直接覆盖 `.ant-input-affix-wrapper`、`.ant-input`、`.ant-input-prefix`

**官方 Semantic DOM**：`root`、`prefix`、`input`、`suffix`、`clear`（6.4.0）、`count`

> ⚠️ 不存在 `wrapper`，之前方案中的 `wrapper` 是凭空写的。

**应改为**：

```tsx
// FileTreeSearch.tsx
<Input
  classNames={{
    root: 'lt-filetree-search',
    input: 'lt-filetree-search-input',
    prefix: 'lt-filetree-search-prefix',
  }}
/>
```

**影响范围**：`tree/index.css` 4 条规则，`FileTreeSearch.tsx`

---

### 2. Tree（FileTreeContent）— P1

**官方 Semantic DOM（仅 5 个 key）：**

| 官方 Key | 版本 | 说明 |
|---|---|---|
| `root` | 6.0.0 | 根元素 |
| `item` | 6.0.0 | treenode 整行（含缩进、切换器、内容） |
| `itemIcon` | 6.0.0 | 节点图标 |
| `itemTitle` | 6.0.0 | 节点标题文字 |
| `itemSwitcher` | 6.4.0 | 展开/收起按钮 |

> ⚠️ 之前方案中的 `indentUnit`、`switcher`、`nodeContent`、`icon`、`title` 均为凭空创造，官方不存在。

**应改为**：

```tsx
// FileTreeContent.tsx
<Tree
  classNames={{
    root: 'lt-filetree-tree',
    item: 'lt-filetree-treenode',
    itemIcon: 'lt-filetree-icon',
    itemTitle: 'lt-filetree-title',
    itemSwitcher: 'lt-filetree-switcher',
  }}
/>
```

**⚠️ 无法用 classNames 覆盖，需保留 `.ant-*` 选择器**：

| 选择器 | 原因 |
|---|---|
| `.ant-tree-indent-unit` | 无 Semantic DOM key |
| `.ant-tree-node-content-wrapper` | 无 Semantic DOM key；item 覆盖的是 treenode 整行 |
| `:hover`/`:selected` 等状态类 | antd 内部仍附加，组合选择器需保留 |

**影响范围**：`tree/index.css` 12 条规则，`FileTreeContent.tsx`

---

### 3. Tabs（Index）— P2（半完成）

**官方 Semantic DOM**：`root`、`header`、`item`、`remove`（6.4.0）、`indicator`、`body`、`content`、`popup.root`

`classNames.root`、`classNames.header`、`classNames.item` 已在 `index.tsx` 中设置 ✅

**遗留 CSS**：`header` 就是 nav 容器，可用它消掉 `.ant-tabs-nav` 引用。

---

### 4. Button（Sidebar / FileTree Header / FileTree More）— P3（低优先）

**当前**：以下 Button 均用双类名提高特异性并配合 `!important` 覆盖背景：

| 位置 | CSS 选择器 | 组件位置 |
|---|---|---|
| 左右侧边栏 | `.lt-sidebar-btn.lt-sidebar-btn` | `leftSidebar/LeftSidebar.tsx`、`rightSidebar/RightSidebar.tsx` |
| 文件树顶部操作栏 | `.lt-filetree-header-btn.lt-filetree-header-btn` | `tree/FileTreeHeader.tsx` |
| 文件树节点更多按钮 | `.lt-filetree-more-btn.lt-filetree-more-btn` | `tree/FileTreeContent.tsx` |

这些写法都没有直接依赖 `.ant-btn` 类名，但使用了 `!important` 覆盖 antd 背景。Button 的 Semantic DOM 有 `root`，理论上可改用 `classNames={{ root: '...' }}` 让选择器更干净，但 `!important` 的问题本质是 theme token 在当前版本不支持我们需要的背景色，不是类名问题。

**建议**：P3 暂不动。双类名已经隔离了对 `.ant-btn` 内部类名的依赖；如果后续 antd token 能覆盖所需背景，再统一改为 `classNames.root`。

---

## 不需要动的

| CSS 文件 | 内容 | 原因 |
|---|---|---|
| `dev/index.css` | `.lt-sidebar-btn:hover` | 不用 `.ant-btn`，双类名已隔离 |
| `dev/index.css` | `.lt-panel-base`、`.lt-sidebar-*` | 纯自定义容器，不涉及 antd 组件 |
| `dev/tree/index.css` | `.lt-filetree-node-row` | 自定义包裹层 |
| `dev/tree/index.css` | `.lt-filetree-search-results` | 搜索结果是自定义列表 |
| `dev/tree/index.css` | `.lt-filetree-ctxmenu` | Dropdown 的 Semantic DOM popup 已是后加的，当前方案用 `rootClassName` 可行 |
| `dev/editor/index.css` | `.lt-editor-body .cm-*` | CodeMirror 不是 antd，有自己的 class 体系 |
| `dev/editor/index.css` | `.lt-editor-readonly-banner`、`.lt-editor-empty` | 纯自定义 |
| `dev/status/index.css` | 全部 | 纯自定义 |

> `.lt-filetree-more-btn` 虽然包裹在自定义 `lt-filetree-node-row` 内，但本身是 antd `Button`，已在上面第 4 项中统一说明。

---

## 执行顺序

| 序号 | 内容 | 优先度 | 预估改动行数 |
|---|---|---|---|
| 1 | Input Semantic DOM | P1 | ~10 行 CSS + 1 组件 |
| 2 | Tree Semantic DOM | P1 | ~15 行 CSS + 1 组件 |
| 3 | Tabs header 收尾 | P2 | ~2 行 CSS，组件已改 |
| 4 | Button `classNames.root` 统一（可选） | P3 | ~6 行 CSS + 3 组件 |

## 补充说明

- `index.tsx` 的 `classNames.header` 已设置，计划里标记为"半完成"的状态可更新为"CSS 待收尾"。
- Tree 的 selected/hover 状态类（如 `.ant-tree-treenode-selected`）由 antd 内部保留，迁移后组合选择器仍需引用这些状态类，不能只改基类前缀。
- Input 的 Semantic DOM key 以 `antd@^6.5.0` 实际导出为准，建议在动手前确认 `Input` 的 `classNames` 类型定义。
