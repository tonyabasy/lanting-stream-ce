# antd Token 使用评审报告

**评审范围**：`web/src/pages/dev/` 下全部 TSX 和 CSS 文件（共 17 个文件）  
**评审日期**：2025-07-21  
**评审方法**：按 SOP 五步法逐文件检查 — 判断角色 → 找参照 → 找规律 → 映射 Token → 定结论

---

## 快速核查（直接判定项）

| 检查项 | 结果 |
|---|---|
| `var(--lt-xxx)` 残留 | ✅ 无 |
| `useModel('theme')` 残留 | ✅ 无 |
| 页面内重复 `<ConfigProvider>` | ✅ 无 |

---

## 🔴 严重（4 个）

### 1. 页面底板背景色语义错误

**[🔴] `index.tsx:23`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 整个 Dev 页面（IDE 布局）的最底层背景 |
| **当前** | `background: token.colorFillTertiary` |
| **核心参照** | antd Token 体系定义：`colorFillTertiary` = Segmented/Slider 轨道填充色（rgba 0,0,0,0.04），`colorBgLayout` = 页面底板 |
| **交叉验证** | JetBrains IDEA、VSCode、Figma 的主布局均使用页面底板色（非组件填充色） |
| **问题** | `colorFillTertiary` 的语义是「组件内部非容器填充」，用在页面级背景会导致暗色模式等主题切换时表现异常 |
| **建议** | 改为 `token.colorBgLayout` |
| **映射** | MapToken → `colorBgLayout` |

---

### 2. 边栏激活按钮背景硬编码

**[🔴] `index.css:28`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 左侧/右侧边栏图标按钮的激活态（选中态）背景 |
| **当前** | `background-color: rgb(0 0 0 / 12%)` |
| **核心参照** | antd `controlItemBgActive` — 「控件项 active（选中）」 |
| **交叉验证** | IDEA 侧边栏激活图标背景 ≈12% 黑色半透明，与 `controlItemBgActive` 等效 |
| **建议** | 改为 `background-color: var(--ant-control-item-bg-active)` |
| **映射** | MapToken → `controlItemBgActive` |

---

### 3. CSS 语法错误：`font-weight: var(500)`

**[🔴] `index.css:72`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 文件树标题「SQL 文件」的字重 |
| **当前** | `font-weight: var(500)` |
| **问题** | `var(500)` 是无效 CSS — `var()` 只接受 CSS 自定义属性名（如 `--xxx`），不接受数值。浏览器会静默丢弃该声明，字重回退为 400 |
| **核心参照** | antd 无 `fontWeightMedium`(500) token，SOP 明确「需要 500 时写死 `font-weight: 500`」 |
| **建议** | 改为 `font-weight: 500` |
| **映射** | 无 token 覆盖 — 写数字合规 |

---

### 4. 搜索结果空态文字硬编码颜色

**[🔴] `FileTreeContent.tsx:182`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 文件树搜索无结果时的提示文字「未找到匹配的文件」 |
| **当前** | `style={{ color: '#999' }}` |
| **核心参照** | antd `colorTextDescription` → `colorTextTertiary`，rgba(0,0,0,0.45)，即 ≈#8c8c8c；`#999` 比标准描述色略浅 |
| **交叉验证** | IDEA/VSCode 空态提示用次要/描述色，非自定义色值 |
| **建议** | 移除 inline style，改用 CSS class 设置 `color: var(--ant-color-text-description)`；或至少改为 `token.colorTextDescription` |
| **映射** | AliasToken → `colorTextDescription` |

---

## 🟡 警告（9 个）

### 5. SVG 图标描边宽度略超参照范围

**[🟡] `index.css:35`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 侧边栏图标 SVG stroke-width |
| **当前** | `stroke-width: 1.6px` |
| **核心参照** | JetBrains IDEA: 1.2~1.4px |
| **交叉验证** | VSCode: 1.5px |
| **建议** | 改为 `1.4px` 或 `1.5px`，与参照产品对齐 |
| **映射** | 无 token 覆盖 — 但数值应参照成熟产品 |

---

### 6. 文件树标题字号偏离梯度

**[🟡] `index.css:70`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 文件树 Header 标题「SQL 文件」 |
| **当前** | `font-size: 11px` |
| **核心参照** | antd 字号梯度：`fontSizeSM`(12px) / `fontSize`(14px) |
| **建议** | 改为 `var(--ant-font-size-sm)` (12px)。注释中称设计稿为 11px，可评估是否统一切换 |
| **映射** | MapToken → `fontSizeSM` |

---

### 7. 搜索框圆角偏离梯度

**[🟡] `index.css:95`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 文件树搜索输入框 |
| **当前** | `border-radius: 5px` |
| **核心参照** | antd 圆角梯度：`borderRadiusSM`(4px) / `borderRadius`(6px) |
| **建议** | 改为 `var(--ant-border-radius)` (6px) 或 `var(--ant-border-radius-sm)` (4px)。5px 在梯度之外 |
| **映射** | MapToken → `borderRadius` |

---

### 8. 搜索框输入文字字号偏离梯度

**[🟡] `index.css:103`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 文件树搜索框输入文字 |
| **当前** | `font-size: 11px` |
| **核心参照** | antd 字号梯度：`fontSizeSM`(12px)，SOP 明确「搜索框字号与正文一致，用 fontSize(14px)，占位符 fontSizeSM(12px) 也常见」 |
| **建议** | 改为 `var(--ant-font-size-sm)` (12px) |
| **映射** | MapToken → `fontSizeSM` |

---

### 9. 右键菜单图标字号偏离梯度

**[🟡] `index.css:246`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 文件树右键菜单项图标（`.ant-dropdown-menu-item-icon`） |
| **当前** | `font-size: 13px` |
| **核心参照** | antd 字号梯度：`fontSizeSM`(12px) / `fontSize`(14px) |
| **附加说明** | 当前菜单图标实际使用 Tabler SVG 组件（`size={CTX_ICON_SIZE}` = 14px），此 CSS 规则对 SVG 图标无效，实际是**死代码** |
| **建议** | 删除该声明，或改为 `var(--ant-font-size-sm)` 以备将来使用 antd 图标字体 |
| **映射** | MapToken → `fontSizeSM`（如保留） |

---

### 10. 搜索结果项字号偏离梯度

**[🟡] `index.css:263`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 文件树搜索结果列表项 |
| **当前** | `font-size: 11px` |
| **核心参照** | antd 字号梯度：`fontSizeSM`(12px) |
| **建议** | 改为 `var(--ant-font-size-sm)` (12px) |
| **映射** | MapToken → `fontSizeSM` |

---

### 11. 搜索结果路径字号偏离梯度

**[🟡] `index.css:290`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 搜索结果项的父路径文字（辅助信息） |
| **当前** | `font-size: 10px` |
| **核心参照** | antd 最小字号梯度为 `fontSizeSM`(12px) |
| **建议** | 改为 `var(--ant-font-size-sm)` (12px)。如设计确实需要 10px，至少统一用 CSS 变量标记意图 |
| **映射** | MapToken → `fontSizeSM` |

---

### 12. 底部状态栏字号偏离梯度

**[🟡] `index.css:386`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 底部状态栏整体文字（面包屑、锁状态） |
| **当前** | `font-size: 10px` |
| **核心参照** | antd 字号梯度：`fontSizeSM`(12px) |
| **交叉验证** | JetBrains IDEA 状态栏：12px；VSCode 状态栏：12px。两款产品均不使用 10px |
| **建议** | 改为 `var(--ant-font-size-sm)` (12px) |
| **映射** | MapToken → `fontSizeSM` |

---

### 13. 文件树搜索框高度 hardcode

**[🟡] `index.css:94-95`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 搜索输入框容器 `.ant-input-affix-wrapper` |
| **当前** | `height: 26px` |
| **核心参照** | antd 控件高度梯度：`controlHeightSM`(24px) / `controlHeight`(32px)。26px 不在梯度内 |
| **建议** | 改为 `var(--ant-control-height-sm)` (24px) 与紧凑控件统一，或保留 26px 并注释说明原因 |
| **映射** | MapToken → `controlHeightSM` |

---

## 🔵 建议（15 个）

### 14. 边栏图标尺寸偏离参照

**[🔵] `index.css:33-34`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 侧边栏工具图标（置入 24×24 容器） |
| **当前** | 图标 20×20（CSS `.lt-sidebar-icon { width: 20px; height: 20px; }`） |
| **核心参照** | JetBrains IDEA：16×16 |
| **交叉验证** | VSCode：16×16；Figma：16×16。三方完全一致 |
| **建议** | 改为 `16px × 16px`，视觉上更精致、与主流 IDE 一致 |
| **映射** | 无 token 覆盖 — 写数字合规，但值应对齐参照 |

---

### 15. 🆕 树节点选中/悬停态 token 语义可更精准

**[🔵] `index.css:138-143`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 文件树节点 hover 与 selected 态背景 |
| **当前** | 两者均用 `var(--ant-color-fill)` |
| **问题** | `colorFill` 是 Slider hover 态语义（最深填充色，rgba 0,0,0,0.15），而树节点选中是「控件激活」语义 |
| **核心参照** | antd `controlItemBgHover` / `controlItemBgActive` |
| **建议** | hover 用 `var(--ant-control-item-bg-hover)`，selected 用 `var(--ant-control-item-bg-active)` |
| **映射** | MapToken → `controlItemBgHover` / `controlItemBgActive` |

---

### 16. 树节点缩进宽度可引用 token

**[🔵] `index.css:146`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 文件树每级缩进单位 `.ant-tree-indent-unit` |
| **当前** | `width: 12px` |
| **核心参照** | antd 间距梯度：`sizeSM`(12px) |
| **建议** | 改为 `var(--ant-size-sm)`，保持与间距体系一致 |
| **映射** | MapToken → `sizeSM` |

---

### 17. 右键菜单容器圆角可引用 token

**[🔵] `index.css:226`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 右键菜单容器 `.ant-dropdown-menu` |
| **当前** | `border-radius: 6px` |
| **核心参照** | antd 圆角梯度：`borderRadius`(6px) |
| **建议** | 改为 `var(--ant-border-radius)` |
| **映射** | MapToken → `borderRadius` |

---

### 18. 右键菜单项字号可引用 token

**[🔵] `index.css:233`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 右键菜单项文字 |
| **当前** | `font-size: 12px` |
| **核心参照** | antd 字号梯度：`fontSizeSM`(12px) |
| **建议** | 改为 `var(--ant-font-size-sm)` |
| **映射** | MapToken → `fontSizeSM` |

---

### 19. 右键菜单项圆角可引用 token

**[🔵] `index.css:235`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 右键菜单项 |
| **当前** | `border-radius: 4px` |
| **核心参照** | antd 圆角梯度：`borderRadiusSM`(4px) |
| **建议** | 改为 `var(--ant-border-radius-sm)` |
| **映射** | MapToken → `borderRadiusSM` |

---

### 20~22. 三处 CSS 变量包含不必要的 fallback

**[🔵] `index.css:283`** — `var(--ant-color-warning-bg, #fff3cd)`  
**[🔵] `index.css:311`** — `var(--ant-color-split, rgba(0,0,0,0.06))`  
**[🔵] `index.css:341-342`** — 同上两处（编辑器只读提示条）

| 维度 | 内容 |
|---|---|
| **问题** | cssVar 模式下，antd 已确保所有 `--ant-*` 变量存在，fallback 值永远不会被使用，属于冗余代码 |
| **建议** | 移除 fallback，改为 `var(--ant-color-warning-bg)` 和 `var(--ant-color-split)` |

---

### 23. 编辑器只读提示条字号可引用 token

**[🔵] `index.css:339`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 编辑器只读提示条文字 |
| **当前** | `font-size: 12px` |
| **建议** | 改为 `var(--ant-font-size-sm)` |
| **映射** | MapToken → `fontSizeSM` |

---

### 24~25. 状态栏/面包屑 `gap` 值不在间距梯度上

**[🔵] `index.css:361`** — `.lt-statusbar-breadcrumb { gap: 3px; }`  
**[🔵] `index.css:384`** — `.lt-statusbar { gap: 3px; }`

| 维度 | 内容 |
|---|---|
| **UI 角色** | 状态栏内元素间距（面包屑分隔、锁状态等） |
| **当前** | `gap: 3px` |
| **核心参照** | antd 间距梯度：最小档 `sizeXXS`(4px) |
| **建议** | 改为 `var(--ant-size-xxs)` (4px)。状态栏紧凑场景下 3→4px 的差异肉眼难以察觉 |

---

### 26. 状态栏右侧间距可引用 token

**[🔵] `index.css:370`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 状态栏右侧区域内部间距 |
| **当前** | `gap: 12px` |
| **核心参照** | antd 间距梯度：`sizeSM`(12px) |
| **建议** | 改为 `var(--ant-size-sm)` |

---

### 27. TopBar 未使用的 token 解构

**[🔵] `TopBar.tsx:5`**

| 维度 | 内容 |
|---|---|
| **当前** | `const { token: t } = theme.useToken();` 但 `t` 在 JSX 中未被使用 |
| **建议** | 移除该行。如将来 TopBar 需要 token，再按需引入 |

---

### 28. 搜索结果空态 padding 可引用 token

**[🔵] `FileTreeContent.tsx:177,182`**

| 维度 | 内容 |
|---|---|
| **UI 角色** | 搜索加载中 / 空态容器的内边距 |
| **当前** | `style={{ padding: 16 }}` 和 `style={{ padding: 16, color: '#999' }}` |
| **建议** | 提取为 CSS class，padding 用 `var(--ant-padding)`，color 用 `var(--ant-color-text-description)` |

---

## 统计汇总

| 严重程度 | 数量 |
|---|---|
| 🔴 严重 | **4** |
| 🟡 警告 | **9** |
| 🔵 建议 | **15** |
| **合计** | **28** |

---

## 修复优先级建议

### 第一批（立即修复）
1. 🔴 `index.css:72` — CSS 语法错误，当前声明完全无效
2. 🔴 `FileTreeContent.tsx:182` — 硬编码颜色 `#999`
3. 🔴 `index.css:28` — 硬编码颜色 `rgb(0 0 0 / 12%)`
4. 🔴 `index.tsx:23` — 页面底板用了错误的填充色语义 token

### 第二批（本迭代修复）
5. 🟡 所有字号偏离梯度（5 处）— 统一为 antd 梯度值
6. 🟡 `index.css:95` — 圆角偏离梯度
7. 🟡 `index.css:35` — SVG stroke-width 略超参照
8. 🟡 `index.css:94` — 搜索框高度偏离控件高度梯度

### 第三批（可顺带清理）
9. 🔵 所有 CSS 变量 fallback 冗余（3 处）
10. 🔵 可引用 token 的硬编码值（圆角、字号、间距，约 8 处）
11. 🔵 `index.css:33-34` — 图标尺寸对齐 IDEA 标准
12. 🔵 `index.css:138-143` — 树节点状态 token 语义修正
13. 🔵 `TopBar.tsx:5` — 删未使用的 token 解构
14. 🔵 `index.css:246` — 死代码清理（SVG 图标不受 font-size 影响）

---

> **评审依据**：antd 三层 Token 体系（Seed → Map → Alias）+ 两级参照体系（核心参考 JetBrains IDEA + 交叉验证 VSCode / Figma）。  
> **未修改任何源代码文件。**
