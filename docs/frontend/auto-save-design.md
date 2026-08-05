# 自动保存概要设计

## 1. 目标

- 用户编辑后停手 1.5 秒自动保存，无需手动 Ctrl+S
- 1MB 大文件 keystroke 路径零开销
- undo 回原位自动消除 dirty 标记（300ms idle 检查）
- 浏览器意外关闭时保护未保存内容
- 未持锁文件不自动保存
- 切换/关闭 tab 时自动保存已持锁文件

## 2. 核心概念

### 2.1 dirty 标记

| 事件 | dirty 变化 | 开销 |
|---|---|---|
| keystroke | 立即置 `true` | O(1)，仅写一个 boolean |
| idle 300ms | `doc.eq(baseline)` → 相等则清 `false` | B-tree 节点级比较，不做字符串分配 |
| undo 回原位 | idle 300ms 后 `eq()` 返回 true，自动清零 | 共享 B-tree 节点，几乎零开销 |
| auto-save 成功 | 清 `false` | — |
| auto-save 失败（锁丢失等） | 保持 | — |

保留 300ms idle 检查的理由：tab 上的 dirty ● 圆点希望在 undo 后尽快消失，而非等 1.5s 保存时才更新。

### 2.2 baseline

`baselineDocs: Record<number, Text>` — CM6 `Text` 对象，不是字符串。语义上永远等于"后端已落盘的内容"。

- **初始化**：打开文件加载内容后 `baselineDocs[id] = Text.of(content.split(/\r?\n/))`
  - `Text.of` 接收字符串数组（每行一个元素），而非单个字符串
  - 用 `/\r?\n/` 拆分可同时兼容后端返回的 `\n` 与 `\r\n`，避免 baseline 与 CM6 内部 doc 的换行表示不一致导致 `eq()` 永远为 false
- **更新**：仅保存成功时更新。发送前保存 snapshot 引用，成功后 `baselineDocs[id] = snapshot`
- **比较**：用 `Text.eq()` 而非 `toString()` + `===`。Text 是持久化 B-tree 结构，同内容共享节点，`eq()` 遇到相同节点直接返回 true

```
为什么用 snapshot 而非 state.doc？

保存 API 是异步的。API 返回成功时 state.doc 可能已被用户后续编辑改变。
如果直接用 state.doc 更新 baseline，baseline 就会"超前"于后端实际内容。

正确做法：
    const snapshot = view.state.doc;         // 发送时刻的 Text 引用
    await saveContent(fileId, snapshot.toString());
    baselineDocs[fileId] = snapshot;         // baseline = 后端已收到的内容
```

### 2.3 saveGeneration 竞态控制

```
auto-save 触发 → gen = ++saveGeneration[fileId]
                 → snapshot = state.doc
                 → 发 saveContent API
用户继续编辑   → gen = ++saveGeneration[fileId]（gen 改变）
API 返回       → 检查 gen === 当前 saveGeneration[fileId]？
                 → 不相等 → 丢弃结果，不更新 baseline/dirty
                 → 相等   → baseline = snapshot, dirty = false
```

防止慢速网络下旧保存结果覆盖用户正在编辑的内容的 dirty 状态。

## 3. 保存触发时机

| 触发条件 | 行为 |
|---|---|
| 用户停手 1.5s + dirty + 持锁 | auto-save |
| Ctrl+S | 立即保存（`force=true`，忽略 `eq()` 检查） |
| 切换 tab | 旧 tab dirty + 持锁 → 调用 `saveTab` 保存 |
| 关闭 tab | dirty + 持锁 → 调用 `saveTab` 保存后关闭；未持锁 → `Modal.confirm` 确认"关闭将丢弃修改" |
| 未持锁（只读） | 不触发 auto-save；Ctrl+S 提示"文件只读，请先抢锁" |
| `autoSave` 入口 `snapshot.eq(baseline)` 为 true | 跳过保存，避免无效请求 |

## 4. beforeunload 保护

任意 tab dirty → `window.addEventListener('beforeunload', e => e.preventDefault())` → 浏览器弹出标准确认框。

auto-save 1.5s 本身就是第一道防线：停手超过 1.5 秒内容已落盘，beforeunload 不拦截。

不做 localStorage 兜底——多文件内容量大、与后端一致性难保证，现有机制已覆盖绝大多数场景。

## 5. 数据流

```
用户编辑
  → updateListener → setDirtyFlags((prev) => ({ ...prev, [fileId]: true }))
  → 重置 idle-timer
     │
     ├─ idle 300ms → checkClean(fileId)
     │    → state.doc.eq(baselineDoc) ? dirty = false : 不变
     │
     └─ idle 1.5s → onSave(fileId, snapshot)
          → autoSave(fileId, snapshot)
             → 未持锁 ? return false
             → !force && snapshot.eq(baseline) ? return false
             → gen = ++saveGeneration[fileId]
             → saveContent(fileId, snapshot.toString())
             → 成功 + gen 未过期 → baseline = snapshot, dirty = false
             → 失败 + 锁丢失   → 切只读

Ctrl+S       → autoSave(..., force=true)
切换 tab     → 旧 tab dirty + 持锁 → saveTab() → autoSave(...)
关闭 dirty tab → 持锁 → saveTab() → autoSave(...) 后关闭
关闭 dirty tab → 未持锁 → Modal.confirm("关闭将丢弃修改") 后关闭
```

### 关于判脏的两层职责

- **300ms `checkClean`**：只为 UI 服务，undo 回原位后尽快让 dirty 圆点消失。
- **`autoSave` 入口 `snapshot.eq(baseline)`**：统一拦截无效保存请求，覆盖自动保存、切换/关闭 tab 等所有入口。

`autoSave` 增加 `force` 参数：
- `force=false`（自动保存、切换/关闭 tab）：先检查 `snapshot.eq(baseline)`，clean 则跳过。
- `force=true`（`Ctrl+S`）：用户显式触发，无论是否 clean 都发一次请求。

## 6. 改动清单

| 文件 | 改动 |
|---|---|
| `web/src/models/editor.ts` | 增加 `baselineDocs`、`dirtyFlags`、`saveGenerationRef`；提供 `markDirty`、`checkClean`、`autoSave`、`beforeunload`；`loadFile` 增加失败提示 |
| `web/src/pages/dev/editor/autoSaveExtension.ts` | **新增**：独立 CodeMirror 6 扩展，封装 `updateListener` + idle timer，通过 Facet 注入回调，便于分屏等多编辑器实例复用 |
| `web/src/pages/dev/editor/CodeEditor.tsx` | 接入 `autoSaveExtension`；`Ctrl+S` 调 `autoSave(force=true)`；通过 `useImperativeHandle` 暴露 `saveTab` |
| `web/src/pages/dev/editor/ReadOnlyBanner.tsx` | **新增**：只读提示横幅 + 抢锁按钮 |
| `web/src/pages/dev/editor/EditorTab.tsx` | 接收 `dirtyFlags`，未保存 tab 显示 ● 圆点 |
| `web/src/pages/dev/panels/EditorPanel.tsx` | 组合 `Index` / `ReadOnlyBanner` / `CodeEditor`；切换/关闭 tab 前保存；关闭未持锁 dirty tab 时弹确认 |
| `web/src/pages/dev/editor/index.css` | 增加未保存圆点样式 |

## 7. 运行成本与性能分析

### 7.1 热路径：keystroke

用户每敲一次键，CodeMirror 触发 `updateListener`：

```ts
if (!update.docChanged) return;
const fileId = this.activeFileId(update.view);
this.config.onDirty(fileId);   // O(1) setState
this.schedule(fileId, view);   // 2 clearTimeout + 2 setTimeout
```

| 操作 | 成本 |
|---|---|
| `update.docChanged` | CM6 内部已有，零额外开销 |
| `onDirty` | 一次 React state 更新，仅写 boolean |
| `schedule` | 4 次原生 timer 调用 |

**结论**：keystroke 路径不做任何 `toString()`、`Text.eq()` 或网络请求，1MB 文件与 1KB 文件成本相同，符合设计目标。

### 7.2 Idle 检查：300ms `checkClean`

```ts
const baseline = baselineDocs[fileId];
if (baseline && doc.eq(baseline)) { dirty = false; }
```

| 场景 | `Text.eq()` 成本 |
|---|---|
| undo 回原位 | **O(1)**：共享 B-tree 根节点直接相等 |
| 少量修改 | O(log n) 或 O(少量节点) |
| 大文件且内容全不同 | O(节点数)，但 300ms 触发频率低，且非 keystroke 路径 |

### 7.3 保存路径：1.5s `autoSave`

```ts
if (!isFileEditable(fileId)) return false;
if (!force && snapshot.eq(baseline)) return false;
const gen = ++saveGenerationRef.current[fileId];
await saveContent(fileId, snapshot.toString());
```

| 操作 | 成本 | 说明 |
|---|---|---|
| `isFileEditable` | O(tabs) | tabs 数量通常很小 |
| `snapshot.eq(baseline)` | O(共享节点数) | 最坏遍历全部节点 |
| `snapshot.toString()` | O(n) + 字符串分配 | **保存路径最大内存开销**，与文件大小成正比 |
| `saveContent` | 网络 + 后端写盘 | 无法避免 |
| `setBaselineDocs` / `setDirtyFlags` | React state 更新 | — |

### 7.4 内存占用

| 状态 | 说明 |
|---|---|
| `baselineDocs` | 每个打开文件的 CM6 `Text` 对象（已落盘基线） |
| `dirtyFlags` | boolean map |

当前编辑 doc 由 CodeMirror 自身维护，不额外保存字符串副本；需要字符串时临时 `toString()`。`Text` 使用持久化 B-tree 共享节点，但当用户编辑后，当前 doc 与 baseline 会分叉，各自持有不同节点。

### 7.5 风险与建议

| 风险 | 说明 | 建议 |
|---|---|---|
| 大文件 `toString()` | 保存时全量转字符串 | 当前 API 需要字符串，暂无法避免；超大文件可考虑流式保存 |
| baseline 职责重叠 | `baselineDocs` 同时用于编辑器加载来源和已落盘基线 | 已通过 `lastLoadedTabRef` 限制只在 tab 切换时加载；后续如需进一步解耦可考虑拆分加载来源与基线 |
| Timer churn | 每次 keystroke 都 clear + set | 开销极小，可忽略 |

### 7.6 总体评估

当前方案在 **keystroke 热路径上零重量级操作**，主要性能开销集中在**保存时刻的 `toString()` 与网络请求**，这是自动保存机制本身不可避免的，现有实现已将通过 `Text.eq()` 判脏、saveGeneration 竞态控制等手段将额外开销降至最低。

## 8. 问题与修复

### 2026-07-29

#### 问题 1：切换/打开 tab 时出现短暂 dirty 圆点

**现象**：首次打开文件或切换到不同 tab 时，tab 上会出现短暂未保存圆点，即使用户没有修改任何内容。

**原因**：`autoSaveExtension` 的 `update` 在每次 `docChanged` 为 true 时都会调用 `onDirty` 置脏。`CodeEditor.tsx` 在加载/切换 tab 时会主动 dispatch 内容到 CodeMirror，这次程序化 dispatch 同样触发 `docChanged`，导致被误判为用户编辑。300ms 后 `checkClean` 发现 `doc.eq(baseline)` 才清掉脏点。

**修复**：
- `CodeEditor.tsx` 主动 dispatch 内容时标注 `Transaction.userEvent.of('programmatic')`。
- `autoSaveExtension` 中检查 `update.transactions`，若全部为 `'programmatic'` 则跳过 `onDirty`，避免加载/切换 tab 时误标脏点。

#### 问题 2：保存期间用户继续输入可能导致内容被覆盖

**现象**：auto-save 是异步请求，若用户在请求返回前继续输入，保存成功后 `baselineDocs` 更新会触发 `CodeEditor.tsx` 的 effect 重新 dispatch，可能覆盖用户的新输入。

**原因**：`baselineDocs` 同时承担"编辑器加载来源"和"已落盘基线"两个职责。保存成功后 baseline 更新被 effect 感知，当作新 tab 加载处理，从而把旧 snapshot 写回编辑器。

**修复**：在 `CodeEditor.tsx` 中引入 `lastLoadedTabRef`，仅在 `activeTabId` 真正变化时才 dispatch；保存导致的 `baselineDocs` 更新不再触发内容回写。
